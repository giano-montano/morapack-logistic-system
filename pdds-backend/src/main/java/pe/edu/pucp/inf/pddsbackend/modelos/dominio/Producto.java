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
    private Boolean esIntercontinental;
    private Instant instanteDisponible;
    private Almacen almacenOrigen;

    private Pedido pedido;
    private Ruta ruta;

    /*
     * Para crear productos existentes
     */
    public Producto(UUID id, Boolean esIntercontinental, Almacen almacenOrigen, UUID idEntidadActual)
    {
        this.id = id;
        this.esIntercontinental = esIntercontinental; 
        this.almacenOrigen = almacenOrigen;
        this.idEntidadActual = idEntidadActual;
        this.esIntercontinental = esIntercontinental;
        this.pedido = null;

    }

    /*
     * Para crear Productos nuevos
     */
    public Producto(Ruta rutaAsignada, Pedido pedidoAsignado)
    {
        this.id = UUID.randomUUID();
        this.idEntidadActual = null;
        this.almacenOrigen = rutaAsignada.getAlmacenOrigen();
        this.esIntercontinental = false;
        this.ruta = rutaAsignada;
        this.pedido = pedidoAsignado;	
    }

    /*
     * Si esta asignado a un producto
     */
    public Boolean estaAsignado()
    {
        return (this.pedido != null);
    }

    /*
     * Para saber si a un determinado momento el Producto estará disponible. Esto solo tiene sentido si el Producto esta en pleno vuelo
     */
    public Boolean estaDisponible(Instant instanteActual)
    {
        return (instanteActual.isAfter(this.instanteDisponible));
    }
}

