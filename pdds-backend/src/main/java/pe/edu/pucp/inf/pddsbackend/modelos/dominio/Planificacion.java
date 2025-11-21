package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Planificacion
{
    private final Long id;
    private Instant instanteActual;
}
