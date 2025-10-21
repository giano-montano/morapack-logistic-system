package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.util.ArrayList;
import java.util.List;

import lombok.ToString;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@ToString
public class Ruta
{
    Double aptitud;
    Almacen origen, destino;
    List<Vuelo> ruta;
    
    Ruta(Almacen destino){
        this.aptitud = 0D;
        this.destino = destino;
        this.ruta = new ArrayList<>();
    }

    
    
}
