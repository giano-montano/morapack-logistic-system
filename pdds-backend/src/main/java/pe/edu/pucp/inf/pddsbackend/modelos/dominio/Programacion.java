package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Programacion implements Serializable
{
    private final Pedido pedido;
    private final Producto producto;
    Ruta ruta;

    // Constructor principal para programaciones que se vayan haciendo en el
    // algoritmo, o sea programaciones nuevas; esta es la única manera de que
    // se creen programaciones oficialmente.
    public Programacion(
            Pedido pedido,
            Producto producto,
            Ruta ruta) {
        this.pedido = pedido;
        this.producto = producto;
        this.ruta = ruta;

    }

    public Programacion(Programacion original)
    {
        this.pedido = new Pedido( original.pedido );
        this.producto = new Producto( original.producto );
        this.ruta = new Ruta ( original.ruta );

    }

    @Override
    public String toString()
    {
        return "Programacion{" +
                "idPedido=" + pedido.getId() +
                ", uuidProducto=" + producto.getId() +
                ", idsVueloRuta=" + ruta.vuelosRuta +
                '}';
    }
}
