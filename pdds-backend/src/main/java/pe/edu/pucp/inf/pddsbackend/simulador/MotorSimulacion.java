package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Data
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

    public void correrHasta(Instant objetivo, long maxEventos) throws Exception {
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
            // procesar fuera del lock
            ctx.establecerElAhora(ev.obtenerInstanteProgramado());
            try {
                ev.procesar(ctx);
                erroresConsecutivos = 0; // Reset contador
            } catch (ColapsadoExceptionTemporal ex) {
                // log y decidir: continuar o abortar
                ctx.log("COLAPSO DETECTADO en " + ctx.obtenerElAhora());
                ctx.registrarMetrica("tiempo_hasta_colapso_minutos",
                        Duration.between(ctx.getReloj().instant(), ctx.getAhora()).toMinutes());

                if (ctx.getParams().tipoSimulacion() == TipoSimulacion.HASTA_COLAPSO) {
                    break; // Terminar simulación
                }
            } catch (Exception ex) {
                erroresConsecutivos++;
                ctx.log("ERROR procesando evento " + ev.getClass().getSimpleName() +
                        ": " + ex.getMessage());

                if (erroresConsecutivos >= MAX_ERRORES_CONSECUTIVOS) {
                    throw new RuntimeException("Demasiados errores consecutivos", ex);
                }
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
        ctx.imprimirReporteLog();
    }
}
