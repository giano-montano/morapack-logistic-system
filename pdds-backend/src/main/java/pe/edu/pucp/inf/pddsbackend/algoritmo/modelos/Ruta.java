package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@Getter
@Setter
@ToString
public class Ruta
{
    Boolean esVacia;
    Double aptitud;
    Almacen origen, destino;
    List<Vuelo> vuelos;

    Ruta(List<Vuelo> vuelos, Almacen destino)
    {
        this.vuelos = vuelos;
        this.aptitud = 0D;
        this.destino = destino;

        if (vuelos.isEmpty())
        {
            this.esVacia = vuelos.isEmpty();
        }
        else
        {
            this.esVacia = vuelos.isEmpty();
            this.origen = this.vuelos.get(0).getAlmacenOrigen();
        }
    }
    
    public Double getAptitud() {
        return aptitud;
    }
}
