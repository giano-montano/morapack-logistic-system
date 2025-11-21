package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class EventoTriggerPlanificacionPeriodica implements EventoSimulacion
{
    private final Instant hora;
    @Setter
    @Getter
    private Duration intervalo;// ⏱️ Configurado a 3 minutos en tiempo real
    private final UUID id;

    private final PlanificacionService planificacionService;
    private final ConfiguracionService configuracionService;
    private final SimulacionWebSocketService webSocketService;

    public EventoTriggerPlanificacionPeriodica(Instant hora, Duration intervalo, UUID id,
            PlanificacionService planificacionService,
            ConfiguracionService configuracionService,
            SimulacionWebSocketService webSocketService)
    {
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
            SimulacionWebSocketService webSocketService)
    {
        this.hora = hora;
        this.id = id;
        this.planificacionService = planificacionService;
        this.configuracionService = configuracionService;
        this.webSocketService = webSocketService;
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return hora;
    }

    @Override
    public void procesar(ContextoSimulacion ctx)
    {
        System.out.println(
                "🔄 EventoTriggerPlanificacionPeriodica procesado en: " + ctx.obtenerElAhora());

        // ejecutar un trigger
        ctx.programarEvento(
                new EventoTriggerPlanificacion(UUID.randomUUID(), ctx.obtenerElAhora(),
                        planificacionService, webSocketService));

        // ✅ Obtener intervalo desde los parámetros de la simulación o usar default de 3
        // minutos
        Long minutosConfig = ctx.getParams().minutosRealesEntrePlanificaciones();
        long minutosIntervalo = minutosConfig != null ? minutosConfig : 3L;

        intervalo = Duration.of(minutosIntervalo, ChronoUnit.MINUTES);

        System.out.println("   📋 Intervalo de planificación configurado: " + intervalo
                + " minutos (tiempo real)");

        // ⏱️ CALCULAR PRÓXIMA PLANIFICACIÓN: minutosIntervalo de TIEMPO REAL
        // El intervalo debe multiplicarse por el speedFactor para convertir tiempo real
        // a tiempo simulado
        Duration intervaloSimulado = calcularIntervaloSimulado(ctx, intervalo);
        Instant next = hora.plus(intervaloSimulado);

        TipoSimulacion tipoSimulacion = ctx.getParams().tipoSimulacion();
        Instant horaInicialSimulacion = ctx.getParams().fechaHoraInicioSimulacion();
        Instant limitesSemanal = horaInicialSimulacion.plus(7, ChronoUnit.DAYS);

        System.out.println("   ⏱️  Intervalo configurado (tiempo real): " + intervalo.toMinutes()
                + " minutos");
        System.out.println("   ⚡ Intervalo en simulación (ajustado): "
                + intervaloSimulado.toMinutes() + " minutos");
        System.out.println("   Tipo simulación: " + tipoSimulacion);
        System.out.println("   Hora actual: " + ctx.obtenerElAhora());
        System.out.println("   Hora inicial simulación: " + horaInicialSimulacion);
        System.out.println("   Próximo trigger: " + next);
        System.out.println("   Límite semanal (inicial + 7 días): " + limitesSemanal);
        System.out.println("   ¿Next está antes del límite?: " + next.isBefore(limitesSemanal));

        switch (tipoSimulacion)
        {
            case HASTA_COLAPSO, TIEMPO_REAL ->
            {
                System.out.println("   ✅ Reprogramando para simulación continua");
                ctx.programarEvento(
                        new EventoTriggerPlanificacionPeriodica(
                                next,
                                intervalo,
                                UUID.randomUUID(),
                                planificacionService,
                                configuracionService,
                                webSocketService));
            }
            case SEMANAL ->
            {
                if (next.isBefore(limitesSemanal))
                {
                    System.out.println(
                            "   ✅ Reprogramando para simulación SEMANAL (dentro del límite)");
                    ctx.programarEvento(new EventoTriggerPlanificacionPeriodica(
                            next,
                            intervalo,
                            UUID.randomUUID(),
                            planificacionService,
                            configuracionService,
                            webSocketService));
                }
                else
                {
                    System.out.println(
                            "   ❌ NO reprogramando - próximo trigger estaría DESPUÉS del límite semanal");
                    System.out.println(
                            "   ⚠️ ESTA ES LA RAZÓN por la que no hay más eventos periódicos");
                }
            }
        }
    }

    /**
     * Calcula el intervalo de tiempo de simulación basado en el intervalo de tiempo
     * real y el factor de aceleración del reloj.
     *
     * Por ejemplo: si queremos planificar cada 3 minutos de tiempo REAL y la
     * simulación corre a 60x, entonces en la simulación debemos sumar 3 * 60 = 180
     * minutos.
     */
    private Duration calcularIntervaloSimulado(ContextoSimulacion ctx, Duration intervaloReal)
    {
        // Obtener el factor de aceleración del reloj
        Clock reloj = ctx.getReloj();
        double speedFactor = 1.0; // Por defecto tiempo real

        if (reloj instanceof pe.edu.pucp.inf.pddsbackend.miscelaneo.RelojEnganado)
        {
            pe.edu.pucp.inf.pddsbackend.miscelaneo.RelojEnganado relojEnganado = (pe.edu.pucp.inf.pddsbackend.miscelaneo.RelojEnganado) reloj;
            speedFactor = relojEnganado.getSpeedFactor();
        }

        // Multiplicar el intervalo real por el speedFactor
        long minutosSimulados = (long) (intervaloReal.toMinutes() * speedFactor);

        System.out.println("   🔧 SpeedFactor detectado: " + speedFactor + "x");
        System.out.println("   🔧 Intervalo real: " + intervaloReal.toMinutes()
                + " min → Simulado: " + minutosSimulados + " min");

        return Duration.of(minutosSimulados, ChronoUnit.MINUTES);
    }

    @Override
    public int getPriority()
    {
        return 5; // QUE NO LE QUITE TIEMPO A NADA!
    }

    @Override
    public String toString()
    {
        return "Evento={" +
                "hora=" + hora +
                ", intervalo: " + intervalo +
                ", id: " + id;
    }
}
