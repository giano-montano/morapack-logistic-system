package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Vuelo implements Serializable
{
    @EqualsAndHashCode.Include
    private final UUID id;

    private final Integer capacidad, capacidadUsada;
    private final Almacen almacenOrigen, almacenDestino;
    private final Instant instanteSalida, instanteLlegada;

    private Double feromona;
    private List<Producto> inventario;

    /*
     * Constructor inicial. No olvidar llamar a setInventario() para que el objeto
     * este bien definido
     */
    public Vuelo(UUID id,
            Integer capacidad,
            Integer capacidadUsada,
            Almacen almacenOrigen,
            Almacen almacenDestino,
            Instant instanteSalida,
            Instant instanteLlegada)
    {
        this.id = id;
        this.capacidad = capacidad;
        this.capacidadUsada = capacidadUsada;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.feromona = 0D;

        this.inventario = new ArrayList<>();
    }

    public Boolean esIntercontinental()
    {
        return Almacen.esIntercontinental(almacenOrigen, almacenDestino);
    }

    /*
     * Inserta Producto en inventario
     */
    public void insertarProducto(Producto producto)
    {
        this.inventario.add(producto);
    }

    /*
     * Obtiene la capacidad disponible
     */
    public Integer getCapacidadDisponible()
    {
        return this.capacidad - this.inventario.size();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("Vuelo (").append(id).append(")\n");
        sb.append("\tOrigen: (").append(formatInstant.apply(instanteSalida)).append("; ")
                .append(almacenOrigen.getCiudad()).append(", ").append(almacenOrigen.getPais())
                .append(")\n");
        sb.append("\tDestino: (").append(formatInstant.apply(instanteLlegada)).append("; ")
                .append(almacenDestino.getCiudad()).append(", ").append(almacenDestino.getPais())
                .append(")\n");
        sb.append("\tCapacidad: ").append(capacidadUsada).append("/").append(capacidad)
                .append("\n");
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
