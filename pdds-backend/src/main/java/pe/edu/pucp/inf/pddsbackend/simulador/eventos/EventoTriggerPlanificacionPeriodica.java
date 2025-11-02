package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

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
    private final ConfiguracionService configuracionService;
    private final SimulacionWebSocketService webSocketService;

    public EventoTriggerPlanificacionPeriodica(Instant hora, Duration intervalo, UUID id, 
                                              PlanificacionService planificacionService, 
                                              ConfiguracionService configuracionService,
                                              SimulacionWebSocketService webSocketService) {
        this.hora = hora;
        this.intervalo = intervalo;
        this.id = id;
        this.planificacionService = planificacionService;
        this.configuracionService = configuracionService;
        this.webSocketService = webSocketService;
    }

    public EventoTriggerPlanificacionPeriodica(Instant hora, UUID id, 
                                              PlanificacionService planificacionService, 
                                              ConfiguracionService configuracionService,
                                              SimulacionWebSocketService webSocketService) {
        this.hora = hora;
        this.id = id;
        this.planificacionService = planificacionService;
        this.configuracionService = configuracionService;
        this.webSocketService = webSocketService;
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
        System.out.println("🔄 EventoTriggerPlanificacionPeriodica procesado en: " + ctx.obtenerElAhora());

        // ejecutar un trigger
        ctx.programarEvento(new EventoTriggerPlanificacion(UUID.randomUUID(), ctx.obtenerElAhora(), planificacionService, webSocketService));
        
        // Verificar si han cambiado en BD:
        ConfiguracionParametrosSistemaDinamicos c = configuracionService.obtenerConfig();
        if(c != null){
            intervalo = Duration.of(c.getMinutosRealesEntrePlanificaciones(), ChronoUnit.MINUTES);
            ctx.log("EventoTriggerPlanificacionPeriodica: Config obtenida para el intervalo: " + c);
        } else {
            ctx.log("EventoTriggerPlanificacionPeriodica: Config NO obtenida, intervalo en minutos: " + intervalo.toMinutes());
        }

        // reprogramarme para la próxima vez
        Instant next = hora.plus(intervalo);
        TipoSimulacion tipoSimulacion = ctx.getParams().tipoSimulacion();
        Instant horaInicialSimulacion = ctx.getParams().fechaHoraInicioSimulacion();
        Instant limitesSemanal = horaInicialSimulacion.plus(7, ChronoUnit.DAYS);
        
        System.out.println("   Tipo simulación: " + tipoSimulacion);
        System.out.println("   Hora actual: " + ctx.obtenerElAhora());
        System.out.println("   Hora inicial simulación: " + horaInicialSimulacion);
        System.out.println("   Próximo trigger: " + next);
        System.out.println("   Límite semanal (inicial + 7 días): " + limitesSemanal);
        System.out.println("   ¿Next está antes del límite?: " + next.isBefore(limitesSemanal));
        
        switch (tipoSimulacion) {
            case HASTA_COLAPSO, TIEMPO_REAL -> {
                System.out.println("   ✅ Reprogramando para simulación continua");
                ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService, configuracionService, webSocketService));
            }
            case SEMANAL -> {
                if (next.isBefore(limitesSemanal)) {
                    System.out.println("   ✅ Reprogramando para simulación SEMANAL (dentro del límite)");
                    ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(next, intervalo, UUID.randomUUID(), planificacionService, configuracionService, webSocketService));
                } else {
                    System.out.println("   ❌ NO reprogramando - próximo trigger estaría DESPUÉS del límite semanal");
                    System.out.println("   ⚠️ ESTA ES LA RAZÓN por la que no hay más eventos periódicos");
                }
            }
        }
    }

    @Override
    public int getPriority() {
        return 5; // QUE NO LE QUITE TIEMPO A NADA!
    }
}
