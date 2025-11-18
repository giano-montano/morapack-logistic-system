package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class Ruta implements Serializable
{
    private final UUID id;
    private final Almacen almacenOrigen, almacenDestino;
    private final Instant instanteSalida, instanteLlegada;
    private final List<Vuelo> vuelos;

    /*
     * Este constructor se usa para cuando se asigna una Ruta valida
     */
    public Ruta(List<Vuelo> vuelos)
    {
        this.id = UUID.randomUUID();
        this.almacenOrigen = vuelos.get(0).getAlmacenOrigen();
        this.almacenDestino = vuelos.get(vuelos.size() - 1).getAlmacenDestino();
        this.instanteSalida = vuelos.get(0).getInstanteSalida();
        this.instanteLlegada = vuelos.get(vuelos.size() - 1).getInstanteLlegada();
        this.vuelos = vuelos;
    }

    /*
     * Ruta vacía, osea que el Producto no se mueve
     */
    public Ruta(Almacen almacen, Instant instanteActual)
    {
        this.id = UUID.randomUUID();
        this.almacenOrigen = almacen;
        this.almacenDestino = almacen;
        this.instanteSalida = instanteActual;
        this.instanteLlegada = instanteActual;
        this.vuelos = null;
    }

    /*
     * Asigna una lista de Productos a la Ruta
     */
    public Boolean asignarProductosARuta(List<Producto> productosAAsignar)
    {
        Boolean asignadoCorrectamente;

        asignadoCorrectamente = true;

        for(Vuelo vuelo : this.vuelos)
        {
            asignadoCorrectamente &= vuelo.asignarProductosAVuelo(productosAAsignar);
        }

        return asignadoCorrectamente;
    }

    /*
     * Des asigna una lista de Productos a la Ruta
     */
    public void desasignarProductosARuta(List<Producto> productosAAsignar)
    {
        for(Vuelo vuelo : this.vuelos)
        {
            vuelo.desasignarProductosDeVuelo(productosAAsignar);
        }
    }

    /*
     * Calcula en toda la Ruta cual es el maximo valor del espacio vacío.
     */
    public Integer calcularEspacioVacioMaximoEnRuta()
    {
        Integer espacioVacioMaximoAbsoluto, espacioVacioMaximoLocal, espacioVacioMaximoSalida, espacioVacioMaximoVuelo, espacioVacioLlegada;
        Almacen almacenOrigen, almacenDestino;

        espacioVacioMaximoAbsoluto = 0;

        for(Vuelo vuelo : this.vuelos)
        {
            almacenOrigen = vuelo.getAlmacenOrigen();
            almacenDestino = vuelo.getAlmacenDestino();
            
            espacioVacioMaximoSalida = almacenOrigen.calcularEspacioVacio(vuelo.getInstanteSalida());
            espacioVacioMaximoVuelo = vuelo.getEspacioVacio();
            espacioVacioLlegada = almacenDestino.calcularEspacioVacio(vuelo.getInstanteLlegada());
            espacioVacioMaximoLocal = Math.min(espacioVacioMaximoSalida, Math.min(espacioVacioMaximoVuelo, espacioVacioLlegada));
            
            if(espacioVacioMaximoAbsoluto == 0 || espacioVacioMaximoAbsoluto > espacioVacioMaximoLocal)
            {
                espacioVacioMaximoAbsoluto = espacioVacioMaximoLocal;
            }
        }

        return espacioVacioMaximoAbsoluto;
    }


    /*
     * Para saber si es ruta vacía
     */
    public Boolean esVacia()
    {
        return Almacen.esIgual(this.almacenOrigen, this.almacenDestino);
    }

    /*
     * Impresión
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("Ruta ").append(")\n");
        sb.append("\t\t\t").append(almacenOrigen.getCiudad()).append(", ")
                .append(almacenOrigen.getPais()).append(" -> ");
        sb.append(almacenDestino.getCiudad()).append(", ")
                .append(almacenDestino.getPais()).append("\n");
        sb.append("\t\t\tVuelos (").append(vuelos.size()).append(" segmentos):\n");

        if (vuelos.isEmpty())
        {
            sb.append("\t\tSin vuelos");
        }
        else
        {
            for (int i = 0; i < vuelos.size(); i++)
            {
                Vuelo vuelo = vuelos.get(i);
                sb.append("\t\t\t").append(i + 1).append(". (")
                        .append(formatInstant.apply(vuelo.getInstanteSalida()))
                        .append(") ")
                        .append(vuelo.getAlmacenOrigen().getCiudad())
                        .append(",").append(vuelo.getAlmacenOrigen().getPais()) 
                        .append(" -> (")
                        .append(formatInstant.apply(vuelo.getInstanteLlegada()))
                        .append(") ")
                        .append(vuelo.getAlmacenDestino().getCiudad())
                        .append(",").append(vuelo.getAlmacenDestino().getPais()) 
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
