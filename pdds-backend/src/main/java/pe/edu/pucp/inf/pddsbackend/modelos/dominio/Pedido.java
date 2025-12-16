package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
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

    private long id;

    private int cantidadProductos;
    private int cantidadProductosSatisfechos;
    private List<Producto> productosEntregados;
    private List<Producto> productosProgramados;

    private Instant instanteRegistro;
    private Instant instanteLimite;
    private Almacen almacenDestino;

    @Setter
    private Double puntaje = null; 

    private boolean intercontinentalAhora = false;
    @Setter
    private EstadoPedido estado;

    // Constructor principal [para pedidos desde BD, llamados desde desdeEntidad]
    public Pedido(long id,
                  Almacen almacenDestino,
                  int cantidadProductos,
                  int cantidadProductosSatisfechos,
                  Instant instanteRegistro,
                  Instant instanteMaximoParaEntregar,
                  boolean intercontinentalAhora,
                  Continente continenteDestino)
    {

        if (id < 0)           throw new IllegalArgumentException("id no puede ser negativo");
        if (cantidadProductos < 0)            throw new IllegalArgumentException("cantidadProductosPedidos < 0");
        if (cantidadProductosSatisfechos < 0)            throw new IllegalArgumentException("cantidadProductosEntregados < 0");

        this.id = id;
        this.almacenDestino = almacenDestino;
        this.cantidadProductos = cantidadProductos;
        this.cantidadProductosSatisfechos = cantidadProductosSatisfechos;

        this.instanteRegistro = instanteRegistro;
        this.instanteLimite = instanteMaximoParaEntregar != null
                ? instanteMaximoParaEntregar
                : instanteRegistro.plus(Hiperparametros.DIAS_CONTINENTAL, ChronoUnit.DAYS); // porsia!

        // if (idsProductosEntregados == null) {
        // this.idsProductosEntregados = new HashSet<>();
        // } else {
        // this.idsProductosEntregados = new HashSet<>(idsProductosEntregados);
        // }

        this.estado = (this.cantidadProductosSatisfechos >= this.cantidadProductos)
                ? EstadoPedido.ENTREGADO
                : EstadoPedido.PENDIENTE;
        this.intercontinentalAhora = intercontinentalAhora;

        this.productosEntregados = new ArrayList<>();
    }

    // constructor copia
    public Pedido(Pedido pedido)
    {
        this.id = pedido.id;
        this.almacenDestino = pedido.almacenDestino;
        this.cantidadProductos = pedido.cantidadProductos;
        this.cantidadProductosSatisfechos = pedido.cantidadProductosSatisfechos;


        this.instanteRegistro = pedido.instanteRegistro;
        this.estado = pedido.estado;
        this.productosEntregados = pedido.productosEntregados;
        this.instanteLimite = pedido.instanteLimite;
        this.intercontinentalAhora = pedido.intercontinentalAhora;
    }

    static public Pedido desdeEntidad(PedidoEntidad p) {
        // System.out.println("intentando parsear: ");
        return new Pedido(
                p.getId(),
                Almacen.desdeEntidad( p.getAlmacenDestino() ),
                p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(),
                p.getInstanteRegistro(),
                p.getInstanteMaximoParaEntregar(),
                p.getEsIntercontinental(),
                p.getAlmacenDestino().getContinente());
    }

    /*
     * Obtiene el instante máximo en el que puede llegar un vuelo para satisfacer el pedido
     *
     * Remplazo de getPlazoParaLlegadaUltimoVuelo
     */
    public Instant instanteMaximoLlegadaUltimoVuelo_v2()
    {
        return this.instanteLimite.minus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));
    }

    /*
     * Obtiene la cantidad de productos necesarios para satisfacer el pedido.
     * Se busca no depender de actualizar la variable CantidadProductosPendientes,
     * pero al parecer es la única forma de saber como va el pedido.
     * En todo caso, no se modifica su valor en ningun momento. Se depende de idsProductosProgramados
     * 
     * Remplazo de getCantidadProductosPendientes
     */
    public int cantidadProductosFaltantes_v2()
    {
        // Revisar
        int faltantes;
        
        faltantes = this.cantidadProductos - this.getProductosProgramados().size() - this.getCantidadProductosSatisfechos();

        return Math.max(faltantes, 0);
    }

    /*
     * Recibe una lista de productos y los guarda en el inventario (idsProductosProgramados), ademas verifica y actualiza si alguno producto es intercontinental. Depende del valor de this.cantidadProductosPendientes, se espera que este valor no cambien en el teimpo
     *
     * Remplazo de agregarProductoProgramadoEnAlgoritmo 
     */
    public boolean registrarProductos_v2(List<Producto> productos, boolean esIntercontinental)
    {
        // Reimplementar:
        int productosTotales;
        List<UUID> nuevosIds;

//        productosTotales = this.idsProductosProgramados.size() + productos.size();
//
//        if(productosTotales <= this.cantidadProductosPendientes) {
//            for(Producto producto : productos) {
//                if(this.idsProductosProgramados.contains(producto.getId()))
//                {
//                    return false;
//                }
//            }
//
//            nuevosIds = productos.stream()
//                    .map(Producto::getId)
//                    .toList();
////            this.idsProductosProgramados.addAll(nuevosIds);
//
//            if(esIntercontinental && !this.intercontinentalAhora)
//            {
//                this.instanteLimite = this.instanteRegistro.plus(Duration.ofDays(DIAS_INTERCONTINENTAL));
//                this.intercontinentalAhora = true;
//            }
//
//            return true;
//        }

        return false;
    }

    /*
     * Disminuye cantidadProductosPendientes, realmente no es lo mejor pero es lo que hay
     */
    public void registrarProducto_v2()
    {
//        this.cantidadProductosPendientes--;
        this.cantidadProductosSatisfechos++; // (??????????)
    }
