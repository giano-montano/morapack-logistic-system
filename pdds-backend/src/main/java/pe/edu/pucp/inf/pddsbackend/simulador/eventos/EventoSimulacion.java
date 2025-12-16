package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.UUID;

public abstract class EventoSimulacion implements Comparable<EventoSimulacion> {

    @Setter
    private SimulacionWebSocketService webSocketService;

    public abstract UUID getId();

    public abstract Instant obtenerInstanteProgramado();

    public abstract void procesar(ContextoSimulacion ctx) throws Exception;

    // TipoEvento getTipoEvento(); // y ahí metida la prioridad :o, pero nah
    public abstract int getPriority();

    @Override
    public int compareTo(EventoSimulacion other)
    { // Para todos, es la sobreescritura de un método DEFAULT en interfaz padre
        int cmp = this.obtenerInstanteProgramado().compareTo(other.obtenerInstanteProgramado());
        if (cmp != 0)
            return cmp;
        // tie-breaker: prioridad por tipo
        int p1 = this.getPriority(); // .getTipoEvento().getPriority()
        int p2 = other.getPriority();
        if (p1 != p2)
            return Integer.compare(p1, p2);

        return this.getId().compareTo(other.getId()); // determinismo
        // return
        // this.obtenerInstanteProgramado().compareTo(other.obtenerInstanteProgramado());
    }

}
