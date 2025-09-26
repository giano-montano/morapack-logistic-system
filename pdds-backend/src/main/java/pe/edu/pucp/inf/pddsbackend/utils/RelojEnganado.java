package pe.edu.pucp.inf.pddsbackend.utils;

import org.springframework.stereotype.Component;

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
}