/* Legacy */
    // dominio:

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
            Continente continenteOrigenProducto) {
        // Reimplementar
//        if (cantidadProductosProgramados + 1 > cantidadProductos)
//            return false;
//        cantidadProductosProgramados += 1;
//        this.recalcularDerivados();
//        idsProductosProgramados.add(producto.getId());
//        if (!continenteDestino.equals(continenteOrigenProducto))
//        {
//            instanteLimite = instanteRegistro.plus(
//                    Hiperparametros.DIAS_INTERCONTINENTAL,
//                    ChronoUnit.DAYS);
//            intercontinentalAhora = true;
//        }
        return true;
    }

    public int getCantidadProductosPendientes() {
        return this.cantidadProductos-this.productosEntregados.size();
    }

    public Instant getPlazoParaLlegadaUltimoVuelo() {
        // Se asume que el instanteLimite ya tiene si es 3 días o 2.
        Instant real = this.instanteLimite != null
                ? this.instanteLimite
                : this.intercontinentalAhora
                        ? this.instanteRegistro.plus(3, ChronoUnit.DAYS)
                        : this.instanteRegistro.plus(2, ChronoUnit.DAYS);
        return real.minus(2, ChronoUnit.HOURS);
    }

    /* Actualiza el estado del pedido en simulación según el producto que se entrega al cliente */
    public boolean agregarProductoEntregado(Producto producto, Continente continenteOrigenProducto) {
        if (cantidadProductosSatisfechos + 1 > cantidadProductos)
            return false;
        cantidadProductosSatisfechos += 1;

        productosEntregados.add(producto);

        if (!almacenDestino.getContinente().equals(continenteOrigenProducto)){ // !!!!!!!!!!!!!
            instanteLimite = instanteRegistro.plus(
                    Hiperparametros.DIAS_INTERCONTINENTAL,
                    ChronoUnit.DAYS);
            intercontinentalAhora = true; // no vuelve a cambiar a false
        }

        return true;
    }

//    public void restablecerProductosProgramadosParaAlgoritmo()
//    {
//        this.idsProductosProgramados = new HashSet<>();
//        this.cantidadProductosProgramados = 0;
//        this.recalcularDerivados();
//    }

    /*
     * La diferencia con el otro método similar es que este no altera si es
     * intercontinental o no.
     */
//    public boolean agregarProductoProgramadoEnSimu(Producto producto)
//    {
//        if (cantidadProductosProgramados + 1 > cantidadProductos)
//            return false;
//        cantidadProductosProgramados += 1;
//        this.recalcularDerivados();
//        idsProductosProgramados.add(producto.getId());
//        return true;
//    }

    @Override
    public String toString()
    {
        return "Pedido{" +
                "id=" + id +
                ", almacenDestino=" + almacenDestino +
                ", pedidas=" + cantidadProductos +
                ", entregadas=" + cantidadProductosSatisfechos +

                ", registro=" + instanteRegistro +
                ", instanteEntregaMax=" + instanteLimite +
                ", estado=" + estado +
                '}';
    }

}
