package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class EventoTriggerPlanificacion implements EventoSimulacion
{

    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramado;

    private final PlanificacionService planificacionService;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    private static final int MAXIMO_ESPERA_ALGORITMO_SEGUNDOS = 300;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramado;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception
    {

        // 📋 LOG INICIO DE PLANIFICACIÓN
        System.out.println("\n� ========= TRIGGER PLANIFICACIÓN EJECUTADO =========");
        System.out.println("⏰ Hora simulación: " + instanteProgramado);
        System.out.println("🔢 Planificación #" + (ctx.getContadorPlanificaciones() + 1));
        System.out.println(
                "📊 Pedidos pendientes en contexto: " + ctx.getEstado().contarPedidosPendientes());
        System.out.println("⏱️  Intervalo configurado: 3 minutos (tiempo real)");
        System.out.println("=======================================================\n");

        ctx.log("📋 EventoTriggerPlanificacion: Comenzando planificación #"
                + (ctx.getContadorPlanificaciones() + 1));

        // ✅ Enviar log simplificado de planificación
        String idSimulacion = String.valueOf(ctx.getIdSimulacion());

        if (webSocketService != null)
        {
            try
            {
                webSocketService.enviarEventoPlanificacion(
                        idSimulacion,
                        instanteProgramado);
            }
            catch (Exception e)
            {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }

        // 0) preparar DTO para planner
        // ✅ CRÍTICO: Pasar el instante actual de la simulación para obtener vuelos
        // correctos
        RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
                .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
                .instanteActual(ctx.getAhora()) // ✅ Hora actual de simulación
                .instanteDesdeTomarPedidos(ctx.getInicioSimulacion()) // ✅ Desde inicio de
                                                                      // simulación
                .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
                .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
                .seed(ctx.getFormaRealizarPlanificacion().getSeed())
                .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
                .usarModoMock(ctx.getFormaRealizarPlanificacion().getUsarModoMock())
                .build();

        ctx.log("EventoTriggerPlanificacion: DTO creado - Modo Mock: " + dto.getUsarModoMock());
        ctx.log("📅 Parámetros de planificación:");
        ctx.log("  - Instante actual simulación: " + ctx.getAhora());
        ctx.log("  - Inicio simulación (desde tomar pedidos): " + ctx.getInicioSimulacion());
        ctx.log("  - El servicio obtendrá vuelos desde: ahora + 2 horas");

        // 🚀 EJECUCIÓN ASÍNCRONA: No bloqueamos la simulación
        ExecutorService exec = Executors.newSingleThreadExecutor();

        // Guardamos las programaciones activas para desactivarlas cuando la
        // planificación termine
        List<Programacion> programacionesActivas = ctx.getEstado().getProgramaciones().stream()
                .filter(Programacion::isActivo).toList();

        ctx.log("🚀 Lanzando planificación de forma ASÍNCRONA (la simulación continuará)");
        System.out.println("🚀 ========= PLANIFICACIÓN ASÍNCRONA INICIADA =========");
        System.out.println("⏰ Hora simulación: " + ctx.obtenerElAhora());
        System.out.println("🔄 La simulación CONTINUARÁ mientras se calcula");
        System.out.println("======================================================\n");

        EstadoGlobal estadoCopiaFiltradoParaAlgoritmo = ctx.getEstado()
                .obtenerDatosParaAlgoritmoDesdeMemoria(instanteProgramado, ctx);

        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoCopiaFiltradoParaAlgoritmo)
                .semilla(dto.getSeed())
                .instanteActual(ctx.obtenerElAhora() != null ? ctx.obtenerElAhora() : Instant.now())
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();

        // Lanzar el algoritmo en un thread separado
        exec.submit(() -> {
            try
            {
                ctx.log("⚙️  Ejecutando algoritmo de planificación...");

                // el filtrado correcto (+2h para vuelos, -30d para pedidos, etc.)
                if (ctx.getSolucionesAcumuladas().size() > 1)
                {
                    ctx.log("AQUÍ DOY PROBLEMAS");
                }
                ResultadoAlgoritmoDTO res = planificacionService
                        .realizarPlanificacionConEntrada(dto, entrada);

                // ✅ LOG RESULTADO DE PLANIFICACIÓ
                System.out.println("\n✅ ========= ALGORITMO COMPLETADO (ASÍNCRONO) =========");
                System.out.println(
                        "📦 Programaciones generadas: " + res.salida().getProgramaciones().size());
                System.out.println("⚡ Tiempo ejecución: " + res.tiempoEjecucionMs() + " ms");
                System.out.println("📈 Fitness: " + res.fitness());
                System.out.println("🔄 Programando aplicación de resultados a la simulación...");
                System.out.println("======================================================\n");

                ctx.log("✅ Planificación completada - " + res.salida().getProgramaciones().size()
                        + " programaciones");

                // 📋 Programar evento para aplicar los resultados en la simulación
                // Lo programamos 1 segundo después de la hora actual de la simulación
                Instant cuandoAplicar = ctx.obtenerElAhora().plusSeconds(1);
                EventoAplicarResultadoPlanificacion eventoAplicar = new EventoAplicarResultadoPlanificacion(
                        UUID.randomUUID(),
                        cuandoAplicar,
                        res,
                        programacionesActivas);

                ctx.programarEvento(eventoAplicar);
                ctx.log("📋 Evento de aplicación de resultados programado para: " + cuandoAplicar);

            }
            catch (Exception ex)
            {
                System.out.println("\n❌ ========= ERROR EN PLANIFICACIÓN ASÍNCRONA =========");
                System.out.println("❌ Error: " + ex.getMessage());
                System.out.println("======================================================\n");
                ctx.log("❌ Error en planificación asíncrona: " + ex.getMessage());
                ctx.setConError(true);
                ctx.setErrorMsj(ex.getMessage());
            }
            finally
            {
                exec.shutdown();
            }
        });

        // 🚀 IMPORTANTE: NO esperamos el resultado aquí, la simulación continúa
        // inmediatamente
        ctx.log("✅ EventoTriggerPlanificacion procesado - algoritmo ejecutándose en background");

        // No hay "res" disponible aquí porque es asíncrono
        // El resultado se aplicará cuando el EventoAplicarResultadoPlanificacion se
        // procese
        // La simulación continúa inmediatamente sin esperar el resultado del algoritmo
    }

    @Override
    public int getPriority()
    {
        return 4; // después de cualquier llegada de avión.
    }
}
// SalidaProblemaPlanificacion reparada = intentarReparacionLocal(salida, ctx);
// if (reparada == null) {
// ctx.log("No se pudo aplicar solucion: conflictos detectados");
// return;
// }
// salida = reparada;
