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

@Getter
@Setter
@EqualsAndHashCode
public class Pedido implements Serializable
{
    private final UUID id;
    private final Integer capacidad;
    private final Instant instanteRegistro;
    private final Almacen almacenDestino;

    private Instant instanteEntrega;
    private List<Producto> inventario;

    /*
     * Constructor de Pedido
     */
    public Pedido(UUID id,
            Integer cantidad,
            Instant instanteRegistro,
            Instant instanteLlegada,
            Almacen almacenDestino,
            List<Producto> productosEntregados)
    {

        this.id = id;
        this.capacidad = cantidad;
        this.instanteRegistro = instanteRegistro;
        this.instanteEntrega = instanteRegistro;
        this.almacenDestino = almacenDestino;

        this.inventario = new ArrayList<>();
    }

    /*
     * Obtiene el instante de entrega máximo dependiendo de si ya tiene Productos
     * intercontinentales
     */
    public Boolean esIntercontinental()
    {
        Long plazoEntrega;

        plazoEntrega = Duration.between(this.instanteRegistro, this.instanteEntrega).toDays();
        

        return (plazoEntrega == 2L)? false : true;
    }

    /*
     * Retorna cuantos Productos faltan entregar
     */
    public Integer getCantidadProductosPendientes()
    {
        return this.capacidad - this.inventario.size();
    }

    /*
     * Asignar un Producto al inventario del Pedido
     */

    public Boolean asignarProductosAPedido(List<Producto> productosAAsignar)
    {
        if(Duration.between(this.instanteRegistro, this.instanteEntrega).toDays() == 2L)
        {
            for(Producto producto : productosAAsignar)
            {
                if(producto.getEsIntercontinental() == true)
                {
                    this.instanteEntrega.plus(Duration.ofDays(1));
                    break;
                }	
            }		
        }

        this.inventario.addAll(productosAAsignar);

        return (this.getCantidadProductosPendientes() < 0)? false : true;
    }

    /*
     * Des asignar un Producto al inventario del Pedido
     */
    public void desasignarProductosAPedido(List<Producto> productosAAsignar)
    {
        Boolean esIntercontinental;

        esIntercontinental = false;
        this.inventario.removeAll(productosAAsignar);

        for(Producto producto : this.inventario)
        {
            if(producto.getEsIntercontinental() == true)
            {
                esIntercontinental = true;
                break;
            } 
        }

        if(Duration.between(this.instanteRegistro, this.instanteEntrega).toDays() == 3L && esIntercontinental == false)
        {
            this.instanteEntrega = this.instanteRegistro.plus(Duration.ofDays(2));
        }
    }

    /*
     * Obtiene la cantidad de Productos faltantes para satisfacerlo
     */
    public Integer getDemanda() {
        return this.capacidad - this.inventario.size();
    }

    /*
     * Impresión
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        // Lambda para formatear instantes
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("Pedido (").append(id).append(")\n");
        sb.append("\tCantidad: ").append(inventario.size()).append("/").append(capacidad)
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
