package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.*;
import pe.edu.pucp.inf.pddsbackend.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.utils.RelojEnganado;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
    private final ConfiguracionService configuracionService;

//    public static int MINUTOS_INTERVALO_EJECUCION_ALGORITMO_EN_VIDA_REAL = 60;

    public Future<ContextoSimulacion> iniciarSimulacionAhora(
            Simulacion simulacionEntidad, SimulacionRequestDTO params,
            ConfiguracionParametrosSistemaDinamicos config, RealizarPlanificacionDTO dataBasePlanificacion,
            String nombreSubCarpeta) {
        return hiloEjecutor.submit(() -> {
            // 1. construir snapshot inicial (deep copy)
            ContextoSimulacion ctx = construirContexto( params, config, dataBasePlanificacion, nombreSubCarpeta);
            ctx.getEstadoGlobalSimuladoNoAlgoritmo().setLoggingReport(ctx.getReport());
            ctx.log(ctx.getEstadoGlobalSimuladoNoAlgoritmo().toString());
            ctx.getReport().setImprimirPorLogger(true); // para tmb ver con consola antes del reporte final archivo.
             // esto ya hace ctx.setScheduler(this) en el constructor
            MotorSimulacion motor = new MotorSimulacion(ctx);
//            ctx.setScheduler(motor); // cuidao con los cíclicos
//            // 2. poblar eventos iniciales (OrderArrivalEvent, FlightArrivalEvent, TriggerPlanificationEvent inicial)
            populateInitialEvents(motor, ctx, config,params);
//            // 3. Ejecutar (hasta el infinito a menos que sea semanal)
            Instant target = Instant.MAX;
            if(params.tipoSimulacion().equals(TipoSimulacion.SEMANAL)){
                target = ctx.getAhora().plus(Duration.ofDays(7));
            }
            motor.correrHasta(target, 10_000_000); // o control por tiempo
//            // 4. al terminar, generar PlanificationSolutionOutput y persistir resultados, metrics

            return ctx;
        });
    }

    public ContextoSimulacion construirContexto(SimulacionRequestDTO params, ConfiguracionParametrosSistemaDinamicos config,
                                                RealizarPlanificacionDTO dataBasePlanificacion, String nombreSubCarpeta) {

        EntradaProblemaPlanificacion dataEntradaPrimerEstadoGlobal =
                planificacionService.obtenerDatosParaAlgoritmo(dataBasePlanificacion); // solo por primera vez en BD
        Clock relojAEmplear = params.tipoSimulacion().equals(TipoSimulacion.TIEMPO_REAL)?
                Clock.systemUTC() : new RelojEnganado(Instant.now(), // su vaina default sino
                config.getFactorDeVelocidad() ,// sí o sí consigue su factor de velocidad, ntp. // todavía no hago que sea dinámico
                ZoneId.of("UTC"));
        LoggingReport loggingReport = new LoggingReport();
        loggingReport.setDirectory(nombreSubCarpeta);
        return ContextoSimulacion.builder()
                .reloj(relojAEmplear)
                .ahora( relojAEmplear.instant() )
                .estadoGlobalSimuladoNoAlgoritmo(
                        EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(dataEntradaPrimerEstadoGlobal))
                .params(params)
                .formaRealizarPlanificacion(dataBasePlanificacion)
                .report(loggingReport) // es una orquestación algo horrible y repetitiva, pero todo por la carpeta.
                .build();
    }

    private void populateInitialEvents(MotorSimulacion motor, ContextoSimulacion ctx,
                                       ConfiguracionParametrosSistemaDinamicos config, SimulacionRequestDTO params){
        // poblar eventos:
        for (PedidoParaAlgoritmo p : ctx.getEstadoGlobalSimuladoNoAlgoritmo().getPedidos().values()) {
            motor.programar(new EventoLlegadaPedido(p.getId(), UUID.randomUUID(), p.getInstanteRegistro()));
        }
        for (VueloParaAlgoritmo v : ctx.getEstadoGlobalSimuladoNoAlgoritmo().getVuelos().values()) {
            motor.programar(new EventoVueloSalida(v.getId(),  UUID.randomUUID(),v.getInicio()));
            motor.programar(new EventoVueloLlegada( v.getId(), UUID.randomUUID(),v.getFin()));
        }

        // CRÍTICO: Inicializar trigger periódico
        Duration intervaloPlanificacion = Duration.ofMinutes(
//                MINUTOS_INTERVALO_EJECUCION_ALGORITMO_EN_VIDA_REAL
                params.minutosRealesEntrePlanificaciones() != null ?
                        params.minutosRealesEntrePlanificaciones() : config.getMinutosRealesEntrePlanificaciones() // repetido xd
        );
        ctx.log("Intervalo planificacion minutos: " + intervaloPlanificacion.toMinutes());
        // Primer trigger inmediato para planificación inicial
        motor.programar(new EventoTriggerPlanificacion(
                UUID.randomUUID(),
                ctx.getAhora(),
                planificacionService
        ));

        // Triggers periódicos según tipo de simulación
//        if (params.tipoSimulacion() != TipoSimulacion.) {
            motor.programar(new EventoTriggerPlanificacionPeriodica(
                    ctx.getAhora().plus(intervaloPlanificacion),
                    intervaloPlanificacion,
                    UUID.randomUUID(),
                    planificacionService,
                    configuracionService
            ));
//        }

    }
}
