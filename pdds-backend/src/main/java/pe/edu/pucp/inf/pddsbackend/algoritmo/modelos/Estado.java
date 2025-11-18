package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.SerializationUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Ruta;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@Getter
@Setter
@ToString
public class Estado implements Serializable
{
    private Instant instanteActual;
    private Map<UUID, Producto> productos;
    private Map<UUID, Almacen> almacenes;
    private Map<UUID, Vuelo> vuelos;
    private Map<UUID, Pedido> pedidos;
    private Map<UUID, List<Vuelo>> adyacencia;

    private Integer demandaTotal, productosExistentes;
    private Mapa mapa;
    private Set<Almacen> almacenesInfinitos, almacenesConInventario, almacenesConDemanda;

    /*
     * Construye el Estado final. La idea es que esto sea inmutable y la única
     * fuente de la verdad. Al crearse un nuevo Estado ese tendrá los Productos
     * existentes, los Almacenes y Vuelos con su inventario, la lista de Almacenes
     * con demanda, la lista de Almacenes con inventario disponible y la demanda
     * total de productos. Finalmente se satisface los Pedidos con el inventario
     * actual de los almacenes destino
     *
     *
     */
    public Estado(Map<UUID, Producto> productosExistentes, Map<UUID, Almacen> almacenes,
            Map<UUID, Vuelo> vuelos, Map<UUID, Pedido> pedidos, Instant instanteActual)
    {
        this.productos = productosExistentes;
        this.almacenes = almacenes;
        this.vuelos = vuelos;
        this.pedidos = pedidos;
        this.instanteActual = instanteActual;

        this.almacenesConDemanda = new HashSet<>();
        this.almacenesConInventario = new HashSet<>();
        this.almacenesInfinitos = this.getAlmacenesInfinitos();

        adyacencia = this.construirAdyacencia(vuelos);
        this.demandaTotal = this.calcularDemanda();
        /*
        this.asignarProductosAVuelosYAlmacenes();
        
        this.productosExistentes = this.productos.size();
        
        this.satisfacerPedidos();
        */
    }

    /*
     * Calcula la demanda en base a los pedidos, osea, cuantos Productos faltan para
     * que los Pedidos estén totalmente satisfechos. Adicionalmente rellena la lista
     * de almacenes con demanda. Además, crea los Productos
     */
    private Integer calcularDemanda()
    {
        Integer demanda, demandaTotal;

        demandaTotal = 0;

        for (Pedido pedido : this.pedidos.values())
        {
            demanda = pedido.getDemanda();

            if (demanda > 0)
            {
                this.almacenesConDemanda.add(pedido.getAlmacenDestino());
                demandaTotal += demanda;
            }
        }

        return demandaTotal;
    }

    /*
     * Operacion atomica de asignacion de una lista de productos a Pedido, Ruta, Almacenes y Vuelos
     */
    public Boolean asignarProductosAPedido_Ruta_Almacenes_Vuelos(Pedido pedido, Ruta rutaAAsignar, List<Producto> productosAAsignar)
    {
        Boolean asignadoCorrectamente;
        
        asignadoCorrectamente = true;
        
        asignadoCorrectamente &= rutaAAsignar.asignarProductosARuta(productosAAsignar);
        asignadoCorrectamente &= pedido.asignarProductosAPedido(productosAAsignar);
        
        if(asignadoCorrectamente == false)
        {
            rutaAAsignar.desasignarProductosARuta(productosAAsignar);
            pedido.desasignarProductosAPedido(productosAAsignar);
        }
        
        return asignadoCorrectamente;
    }

    /*
     * Devuelve los almacenes infinitos (destacados con capacidad negativa)
     */
    public Set<Almacen> getAlmacenesInfinitos()
    {
        Set<Almacen> almacenesInfinitos = new HashSet<>();

        for (Almacen almacen : this.almacenes.values())
        {
            if (almacen.getEsInfinito())
            {
                almacenesInfinitos.add(almacen);
            }
        }

        return almacenesInfinitos;
    }

    /*
     * Esta función inicializa la lista de adyacencia que empareja los almacenes con
     * todos aquellos vuelos que tiene com origen ese almacén
     */
    private Map<UUID, List<Vuelo>> construirAdyacencia(
            Map<UUID, Vuelo> vuelos)
    {
        Map<UUID, List<Vuelo>> adyacencia;

        adyacencia = new HashMap<>();

        for (Vuelo vuelo : vuelos.values())
        {
            Almacen almacenOrigen = vuelo.getAlmacenOrigen();
            adyacencia.computeIfAbsent(almacenOrigen.getId(), k -> new ArrayList<>())
                    .add(vuelo);
        }

        for (List<Vuelo> lista : adyacencia.values())
        {
            lista.sort(Comparator.comparing(Vuelo::getInstanteSalida));
        }

        return adyacencia;
    }


    /*
     * Crea una copia profunda. La implementación es mala pero funciona
     */
    public Estado copiar()
    {
        return SerializationUtils.clone(this);
    }

    /*
     * Obtiene el Mapa con las rutas para cada almacen
     */
    public void obtenerMapa()
    {
        this.mapa = new Mapa(this.adyacencia, this.almacenesInfinitos, this.almacenesConInventario,
                this.almacenesConDemanda, this.instanteActual);
    }

    public Almacen buscarAlmacen(UUID id)
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
