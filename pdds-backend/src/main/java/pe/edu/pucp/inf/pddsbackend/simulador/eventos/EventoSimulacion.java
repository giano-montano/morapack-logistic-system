package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.UUID;

public interface EventoSimulacion extends Comparable<EventoSimulacion>
{

    UUID getId();

    Instant obtenerInstanteProgramado();

    void procesar(ContextoSimulacion ctx) throws Exception;

    // TipoEvento getTipoEvento(); // y ahí metida la prioridad :o, pero nah
    int getPriority();

    @Override
    default int compareTo(EventoSimulacion other)
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
