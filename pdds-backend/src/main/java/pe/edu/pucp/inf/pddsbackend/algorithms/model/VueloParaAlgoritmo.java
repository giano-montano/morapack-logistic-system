package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;

import java.time.Instant;
import java.util.HashSet;


/**
 * Constructor: recibe los valores primarios (sin derivados).
 * Valida y normaliza ocupados/reservados para que no excedan la capacidad máxima.
 */
@Getter
public class VueloParaAlgoritmo {
    long id;
    Instant inicio;
    Instant fin;
    long idAlmacenOrigen;
    long idAlmacenDestino;
    int capacidadMaximaProductos;
    int capacidadOcupadaProductos; // la capacidad ocupada cuando sale, es como una reservada también.
    // O sea, la cantidad que llenarán cuando esté por despegar
    // ME OLVIDÉ SU CÓDIGO XD, PERO NO IMPORTA, ID BASTA CREO
    //    int capacidadReservadaHastaAhora; // por una ruta previa... o dinámico tmb? puede ser
    // derivados
    int capacidadSinOcupar;
//    int capacidadDisponibleParaReservar;

    HashSet<Long> idsRutasProgramadasDePlanifNoColapsNiReprog;
    HashSet<Long> idsPedidosDeRutasProgramadas;


    /**
     * Constructor: recibe los valores primarios (sin derivados).
     * Valida y normaliza ocupados/reservados para que no excedan la capacidad máxima.
     */
    public VueloParaAlgoritmo(long id,
                              Instant inicio,
                              Instant fin,
                              long idAlmacenOrigen,
                              long idAlmacenDestino,
                              int capacidadMaximaProductos,
                              int capacidadOcupadaProductos,
                              HashSet<Long> idsRutasProgramadasDePlanifNoColapsNiReprog,
                              HashSet<Long> idsPedidosDeRutasProgramadas) {
        this.id = id;
        this.inicio = inicio;
        this.fin = fin;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.capacidadMaximaProductos = Math.max(0, capacidadMaximaProductos);

        // normalizar ocupados y reservados dentro de límites razonables
        this.capacidadOcupadaProductos = Math.max(0, Math.min(this.capacidadMaximaProductos, capacidadOcupadaProductos));
        int maxReservable = Math.max(0, this.capacidadMaximaProductos - this.capacidadOcupadaProductos);
//        this.capacidadReservadaHastaAhora = 0;

        // inicializar sets si vienen nulos
        this.idsRutasProgramadasDePlanifNoColapsNiReprog = (idsRutasProgramadasDePlanifNoColapsNiReprog == null)
                ? new HashSet<>() : idsRutasProgramadasDePlanifNoColapsNiReprog;
        this.idsPedidosDeRutasProgramadas = (idsPedidosDeRutasProgramadas == null)
                ? new HashSet<>() : idsPedidosDeRutasProgramadas;

        // calcular campos derivados
        recalcularDerivados();
    }
    public static VueloParaAlgoritmo desdeEntidad(Vuelo v ){
        return new VueloParaAlgoritmo(v.getId(),v.getFechaHoraInicioUtc(),v.getFechaHoraFinUtc(),
                v.getAlmacenOrigen().getId(), v.getAlmacenDestino().getId(), v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),null,null);
    }

    // ------------------ helpers privados ------------------

    /** Recalcula campos derivados según ocupados/reservados. */
    private void recalcularDerivados() {
        capacidadSinOcupar = Math.max(0, capacidadMaximaProductos - capacidadOcupadaProductos);
//        capacidadDisponibleParaReservar = Math.max(0, capacidadMaximaProductos - capacidadOcupadaProductos - capacidadReservadaHastaAhora);
    }

    // ------------------ operaciones públicas (sin bloqueo externo) ------------------

    /**
     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas).
     *
     * @return true si se pudo ocupar; false si no hay suficiente capacidad sin ocupar.
     */
    public synchronized boolean ocuparCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        if (capacidadSinOcupar >= cantidad) {
            capacidadOcupadaProductos += cantidad;
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
        if (capacidadOcupadaProductos >= cantidad) {
            capacidadOcupadaProductos -= cantidad;
            recalcularDerivados();
            return true;
        }
        return false;
    }

    /**
     * Obtiene la cantidad actualmente sin ocupar.
     */
    public synchronized int obtenerCapacidadSinOcupar() {
        recalcularDerivados();
        return capacidadSinOcupar;
    }

    /**
     * Reserva 'cantidad' unidades para envíos futuros (no las ocupa aún).
     *
     * @return true si la reserva fue aceptada; false si no hay suficiente capacidad disponible para reservar.
     */
//    public synchronized boolean reservarCapacidad(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadDisponibleParaReservar >= cantidad) {
//            capacidadReservadaHastaAhora += cantidad;
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }

    /**
     * Libera 'cantidad' de capacidad previamente reservada (sin convertir en ocupada).
     *
     * @return true si existía suficiente reservado y se liberó; false si no.
     */
//    public synchronized boolean liberarCapacidadReservada(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadReservadaHastaAhora >= cantidad) {
//            capacidadReservadaHastaAhora -= cantidad;
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }

    /**
     * Convierte parte de la reserva en ocupación (reserved -> occupied).
     *
     * @return true si existía suficiente reservado y se confirmó; false si no.
     */
//    public synchronized boolean confirmarReservaComoOcupada(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadReservadaHastaAhora >= cantidad) {
//            capacidadReservadaHastaAhora -= cantidad;
//            capacidadOcupadaProductos += cantidad;
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }
//
//    /**
//     * Obtiene la cantidad actualmente disponible para reservar.
//     */
//    public synchronized int obtenerCapacidadDisponibleParaReservar() {
//        recalcularDerivados();
//        return capacidadDisponibleParaReservar;
//    }



    public boolean tieneCapacidadDisponible(){
        return obtenerCapacidadSinOcupar() > 0;
    }
    public boolean yaPartio(){
        return inicio.isBefore(Instant.now());
    }

}
