package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;

import java.util.ArrayList;
import java.util.List;

@Data
public class PedidoParaAxel
{
    Pedido pedidoObjeto;

    List<Programacion> miniPedidos;

    public PedidoParaAxel()
    {
        this.miniPedidos = new ArrayList<>();
    }

    public PedidoParaAxel(Pedido pedidoObjeto)
    {
        this.pedidoObjeto = new Pedido(pedidoObjeto);
        this.miniPedidos = new ArrayList<>();
    }

    public Integer getCantidad()
    {
        return this.pedidoObjeto.getCantidadProductosPedidos();
    }
}
