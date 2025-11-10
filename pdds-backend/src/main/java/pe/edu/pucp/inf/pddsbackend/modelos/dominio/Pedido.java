package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Constantes;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter // aquí es seguro creo.
public class Pedido {

    // dominio:
    private long id;
    private long idAlmacenDestino;

    private int cantidadProductosPedidos;
    private int cantidadProductosEntregados;
    private int cantidadProductosProgramados; // no sé si se usará
    private int cantidadProductosPendientes; // pedidos - programs - entregs

    @Setter
    private Set<UUID>idsProductosEntregados;
    private Set<UUID>idsProductosProgramados = new HashSet<>(); // puede ser o no

    private Instant instanteRegistro;
    private Instant instanteMaximoParaEntregar; // en pedidos nuevos será nulo o 2 días?

    private boolean intercontinentalAhora=false;
    @Setter
    private EstadoPedido estado; // podría incluir si está completamente programado...
    private Continente continenteDestino;
    // índices:

    // Constructor principal
    public Pedido(long id,
                  long idAlmacenDestino,
                  int cantidadProductosPedidos,
                  int cantidadProductosEntregados,
                  Instant instanteRegistro,
                  Instant instanteMaximoParaEntregar,
                  boolean intercontinentalAhora,
                  Continente continenteDestino
    ) {

        if (id < 0) throw new IllegalArgumentException("id no puede ser negativo");
        if (cantidadProductosPedidos < 0) throw new IllegalArgumentException("cantidadProductosPedidos < 0");
        if (cantidadProductosEntregados < 0) throw new IllegalArgumentException("cantidadProductosEntregados < 0");

        this.id = id;
        this.idAlmacenDestino = idAlmacenDestino;
        this.cantidadProductosPedidos = cantidadProductosPedidos;
        this.cantidadProductosEntregados = cantidadProductosEntregados;
        this.cantidadProductosProgramados = 0;
        this.recalcularDerivados(); // para los productos pendientes
        this.instanteRegistro = instanteRegistro;
        this.instanteMaximoParaEntregar = instanteMaximoParaEntregar!=null?
                instanteMaximoParaEntregar: instanteRegistro.plus(Constantes.DIAS_CONTINENTAL,ChronoUnit.DAYS); // porsia!

//        if (idsProductosEntregados == null) {
//            this.idsProductosEntregados = new HashSet<>();
//        } else {
//            this.idsProductosEntregados = new HashSet<>(idsProductosEntregados);
//        }

        this.estado = (this.cantidadProductosEntregados>=this.cantidadProductosPedidos)?
                EstadoPedido.ENTREGADO:EstadoPedido.PENDIENTE;
        this.intercontinentalAhora=intercontinentalAhora;
        this.continenteDestino = continenteDestino;

        this.idsProductosEntregados = new HashSet<>();
        this.idsProductosProgramados = new HashSet<>();
    }

    // constructor copia
    public Pedido(Pedido pedido) {
        this.id = pedido.id;
        this.idAlmacenDestino = pedido.idAlmacenDestino;
        this.cantidadProductosPedidos = pedido.cantidadProductosPedidos;
        this.cantidadProductosEntregados = pedido.cantidadProductosEntregados;
        this.cantidadProductosProgramados = pedido.cantidadProductosProgramados;
        this.cantidadProductosPendientes = pedido.cantidadProductosPendientes;
        this.instanteRegistro = pedido.instanteRegistro;
        this.estado = pedido.estado;
        this.idsProductosEntregados = pedido.idsProductosEntregados;
        this.idsProductosProgramados = pedido.idsProductosProgramados;
        this.instanteMaximoParaEntregar = pedido.instanteMaximoParaEntregar;
        this.intercontinentalAhora = pedido.intercontinentalAhora;
        this.continenteDestino = pedido.continenteDestino;
    }

    static public Pedido desdeEntidad(PedidoEntidad p){
        return new Pedido(
                p.getId(),
                p.getAlmacenDestino().getId(),
                p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(),
                p.getInstanteRegistro(),
                p.getInstanteMaximoParaEntregar(),
                p.getEsIntercontinental(),
                p.getAlmacenDestino().getContinente()
        );
    }

    // Métodos encapsuladores (actualizar y mostrar estado íntegramente):
    public void recalcularDerivados(){
        cantidadProductosPendientes = cantidadProductosPedidos-cantidadProductosEntregados-cantidadProductosProgramados;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public boolean agregarProductoProgramado(Producto producto, Continente continenteOrigenProducto) {
        if(cantidadProductosProgramados + 1 > cantidadProductosPedidos)
            return false;
        cantidadProductosProgramados += 1;
        recalcularDerivados();
        idsProductosProgramados.add(producto.getUuid());
        if(!continenteDestino.equals(continenteOrigenProducto)) {
            instanteMaximoParaEntregar = instanteRegistro.plus(Constantes.DIAS_INTERCONTINENTAL, ChronoUnit.DAYS);
            intercontinentalAhora = true;
        }
        return true;
    }

    public int getCantidadProductosPendientes(){
        return cantidadProductosPendientes;
    }

    public Instant getPlazoParaLlegadaUltimoVuelo(){
        Instant real = instanteMaximoParaEntregar!=null?
                instanteMaximoParaEntregar:instanteRegistro.plus(2, ChronoUnit.DAYS);
        return real.minus(2, ChronoUnit.HOURS);
    }

    public boolean agregarProductoEntregado(Producto producto) {
        if(cantidadProductosEntregados + 1 > cantidadProductosPedidos)
            return false;
        cantidadProductosEntregados += 1;
        idsProductosEntregados.add(producto.getUuid());
        return true;
    }

    @Override
    public String toString() {
        return "PedidoParaAlgoritmo{" +
                "id=" + id +
                ", idAlmacenDestino=" + idAlmacenDestino +
                ", pedidas=" + cantidadProductosPedidos +
                ", entregadas=" + cantidadProductosEntregados +
                ", programadas=" + cantidadProductosProgramados +
                ", restante o pendientes=" + cantidadProductosPendientes +
                ", registro="+instanteRegistro +
                ", instanteEntregaMax="+instanteMaximoParaEntregar +
//                ", estado=" + estado +
                '}';
    }


}