package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.RazonFin;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoLlegadaPedido;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoVueloLlegada;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoVueloSalida;
import pe.edu.pucp.inf.pddsbackend.utils.RelojEnganado;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
public class EjecutorSimulacion {

    private final ExecutorService hiloEjecutor = Executors.newSingleThreadExecutor();
    private final PlanificacionService planificacionService;
    private final SimulacionRepository simulacionRepo;

    public Future<?> startSimulation(
            Simulacion simulacionEntidad, SimulacionRequestDTO params,
            ConfiguracionParametrosSistemaDinamicos config, RealizarPlanificacionDTO dataBasePlanificacion) {
        return hiloEjecutor.submit(() -> {
            // 1. construir snapshot inicial (deep copy)
            ContextoSimulacion ctx = construirContexto( params, config, dataBasePlanificacion); // NO SÉ QUÉ HACER A PARTIR DE AQUÍ.
             // esto ya hace ctx.setScheduler(this) en el constructor
            MotorSimulacion motor = new MotorSimulacion(ctx);
            ctx.setScheduler(motor); // cuidao con los cíclicos
//            // 2. poblar eventos iniciales (OrderArrivalEvent, FlightArrivalEvent, TriggerPlanificationEvent inicial)
            populateInitialEvents(motor, ctx, params);
//            // 3. Ejecutar (hasta el infinito a menos que sea semanal)
            Instant target = Instant.MAX;
            if(params.tipoSimulacion().equals(TipoSimulacion.SEMANAL)){
                target = ctx.getAhora().plus(Duration.ofDays(7));
            }
            motor.correrHasta(target, 10_000_000); // o control por tiempo
//            // 4. al terminar, generar PlanificationSolutionOutput y persistir resultados, metrics
            List<SalidaProblemaPlanificacion> planOut = ctx.getSolucionesAcumuladas(); // si recogiste soluciones
            simulacionEntidad.setFechaHoraFin(Instant.now());
            simulacionEntidad.setRazonFin(RazonFin.NATURAL); // no colapso Fin Normal
            return planOut;
        });
    }

    public ContextoSimulacion construirContexto(SimulacionRequestDTO params, ConfiguracionParametrosSistemaDinamicos config,
                                                RealizarPlanificacionDTO dataBasePlanificacion) {

        EntradaProblemaPlanificacion dataEntradaPrimerEstadoGlobal =  planificacionService.obtenerDatosParaAlgoritmo(dataBasePlanificacion);
        Clock relojEnganado = new RelojEnganado(Instant.now(),
                config.getFactorDeVelocidad()!=null?config.getFactorDeVelocidad():60, ZoneId.of("UTC"));

        return ContextoSimulacion.builder()
                .reloj( relojEnganado)
                .ahora( relojEnganado.instant() )
                .estadoGlobal(EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(dataEntradaPrimerEstadoGlobal))
                .params(params)
                .formaRealizarPlanificacion(dataBasePlanificacion)
                .build();
    }

    private void populateInitialEvents(MotorSimulacion motor, ContextoSimulacion ctx , SimulacionRequestDTO params){
        // poblar eventos:
        for (PedidoParaAlgoritmo p : ctx.getEstadoGlobal().getPedidos().values()) {
            motor.programar(new EventoLlegadaPedido(p.getId(), UUID.randomUUID(), p.getInstanteRegistro()));
        }
        for (VueloParaAlgoritmo v : ctx.getEstadoGlobal().getVuelos().values()) {
            motor.programar(new EventoVueloSalida(v.getId(),  UUID.randomUUID(),v.getInicio()));
            motor.programar(new EventoVueloLlegada( v.getId(), UUID.randomUUID(),v.getFin()));
        }
// trigger inicial
        motor.programar(null ); //???
    }
}
