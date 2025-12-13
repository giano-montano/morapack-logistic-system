package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class Programacion implements Serializable
{
    @Setter
    private boolean aPuntoDeCumplirse = false; //Supuestamente lo actualiza la simulación
    private final Pedido pedido;
    private final Producto producto;
    LinkedList<Vuelo> ruta;
    private final UUID uuidProducto; //No sé si es seguro eliminarlo
    

    public Programacion(Pedido pedido, Producto producto, LinkedList<Vuelo> ruta)
    {
        this.pedido = pedido;
        this.producto = producto;
        this.ruta = ruta;
        this.aPuntoDeCumplirse = false;
        this.activo = true;

        this.idsVueloRuta = convertirRutaAVuelosId(ruta); //legacy
        this.idPedido = this.pedido.getId(); //legacy
        this.uuidProducto = this.producto.getUuid(); //legacy
    }

    public Programacion(Programacion original)
    {
        this.pedido = original.pedido;
        this.producto = original.producto;
        this.ruta = original.ruta;
        this.aPuntoDeCumplirse = original.aPuntoDeCumplirse;
        this.activo = original.activo;

        this.idsVueloRuta = original.idsVueloRuta; //legacy
        this.idPlanificacion = original.idPlanificacion; //legacy
        this.idPedido = original.idPedido; //legacy
        this.uuidProducto = original.uuidProducto; //legacy

    }

    /*
     * Convierte una lista de Vuelos a una lista de ids de vuelos. Legacy
     */
    private LinkedList<Long> convertirRutaAVuelosId(List<Vuelo> ruta)
    {
        return ruta.stream()
                .map(Vuelo::getId)
                .collect(Collectors.toCollection(LinkedList::new));
    }


/* Legacy */
    // private final long id;
    private long idPedido;
     // exista ya o no, tiene id
    private final LinkedList<Long> idsVueloRuta;
    private long idPlanificacion; // no interesa mucho por ahora, capaz safa
    @Setter
    private boolean activo = true; // recordemos que se irán descartando programaciones anteriores
                                   // en cada planif
    // o sea le pondremos activo=false a la "tanda" anterior. <- LEGACY

     // NUEVO: lo actualiza el evento:
    // SERVIRÁN PARA INICIALIZAR CAMBIOS POR TIEMPO EN ALMACÉN


    // Constructor principal para programaciones que se vayan haciendo en el
    // algoritmo, o sea programaciones nuevas; esta es la única manera de que
    // se creen programaciones oficialmente.
    public Programacion(
            long idPedido,
            UUID uuidProducto,
            LinkedList<Long> ruta)
    {
        this.idPedido = idPedido;
        this.uuidProducto = uuidProducto;
        this.idsVueloRuta = ruta;
        this.aPuntoDeCumplirse = false;
        this.activo = true;

        this.pedido = null; //usar el nuevo constructor
        this.producto = null; //usar el nuevo constructor
    }

    public LinkedList<Long> getIdsVueloRuta()
    {
        return new LinkedList<>(idsVueloRuta);
    }

    public void marcarComoAPuntoDeCumplirse(){
        this.aPuntoDeCumplirse = true;
    }

    @Override
    public String toString()
    {
        return "Programacion{" +
                "idPedido=" + idPedido +
                ", uuidProducto=" + uuidProducto +
                ", idsVueloRuta=" + idsVueloRuta +
                ", idPlanificacion=" + idPlanificacion +
                ", activo=" + activo +
                '}';
    }
}
