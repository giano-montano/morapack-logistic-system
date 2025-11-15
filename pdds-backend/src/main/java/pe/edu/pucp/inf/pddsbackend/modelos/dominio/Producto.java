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
    private final UUID id, idEntidadActual;
    private final Instant instanteCreacion;
    private Instant instanteDisponible;
    private Almacen almacenOrigen;

    private Ruta ruta;

    /*
     * Para crear productos existentes
     */
    public Producto(UUID id,
            Almacen almacenOrigen,
            UUID idEntidadActual,
            Instant instanteCreacion)
    {
        this.id = id;
        this.almacenOrigen = almacenOrigen;
        this.idEntidadActual = idEntidadActual;
        this.instanteCreacion = instanteCreacion;
    }

    /*
     * Para crear productos inexistentes
     */
    public Producto(Almacen almacenOrigen,
            Instant instanteCreacion)
    {
        this.id = UUID.randomUUID();
        this.almacenOrigen = almacenOrigen;
        this.idEntidadActual = null;
        this.instanteCreacion = instanteCreacion;
        this.instanteDisponible = instanteCreacion;
    }

}
