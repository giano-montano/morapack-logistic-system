package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Ruta
{
    private Double aptitud;
    private Almacen almacenOrigen, almacenDestino;
    List<Vuelo> vuelos;

    /*
     * No incluir vuelos vacíos
     */
    public Ruta(Almacen almacenOrigen, List<Vuelo> vuelos, Almacen almacenDestino)
    {
        this.aptitud = Ruta.evaluarAptitud(vuelos);
        this.vuelos = vuelos;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
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
        return (this.vuelos.size() == 0) ? true : false;
    }
}
