package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Ruta implements Serializable
{
    private UUID id;
    private Integer capacidadMinima;
    private Double aptitud;
    private Almacen almacenOrigen, almacenDestino;
    private List<Vuelo> vuelos;

    /*
     * Este constructor se usa para cuando se asigna una Ruta valida
     */
    public Ruta(Almacen almacenOrigen, List<Vuelo> vuelos, Almacen almacenDestino)
    {
        this.id = UUID.randomUUID();
        this.aptitud = Ruta.evaluarAptitud(vuelos);
        this.vuelos = vuelos;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
        capacidadMinima = this.calculaCapacidadMinima(vuelos);
    }

    /*
     * Calcula el vuelo de menor capacidad disponible
     */
    private Integer calculaCapacidadMinima(List<Vuelo> vuelos) {
        Integer capacidadMinima, capacidad;

        capacidadMinima = 0;

        for(Vuelo vuelo : vuelos)
        {
            capacidad = vuelo.getCapacidadUsada();

            if(capacidadMinima == 0 || capacidad < capacidadMinima)
            {
                capacidadMinima = capacidad;
            }
        }

        return capacidadMinima;
    }



    /*
     * Este constructor se usa para cuando se asigna una Ruta vacía. Indica que ese
     * Producto se entrega en el almacén donde esta.
     */
    public Ruta(Almacen almacenActual)
    {
        this.id = UUID.randomUUID();
        this.almacenOrigen = almacenActual;
        this.almacenDestino = almacenActual;
    }

    /*
     * Función fitness para evaluar una ruta
     */
    private static Double evaluarAptitud(List<Vuelo> vuelos)
    {
        return 0D;
    }

    public Boolean esVacia()
    {
        return Almacen.esIgual(this.almacenOrigen, this.almacenDestino);
    }


    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("Ruta (Aptitud: ").append(String.format("%.2f", aptitud)).append(")\n");
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
