package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.DIAS_INTERCONTINENTAL;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Getter
public class Pedido implements Serializable {
    private final long id;
    private final int cantidadProductos;
    private final Instant instanteRegistro;
    private Instant instanteLimite;
    private final Almacen almacenDestino;
    @Setter private EstadoPedido estado;

    private List<Producto> productosEntregados;
    private List<Producto> productosProgramados;
    @Setter private double puntaje;
    
    /*
     * Constructor para la BD
     */
    public Pedido(long id, Almacen almacenDestino, int cantidadProductos, int cantidadProductosSatisfechos, Instant instanteRegistro, Instant instanteMaximoParaEntregar, boolean intercontinentalAhora, Continente continenteDestino) {
        this.id = id;
        this.almacenDestino = almacenDestino;
        this.cantidadProductos = cantidadProductos;
        this.instanteRegistro = instanteRegistro;
        this.instanteLimite = instanteRegistro.plus(Duration.ofDays(Hiperparametros.DIAS_CONTINENTAL));
        this.estado = EstadoPedido.PENDIENTE;
        this.productosEntregados = new ArrayList<>();
        this.productosProgramados = new ArrayList<>();
        this.puntaje = 0.0;
    }

    /*
     * Constructor copia
     */
    public Pedido(Pedido pedido) {
        this.id = pedido.id;
        this.almacenDestino = pedido.almacenDestino;
        this.cantidadProductos = pedido.cantidadProductos;
        this.instanteRegistro = pedido.instanteRegistro;
        this.estado = pedido.estado;
        this.productosEntregados = pedido.productosEntregados;
        this.instanteLimite = pedido.instanteLimite;
        this.productosEntregados = new ArrayList<>(pedido.productosEntregados);
        this.productosProgramados = new ArrayList<>(pedido.productosProgramados);
    }

    /*
     * Convierte una entidad de pedido a un objeto de dominio de pedido
     */
    static public Pedido desdeEntidad(PedidoEntidad p) {
        return new Pedido(
                p.getId(),
                Almacen.desdeEntidad( p.getAlmacenDestino()),
                p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(),
                p.getInstanteRegistro(),
                p.getInstanteMaximoParaEntregar(),
                p.getEsIntercontinental(),
                p.getAlmacenDestino().getContinente());
    }

    /*
     * Obtiene el instante máximo en el que puede llegar un vuelo para satisfacer el pedido
     */
    public Instant obtenerInstanteMaximoLlegadaUltimoVuelo() {
        return this.instanteLimite.minus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));
    }

    /*
     * Obtiene la cantidad de productos necesarios para satisfacer el pedido.
     * Definido por Pd = cantidadProductos - productosEntregados.size()
     */
    public int obtenerCantidadProductosFaltantes() {
        return this.cantidadProductos - this.productosEntregados.size();
    }

    /*
     * Obtiene la cantidad de programaciones necesarias para satisfacer el pedido.
     */
    public int obtenerCantidadProgramacionesFaltantes() {
        return this.cantidadProductos - this.productosEntregados.size() - this.productosProgramados.size();
    }

    /*
     * Registra un producto como entregado. Verifica que este programado
     */
    public boolean registrarProductoEntregado(Producto producto)
    {
        if (this.productosProgramados.contains(producto)) {
            if (producto.validarIncancelable_B()) {
                this.productosProgramados.remove(producto);
                this.productosEntregados.add(producto);
                obtenerSiPedidoEsIntercontinental();
                return true;
            }
        }

        return false;
    }

    /*
     * Registra una lista de productos como entregados. 
     */
    public boolean registrarProductoEntregado(List<Producto> productos)
    {
        boolean valido = true;

        for (Producto p : productos) {
            valido &= registrarProductoEntregado(p);
            
            if (!valido) {
                break;
            }
        }

        return valido;
    }

    /*
     * Registra un producto como programado. 
     */
    public boolean registrarProductoProgramado(Producto producto)
    {
        if (this.productosEntregados.contains(producto)) {
            String error = String.format("ERROR (Registro programado): El producto ya está entregado");
            Bitacora.escribir(error);
            throw new IllegalStateException(error);
        }
        
        if (this.productosEntregados.size() + this.productosProgramados.size() + 1 > this.cantidadProductos) {
            String error = String.format("ERROR (Registro programado): Se excedería la capacidad del pedido");
            Bitacora.escribir(error);
            throw new IllegalStateException(error);
        }
        
        this.productosProgramados.add(producto);
        obtenerSiPedidoEsIntercontinental();
        
        return true;
    }

    /*
     * Registra una lista de productos como programados. 
     */
    public boolean registrarProductoProgramado(List<Producto> productos)
    {
        boolean valido = true;

        for (Producto p : productos) {
            valido &= registrarProductoProgramado(p);

            if (!valido) {
                break;
            }
        }

        return valido;
    }

    /*
     * Recorre las listas de productos y actualiza instanteLimite si encuentra productos intercontinentales
     */
    public boolean obtenerSiPedidoEsIntercontinental() {
        boolean hayIntercontinental = false;
        
        for (Producto producto : this.productosEntregados) {
            if (!Almacen.verificarIntercontinental(producto.getAlmacenOrigen(), this.almacenDestino)) {
                hayIntercontinental = true;
                break;
            }
        }
        
        if (!hayIntercontinental) {
            for (Producto producto : this.productosProgramados) {
                if (!Almacen.verificarIntercontinental(producto.getAlmacenOrigen(), this.almacenDestino)) {
                    hayIntercontinental = true;
                    break;
                }
            }
        }
        
        if (hayIntercontinental) {
            this.instanteLimite = this.instanteRegistro.plus(Duration.ofDays(Hiperparametros.DIAS_INTERCONTINENTAL));
        } else {
            this.instanteLimite = this.instanteRegistro.plus(Duration.ofDays(Hiperparametros.DIAS_CONTINENTAL));
        }

        return hayIntercontinental;
    }

    @Override
    public String toString()
    {
        return "Pedido{" +
                "id=" + id +
                ", cantidadProductos=" + cantidadProductos +
                ", instanteRegistro=" + instanteRegistro +
                ", instanteLimite=" + instanteLimite +
                ", almacenDestino=" + almacenDestino +
                ", estado=" + estado +
                ", productosEntregados=" + productosEntregados.size() +
                ", productosProgramados=" + productosProgramados.size() +
                ", puntaje=" + puntaje +
                '}';
    }
}
