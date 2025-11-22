package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.*;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.RelojEnganado;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaAlmacenesUnico;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaCancelacionesUnico;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaDescargaPedidosDiario;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaDescargaVuelosDiario;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoTriggerPlanificacionPeriodica;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
public class EjecutorSimulacion
{

    private final ExecutorService hiloEjecutor = Executors.newSingleThreadExecutor();
    private final PlanificacionService planificacionService;
    private final SimulacionRepository simulacionRepo;
    private final ConfiguracionService configuracionService;
    private final SimulacionWebSocketService webSocketService;

    private final VueloProgramadoRepository vueloProgramadoRepository;
    private final VueloService vueloService;
    private final AlmacenRepository almacenRepository;
    private final CancelacionVueloRepository cancelacionVueloRepository;

    // ✅ Mapa para rastrear motores de simulación activos (permite cancelarlos)
    private final Map<Long, MotorSimulacion> motoresActivos = new ConcurrentHashMap<>();
    private final PedidoRepository pedidoRepository;

    // public static int MINUTOS_INTERVALO_EJECUCION_ALGORITMO_EN_VIDA_REAL = 60;

    public Future<ContextoSimulacion> iniciarSimulacionAhora(
            Simulacion simulacionEntidad, SimulacionRequestDTO params,
            ConfiguracionParametrosSistemaDinamicos config,
            RealizarPlanificacionDTO dataBasePlanificacion,
            String nombreSubCarpeta)
    {
        return hiloEjecutor.submit(() -> {
            Long idSimulacion = simulacionEntidad.getId();
            try
            {
                // 1. construir snapshot inicial (deep copy) - PASAR ID DE SIMULACIÓN
                ContextoSimulacion ctx = construirContexto(idSimulacion, params, config,
                        dataBasePlanificacion, nombreSubCarpeta);
                ctx.getEstado().setLr(ctx.getReport());
                ctx.log(ctx.getEstado().toString());
                ctx.getReport().setImprimirPorLogger(true); // para tmb ver con consola antes del
                                                            // reporte final archivo.
                // esto ya hace ctx.setScheduler(this) en el constructor
                MotorSimulacion motor = new MotorSimulacion(ctx);

                // ✅ Registrar motor activo para permitir cancelación
                motoresActivos.put(idSimulacion, motor);
                System.out.println("🟢 Motor de simulación " + idSimulacion + " registrado");

                // ✅ SINCRONIZACIÓN: Enviar información de reloj al frontend
                // ⚠️ Esperamos 1.5 segundos para asegurar que el frontend se haya suscrito al WebSocket
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                Instant horaRealArranque = Instant.now();
                Instant horaSimuladaInicio = params.fechaHoraInicioSimulacion() != null
                        ? params.fechaHoraInicioSimulacion()
                        : Instant.now();
                Double factorVelocidad = config.getFactorDeVelocidad();
                Long minutosEntrePlanificaciones = params.minutosRealesEntrePlanificaciones() != null
                        ? params.minutosRealesEntrePlanificaciones()
                        : config.getMinutosRealesEntrePlanificaciones();
                
                System.out.println("🔄 Preparando sincronización de reloj:");
                System.out.println("   - Hora real arranque: " + horaRealArranque);
                System.out.println("   - Hora simulada inicio: " + horaSimuladaInicio);
                System.out.println("   - Factor velocidad: " + factorVelocidad + "x");
                
                webSocketService.enviarSincronizacion(
                        idSimulacion,
                        horaRealArranque,
                        horaSimuladaInicio,
                        factorVelocidad,
                        minutosEntrePlanificaciones);
                
                // ctx.setScheduler(motor); // cuidao con los cíclicos
                // // 2. poblar eventos iniciales (OrderArrivalEvent, FlightArrivalEvent,
                // TriggerPlanificationEvent inicial)
                System.out.println("📅 Poblando eventos iniciales...");
                populateInitialEvents(motor, ctx, config, params);
                System.out.println("✅ Eventos iniciales poblados:");
                System.out
                        .println("   • Total eventos en cola: " + motor.getColaDeEventos().size());
                System.out.println("   • Pedidos: " + ctx.getEstado().getPedidos().size());
                System.out.println("   • Vuelos: " + ctx.getEstado().getVuelos().size());
                System.out.println("   • Almacenes: " + ctx.getEstado().getAlmacenes().size());

                // // 3. Ejecutar (hasta el infinito a menos que sea semanal)
                Instant target = Instant.MAX;
                if (params.tipoSimulacion().equals(TipoSimulacion.SEMANAL))
                {
                    target = ctx.getAhora().plus(Duration.ofDays(7));
                }
                System.out.println("🚀 Iniciando motor de simulación hasta: " + target);
                System.out.println("⏰ Hora actual simulación: " + ctx.getAhora());
                motor.correrHasta(target, 10_000_000); // o control por tiempo
                System.out.println("🏁 Motor de simulación terminó");
                // // 4. al terminar, generar PlanificationSolutionOutput y persistir
                // resultados, metrics

                return ctx;
            }
            finally
            {
                // ✅ Limpiar motor al terminar (sea por finalización natural o cancelación)
                motoresActivos.remove(idSimulacion);
                ContextoSimulacion.resetInstancia(); // 🧹 Limpiar singleton para permitir nuevas
                                                     // simulaciones
                System.out.println(
                        "🔴 Motor de simulación " + idSimulacion + " removido y contexto limpiado");
            }
        });
    }

