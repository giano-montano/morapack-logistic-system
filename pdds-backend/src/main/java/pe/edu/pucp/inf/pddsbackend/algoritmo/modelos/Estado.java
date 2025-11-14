package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import lombok.ToString;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@ToString
public class Estado
{
    private Map<String, Almacen> almacenes;
    private Map<UUID, Vuelo> vuelos;
    private Map<UUID, Pedido> pedidos;
    private MatrizTiempo matrizTiempo;
    private Mapa mapa;
    

    private List<Almacen> almacenesInfinitos;

    /*
     * Construye el Estado final. La idea es que esto sea inmutable y la única fuente de la verdad
     */
    public Estado(Map<String, Almacen> almacenes, Map<UUID, Vuelo> vuelos, Map<UUID, Pedido> pedidos, Instant inicioOperaciones)
    {
        List<Almacen> almacenesOrigen;

        this.almacenes = almacenes;
        this.vuelos = vuelos;
        this.pedidos = pedidos;

        this.almacenesInfinitos = this.getAlmacenesInfinitos();
        this.matrizTiempo = new MatrizTiempo();
        almacenesOrigen = this.construirAlmacenesOrigen();
        this.mapa = new Mapa(this.vuelos, inicioOperaciones, almacenesOrigen);
    }

    public Almacen buscarAlmacen(String id)
    {
        return this.almacenes.get(id);
    }

    public Vuelo buscarVuelo(UUID id)
    {
        return this.vuelos.get(id);
    }

    public Pedido buscarPedido(UUID id)
    {
        return this.pedidos.get(id);
    }

    /*
     * Crea una lista de almacenes que pueden ser origenes para los vuelos. Esto implica que tienen mas de  UMBRAL_CAPACIDAD_OCUPADA productos en el inventario
     * 
     */
    public List<Almacen> construirAlmacenesOrigen()
    {
        List<Almacen> almacenesOrigen;

        almacenesOrigen = new ArrayList<>();

        for(Almacen almacen : this.almacenes.values())
        {
            if(almacen.getCapacidadOcupada() > Hiperparametros.UMBRAL_CAPACIDAD_OCUPADA)
            {
                almacenesOrigen.add(almacen);
            }
        }

        return almacenesOrigen;
    }

    /*
     * Devuelve los almacenes infinitos (destacados con capacidad negativa)
     */
    public List<Almacen> getAlmacenesInfinitos()
    {
        List<Almacen> almacenesInfinitos = new ArrayList<>();

        for (Almacen almacen : this.almacenes.values())
        {
            if (almacen.getCapacidad() < 0L)
            {
                almacenesInfinitos.add(almacen);
            }
        }

        return almacenesInfinitos;
    }

}
