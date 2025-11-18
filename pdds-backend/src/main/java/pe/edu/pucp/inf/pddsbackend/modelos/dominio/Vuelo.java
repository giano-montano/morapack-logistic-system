package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Vuelo implements Serializable
{
    @EqualsAndHashCode.Include
    private final UUID id;

    private final Integer capacidad;
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
            Almacen almacenOrigen,
            Almacen almacenDestino,
            Instant instanteSalida,
            Instant instanteLlegada)
    {
        this.id = id;
        this.capacidad = capacidad;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.feromona = 0D;

        this.inventario = new ArrayList<>();
    }

    /*
     * Asigna una lista de Productos al inventario del Vuelo y a los Almacenes
     */
    
    public Boolean asignarProductosAVuelo(List<Producto> productosAAsignar)
    {
        Boolean asignadoCorrectamente;
        
        asignadoCorrectamente = true;
        
        asignadoCorrectamente &= this.almacenOrigen.registrarCambioNegativo(this.instanteSalida, productosAAsignar.size());
        asignadoCorrectamente &= this.asignarProductosAInventario(productosAAsignar);
        asignadoCorrectamente &= this.almacenDestino.registrarCambioPositivo(this.instanteLlegada, productosAAsignar.size());
        
        return asignadoCorrectamente;
    }

    /*
     * Desasigna una lista de Productos al inventario del Vuelo y a los Almacenes
     */
    public void desasignarProductosDeVuelo(List<Producto> productosADesasignar)
    {
        this.almacenOrigen.deshacerCambioNegativo(this.instanteSalida, productosADesasignar.size());
        this.desasignarProductosAInventario(productosADesasignar);
        this.almacenOrigen.deshacerCambioPositivo(this.instanteLlegada, productosADesasignar.size());
    }


    /*
     * Asigna una lista de Productos al inventario del Vuelo
     */
    private Boolean asignarProductosAInventario(List<Producto> productosAAsignar)
    {
        Boolean asignadoCorrectamente;

        if(this.capacidad > this.inventario.size() + productosAAsignar.size())
        {
            asignadoCorrectamente =  true;
        }else{
            asignadoCorrectamente = false;
        }

        this.inventario.addAll(productosAAsignar);

        return asignadoCorrectamente;
    }

    /*
     * Des asigna una lista de Productos al inventario del Vuelo
     */
    private void desasignarProductosAInventario(List<Producto> productosAAsignar)
    {
        this.inventario.removeAll(productosAAsignar);
    }

    /*
     * 
     */
    public Integer getEspacioVacio()
    {
        return this.capacidad - this.inventario.size();
    }
    /*
     * Para saber si el vuelo es intercontinental
     */
    public Boolean esIntercontinental()
    {
        return Almacen.esIntercontinental(almacenOrigen, almacenDestino);
    }

    /*
     * Impresión
     */
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
        sb.append("\tCapacidad: ").append(inventario.size()).append("/").append(capacidad)
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
