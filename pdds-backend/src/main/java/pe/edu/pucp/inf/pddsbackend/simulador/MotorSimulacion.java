package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

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
        while (true) {
            EventoSimulacion ev;
            lock.lock();
            try {
                ev = colaDeEventos.peek();
                if (ev == null) break;
                if (ev.obtenerInstanteProgramado().isAfter(objetivo)) break;
                ev = colaDeEventos.poll();
            } finally {
                lock.unlock();
            }
            if (ev == null) break;
            // procesar fuera del lock
            ctx.establecerElAhora(ev.obtenerInstanteProgramado());
            try {
                ev.procesar(ctx);
            } catch (Exception ex) {
                // log y decidir: continuar o abortar
            }
            procesados++;
            if (procesados >= maxEventos) break;
        }
    }
}
