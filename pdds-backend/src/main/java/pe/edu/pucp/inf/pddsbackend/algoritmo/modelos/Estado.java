package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@Getter
@Setter
@ToString
public class Estado
{
    private Map<UUID, Producto> productos;
    private Map<UUID, Almacen> almacenes;
    private Map<UUID, Vuelo> vuelos;
    private Map<UUID, Pedido> pedidos;
    private Instant instanteActual;

    private Integer demandaTotal, productosExistentes;
    private Mapa mapa;
    private Set<Almacen> almacenesInfinitos, almacenesConDemanda, almacenesConInventario;

    /*
     * Construye el Estado final. La idea es que esto sea inmutable y la única
     * fuente de la verdad. Al crearse un nuevo Estado ese tendrá los Productos
     * existentes, los Almacénes y Vuelos con su inventario, la lista de Almacenes
     * con demanda, la lista de Almacenes con inventario disponible y la demanda
     * total de productos
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

        this.asignarProductosAVuelosYAlmacenes();
        this.demandaTotal = this.calcularDemanda();
        this.productosExistentes = this.productos.size();
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
     * Asigna los Productos existentes a los inventarios de los Vuelos y Almacenes.
     * Además, obtiene los almacenes con inventario disponible
     */
    private void asignarProductosAVuelosYAlmacenes()
    {
        UUID idEntidadActual;
        Instant instanteLlegada;
        Almacen almacenActual, almacenFuturo;
        Vuelo vueloActual;

        for (Producto producto : this.productos.values())
        {
            idEntidadActual = producto.getIdEntidadActual();
            almacenActual = this.almacenes.get(idEntidadActual);
            vueloActual = this.vuelos.get(idEntidadActual);

            if (almacenActual != null)
            {
                almacenActual.insertarProducto(producto);
                this.almacenesConInventario.add(almacenActual);
            }

            if (vueloActual != null)
            {
                instanteLlegada = vueloActual.getInstanteLlegada();
                almacenFuturo = vueloActual.getAlmacenDestino();

                producto.setInstanteDisponible(instanteLlegada);
                almacenFuturo.insertarCambio(vueloActual.getInstanteLlegada(), 1);
                vueloActual.insertarProducto(producto);
            }
        }
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
