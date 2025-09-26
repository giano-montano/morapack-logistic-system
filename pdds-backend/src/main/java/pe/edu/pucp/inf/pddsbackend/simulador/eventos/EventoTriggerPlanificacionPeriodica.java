package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class EventoTriggerPlanificacionPeriodica implements EventoSimulacion {
    private final Instant hora;
    @Setter
    private Duration intervalo = Duration.of(5, ChronoUnit.MINUTES); // en base a param!!! y dinámico?!
    private final UUID id;

    private final PlanificacionService planificacionService;

    public EventoTriggerPlanificacionPeriodica(Instant hora, Duration intervalo, UUID id, PlanificacionService planificacionService) {
        this.hora = hora;
        this.intervalo = intervalo;
        this.id = id;
        this.planificacionService = planificacionService;
    }

    public EventoTriggerPlanificacionPeriodica(Instant hora,  UUID id, PlanificacionService planificacionService) {
        this.hora = hora;
        this.id = id;
        this.planificacionService = planificacionService;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return null;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) {

        // ejecutar un trigger (delegar al EventoTriggerPlanificacion o llamar su lógica)
        ctx.programarEvento(new EventoTriggerPlanificacion(UUID.randomUUID(), ctx.obtenerElAhora(), planificacionService)); //, "periodico",
        // reprogramarme para la próxima vez
        Instant next = hora.plus(intervalo);
        TipoSimulacion tipoSimulacion = ctx.getParams().tipoSimulacion();
        switch (tipoSimulacion) {
            case HASTA_COLAPSO, TIEMPO_REAL -> {
                ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService));
            }
            case SEMANAL -> {
                if (next.isBefore(ctx.obtenerElAhora().plus(7, ChronoUnit.DAYS))) {
                    ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService));
                }
            }
        }

        // reprogramarme para la próxima vez
//        Instant next = hora.plus(intervalo);
//        if (next.isBefore(ctx.obtenerElAhora().plus(ctx.getParams().getDiasASimular()))) {
//            ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID()));
//        }

    }
}
