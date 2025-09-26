package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.UUID;

public interface EventoSimulacion extends Comparable<EventoSimulacion>  {

    UUID getId();

    Instant obtenerInstanteProgramado();

    void procesar(ContextoSimulacion ctx) throws Exception;

    @Override
    default int compareTo(EventoSimulacion other) {
        return this.obtenerInstanteProgramado().compareTo(other.obtenerInstanteProgramado());
    }


}
