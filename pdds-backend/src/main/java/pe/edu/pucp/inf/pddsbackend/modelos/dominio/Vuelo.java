package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

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

    public Vuelo(long id,
                 long idAlmacenOrigen,
                 long idAlmacenDestino,
                  String codigo,
                  Instant inicio,
                  Instant fin,
                  int capacidadMaxima,
                  int capacidadOcupada,
                 int capacidadReservada
    ) {
        this.id = id;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.codigo = codigo;
        this.inicio = inicio;
        this.fin = fin;
        this.capacidadMaxima = Math.max(0, capacidadMaxima);
        this.capacidadOcupada = Math.max(0, Math.min(this.capacidadMaxima, capacidadOcupada)); // xd
        this.capacidadReservada = Math.max(0, capacidadReservada);
        this.recalcularDerivados();
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
    public synchronized boolean ocuparCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        if (capacidadSinOcupar >= cantidad) { // QUE  XD
            capacidadOcupada += cantidad;
            recalcularDerivados();
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
    public synchronized boolean desocuparCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        if (capacidadOcupada >= cantidad) {
            capacidadOcupada -= cantidad;
            recalcularDerivados();
            return true;
        }
        return false;
    }

    public boolean entregariaPedidoEnPlazoReal(Pedido pedido){
        Instant plazoMaximo = pedido.getPlazoParaLlegadaUltimoVuelo();
        return this.getFin().minus(2, ChronoUnit.HOURS).isBefore(plazoMaximo);
    }



    @Override
    public String toString() {
        return "Vuelo{" +
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
