package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
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
public class Producto implements Serializable
{
    private final UUID id, idEntidadActual;
    private Instant instanteDisponible;
    private Almacen almacenOrigen;

    private Pedido pedidoAsignado;
    private Ruta ruta;

    /*
     * Para crear productos existentes
     */
    public Producto(UUID id,
            Almacen almacenOrigen,
            UUID idEntidadActual)
    {
        this.id = id;
        this.almacenOrigen = almacenOrigen;
        this.idEntidadActual = idEntidadActual;

    }

    /*
     * Para crear productos inexistentes
     */
    public Producto(Almacen almacenOrigen,
            Instant instanteDisponible,
            Ruta ruta)
    {
        this.id = UUID.randomUUID();
        this.almacenOrigen = almacenOrigen;
        this.idEntidadActual = null;
        this.ruta = ruta;
        this.instanteDisponible = instanteDisponible;
    }

    /*
     * Asignar Producto a Pedido
     */
    public void asignarAPedido(Pedido pedido, Ruta ruta)
    {
        this.pedidoAsignado = pedido;
        this.ruta = ruta;
    }

    /*
     * Método para saber si el Producto ya fue asignado a un Pedido. Osea, si ya fue
     * planificado
     */
    public Boolean estaAsignadoAUnPedido()
    {
        return (this.ruta != null && this.pedidoAsignado != null);
    }
}
