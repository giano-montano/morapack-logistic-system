package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.Data;
import lombok.ToString;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.utils.RelojEnganado;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Data
@ToString(exclude = {"ctx"} )
public class MotorSimulacion implements SchedulerSimulacion {
    private final PriorityQueue<EventoSimulacion> colaDeEventos = new PriorityQueue<>();
    private final ContextoSimulacion ctx;
    private final ReentrantLock lock = new ReentrantLock();

    public MotorSimulacion(ContextoSimulacion ctx) {
        this.ctx = ctx;
        // link back: permitir ctx.programar delegar a este motor
        ctx.setScheduler(this);
    }

    @Override
    public void programar(EventoSimulacion e) {
        lock.lock();
        try {
            colaDeEventos.add(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean cancelar(UUID eventId) {
        lock.lock();
        try {
            // opción simple: iterar q y remover matching id (ineficiente para cola grande)
            Iterator<EventoSimulacion> it = colaDeEventos.iterator();
            while (it.hasNext()) {
                EventoSimulacion ev = it.next();
                if (ev.getId().equals(eventId)) { it.remove(); return true; }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public ContextoSimulacion correrHasta(Instant objetivo, long maxEventos) throws Exception {
        long procesados = 0;
        int erroresConsecutivos = 0;
        final int MAX_ERRORES_CONSECUTIVOS = 5;

        while (true) {
            EventoSimulacion ev;
            lock.lock();
            try {
                ev = colaDeEventos.peek();
                if (ev == null) {
                    ctx.log("Simulación terminada: cola de eventos vacía");
                    break;
                }
                if (ev.obtenerInstanteProgramado().isAfter(objetivo)) {
                    ctx.log("Simulación alcanzó tiempo objetivo");
                    break;
                }
                ev = colaDeEventos.poll();
            } finally {
                lock.unlock();
            }
            if (ev == null) break;

            // --- Paceo: esperar hasta que el "real clock" alcance el instante simulado del evento
            Clock reloj = ctx.getReloj(); // añade getter si no existe
            if (reloj instanceof RelojEnganado) {
                RelojEnganado r = (RelojEnganado) reloj;
                final long LOG_THROTTLE_MS = 1000L; // no loguear más de 1 vez por segundo (ajustable)
                long lastLogTs = 0L;
                // si está pausado, quedarnos en loop hasta resume (con sleep corto)
                while (r.isPaused()) {
                    ctx.actualizarAhoraDesdeReloj(); // mostrará pausedSimInstant
                    long nowMillis = System.currentTimeMillis();
                    if (nowMillis - lastLogTs > LOG_THROTTLE_MS) {
                        ctx.log("Simulación en PAUSA. ahora sim: " + ctx.obtenerElAhora());
                        lastLogTs = nowMillis;
                    }
                    try {
                        Thread.sleep(200L); // short sleep para ser responsive a resume()
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // Ahora esperar hasta el real time correspondiente al instante del evento,
                // fragmentando el sleep para poder reaccionar a cambios en factor/pause
                long msToWait = r.millisUntilRealTime(ev.obtenerInstanteProgramado());
                // Si estamos muy adelantados (msToWait > 0) esperamos, en trozos para ser responsive
                while (msToWait > 0) {
                    // antes de dormir, sincronizamos 'ahora' con el reloj para que logs muestren la hora simulada actual
                    ctx.actualizarAhoraDesdeReloj();
                    // límite máximo por iteración para que podamos reaccionar a pause/resume/vel changes
                    long sleepChunk = Math.min(msToWait, 1000L);
                    long nowMillis = System.currentTimeMillis();
                    if (nowMillis - lastLogTs > LOG_THROTTLE_MS) {
                        ctx.log("Jateando " + sleepChunk + " ms (faltan: " + msToWait + " ms). ahora sim: " + ctx.obtenerElAhora());
                        lastLogTs = nowMillis;
                    }
                    try {
                        Thread.sleep(sleepChunk);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    // si en meantime se pausó, salimos al loop de arriba
                    if (r.isPaused()) break;
                    msToWait = r.millisUntilRealTime(ev.obtenerInstanteProgramado());
                }
                // si msToWait <= 0 o se pausó -> procesamos (o volveremos a esperar en siguiente iteración)
            } else {
                // Clock real: no pacing necesario (ejecución en tiempo real se hará por relojes externos)
                // no hacemos nada especial
            }

            // procesar fuera del lock
            ctx.establecerElAhora(ev.obtenerInstanteProgramado());
            ctx.log("Ahora son las: " +ev.obtenerInstanteProgramado());
            try {
                ev.procesar(ctx);
                erroresConsecutivos = 0; // Reset contador
            } catch (ColapsadoExceptionTemporal ex) {
                // log y decidir: continuar o abortar
                ctx.setColapsado(true); // observer
                ctx.log("Motor: colapso detectado -> detener simulación, razón colapso: \n"+ ex.getMessage());
                break; // Terminar simulación
            } catch (Exception ex) {
                erroresConsecutivos++;
                ctx.log("ERROR procesando evento " + ev.getClass().getSimpleName() +
                        ": " + ex.getMessage() + " : " + ex.getCause());
                ctx.setConError(true);
                ctx.setErrorMsj(ex.getMessage());
                if (erroresConsecutivos >= MAX_ERRORES_CONSECUTIVOS) {
                    throw new RuntimeException("Demasiados errores consecutivos", ex);
                }
                ctx.imprimirReporteLog(); // no returnear inmediatamente, para análisis de eventos malos.
            }
            procesados++;
            if (procesados >= maxEventos) {
                ctx.log("Alcanzado límite máximo de eventos: " + maxEventos);
                break;
            }

            // Checkpoint periódico
            if (procesados % 1000 == 0 && ctx.shouldCheckpointNow()) {
//                guardarCheckpoint(ctx);
            }
        }
        // Generar reporte final
//        ctx.log("Estado final: " + ctx.getEstadoGlobalSimuladoNoAlgoritmo());
        ctx.imprimirReporteLog();
        return ctx;
    }
    /*
    Consideraciones en este fragmento:

ctx.getReloj() asumo que tienes getter; sino, usa ctx.getClock() o cómo tengas. Si no existe, encapsula el reloj
en ContextoSimulacion.
Esperamos en trozos de 1000 ms (ajustable) para ser capaces de reaccionar a pause()/resume()/setSpeedFactor()
sin quedarse bloqueado durante largos periodos.
Si msToWait es negativo (llegamos tarde) procesamos inmediatamente.
Si el reloj no es RelojEnganado (por ejemplo Clock.systemUTC() para simulación en tiempo real), no hacemos espera en
el motor (supones que el evento fue programado acorde con la ejecución real).

     */
}

/*

Casos especiales y recomendaciones adicionales

Eventos simultáneos: si dos o más eventos tienen exactamente el mismo Instant simulado,
millisUntilRealTime será (aprox.) el mismo para ambos; tras procesar el primero no vuelvas a dormir
porque el siguiente tendrá msToWait <= 0 (o muy pequeño). Por eso no deberías dormir entre eventos con el mismo
instante simulado. El código anterior ya respeta eso porque recalcula msToWait al inicio de cada iteración.

Precisión y redondeo: hay redondeos por millis. Para simulaciones de alta resolución tal vez quieras usar
nanos o double con Duration.toNanos() y conversiones, pero para la mayoría millis bastan.

Cambio de factor o rebasing en runtime: tu setSpeedFactor hace rebase (perfecto). Si el factor cambia mientras
estás durmiendo, el ciclo fragmentado volverá a llamar millisUntilRealTime y ajustará la espera.

Pause/Resume: RelojEnganado.pause() establece pausedSimInstant y paused=true.
El motor comprueba isPaused() y se quedará dormido hasta resume(). Cuando resume() rebasea, millisUntilRealTime
devuelve un valor correcto acorde a realBase nuevo.

Integración con otras señales: si quieres que pause() interrumpa inmediatamente un Thread.sleep() en progreso, puedes:

Llamar future.cancel(true) desde otro hilo (si gestionas la tarea con Future y capturas InterruptedException).

O tener un Object compartido y hacer wait/notify en vez de Thread.sleep. La opción de sleep fragmentado
suele ser suficiente y simple.

Simulación en modo “batch”: si te interesa ejecutar la simulación muy rápido (sin pacing), usa Clock.systemUTC()
  una variante que devuelva instant() avanzando con advanceBy() manualmente.
  Tu método advanceBy(Duration) ya ayuda para pruebas por lotes: en vez de dormir, puedes avanzar manualmente la hora y procesar.

Responsividad: el valor del chunk de sleep (p.ej. 200 ms o 1000 ms) es un trade-off
entre CPU y tiempo de respuesta a pause(). Si esperas pausas frecuentes usa trozos más cortos (200ms).
 */
