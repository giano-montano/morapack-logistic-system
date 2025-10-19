package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Producto
{
    private final UUID id;
    private final Almacen origen;
    private final Instant instanteCreacion;

    private Boolean existe, entregado;
    private Almacen destino;

    public Producto(Almacen origen,
            Almacen destino,
            Instant creacion)
    {
        this.id = UUID.randomUUID();
        this.origen = origen;
        this.instanteCreacion = creacion;

        this.destino = destino;
        this.existe = false;
        this.entregado = false;
    }
}
