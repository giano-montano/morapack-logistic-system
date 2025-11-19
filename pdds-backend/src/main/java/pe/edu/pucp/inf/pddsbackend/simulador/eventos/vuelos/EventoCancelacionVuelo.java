package pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoTriggerPlanificacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoTriggerPlanificacionPeriodica;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Duration;
import java.time.Instant;
import java.util.PriorityQueue;
import java.util.UUID;
@Getter
@AllArgsConstructor
public class EventoCancelacionVuelo  implements EventoSimulacion {

    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoCancelacion;

    private final PlanificacionService planificacionService;
    private final SimulacionWebSocketService webSocketService;
    private final ConfiguracionService configuracionService;

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramadoCancelacion;
    }

    @Override
    public void procesar(ContextoSimulacion ctx)  {

        EstadoGlobal estado = ctx.getEstado();

        Vuelo vueloACancelar = estado.getVuelos().get(idVuelo);

        ctx.log("Se está cancelando el vuelo" + vueloACancelar);

        vueloACancelar.setCancelado(true); // finalmente, ha sido cancelado, no nos preocupamos más
        // porque en la simulación los vuelos que se agarran para el algoritmo tienene !vuelo.isCancelado()

//        ctx.log("Se ha programado una planificación para AHORA MISMO" + vueloACancelar);
//        ctx.getScheduler().programar(
//                new EventoTriggerPlanificacion(
//                        UUID.randomUUID(),
//                        ctx.obtenerElAhora(),
//                        planificacionService,
//                        webSocketService
//                        )
//        );

        ctx.log("Se eliminarán las planificaciones periódicas y se creará una nueva para reinicializar ciclo");

        PriorityQueue<EventoSimulacion> eventos = ctx.getScheduler().getEventosSimulacion();

        Duration intervaloPlanificacion = null;
        for (EventoSimulacion evento : eventos) {
            if(evento instanceof EventoTriggerPlanificacionPeriodica){
                intervaloPlanificacion = ((EventoTriggerPlanificacionPeriodica) evento).getIntervalo();
                ctx.log("Cancelando evento: " + evento);
                ctx.getScheduler().cancelar(evento.getId());
            }
        }
        assert intervaloPlanificacion != null; // CONFIANDO 🙏🙏🙏

        ctx.log("Creando nueva PlanificacionPeriodica evento");
        ctx.getScheduler().programar(new EventoTriggerPlanificacionPeriodica(
                ctx.getAhora(),
                intervaloPlanificacion,
                UUID.randomUUID(),
                planificacionService,
                configuracionService,
                webSocketService));
    }

    @Override
    public int getPriority() {
        return 0;
    }

}
