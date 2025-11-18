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
public class Almacen implements Serializable
{
    @EqualsAndHashCode.Include
    private final UUID id;

    private final Boolean esInfinito;
    private final Integer capacidad, capacidadUsada, utc;
    private final String ciudad, pais;
    private final Continente continente;

    private List<Producto> inventario;
    private Cambios cambios;

    /*
     * Constructor inicial. No olvidar llamar a setInventario() para que el objeto
     * este bien definido
     */
    public Almacen(String id,
            Integer capacidad,
            Integer capacidadUsada,
            Integer utc,
            String ciudad,
            String pais,
            Continente continente)
    {
        this.id = UUID.nameUUIDFromBytes(id.getBytes());
        this.capacidad = capacidad;
        this.capacidadUsada = capacidadUsada;
        this.utc = utc;
        this.ciudad = ciudad;
        this.pais = pais;
        this.continente = continente;
        this.esInfinito = (this.capacidad < 0) ? true : false;

        this.inventario = new ArrayList<>();
        this.cambios = new Cambios(this.capacidadUsada);
    }

    /*
     * Método para comparar dos almacenes y saber si están en continentes diferentes
     */
    public static Boolean esIntercontinental(Almacen origen, Almacen destino)
    {
        return origen.continente != destino.continente;
    }

    /*
     * Método para comparar dos almacenes y saber si son el mismo
     */
    public static Boolean esIgual(Almacen origen, Almacen destino)
    {
        return origen.id == destino.id;
    }

    /*
     * Inserta un cambio en el almacén
     */
    public void insertarCambio(Instant instante, Integer cambio)
    {
        this.cambios.add(instante, cambio);
    }

    /*
     * Saber si para un instante dado el almacén va a tener espacio
     */

    public Boolean hayEspacio(Instant instante, Integer espacioRequerido)
    {
        Integer inventarioEnInstante, inventarioFinal;

        inventarioEnInstante = this.cambios.calcularInventarioEnInstante(instante);
        inventarioFinal = inventarioEnInstante + espacioRequerido;

        return (inventarioFinal <= this.capacidad) ? true : false;
    }

    /*
     * Inserta Producto en inventario
     */
    public void insertarProducto(Producto producto)
    {
        this.inventario.add(producto);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Almacen (").append(id).append(")\n");
        sb.append("\tUbicacion: ").append(ciudad).append(", ").append(pais).append("\n");
        sb.append("\tContinente: ").append(continente).append(" (UTC").append(utc >= 0 ? "+" : "")
                .append(utc).append(")\n");
        sb.append("\tCapacidad: ").append(capacidadUsada).append("/").append(capacidad);
        if (esInfinito)
        {
            sb.append(" (Infinito)");
        }
        sb.append("\n");
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
