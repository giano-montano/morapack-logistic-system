package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class Pedido implements Serializable
{
    @Setter
    private Double puntaje = null; 


/* Legacy */
    // dominio:
    private long id;
    private long idAlmacenDestino;

    private int cantidadProductosPedidos;
    private int cantidadProductosEntregados;
    private int cantidadProductosProgramados; // no sé si se usará
    private int cantidadProductosPendientes; // pedidos - programs - entregs

    private final Set<UUID> idsProductosEntregados;
    private Set<UUID> idsProductosProgramados = new HashSet<>(); // Esto al algoritmo debe llegar vacío, pero
    // en el contexto de la simulación puede estar solo para ofrecer la información
    // al cliente

    private Instant instanteRegistro;
    private Instant instanteMaximoParaEntregar; // en pedidos nuevos será nulo o 2 días?

    private boolean intercontinentalAhora = false;
    // private boolean esIntercontinentalSegunPlanifActual = false;
    @Setter
    private EstadoPedido estado; // podría incluir si está completamente programado...
    // Por ahora ENTREGADO es más bien, "no requiere ser programado ahora"

    private Continente continenteDestino;
    // índices:

    // Constructor principal [para pedidos desde BD, llamados desde desdeEntidad]
    public Pedido(long id,
            long idAlmacenDestino,
            int cantidadProductosPedidos,
            int cantidadProductosEntregados,
            Instant instanteRegistro,
            Instant instanteMaximoParaEntregar,
            boolean intercontinentalAhora,
            Continente continenteDestino)
    {

        if (id < 0)
            throw new IllegalArgumentException("id no puede ser negativo");
        if (cantidadProductosPedidos < 0)
            throw new IllegalArgumentException("cantidadProductosPedidos < 0");
        if (cantidadProductosEntregados < 0)
            throw new IllegalArgumentException("cantidadProductosEntregados < 0");

        this.id = id;
        this.idAlmacenDestino = idAlmacenDestino;
        this.cantidadProductosPedidos = cantidadProductosPedidos;
        this.cantidadProductosEntregados = cantidadProductosEntregados;
        this.cantidadProductosProgramados = 0;
        this.recalcularDerivados(); // para los productos pendientes
        this.instanteRegistro = instanteRegistro;
        this.instanteMaximoParaEntregar = instanteMaximoParaEntregar != null
                ? instanteMaximoParaEntregar
                : instanteRegistro.plus(Hiperparametros.DIAS_CONTINENTAL, ChronoUnit.DAYS); // porsia!

        // if (idsProductosEntregados == null) {
        // this.idsProductosEntregados = new HashSet<>();
        // } else {
        // this.idsProductosEntregados = new HashSet<>(idsProductosEntregados);
        // }

        this.estado = (this.cantidadProductosEntregados >= this.cantidadProductosPedidos)
                ? EstadoPedido.ENTREGADO
                : EstadoPedido.PENDIENTE;
        this.intercontinentalAhora = intercontinentalAhora;
        this.continenteDestino = continenteDestino;

        this.idsProductosEntregados = new HashSet<>();
        this.idsProductosProgramados = new HashSet<>();
    }

    // constructor copia
    public Pedido(Pedido pedido)
    {
        this.id = pedido.id;
        this.idAlmacenDestino = pedido.idAlmacenDestino;
        this.cantidadProductosPedidos = pedido.cantidadProductosPedidos;
        this.cantidadProductosEntregados = pedido.cantidadProductosEntregados;
        this.cantidadProductosProgramados = pedido.cantidadProductosProgramados;
        this.cantidadProductosPendientes = pedido.cantidadProductosPendientes;
        this.instanteRegistro = pedido.instanteRegistro;
        this.estado = pedido.estado;
        this.idsProductosEntregados = new HashSet<>( pedido.idsProductosEntregados );
        this.idsProductosProgramados = new HashSet<>( pedido.idsProductosProgramados );
        this.instanteMaximoParaEntregar = pedido.instanteMaximoParaEntregar;
        this.intercontinentalAhora = pedido.intercontinentalAhora;
        this.continenteDestino = pedido.continenteDestino;
    }

    static public Pedido desdeEntidad(PedidoEntidad p) {
        // System.out.println("intentando parsear: ");
        return new Pedido(
                p.getId(),
                p.getAlmacenDestino().getId(),
                p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(),
                p.getInstanteRegistro(),
                p.getInstanteMaximoParaEntregar(),
                p.getEsIntercontinental(),
                p.getAlmacenDestino().getContinente());
    }

    // Métodos encapsuladores (actualizar y mostrar estado íntegramente):
    public void recalcularDerivados() {
        cantidadProductosPendientes = cantidadProductosPedidos - cantidadProductosEntregados
                - cantidadProductosProgramados;
        if (cantidadProductosPendientes <= 0)
        {
//            this.estado = EstadoPedido.ENTREGADO; // <- Entregado es más bien, "no requiere ser
//                                                  // programado ahora"
        }
        if(cantidadProductosEntregados >= cantidadProductosPedidos){
            this.estado = EstadoPedido.ENTREGADO;
        }
        ;
    }

    public EstadoPedido getEstado()
    {
        return estado;
    }

    /*
     * Altera si es intercontinental o no. para efectos de que el algoritmo
     * planifique interconts a partir del momento en que se modifique
     * "intercontinentalAhora"; sin embargo, sería más limpio si fuera un atributo
     * aparte, ya que se mezcla con el pedido de la simulación que NO altera su
     * "intercontinentalAhora" sino hasta que un prod intercont llega a las manos de
     * un cliente (VueloLlegada). .
     */
    public boolean agregarProductoProgramadoEnAlgoritmo(Producto producto,
            Continente continenteOrigenProducto)
    {
        if (cantidadProductosProgramados + 1 > cantidadProductosPedidos)
            return false;
        cantidadProductosProgramados += 1;
        this.recalcularDerivados();
        idsProductosProgramados.add(producto.getUuid());
        if (!continenteDestino.equals(continenteOrigenProducto))
        {
            instanteMaximoParaEntregar = instanteRegistro.plus(
                    Hiperparametros.DIAS_INTERCONTINENTAL,
                    ChronoUnit.DAYS);
            intercontinentalAhora = true;
        }
        return true;
    }

    public int getCantidadProductosPendientes()
    {
        return cantidadProductosPendientes;
    }

    public Instant getPlazoParaLlegadaUltimoVuelo()
    {
        // Se asume que el instanteMaximoParaEntregar ya tiene si es 3 días o 2.
        Instant real = this.instanteMaximoParaEntregar != null
                ? this.instanteMaximoParaEntregar
                : this.intercontinentalAhora
                        ? this.instanteRegistro.plus(3, ChronoUnit.DAYS)
                        : this.instanteRegistro.plus(2, ChronoUnit.DAYS);
        return real.minus(2, ChronoUnit.HOURS);
    }

    /* Actualiza el estado del pedido en simulación según el producto que se entrega al cliente */
    public boolean agregarProductoEntregado(Producto producto, Continente continenteOrigenProducto){
        if (cantidadProductosEntregados + 1 > cantidadProductosPedidos)
            return false;
        cantidadProductosEntregados += 1;
        this.recalcularDerivados();
        idsProductosEntregados.add(producto.getUuid());

        if (!continenteDestino.equals(continenteOrigenProducto)){
            instanteMaximoParaEntregar = instanteRegistro.plus(
                    Hiperparametros.DIAS_INTERCONTINENTAL,
                    ChronoUnit.DAYS);
            intercontinentalAhora = true; // no vuelve a cambiar a false
        }

        return true;
    }

    public void restablecerProductosProgramadosParaAlgoritmo()
    {
        this.idsProductosProgramados = new HashSet<>();
        this.cantidadProductosProgramados = 0;
        this.recalcularDerivados();
    }

    /*
     * La diferencia con el otro método similar es que este no altera si es
     * intercontinental o no.
     */
    public boolean agregarProductoProgramadoEnSimu(Producto producto)
    {
        if (cantidadProductosProgramados + 1 > cantidadProductosPedidos)
            return false;
        cantidadProductosProgramados += 1;
        this.recalcularDerivados();
        idsProductosProgramados.add(producto.getUuid());
        return true;
    }

    @Override
    public String toString()
    {
        return "Pedido{" +
                "id=" + id +
                ", idAlmacenDestino=" + idAlmacenDestino +
                ", pedidas=" + cantidadProductosPedidos +
                ", entregadas=" + cantidadProductosEntregados +
                ", programadas=" + cantidadProductosProgramados +
                ", restante o pendientes=" + cantidadProductosPendientes +
                ", registro=" + instanteRegistro +
                ", instanteEntregaMax=" + instanteMaximoParaEntregar +
                ", estado=" + estado +
                '}';
    }

}
