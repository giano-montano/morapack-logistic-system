package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
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
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

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
        // 1.a Guardar/obtener config y sim en transacciones cortas
        ConfiguracionParametrosSistemaDinamicos config = configuracionService.crearYAsegurarConfig(params);
        Simulacion saved = crearSimulacionPreliminar(params);

        String nombreSubCarpeta = "Simulación_"+saved.getId()+"_"+LocalDateTime.now().format(TS_FMT);
        RealizarPlanificacionDTO realizarPlanificacionDTO = RealizarPlanificacionDTO.builder()
                .idSimulacion(saved.getId())
                .estrategiaFija(config.getUsarPlanificacionRapida()? EstrategiaFija.RAPIDA: EstrategiaFija.PROFUNDA)
                .seed(params.seed()!=null? params.seed() : new Random().nextLong())
                .subCarpetaReportes(nombreSubCarpeta)
                .parametros(params.parametros())
                .usarModoMock(params.usarModoMock() != null && params.usarModoMock()) // Activar modo mock si se solicita
                .build();

//        try {
// Los errores los maneja el ejecutor a nivel simulación; nosotros nos quedamos con el objeto de negocio simulación
        ContextoSimulacion contextoSimulacionActualizado = ejecutorSimulacion.iniciarSimulacionAhora(saved, params, config, realizarPlanificacionDTO,nombreSubCarpeta)
                    .get();
//        }catch ()

        saved.setFechaHoraFin(Instant.now());
        if(contextoSimulacionActualizado == null) {
            return simulacionRepository.save(saved);
        }
            if(contextoSimulacionActualizado.isConError()){
                saved.setRazonFin(RazonFin.ERROR_INTERNO);
            }else{
                if(contextoSimulacionActualizado.isColapsado()){
                    saved.setRazonFin(RazonFin.POR_COLAPSO);
                }else{
                    saved.setRazonFin(RazonFin.NATURAL); // no colapso Fin Normal
                }
            }
            List<SalidaProblemaPlanificacion> planOut = contextoSimulacionActualizado.getSolucionesAcumuladas(); // si recogiste soluciones
            if(planOut!=null ){
                System.out.println("Número de soluciones acumuladas = " + planOut.size());
            }
        return simulacionRepository.save(saved);
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
    @Transactional
    protected void actualizarSimulacionFinal(Long simId, ContextoSimulacion ctx) {
        Simulacion sim = simulacionRepository.findById(simId).orElseThrow();
        sim.setFechaHoraFin(Instant.now());
        if (ctx != null) {
            if (ctx.isColapsado()) sim.setRazonFin(RazonFin.POR_COLAPSO);
            else if (ctx.isConError()) sim.setRazonFin(RazonFin.ERROR_INTERNO);
            else sim.setRazonFin(RazonFin.NATURAL);
        }
        simulacionRepository.save(sim);
    }
}
