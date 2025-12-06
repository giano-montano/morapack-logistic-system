package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    static int contador = 0;

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception
    {
        if (ctx.isPlanificacionDesactivada()) {
            return;
        }
Bitacora.escribir("=================== %d", ++this.contador);
        

        Instant instanteAlgoritmo, instanteSimulacion;
        EntradaProblemaPlanificacion entradaAlgoritmo;
        EstadoGlobal estadoFiltrado, estadoFiltrado2;
        ExecutorService executor;

        instanteSimulacion = ctx.getAhora(); //this.instanteProgramado
        instanteAlgoritmo = instanteSimulacion.plus(Duration.ofHours(Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX));
        executor = Executors.newSingleThreadExecutor();

Bitacora.escribir("Hora de la simulación:", instanteSimulacion);
Bitacora.escribir("Hora del algoritmo   :", instanteAlgoritmo);

        /* Aquí debería ir el WebSocket*/

Bitacora.escribir(ctx.getEstado(), "EstadoGlobal original en EventoTriggerPlanificacion", false);

        //ESTO EN V2 YA SERA UNA COPIA, HABRIA QUE QUITAR EL NEW EN ENTRADAPROBLEMAPLANIFICAC
        //estadoFiltrado2 = ctx.getEstado().obtenerDatosParaAlgoritmoDesdeMemoria_v2(ctx.getEstado(), instanteAlgoritmo);
        estadoFiltrado = ctx.getEstado().obtenerDatosParaAlgoritmoDesdeMemoria(instanteAlgoritmo, ctx);

if(this.contador == 4)
{
    Bitacora.escribir("Estado en llamada %d guardado", this.contador);
    try {
        Bitacora.guardar(estadoFiltrado, "./EstadoFiltrado.ser");    
    } catch (Exception e) {
        Bitacora.escribir(e.toString());
    }
    
}

Bitacora.escribir(estadoFiltrado, "EstadoGlobal filtrado en EventoTriggerPlanificacion", false);

        entradaAlgoritmo = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoFiltrado)
                .semilla(18112001L)
                .instanteActual(instanteAlgoritmo)
                .build();


        Future<ResultadoAlgoritmoDTO> respuestaAlgoritmo = executor.submit(() -> {
            ResultadoAlgoritmoDTO resultado;

            resultado = planificacionService.realizarPlanificacionConEntrada_v2(entradaAlgoritmo);

Bitacora.escribir(resultado, estadoFiltrado, "Resultado del algoritmo");

            return resultado;
        });

        executor.submit(() -> {
            try{
                ResultadoAlgoritmoDTO resultado;
                EventoAplicarResultadoPlanificacion eventoAplicarResultados;
                
                resultado = respuestaAlgoritmo.get(Hiperparametros.MAX_MINUTOS_ALGORITMO, TimeUnit.MINUTES);
                eventoAplicarResultados = new EventoAplicarResultadoPlanificacion(UUID.randomUUID(), instanteAlgoritmo, resultado);

                ctx.programarEvento(eventoAplicarResultados);
            }
            catch (TimeoutException timeoutEx){
                respuestaAlgoritmo.cancel(true);
                
                EventoTriggerPlanificacion eventoNuevaPlanificacion = new EventoTriggerPlanificacion(
                    UUID.randomUUID(),
                    instanteSimulacion,
                    this.planificacionService,
                    this.webSocketService
                ); 
                
                ctx.programarEvento(eventoNuevaPlanificacion);
            }
            catch (Exception ex){
                Bitacora.escribir("Error en el algoritmo: %s", ex.getMessage());
            }
            finally{
                executor.shutdown();
            }
        });
    }

    public void procesar_v1(ContextoSimulacion ctx) throws Exception{
        // ✅ Verificar si la planificación está desactivada
        if (ctx.isPlanificacionDesactivada()) {
            System.out.println("\n⏸️  ========= PLANIFICACIÓN DESACTIVADA =========");
            System.out.println("⏸️  La planificación está pausada - la simulación continúa");
            System.out.println("⏸️  Trigger #" + (ctx.getContadorPlanificaciones() + 1) + " omitido");
            System.out.println("====================================================\n");
            ctx.log("⏸️  EventoTriggerPlanificación omitido - planificación desactivada");
            return; // ✅ Salir sin ejecutar el algoritmo
        }

        Instant instanteFuturoQueRecibiraAlgoritmo = instanteProgramado.plus(
                Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX, ChronoUnit.HOURS);

        // 📋 LOG INICIO DE PLANIFICACIÓN
        System.out.println("\n� ========= TRIGGER PLANIFICACIÓN EJECUTADO =========");
        System.out.println("⏰ Hora de simulación futura para algoritmo: " + instanteFuturoQueRecibiraAlgoritmo);
        System.out.println("⏰ Hora real ctx: " + ctx.getAhora());
        System.out.println("⏰ Instante programado de este trigger: " + instanteProgramado);
        System.out.println("🔢 Planificación #" + (ctx.getContadorPlanificaciones() + 1));
        System.out.println(
                "📊 Pedidos pendientes en contexto: " + ctx.getEstado().contarPedidosPendientes());
        System.out.println("⏱️  Intervalo configurado: 3 minutos (tiempo real)");
        System.out.println("=======================================================\n");

        ctx.log("📋 EventoTriggerPlanificacion: Comenzando planificación #"
                + (ctx.getContadorPlanificaciones() + 1));

        // ✅ Enviar log simplificado de planificación
        String idSimulacion = String.valueOf(ctx.getIdSimulacion());

        if (webSocketService != null){
            try{
                webSocketService.enviarEventoPlanificacion(
                        idSimulacion,
                        instanteProgramado);
            }
            catch (Exception e){
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }

        // 0) preparar DTO para planner
        // ✅ CRÍTICO: Pasar el instante actual de la simulación para obtener vuelos
        // correctos
        RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
                .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
                .instanteActual(
                        ctx.getAhora().plus(Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX,ChronoUnit.HOURS)
                ) // ✅ Hora actual de simulación en el futuro con el time out max
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
        ctx.log("  - Instante que le daremos a algoritmo: " + instanteProgramado.plus(Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX, ChronoUnit.HOURS));
        ctx.log("  - Inicio simulación (desde tomar pedidos): " + ctx.getInicioSimulacion());
        ctx.log("  - El servicio obtendrá vuelos desde: ahora + " + Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX +" horas");

        // 🚀 EJECUCIÓN ASÍNCRONA: No bloqueamos la simulación
        ExecutorService exec = Executors.newSingleThreadExecutor();

        // Guardamos las programaciones activas para desactivarlas cuando la
        // planificación termine
        List<Programacion> programacionesActivas = ctx.getEstado().getProgramaciones().stream()
                .filter(Programacion::isActivo).toList();

        ctx.log("🚀 Lanzando planificación de forma ASÍNCRONA (la simulación continuará)");
        System.out.println("🚀 ========= PLANIFICACIÓN ASÍNCRONA INICIADA =========");
        System.out.println("⏰ Hora simulación: " + instanteProgramado);
        System.out.println("🔄 La simulación CONTINUARÁ mientras se calcula");
        System.out.println("======================================================\n");

        EstadoGlobal estadoCopiaFiltradoParaAlgoritmo = ctx.getEstado()
                .obtenerDatosParaAlgoritmoDesdeMemoria(instanteProgramado, ctx); // instanteProgramado y del ctx son iguales

        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoCopiaFiltradoParaAlgoritmo)
                .semilla(dto.getSeed())
                .instanteActual(ctx.obtenerElAhora() != null ?
                        ctx.obtenerElAhora() .plus(Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX, ChronoUnit.HOURS)
                        : Instant.now())
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();

        // Lanzar el algoritmo en un thread separado con timeout
        Future<ResultadoAlgoritmoDTO> futureAlgoritmo = exec.submit(() -> {
            ctx.log("⚙️  Ejecutando algoritmo de planificación...");
            ctx.log(" Hora dada al algoritmo (futuro): " + entrada.getInstanteActual());

            // el filtrado correcto (+2h para vuelos, -30d para pedidos, etc.)
            if (ctx.getSolucionesAcumuladas().size() > 1){
//                    ctx.log("AQUÍ DOY PROBLEMAS");
            }
            return planificacionService.realizarPlanificacionConEntrada(dto, entrada);
        });

        // 🚀 Thread separado para manejar el resultado con timeout
        exec.submit(() -> {
            try{
                // ⏱️ Esperar máximo MAX_MINUTOS_ALGORITMO minutos
                System.out.println("⏱️  Esperando resultado del algoritmo (timeout: " 
                    + Hiperparametros.MAX_MINUTOS_ALGORITMO + " minutos)...");
                
                ResultadoAlgoritmoDTO res = futureAlgoritmo.get(
                    Hiperparametros.MAX_MINUTOS_ALGORITMO, 
                    TimeUnit.MINUTES
                );

                // ✅ LOG RESULTADO DE PLANIFICACIÓ
                System.out.println("\n✅ ========= ALGORITMO COMPLETADO (ASÍNCRONO) =========");
                System.out.println(
                        "📦 Programaciones generadas: " + res.salida().getProgramaciones().size());
                System.out.println("⚡ Tiempo ejecución: " + res.tiempoEjecucionMs() + " ms");
                System.out.println("📈 Fitness: " + res.fitness());
                System.out.println("🔄 Programando aplicación de resultados a la simulación...");
                System.out.println("======================================================\n");

                ctx.log("✅ Planificación completada - " + res.salida().getProgramaciones().size()
                        + " programaciones. Tiempo ejecución: "+ res.tiempoEjecucionMs() + " ms. Fitness: " + res.fitness());

                // 📋 Programar evento para aplicar los resultados en la simulación
                // Lo programamos para aplicarse justo después del momento que simuló en el futuro
                Instant cuandoAplicar = instanteProgramado.plus(
                        Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX,  ChronoUnit.HOURS
                );
                EventoAplicarResultadoPlanificacion eventoAplicar = new EventoAplicarResultadoPlanificacion(
                        UUID.randomUUID(),
                        cuandoAplicar,
                        res);

                ctx.programarEvento(eventoAplicar);
                ctx.log("📋 Evento de aplicación de resultados programado para: " + cuandoAplicar);

            }
            catch (TimeoutException timeoutEx){
                // ⏱️ TIMEOUT: El algoritmo tardó demasiado - REINTENTAR
                System.out.println("\n⏱️  ========= TIMEOUT EN PLANIFICACIÓN =========");
                System.out.println("⏱️  El algoritmo excedió el tiempo límite de " 
                    + Hiperparametros.MAX_MINUTOS_ALGORITMO + " minutos");
                System.out.println("🔄 REINICIANDO planificación automáticamente...");
                System.out.println("⏹️  Cancelando ejecución del algoritmo actual...");
                System.out.println("================================================\n");
                
                ctx.log("⏱️  TIMEOUT: Algoritmo excedió " + Hiperparametros.MAX_MINUTOS_ALGORITMO 
                    + " minutos. REINICIANDO planificación automáticamente.");
                
                // 🛑 Cancelar el Future para intentar detener el algoritmo
                futureAlgoritmo.cancel(true);
                
                // 🔄 PROGRAMAR NUEVA PLANIFICACIÓN INMEDIATA
                System.out.println("🔄 Programando nueva planificación inmediata después del timeout...");
                EventoTriggerPlanificacion nuevoEvento = new EventoTriggerPlanificacion(
                    UUID.randomUUID(),
                    ctx.obtenerElAhora(), // Ejecutar ahora mismo
                    planificacionService,
                    webSocketService
                ); // revisar bien la lógica, recordar que las planificaciones están controladas
                // por EventoTriggerPlanificacionPeriodica
                
                ctx.programarEvento(nuevoEvento);
                ctx.log("✅ Nueva planificación programada para: " + ctx.obtenerElAhora());
            }
            catch (Exception ex){
                ex.printStackTrace(); // pa ver el errorcito por consola
                System.out.println("\n❌ ========= ERROR EN PLANIFICACIÓN ASÍNCRONA =========");
                System.out.println("❌ Error: " + ex.getMessage());
                System.out.println("======================================================\n");
                ctx.log("❌ Error en planificación asíncrona: " + ex.getMessage());
                ctx.setConError(true);
                ctx.setErrorMsj(ex.getMessage());
            }
            finally{
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
