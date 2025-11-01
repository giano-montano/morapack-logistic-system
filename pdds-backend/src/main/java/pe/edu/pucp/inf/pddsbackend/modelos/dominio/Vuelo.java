package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Vuelo {
    long id;
    long idAlmacenOrigen;
    long idAlmacenDestino;

    String codigo;

    Instant inicio;
    Instant fin;

    int capacidadMaxima;
    int capacidadOcupada;
    int capacidadSinOcupar;
    int capacidadReservada;
    int capacidadDisponibleParaReserva;

    boolean esIntercontinental;

    boolean cancelado=false;

    private List<UUID> idsProductosContenidos; // solo para facilitar, no deberíamos persistir desde acá, solo desde program.

    public Vuelo(long id,
                 long idAlmacenOrigen,
                 long idAlmacenDestino,
                 String codigo,
                 Instant inicio,
                 Instant fin,
                 int capacidadMaxima,
                 int capacidadOcupada
    ) {
        this.id = id;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.codigo = codigo;
        this.inicio = inicio;
        this.fin = fin;
        this.capacidadMaxima = Math.max(0, capacidadMaxima);
        this.capacidadOcupada = Math.max(0, Math.min(this.capacidadMaxima, capacidadOcupada)); // xd
        this.capacidadReservada = 0;
        this.recalcularDerivados();
        this.idsProductosContenidos = new ArrayList<>();
    }

    public Vuelo(Vuelo other) {
        this.id = other.id;
        this.inicio = other.inicio;
        this.fin = other.fin;
        this.idAlmacenOrigen = other.idAlmacenOrigen;
        this.idAlmacenDestino = other.idAlmacenDestino;
        this.codigo = other.codigo;
        this.capacidadMaxima = other.capacidadMaxima;
        this.capacidadOcupada = other.capacidadOcupada;
        this.capacidadSinOcupar = other.capacidadSinOcupar;
        this.capacidadReservada = other.capacidadReservada;
        this.capacidadDisponibleParaReserva = other.capacidadDisponibleParaReserva;
        this.idsProductosContenidos = new ArrayList<>();
    }

    public static Vuelo desdeEntidad(VueloEntidad v ){
        return new Vuelo(
                v.getId(),
                v.getAlmacenOrigen().getId(),
                v.getAlmacenDestino().getId(),
                v.getCodigo4Letras(),
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada()
        );
    }

    /** Recalcula campos derivados según ocupados/reservados. */
    private void recalcularDerivados() {
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
        capacidadDisponibleParaReserva = Math.max(0, capacidadMaxima - capacidadOcupada - capacidadReservada);
    }

    public boolean yaPartio() {
        return inicio.isBefore(Instant.now());
    }

    public int getCapacidadSinOcupar() {
        return capacidadSinOcupar;
    }

    /**
     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas).
     *
     * @return true si se pudo ocupar; false si no hay suficiente capacidad sin ocupar.
     */
    public synchronized boolean ocuparConProducto(Producto producto) {
//        if (cantidad <= 0) return false;
//        if (capacidadSinOcupar >= cantidad) { // QUE  XD
//            capacidadOcupada += cantidad;
//            recalcularDerivados();
//            return true;
//        }
//        return false;
        if (capacidadSinOcupar >= 1) { // un solo productito
            capacidadOcupada += 1;
            recalcularDerivados();
            idsProductosContenidos.add(producto.getUuid());
            return true;
        }
        return false;
    }

    public boolean reservarCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        if (capacidadDisponibleParaReserva >= cantidad) {
            capacidadReservada += cantidad;
            recalcularDerivados();
            return true;
        }
        return false;
    }

    /**
     * Libera (desocupa) 'cantidad' unidades que estaban ocupadas.
     *
     * @return true si había suficiente ocupado y se desocupó; false en otro caso.
     */
    public synchronized boolean desocuparConProducto(Producto producto) {
        if (capacidadOcupada >= 1) {
            capacidadOcupada -= 1;
            recalcularDerivados();
            idsProductosContenidos.remove(producto.getUuid());
            return true;
        }
        return false;
    }

    public boolean agregarVarios(List<Producto> productos) {
        for(Producto producto: productos) {
            if (!ocuparConProducto(producto)) return false;
        }
        return true;
    }

    public boolean quitarVarios(List<Producto> productos) {
        for(Producto producto: productos) {
            if (!desocuparConProducto(producto)) return false;
        }
        return true;
    }

    public boolean entregariaPedidoEnPlazoReal(Pedido pedido){
        Instant plazoMaximoReal = pedido.getPlazoParaLlegadaUltimoVuelo();
        return this.getFin().isBefore(plazoMaximoReal);
    }



    @Override
    public String toString() {
        return "VueloEntidad{" +
                "id=" + id +
                ", inicio=" + inicio +
                ", fin=" + fin +
                ", idAlmacenOrigen=" + idAlmacenOrigen +
                ", idAlmacenDestino=" + idAlmacenDestino +
                ", capacidadMaximaProductos=" + capacidadMaxima +
                ", capacidadOcupadaProductos=" + capacidadOcupada +
                ", capacidadSinOcupar="+ capacidadSinOcupar +
                '}';
    }

}
