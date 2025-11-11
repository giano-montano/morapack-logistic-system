package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.RazonFin;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.EjecutorSimulacion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport.TS_FMT;

@RequiredArgsConstructor
@Service
public class SimulacionServiceImpl implements SimulacionService {

    private final SimulacionRepository simulacionRepository;
//    PlanificacionService planificacionService;

    private final EjecutorSimulacion ejecutorSimulacion;
    private final ConfiguracionService configuracionService;


    @Override
    @Transactional
    public Simulacion iniciarSimulacionAhora(SimulacionRequestDTO params) throws ExecutionException, InterruptedException {
        // 1. Guardar/obtener config y sim en transacciones cortas
        ConfiguracionParametrosSistemaDinamicos config = configuracionService.crearYAsegurarConfig(params);
        Simulacion saved = crearSimulacionPreliminar(params);

        String nombreSubCarpeta = "Simulación_"+saved.getId()+"_"+LocalDateTime.now().format(TS_FMT);
        
        // ✅ CRÍTICO: Pasar la fecha de inicio de la simulación al DTO de planificación
        // Esto asegura que el algoritmo obtenga vuelos/pedidos desde la fecha correcta
        Instant fechaInicioSimulacion = params.fechaHoraInicioSimulacion() != null 
                ? params.fechaHoraInicioSimulacion() 
                : Instant.now();
        
        System.out.println("📅 Creando DTO planificación con fecha inicio: " + fechaInicioSimulacion);
        
        RealizarPlanificacionDTO realizarPlanificacionDTO = RealizarPlanificacionDTO.builder()
                .idSimulacion(saved.getId())
                .instanteActual(fechaInicioSimulacion) // ✅ Pasar fecha de inicio
                .instanteDesdeTomarPedidos(fechaInicioSimulacion) // NUEVO: para no tomar pedidos tan viejos
                .estrategiaFija(config.getUsarPlanificacionRapida()? EstrategiaFija.RAPIDA: EstrategiaFija.PROFUNDA)
                .seed(params.seed()!=null? params.seed() : new Random().nextLong())
                .subCarpetaReportes(nombreSubCarpeta)
                .parametros(params.parametros())
                .usarModoMock(params.usarModoMock() != null && params.usarModoMock()) // Activar modo mock si se solicita
                .build();

        // ✅ CRÍTICO: Ejecutar en segundo plano SIN bloquear con .get()
        // Esto permite devolver el ID inmediatamente al frontend
        Future<ContextoSimulacion> futureSimulacion = ejecutorSimulacion.iniciarSimulacionAhora(
                saved, params, config, realizarPlanificacionDTO, nombreSubCarpeta);
        
        // ✅ Procesar resultado en segundo plano (callback asíncrono)
        CompletableFuture.runAsync(() -> {
            try {
                ContextoSimulacion contextoSimulacionActualizado = futureSimulacion.get();
                actualizarSimulacionFinal(saved.getId(), contextoSimulacionActualizado);
            } catch (Exception e) {
                System.err.println("❌ Error en simulación ID " + saved.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // ✅ Devolver inmediatamente la entidad con ID para que el frontend se conecte al WebSocket
        return saved;
    }

    // CREA la simulacion y la persiste (TRANSACCIÓN CORTA)
    @Transactional
    protected Simulacion crearSimulacionPreliminar(SimulacionRequestDTO params) {
        Simulacion simulacion = Simulacion.builder()
                .tipo(params.tipoSimulacion())
                .fechaHoraInicio(Instant.now())
                .fechaHoraFin(null)
                .razonFin(null)
                .build();
        return simulacionRepository.save(simulacion);
    }

    // ACTUALIZA estado final de simulacion (TRANSACCIÓN CORTA)
//    @Transactional
    protected void actualizarSimulacionFinal(Long simId, ContextoSimulacion ctx) {
        Optional<Simulacion> simOpt = simulacionRepository.findById(simId);
        Simulacion sim = simOpt.orElse(Simulacion.builder().id(simId).build());

        sim.setFechaHoraFin(Instant.now());
        if (ctx != null) {
            if (ctx.isColapsado()) sim.setRazonFin(RazonFin.POR_COLAPSO);
            else if (ctx.isConError()) sim.setRazonFin(RazonFin.ERROR_INTERNO);
            else sim.setRazonFin(RazonFin.NATURAL);
        }
        simulacionRepository.save(sim);
    }
    
    @Override
    public boolean cancelarSimulacion(Long idSimulacion) {
        // 1. Cancelar el motor de simulación en ejecución
        boolean cancelado = ejecutorSimulacion.cancelarSimulacion(idSimulacion);
        
        if (cancelado) {
            // 2. Actualizar la BD marcando como POR_USUARIO (cancelada por usuario)
            Simulacion sim = simulacionRepository.findById(idSimulacion).orElse(null);
            if (sim != null) {
                sim.setFechaHoraFin(Instant.now());
                sim.setRazonFin(RazonFin.POR_USUARIO); // ✅ Usar POR_USUARIO en lugar de CANCELADA
                simulacionRepository.save(sim);
                System.out.println("✅ Simulación " + idSimulacion + " marcada como POR_USUARIO en BD");
            }
        }
        
        return cancelado;
    }
}
