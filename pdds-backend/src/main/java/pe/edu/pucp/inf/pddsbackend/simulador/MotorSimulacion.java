package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.Data;
import lombok.Setter;
import lombok.ToString;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.RelojEnganado;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.FinSimulacionDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.RazonFinSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.RutaPorPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Data
@ToString(exclude =
{"ctx", "webSocketService"})
public class MotorSimulacion implements SchedulerSimulacion
{
    @Setter // workaround!!! le quité el final también a esto
    private  PriorityQueue<EventoSimulacion> colaDeEventos = new PriorityQueue<>();
    private final ContextoSimulacion ctx;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean cancelado = false; // ✅ Flag para cancelar la simulación
    private SimulacionWebSocketService webSocketService; // Servicio para enviar eventos WebSocket

    // Constructor de toda la vida
    public MotorSimulacion(ContextoSimulacion ctx)    {
        this.ctx = ctx;
        // link back: permitir ctx.marcarComoProgramado delegar a este motor
        ctx.setScheduler(this);
    }

    /**
     * Configura el servicio WebSocket para enviar notificaciones
     */
    public void setWebSocketService(SimulacionWebSocketService webSocketService)
    {
        this.webSocketService = webSocketService;
    }

    @Override
    public void programar(EventoSimulacion e)
    {
        lock.lock();
        try
        {
            colaDeEventos.add(e);
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public boolean cancelar(UUID eventId)
    {
        lock.lock();
        try
        {
            // opción simple: iterar q y remover matching id (ineficiente para cola grande)
            Iterator<EventoSimulacion> it = colaDeEventos.iterator();
            while (it.hasNext())
            {
                EventoSimulacion ev = it.next();
                if (ev.getId().equals(eventId))
                {
                    it.remove();
                    return true;
                }
            }
            return false;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public PriorityQueue<EventoSimulacion> getEventosSimulacionNuevaQueue()
    {
        return new PriorityQueue<>(colaDeEventos);
    }

    public ContextoSimulacion correrHasta(Instant objetivo, long maxEventos) throws Exception{
        long procesados = 0;
        int erroresConsecutivos = 0;
        final int MAX_ERRORES_CONSECUTIVOS = 10;

        System.out.println("🎬 Motor.correrHasta() INICIADO");
        System.out.println("   - Objetivo: " + objetivo);
        System.out.println("   - Eventos en cola: " + colaDeEventos.size());
        System.out.println("   - Hora actual ctx: " + ctx.getAhora());

        while (true){
            // ✅ Verificar si la simulación fue cancelada
            if (cancelado){
                ctx.log("⛔ Simulación CANCELADA por usuario");
                System.out.println("⛔ Motor detectó cancelación");
                enviarFinSimulacion(RazonFinSimulacion.CANCELADA_POR_USUARIO, 
                    "La simulación fue cancelada manualmente por el usuario");
                break;
            }

            EventoSimulacion ev;
            lock.lock();
            try{
                ev = colaDeEventos.peek();
                if (ev == null){
                    ctx.log("Simulación terminada: cola de eventos vacía");
                    System.out.println("⚠️ ========================================");
                    System.out.println("⚠️ COLA DE EVENTOS VACÍA - TERMINANDO SIMULACIÓN");
                    System.out.println("⚠️ ========================================");
                    System.out.println("   Eventos procesados: " + procesados);
                    System.out.println("   Tiempo actual: " + ctx.getAhora());
                    System.out.println("   Tiempo objetivo: " + objetivo);
                    System.out.println("   Tipo simulación: " + ctx.getParams().tipoSimulacion());
                    System.out.println("   ¿Por qué está vacía?");
                    System.out.println(
                            "   - Si es SEMANAL: verificar que eventos periódicos se estén reprogramando");
                    System.out.println(
                            "   - Si todos los vuelos terminaron: verificar que haya más eventos programados");
                    System.out.println("⚠️ ========================================");
                    
                    // Enviar fin de simulación por cola vacía (probablemente terminó normalmente)
                    enviarFinSimulacion(RazonFinSimulacion.FIN_POR_TIEMPO, 
                        "Simulación finalizó: no quedan eventos programados");
                    break;
                }
                if (ev.obtenerInstanteProgramado().isAfter(objetivo)){
                    ctx.log("Simulación alcanzó tiempo objetivo");
                    System.out.println("🎯 Alcanzó tiempo objetivo");
                    
                    // Enviar fin de simulación por alcanzar tiempo objetivo
                    enviarFinSimulacion(RazonFinSimulacion.FIN_POR_TIEMPO, 
                        "Simulación finalizó exitosamente al alcanzar el tiempo objetivo de " + objetivo);
                    break;
                }
                ev = colaDeEventos.poll();
                // System.out.println("📤 Procesando evento #" + (procesados + 1) + ": " +
                // ev.getClass().getSimpleName() +
                // " | Cola restante: " + colaDeEventos.size() +
                // " | Hora: " + ev.obtenerInstanteProgramado());
            }
            finally{
                lock.unlock();
            }
            if (ev == null)
                break;

            // --- Paceo: esperar hasta que el "real clock" alcance el instante simulado del
            // evento
            Clock reloj = ctx.getReloj(); // añade getter si no existe
            if (reloj instanceof RelojEnganado){
                RelojEnganado r = (RelojEnganado) reloj;
                final long LOG_THROTTLE_MS = 1000L; // no loguear más de 1 vez por segundo
                                                    // (ajustable)
                long lastLogTs = 0L;
                // si está pausado, quedarnos en loop hasta resume (con sleep corto)
                while (r.isPaused()){
                    ctx.actualizarAhoraDesdeReloj(); // mostrará pausedSimInstant
                    long nowMillis = System.currentTimeMillis();
                    if (nowMillis - lastLogTs > LOG_THROTTLE_MS){
                        ctx.log("Simulación en PAUSA. ahora sim: " + ctx.obtenerElAhora());
                        lastLogTs = nowMillis;
                    }
                    try{
                        Thread.sleep(200L); // short sleep para ser responsive a resume()
                    }
                    catch (InterruptedException ie){
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // Ahora esperar hasta el real time correspondiente al instante del evento,
                // fragmentando el sleep para poder reaccionar a cambios en factor/pause
                long msToWait = r.millisUntilRealTime(ev.obtenerInstanteProgramado());
                // Si estamos muy adelantados (msToWait > 0) esperamos, en trozos para ser
                // responsive
                while (msToWait > 0){
                    // antes de dormir, sincronizamos 'ahora' con el reloj para que logs muestren la
                    // hora simulada actual
                    ctx.actualizarAhoraDesdeReloj();
                    // límite máximo por iteración para que podamos reaccionar a pause/resume/vel
                    // changes
                    long sleepChunk = Math.min(msToWait, 1000L);
                    long nowMillis = System.currentTimeMillis();
                    if (nowMillis - lastLogTs > LOG_THROTTLE_MS){
                        // ctx.log("Jateando " + sleepChunk + " ms (faltan: " + msToWait + " ms).
                        // ahora sim: " + ctx.obtenerElAhora());
                        lastLogTs = nowMillis;
                    }
                    try{
                        Thread.sleep(sleepChunk);
                    }
                    catch (InterruptedException ie){
                        Thread.currentThread().interrupt();
                        break;
                    }
                    // si en meantime se pausó, salimos al loop de arriba
                    if (r.isPaused())
                        break;
                    msToWait = r.millisUntilRealTime(ev.obtenerInstanteProgramado());
                }
                // si msToWait <= 0 o se pausó -> procesamos (o volveremos a esperar en
                // siguiente iteración)
            }
            else{
                // Clock real: no pacing necesario (ejecución en tiempo real se hará por relojes
                // externos)
                // no hacemos nada especial
            }

            // procesar fuera del lock
            ctx.establecerElAhora(ev.obtenerInstanteProgramado());
            // ctx.log("Ahora son las: " +ev.obtenerInstanteProgramado());
            try{
                ev.procesar(ctx);
                erroresConsecutivos = 0; // Reset contador
            }
            catch (ColapsadoExceptionTemporal ex){
                // log y decidir: continuar o abortar
                ctx.setColapsado(true); // observer
                System.out.println("\n🚨 ========================================");
                System.out.println("🚨 ¡COLAPSO DETECTADO!");
                System.out.println("🚨 ========================================");
                System.out.println("   Evento que causó colapso: " + ev.getClass().getSimpleName());
                System.out.println("   Hora del colapso: " + ctx.getAhora());
                System.out.println("   Razón: " + ex.getMessage());
                System.out.println("🚨 LA SIMULACIÓN SE DETENDRÁ");
                System.out.println("🚨 ========================================\n");
                ctx.log("Motor: colapso detectado -> detener simulación, razón colapso: \n"
                        + ex.getMessage());

                // Determinar tipo específico de colapso y enviar DTO
                RazonFinSimulacion razonColapso = determinarRazonColapso(ex.getMessage());
                enviarFinSimulacion(razonColapso, ex.getMessage());

                break; // Terminar simulación
            }
            catch (Exception ex){
                ex.printStackTrace();
                erroresConsecutivos++;
                ctx.log("ERROR procesando evento " + ev.getClass().getSimpleName() +
                        ": " + ex.getMessage() + " : " + ex.getCause() + " - "
                        + Arrays.stream(ex.getStackTrace()).toList());
                ctx.setConError(true);
                ctx.setErrorMsj(ex.getMessage());
                if (erroresConsecutivos >= MAX_ERRORES_CONSECUTIVOS)
                {
                    throw new RuntimeException("Demasiados errores consecutivos", ex);
                }
                ctx.imprimirReporteLogError(); // no returnear inmediatamente, para análisis de eventos
                                          // malos.
            }
            procesados++;
            if (procesados >= maxEventos){
                ctx.log("Alcanzado límite máximo de eventos: " + maxEventos);
                break;
            }

            // Checkpoint periódico
            if (procesados % 1000 == 0 && ctx.shouldCheckpointNow()){
                // guardarCheckpoint(ctx);
            }
        }
        // Generar reporte final
        // ctx.log("Estado final: " + ctx.getEstadoGlobalSimuladoNoAlgoritmo());
        ctx.imprimirReporteLog();
        return ctx;
    }
    /*
     * Consideraciones en este fragmento:
     *
     * ctx.getReloj() asumo que tienes getter; sino, usa ctx.getClock() o cómo
     * tengas. Si no existe, encapsula el reloj en ContextoSimulacion. Esperamos en
     * trozos de 1000 ms (ajustable) para ser capaces de reaccionar a
     * pause()/resume()/setSpeedFactor() sin quedarse bloqueado durante largos
     * periodos. Si msToWait es negativo (llegamos tarde) procesamos inmediatamente.
     * Si el reloj no es RelojEnganado (por ejemplo Clock.systemUTC() para
     * simulación en tiempo real), no hacemos espera en el motor (supones que el
     * evento fue programado acorde con la ejecución real).
     *
     */

    /**
     * ✅ Método para cancelar la simulación en ejecución Establece el flag que hará
     * que el loop principal se detenga
     */
    public void cancelar()
    {
        this.cancelado = true;
        ctx.log("🛑 Señal de cancelación enviada al motor de simulación");
    }

    /**
     * Envía el DTO de fin de simulación por WebSocket
     * @param razon Razón del fin de la simulación
     * @param mensajeDetalle Mensaje descriptivo adicional
     */
    private void enviarFinSimulacion(RazonFinSimulacion razon, String mensajeDetalle)
    {
        if (webSocketService == null)
        {
            System.out.println("⚠️ WebSocketService no disponible, no se puede enviar fin de simulación");
            return;
        }

        try
        {
            // Construir rutas de la última planificación
            List<RutaPorPedidoDTO> rutasPorPedido = ctx.construirRutasPorPedidoUltimaPlanificacion();

            // ✅ Contar pedidos completados desde el estado actual de la simulación
            // Un pedido está completo si cantidadProductosEntregados == cantidadProductosPedidos
            long totalPedidosCompletados = ctx.getEstado().getPedidos().values().stream()
                    .filter(pedido -> pedido.getCantidadProductosSatisfechos() == pedido.getCantidadProductos())
                    .count();
            
            System.out.println("📊 FIN SIMULACIÓN - Pedidos completados: " + totalPedidosCompletados + 
                " de " + ctx.getEstado().getPedidos().size() + " totales");

            // Determinar si TODOS los pedidos fueron completados
            boolean todosPedidosCompletados = (razon == RazonFinSimulacion.FIN_POR_TIEMPO) &&
                    !ctx.getEstado().hayPedidosPendientesPorProgramar();

            FinSimulacionDTO finDTO = new FinSimulacionDTO(
                    ctx.getAhora(), // instante fin
                    razon, // razón del fin
                    mensajeDetalle, // mensaje detalle
                    ctx.getUltimaPlanificacion(), // instante última planificación
                    rutasPorPedido, // rutas por pedido
                    ctx.getContadorPlanificaciones(), // total planificaciones
                    todosPedidosCompletados, // ¿TODOS los pedidos completados?
                    totalPedidosCompletados // ✅ Total de pedidos completados
            );

            String idSimulacion = String.valueOf(ctx.getIdSimulacion());
            webSocketService.enviarFinSimulacion(idSimulacion, finDTO);
            
            ctx.log("✅ DTO de fin de simulación enviado por WebSocket - Razón: " + razon);
        }
        catch (Exception e)
        {
            System.err.println("❌ Error al enviar fin de simulación por WebSocket: " + e.getMessage());
            e.printStackTrace();
            ctx.log("❌ Error al enviar fin de simulación: " + e.getMessage());
        }
    }

    /**
     * Determina la razón específica del colapso basándose en el mensaje de la excepción
     */
    private RazonFinSimulacion determinarRazonColapso(String mensajeError)
    {
        if (mensajeError == null)
        {
            return RazonFinSimulacion.COLAPSO_PLANIFICACION_INCOMPLETA;
        }

        String mensajeLower = mensajeError.toLowerCase();
        
        if (mensajeLower.contains("no tiene los productos para cargar") || 
            mensajeLower.contains("almacén origen"))
        {
            return RazonFinSimulacion.COLAPSO_ALMACEN_ORIGEN_SIN_PRODUCTOS;
        }
        else if (mensajeLower.contains("vuelo no tiene capacidad"))
        {
            return RazonFinSimulacion.COLAPSO_VUELO_SIN_CAPACIDAD;
        }
        else if (mensajeLower.contains("almacén no aguanta") || 
                 mensajeLower.contains("almacén destino"))
        {
            return RazonFinSimulacion.COLAPSO_ALMACEN_DESTINO_SIN_ESPACIO;
        }
        else if (mensajeLower.contains("colapso en planificación") || 
                 mensajeLower.contains("no se pudo satisfacer"))
        {
            return RazonFinSimulacion.COLAPSO_PLANIFICACION_INCOMPLETA;
        }
        
        // Por defecto, asumimos que es un colapso de planificación
        return RazonFinSimulacion.COLAPSO_PLANIFICACION_INCOMPLETA;
    }

    // Deep copy !
    public static MotorSimulacion crearCopiaMotor(MotorSimulacion motorReferencia){
        ContextoSimulacion nuevoContexto = new ContextoSimulacion(motorReferencia.getCtx());
        MotorSimulacion nuevoMotor =  new MotorSimulacion(nuevoContexto); // ya le da la referencia cíclica al contexto xd
        nuevoMotor.setWebSocketService(null); // para que no envíe wbds.
        return nuevoMotor;
    }
    // en MotorSimulacion
    public EventoSimulacion pollNextEvent() {
        lock.lock();
        try {
            return colaDeEventos.poll(); // devuelve null si vacía
        } finally {
            lock.unlock();
        }
    }

    // opcional: ver la cabeza sin remover
    public EventoSimulacion peekNextEvent() {
        lock.lock();
        try {
            return colaDeEventos.peek();
        } finally {
            lock.unlock();
        }
    }

}

/*
 *
 * Casos especiales y recomendaciones adicionales
 *
 * Eventos simultáneos: si dos o más eventos tienen exactamente el mismo Instant
 * simulado, millisUntilRealTime será (aprox.) el mismo para ambos; tras
 * procesar el primero no vuelvas a dormir porque el siguiente tendrá msToWait
 * <= 0 (o muy pequeño). Por eso no deberías dormir entre eventos con el mismo
 * instante simulado. El código anterior ya respeta eso porque recalcula
 * msToWait al inicio de cada iteración.
 *
 * Precisión y redondeo: hay redondeos por millis. Para simulaciones de alta
 * resolución tal vez quieras usar nanos o double con Duration.toNanos() y
 * conversiones, pero para la mayoría millis bastan.
 *
 * Cambio de factor o rebasing en runtime: tu setSpeedFactor hace rebase
 * (perfecto). Si el factor cambia mientras estás durmiendo, el ciclo
 * fragmentado volverá a llamar millisUntilRealTime y ajustará la espera.
 *
 * Pause/Resume: RelojEnganado.pause() establece pausedSimInstant y paused=true.
 * El motor comprueba isPaused() y se quedará dormido hasta resume(). Cuando
 * resume() rebasea, millisUntilRealTime devuelve un valor correcto acorde a
 * realBase nuevo.
 *
 * Integración con otras señales: si quieres que pause() interrumpa
 * inmediatamente un Thread.sleep() en progreso, puedes:
 *
 * Llamar future.cancel(true) desde otro hilo (si gestionas la tarea con Future
 * y capturas InterruptedException).
 *
 * O tener un Object compartido y hacer wait/notify en vez de Thread.sleep. La
 * opción de sleep fragmentado suele ser suficiente y simple.
 *
 * Simulación en modo “batch”: si te interesa ejecutar la simulación muy rápido
 * (sin pacing), usa Clock.systemUTC() una variante que devuelva instant()
 * avanzando con advanceBy() manualmente. Tu método advanceBy(Duration) ya ayuda
 * para pruebas por lotes: en vez de dormir, puedes avanzar manualmente la hora
 * y procesar.
 *
 * Responsividad: el valor del chunk de sleep (p.ej. 200 ms o 1000 ms) es un
 * trade-off entre CPU y tiempo de respuesta a pause(). Si esperas pausas
 * frecuentes usa trozos más cortos (200ms).
 */
