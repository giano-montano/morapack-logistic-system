package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.locks.ReentrantLock;

public class RelojEnganado extends Clock {
    private final ZoneId zone;
    private final ReentrantLock lock = new ReentrantLock();

    // puntos de referencia para el cálculo: sim = simBase + (nowReal - realBase) * factor
    private Instant realBase;    // momento real de referencia (System.currentTimeMillis)
    private Instant simBase;     // instante simulado correspondiente a realBase
    private double speedFactor;  // 1.0 = tiempo real, 60.0 = 60x

    private boolean paused = false;
    private Instant pausedSimInstant = null;

    public RelojEnganado(Instant simStart, double speedFactor, ZoneId zone) {
        this.realBase = Instant.now();
        this.simBase = simStart;
        this.speedFactor = speedFactor;
        this.zone = zone == null ? ZoneId.of("UTC") : zone; // o la zona default del sistema?
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new RelojEnganado(this.getSimulatedInstant(), this.speedFactor, zone);
    }

    @Override
    public Instant instant() {
        lock.lock();
        try {
            if (paused) {
                return pausedSimInstant;
            }
            Instant nowReal = Instant.now();
            long deltaRealMillis = Duration.between(realBase, nowReal).toMillis();
            // simDeltaMillis = deltaRealMillis * factor
            long simDeltaMillis = (long) (deltaRealMillis * speedFactor);
            return simBase.plusMillis(simDeltaMillis);
        } finally {
            lock.unlock();
        }
    }

    // helpers
    public Instant getSimulatedInstant() {
        return instant();
    }

    public void setSpeedFactor(double newFactor) {
        if (newFactor <= 0) throw new IllegalArgumentException("factor > 0");
        lock.lock();
        try {
            // "rebase" to preserve continuity: simBase <- currentSim, realBase <- nowReal
            Instant currentSim = instant();
            this.realBase = Instant.now();
            this.simBase = currentSim;
            this.speedFactor = newFactor;
        } finally {
            lock.unlock();
        }
    }

    public double getSpeedFactor() {
        lock.lock();
        try { return speedFactor; } finally { lock.unlock(); }
    }

    public void pause() {
        lock.lock();
        try {
            if (!paused) {
                pausedSimInstant = instant();
                paused = true;
            }
        } finally { lock.unlock(); }
    }

    public void resume() {
        lock.lock();
        try {
            if (paused) {
                // rebasing for continuity
                realBase = Instant.now();
                simBase = pausedSimInstant;
                paused = false;
                pausedSimInstant = null;
            }
        } finally { lock.unlock(); }
    }

    // For batch tests: advance simulated clock manually (useful for integration with DES)
    public void advanceBy(Duration d) {
        lock.lock();
        try {
            if (paused) {
                pausedSimInstant = pausedSimInstant.plus(d);
            } else {
                // rebase simBase forward, set realBase to now
                simBase = instant().plus(d);
                realBase = Instant.now();
            }
        } finally { lock.unlock(); }
    }

    // dentro de RelojEnganado (añadir después de los métodos existentes)

    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Convierte un instante simulado en el instante real correspondiente
     * atendiendo a realBase, simBase y speedFactor.
     */
    public Instant toRealInstant(Instant simInstant) {
        lock.lock();
        try {
            if (paused) {
                // Si está pausado no hay un instante real asociado de forma útil.
                // Devuelve now para que el motor se quede esperando externamente.
                return Instant.now();
            }
            long simDeltaMillis = Duration.between(simBase, simInstant).toMillis();
            // evitar división por 0 — speedFactor siempre > 0 si lo validas antes
            long realDeltaMillis = (long) Math.floor(simDeltaMillis / speedFactor);
            return realBase.plusMillis(realDeltaMillis);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Milisegundos desde Instant.now() hasta el instante real
     * que corresponde a simInstant. Puede ser negativo si estamos "retrasados".
     */
    public long millisUntilRealTime(Instant simInstant) {
        lock.lock();
        try {
            if (paused) {
                // Signal largo para indicar que debe esperar (motor comprobará isPaused)
                return Long.MAX_VALUE;
            }
            Instant targetReal = toRealInstant(simInstant);
            return Duration.between(Instant.now(), targetReal).toMillis();
        } finally {
            lock.unlock();
        }
    }
    /*
    * Notas:

Usamos floor en el cálculo realDeltaMillis para no adelantar el reloj real por redondeos; puedes usar Math.round si prefieres.

millisUntilRealTime devuelve Long.MAX_VALUE si está pausado — el motor comprobará isPaused() y actuará en consecuencia.
* */

}
