package pe.edu.pucp.inf.pddsbackend.algoritmo;

import java.util.HashMap;
import java.util.UUID;

import lombok.ToString;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@ToString
public class Estado
{
    private HashMap<String, Almacen> almacenes;
    private HashMap<UUID, Vuelo> vuelos;
    private HashMap<UUID, Pedido> pedidos;

    public Estado()
    {
        this.almacenes = new HashMap<>();
        this.vuelos = new HashMap<>();
        this.pedidos = new HashMap<>();
    }

    public void agregarAlmacen(Almacen almacen)
    {
        this.almacenes.put(almacen.getId(), almacen);
    }

    public void agregarVuelo(Vuelo vuelo)
    {
        this.vuelos.put(vuelo.getId(), vuelo);
    }

    public void agregarPedido(Pedido pedido)
    {
        this.pedidos.put(pedido.getId(), pedido);
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


}
