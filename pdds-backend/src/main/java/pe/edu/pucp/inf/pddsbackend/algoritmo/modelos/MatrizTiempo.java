package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

public class MatrizTiempo {
    private Celda[][] cambiosInventario;
    private final List<Instant> instantesDiscretos;               
    private final Map<String, Integer> ejeAlmacenes;
    private final Map<Instant, Integer> ejeTemporal;
    private final Map<String, Long> inventarioInicial;
    
    
    /*
     * Construye la matriz de tiempo en base a los vuelos y los almacenes
     */
    public MatrizTiempo(Map<UUID, Vuelo> vuelos, Map<String, Almacen> almacenes)
    {
        this.instantesDiscretos = this.discretizarTiempo(vuelos);
        
    }

    /*
     * Recorre todos los vuelos y ordena el sus tiempos de salida y llegada
     */
    private List<Instant> discretizarTiempo(Map<UUID,Vuelo> vuelos) {
        Instant instanteSalida, instanteLlegada; 
        TreeSet<Instant> instantesDiscretos;

        instantesDiscretos = new TreeSet<>();

        for(Vuelo vuelo : vuelos.values())
        {
            instanteSalida = vuelo.getInstanteSalida();
            instanteLlegada = vuelo.getInstanteLlegada();
            instantesDiscretos.add(instanteLlegada);
            instantesDiscretos.add(instanteLlegada);
        }

        return new ArrayList<>(instantesDiscretos);
    }



    private class Celda{
        private Long inventario;
        private List<Producto> productos;

        public Celda(){
            this.inventario = 0L;
        }
    }
}
