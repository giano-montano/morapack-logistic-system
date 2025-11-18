package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Pedido implements Serializable
{
    private final UUID id;
    private final Integer cantidad, cantidadEntregada;
    private final Instant instanteRegistro;
    private final Almacen almacenDestino;

    private Instant instanteEntrega;
    private List<Producto> inventario;

    /*
     * Constructor inicial. No olvidar llamar a setInventario() para que el objeto
     * este bien definido
     *
     * List<Producto> inventario -> Solo almacena los productos que van a satisfacer
     * la demanda. Se cumple inventario.size() = cantidad - cantidadEntregada
     */
    public Pedido(UUID id,
            Integer cantidad,
            Integer cantidadEntregada,
            Instant instanteRegistro,
            Almacen almacenDestino,
            List<Producto> productosEntregados)
    {

        this.id = id;
        this.cantidad = cantidad;
        this.cantidadEntregada = cantidadEntregada;
        this.instanteRegistro = instanteRegistro;
        this.almacenDestino = almacenDestino;
        this.instanteEntrega = Pedido.obtenerInstanteMaximoEntrega(productosEntregados,
                this.almacenDestino, this.instanteRegistro);

        this.inventario = new ArrayList<>();
    }

    /*
     * Obtiene el instante de entrega máximo dependiendo de si ya tiene Productos
     * intercontinentales
     */
    private static Instant obtenerInstanteMaximoEntrega(List<Producto> productosEntregados,
            Almacen almacenDestino, Instant instanteRegistro)
    {
        Boolean esIntercontinental;

        esIntercontinental = false;

        for (Producto producto : productosEntregados)
        {
            if (Almacen.esIntercontinental(producto.getAlmacenOrigen(), almacenDestino))
            {
                esIntercontinental = true;
                break;
            }
            else
            {
                esIntercontinental = false;
            }
        }

        return (esIntercontinental)
                ? instanteRegistro.plus(Duration.ofDays(3))
                : instanteRegistro.plus(Duration.ofDays(2));
    }

    /*
     * Retorna cuantos Productos faltan entregar
     */
    public Integer getDemanda()
    {
        return this.cantidad - this.cantidadEntregada - this.inventario.size();
    }

    /*
     * Asignar un producto
     */
    public Boolean asignarProducto(Producto producto)
    {
        if (this.getDemanda() - this.inventario.size() > 0)
        {
            this.inventario.add(producto);
            return true;
        }

        return false;
    }

    /*
     * Verifica que esta satisfecho totalmente
     */
    public Boolean estaSatisfecho()
    {
        return (this.getDemanda() == this.inventario.size());
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        // Lambda para formatear instantes
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("Pedido (").append(id).append(")\n");
        sb.append("\tCantidad: ").append(cantidadEntregada).append("/").append(cantidad)
                .append("\n");
        sb.append("\tRegistro: ").append(formatInstant.apply(instanteRegistro)).append("\n");

        if (instanteEntrega != null)
        {
            sb.append("\tEntrega: ").append(formatInstant.apply(instanteEntrega)).append("\n");
        }
        else
        {
            sb.append("\tEntrega: Pendiente\n");
        }

        sb.append("\tDestino: ").append(almacenDestino.getCiudad()).append(", ")
                .append(almacenDestino.getPais()).append("\n");
        sb.append("\tInventario (").append(inventario.size()).append(" productos):\n");

        if (inventario.isEmpty())
        {
            sb.append("\t\tVacio");
        }
        else
        {
            sb.append("\t\t[");
            for (int i = 0; i < inventario.size(); i++)
            {
                if (i > 0)
                    sb.append(", ");
                sb.append(inventario.get(i).getId().toString().substring(0, 8)).append("...");
            }
            sb.append("]");
        }

        return sb.toString();
    }
}
