package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class EventoTriggerPlanificacionPeriodica implements EventoSimulacion {
    private final Instant hora;
    @Setter
    private Duration intervalo = Duration.of(5, ChronoUnit.MINUTES); // en base a param!!! y dinámico?!
    private final UUID id;

    private final PlanificacionService planificacionService;
    private final ConfiguracionService configuracionService;

    public EventoTriggerPlanificacionPeriodica(Instant hora, Duration intervalo, UUID id, PlanificacionService planificacionService, ConfiguracionService configuracionService) {
        this.hora = hora;
        this.intervalo = intervalo;
        this.id = id;
        this.planificacionService = planificacionService;
        this.configuracionService = configuracionService;
    }

    public EventoTriggerPlanificacionPeriodica(Instant hora, UUID id, PlanificacionService planificacionService, ConfiguracionService configuracionService) {
        this.hora = hora;
        this.id = id;
        this.planificacionService = planificacionService;
        this.configuracionService = configuracionService;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return hora;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) {

        // ejecutar un trigger (delegar al EventoTriggerPlanificacion o llamar su lógica)
        ctx.programarEvento(new EventoTriggerPlanificacion(UUID.randomUUID(), ctx.obtenerElAhora(), planificacionService)); //, "periodico",
        // Verificar si han cambiado en BD:
        ConfiguracionParametrosSistemaDinamicos c =configuracionService.obtenerConfig();
        if(c!=null){
            intervalo = Duration.of(c.getMinutosRealesEntrePlanificaciones(), ChronoUnit.MINUTES);
            ctx.log("EventoTriggerPlanificacionPeriodica: Config obtenida para el intervalo: " + c);
        }else ctx.log("EventoTriggerPlanificacionPeriodica: Config NO obtenida, intervalo en minuts: " + intervalo.toMinutes());

        // reprogramarme para la próxima vez
        Instant next = hora.plus(intervalo);
        TipoSimulacion tipoSimulacion = ctx.getParams().tipoSimulacion();
        switch (tipoSimulacion) {
            case HASTA_COLAPSO, TIEMPO_REAL -> {
                ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService, configuracionService));
            }
            case SEMANAL -> {
                if (next.isBefore(ctx.obtenerElAhora().plus(7, ChronoUnit.DAYS))) {
                    ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService, configuracionService));
                }
            }
        }

        // reprogramarme para la próxima vez
//        Instant next = hora.plus(intervalo);
//        if (next.isBefore(ctx.obtenerElAhora().plus(ctx.getParams().getDiasASimular()))) {
//            ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID()));
//        }

    }

    @Override
    public int getPriority() {
        return 5; // QUE NO LE QUITE TIEMPO A NADA!
    }
}
