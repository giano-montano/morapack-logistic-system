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
public class EventoCancelacionVuelo implements EventoSimulacion{

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
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramadoCancelacion;
    }

    @Override
    public void procesar(ContextoSimulacion ctx){

        EstadoGlobal estado = ctx.getEstado();

        Vuelo vueloACancelar = estado.getVuelos().get(idVuelo);

        ctx.log("🚫 Cancelando vuelo: " + vueloACancelar);
        System.out.println("\n🚫 ========= VUELO CANCELADO =========");
        System.out.println("✈️  Vuelo ID: " + idVuelo);
        System.out.println("🕐 Hora de cancelación: " + ctx.getAhora());
        System.out.println("=====================================\n");

        vueloACancelar.setCancelado(true); // Marcar como cancelado
        
        // 🛑 PAUSAR planificaciones temporalmente (equivalente a pausarPlanificacion())
        // Esto evita condiciones de carrera si hay una planificación ejecutándose
        ctx.setPlanificacionDesactivada(true);
        ctx.log("⏸️  Pausando planificaciones temporalmente para evitar conflictos");
        System.out.println("⏸️  Pausando planificaciones...");
        
        // 🔄 CANCELAR todas las planificaciones periódicas existentes
        PriorityQueue<EventoSimulacion> eventos = ctx.getScheduler().getEventosSimulacion();
        Duration intervaloPlanificacion = null;
        
        for (EventoSimulacion evento : eventos){
            if (evento instanceof EventoTriggerPlanificacionPeriodica){
                intervaloPlanificacion = ((EventoTriggerPlanificacionPeriodica) evento).getIntervalo();
                ctx.getScheduler().cancelar(evento.getId());
                ctx.log("❌ Cancelada planificación periódica: " + evento.getId());
            }
        }
        
        // 🔄 RECREAR planificación periódica desde ahora
        if (intervaloPlanificacion != null) {
            ctx.getScheduler().programar(new EventoTriggerPlanificacionPeriodica(
                ctx.getAhora(),
                intervaloPlanificacion,
                UUID.randomUUID(),
                planificacionService,
                configuracionService,
                webSocketService
            ));
            ctx.log("✅ Nueva planificación periódica creada con intervalo: " + intervaloPlanificacion);
        }
        
        // 🔄 PROGRAMAR replanificación INMEDIATA (equivalente a reanudarPlanificacion())
        ctx.log("🔄 Programando replanificación inmediata tras cancelación de vuelo");
        System.out.println("🔄 Programando replanificación automática...");
        
        EventoTriggerPlanificacion eventoReplanificacion = new EventoTriggerPlanificacion(
            UUID.randomUUID(),
            ctx.obtenerElAhora(),
            planificacionService,
            webSocketService
        );
        
        ctx.programarEvento(eventoReplanificacion);
        
        // ✅ REANUDAR planificaciones (equivalente a setPlanificacionDesactivada(false))
        ctx.setPlanificacionDesactivada(false);
        ctx.log("▶️  Planificaciones reactivadas - replanificación programada para: " + ctx.obtenerElAhora());
        System.out.println("▶️  Planificaciones reactivadas\n");
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

}
