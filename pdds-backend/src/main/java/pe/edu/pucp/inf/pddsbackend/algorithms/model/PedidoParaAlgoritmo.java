package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoPedido;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;

@Getter
public class PedidoParaAlgoritmo {
    private long id;
    //cliente
    private long idAlmacenDestino; // No clase Almacen, no necesario
    private int cantidadProductosPedidos;

    private int cantidadProductosEntregados;
    HashSet<Long> idsProductosYaEntregados;
    private int cantidadProductosProgramados;
    HashSet<Long> idsProductosProgramados; // opcional creo.

    private Instant instanteRegistro;
    @Setter
    private Instant instanteMaximoParaEntregar; // deberá darse en constructor

    private boolean esIntercontinentalPorYaEntregados=false; // deberá darse en constructor
    @Setter
    private boolean esIntercontinentalAhora= esIntercontinentalPorYaEntregados;

    private EstadoPedido estado;
    private HashSet<Long> idsRutasProgramadas;

    /**
     * Constructor validante.
     *
     * @param id                             identificador del pedido (debe ser >= 0)
     * @param idAlmacenDestino               id del almacén destino
     * @param cantidadProductosPedidos       cantidad total pedida (>= 0)
     * @param cantidadProductosEntregados    cantidad ya entregada (>= 0, <= cantidadProductosPedidos)
     * @param instanteRegistro               instante de registro (puede ser null)
     * @param instanteMaximoParaEntregar     plazo máximo de entrega (puede ser null)
     * @param idsRutasProgramadas            set de ids de rutas ya relacionadas (puede ser null)
     * @param estado                         estado dado del pedido (no nulo)
     */
    public PedidoParaAlgoritmo(long id,
                               long idAlmacenDestino,
                               int cantidadProductosPedidos,
                               int cantidadProductosEntregados,
                               Instant instanteRegistro,
                               Instant instanteMaximoParaEntregar,
                               HashSet<Long> idsRutasProgramadas
            ,
                               EstadoPedido estado
    ) {

        if (id < 0) throw new IllegalArgumentException("id no puede ser negativo");
        if (cantidadProductosPedidos < 0) throw new IllegalArgumentException("cantidadProductosPedidos < 0");
        if (cantidadProductosEntregados < 0) throw new IllegalArgumentException("cantidadProductosEntregados < 0");
        if (cantidadProductosEntregados + cantidadProductosProgramados > cantidadProductosPedidos) {
            throw new IllegalArgumentException("cantidadEntregada + cantidadProgramada no puede exceder cantidadPedidos");
        }
//        Objects.requireNonNull(estado, "estado no puede ser nulo");

        this.id = id;
        this.idAlmacenDestino = idAlmacenDestino;
        this.cantidadProductosPedidos = cantidadProductosPedidos;
        this.cantidadProductosEntregados = cantidadProductosEntregados;
        this.cantidadProductosProgramados = 0;
        this.instanteRegistro = instanteRegistro;
        this.instanteMaximoParaEntregar = instanteMaximoParaEntregar;

        // copia defensiva del set (si es null, lo dejamos vacío)
        if (idsRutasProgramadas == null) {
            this.idsRutasProgramadas = new HashSet<>();
        } else {
            this.idsRutasProgramadas = new HashSet<>(idsRutasProgramadas);
        }

        this.estado = estado;
    }

    static public PedidoParaAlgoritmo desdeEntidad(Pedido p){
        return new PedidoParaAlgoritmo(p.getId(),p.getAlmacenDestino().getId(), p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(), p.getInstanteRegistro(), p.getInstanteMaximoParaEntregar(),null
                ,null // no existe xd
                );
    }

    /**
     * Cantidad restante por atender del pedido: pedida - entregada - programada.
     * Valor no negativo.
     */ // renómbrenlo que ni yo lo entiendo xd, pero sí es muy útil
    public int getCantidadRestanteDeEntregaYProgram() {
        int restante = cantidadProductosPedidos - (cantidadProductosEntregados + cantidadProductosProgramados);
        return Math.max(0, restante);
    }

    public boolean agregarCantidadProgramada(int cantidad) {
        if(cantidadProductosProgramados + cantidad > cantidadProductosPedidos)
            return false;
        cantidadProductosProgramados += cantidad;
        return true;
    }

    public boolean agregarCantidadEntregada(int cantidad) {
        if(cantidadProductosEntregados + cantidad > cantidadProductosPedidos)
            return false;
        cantidadProductosEntregados += cantidad;
        return true;
    }

    public boolean quitarCantidadProgramada(int cantidad) {
        if(cantidadProductosProgramados - cantidad > 0)
            return false;
        cantidadProductosProgramados -= cantidad;

        return true;
    }
    /**
     * Indica si el pedido tiene todavía cantidad pendiente por programar/entregar.
     */
    public boolean estaPendienteDeProgramarOEntregar() {
        return getCantidadRestanteDeEntregaYProgram() > 0;
    }

    /**
     * Devuelve un set inmutable con los ids de rutas asociadas (copia defensiva de lectura).
     */
    public HashSet<Long> obtenerIdsRutasProgramadas() {
        return (HashSet<Long>) Collections.unmodifiableSet(idsRutasProgramadas);
    }
    @Override
    public String toString() {
        return "PedidoParaAlgoritmo{" +
                "id=" + id +
                ", idAlmacenDestino=" + idAlmacenDestino +
                ", pedidas=" + cantidadProductosPedidos +
                ", entregadas=" + cantidadProductosEntregados +
                ", programadas=" + cantidadProductosProgramados +
                ", restante=" + getCantidadRestanteDeEntregaYProgram() +
//                ", estado=" + estado +
                '}';
    }

    public void restarCantidadProgramada(int cantidad) {
        cantidadProductosProgramados = cantidadProductosProgramados - cantidad;
    }
}
