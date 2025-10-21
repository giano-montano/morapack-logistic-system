package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Duration;
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

    private Boolean existe, entregado, esIntercontinental;
    private Almacen destino;
    private Instant instanteEntrega;

    /*
     * Para crear productos inexistentes
     */
    public Producto(Almacen origen,
            Almacen destino,
            Instant instanteCreacion)
    {
        this.id = UUID.randomUUID();
        this.origen = origen;
        this.instanteCreacion = instanteCreacion;

        this.existe = false;
        this.entregado = false;
        this.esIntercontinental = Almacen.esIntercontinental(origen, destino);
        this.destino = destino;
        this.instanteEntrega = instanteCreacion
                .plus(Duration.ofDays((this.esIntercontinental ? 3 : 2)));
    }

    /*
     * Para crear productos existentes
     */
    public Producto(UUID id,
            Almacen origen,
            Almacen destino,
            Instant instanteCreacion)
    {
        this.id = id;
        this.origen = origen;
        this.instanteCreacion = instanteCreacion;

        this.existe = true;
        this.entregado = false;
        this.esIntercontinental = Almacen.esIntercontinental(origen, destino);
        this.destino = destino;
        this.instanteEntrega = instanteCreacion
                .plus(Duration.ofDays((this.esIntercontinental ? 3 : 2)));
    }
}
