package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Planificacion
{
    private final UUID id;
    private final Long semilla;
    private Instant instanteActual, inicioOperaciones;
}