    public ContextoSimulacion construirContexto(Long idSimulacion, SimulacionRequestDTO params,
            ConfiguracionParametrosSistemaDinamicos config,
            RealizarPlanificacionDTO dataBasePlanificacion, String nombreSubCarpeta)
    {

        // EstadoGlobal estadoInicial =
        // planificacionService.obtenerDatosParaAlgoritmo(dataBasePlanificacion, true);
        // // solo por primera vez en BD // <- YA NO

        // Determinar el instante de inicio de la simulación
        Instant instanteInicio = params.fechaHoraInicioSimulacion() != null
                ? params.fechaHoraInicioSimulacion()
                : Instant.now();

        Clock relojAEmplear = params.tipoSimulacion().equals(TipoSimulacion.TIEMPO_REAL)
                ? Clock.systemUTC()
                : new RelojEnganado(instanteInicio, // Usar fecha especificada o actual
                        config.getFactorDeVelocidad(), // sí o sí consigue su factor de velocidad,
                                                       // ntp. // todavía no hago que sea dinámico
                        ZoneId.of("UTC"));

        LoggingReport loggingReport = new LoggingReport();
        loggingReport.setDirectory(nombreSubCarpeta);
        ContextoSimulacion ctx = ContextoSimulacion.obtenerOCrearUnicaInstancia(
                relojAEmplear,
                new EstadoGlobal(null, null, null, null, null),
                dataBasePlanificacion,
                loggingReport,
                params);
        ctx.log("Estado inicializado por primera vez sin nada (lo llenarán los eventos): "
                + ctx.getEstado());

        return ctx;
        // return ContextoSimulacion.builder()
        // .reloj(relojAEmplear)
        // .ahora( relojAEmplear.instant() )
        // .estado(estadoInicial)
        // .params(params)
        // .formaRealizarPlanificacion(dataBasePlanificacion)
        // .report(loggingReport) // es una orquestación algo horrible y repetitiva,
        // pero todo por la carpeta.
        // .build();
    }

    private void populateInitialEvents(MotorSimulacion motor, ContextoSimulacion ctx,
            ConfiguracionParametrosSistemaDinamicos config, SimulacionRequestDTO params)
    {
        // poblar eventos:
        // for (Pedido p : ctx.getEstado().getPedidos().values()) {
        // motor.marcarComoProgramado(new EventoLlegadaPedido(p.getId(), UUID.randomUUID(),
        // p.getInstanteRegistro()));
        // }
        // for (Vuelo v : ctx.getEstado().getVuelos().values()) {
        // motor.marcarComoProgramado(new EventoVueloSalida(v.getId(),
        // UUID.randomUUID(),v.getInicio(), webSocketService));
        // motor.marcarComoProgramado(new EventoVueloLlegada( v.getId(),
        // UUID.randomUUID(),v.getFin(), webSocketService));
        // }

        // CRÍTICO: Inicializar trigger periódico
        Duration intervaloPlanificacion = Duration.ofMinutes(
                // MINUTOS_INTERVALO_EJECUCION_ALGORITMO_EN_VIDA_REAL
                params.minutosRealesEntrePlanificaciones() != null
                        ? params.minutosRealesEntrePlanificaciones()
                        : config.getMinutosRealesEntrePlanificaciones() // repetido xd
        );
        ctx.log("Intervalo planificacion minutos: " + intervaloPlanificacion.toMinutes());

        // NUEVO CARGAS PERIÓDICAS AL ESTADO GLOBAL OBLIGATORIAS DESDE EL PRIMER
        // MOMENTO!!!
        // Tienen prioridad 0, o sea nadie les va a robar su turno.
        motor.programar(new EventoCargaAlmacenesUnico(UUID.randomUUID(), ctx.obtenerElAhora(),
                planificacionService));
        motor.programar(new EventoCargaDescargaVuelosDiario(
                UUID.randomUUID(), ctx.obtenerElAhora(), webSocketService,
                vueloProgramadoRepository, vueloService, almacenRepository));
        motor.programar(new EventoCargaDescargaPedidosDiario(
                UUID.randomUUID(), ctx.obtenerElAhora(), webSocketService, pedidoRepository));
        motor.programar(new EventoCargaCancelacionesUnico(
                UUID.randomUUID(), ctx.obtenerElAhora(), cancelacionVueloRepository,
                planificacionService,
                webSocketService, configuracionService));

        // ✅ SOLO marcarComoProgramado el trigger periódico que se encargará de marcarComoProgramado
        // planificaciones
        // El EventoTriggerPlanificacionPeriodica internamente programa
        // EventoTriggerPlanificacion
        // Programamos el primer trigger periódico para que se ejecute INMEDIATAMENTE
        // y él se encargue de marcarComoProgramado tanto la primera planificación como los
        // subsecuentes triggers
        motor.programar(new EventoTriggerPlanificacionPeriodica(
                ctx.getAhora(), // ✅ Primer trigger inmediato
                intervaloPlanificacion,
                UUID.randomUUID(),
                planificacionService,
                configuracionService,
                webSocketService));

    }

    /**
     * ✅ Cancela una simulación en ejecución
     *
     * @param idSimulacion
     *            ID de la simulación a cancelar
     * @return true si se encontró y canceló, false si no está en ejecución
     */
    public boolean cancelarSimulacion(Long idSimulacion)
    {
        MotorSimulacion motor = motoresActivos.get(idSimulacion);
        if (motor != null)
        {
            System.out.println("🛑 Cancelando simulación " + idSimulacion);
            motor.cancelar();
            return true;
        }
        else
        {
            System.out.println("⚠️ Simulación " + idSimulacion + " no está en ejecución");
            return false;
        }
    }
}
