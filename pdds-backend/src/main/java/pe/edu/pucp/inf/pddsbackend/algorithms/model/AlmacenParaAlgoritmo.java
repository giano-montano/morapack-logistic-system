package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;

import java.util.HashSet;

@NoArgsConstructor
@Getter
public class AlmacenParaAlgoritmo {
    long id;
    boolean esInfinito;
    int capacidadMaxima;
    int capacidadOcupada;
    int capacidadSinOcupar; // sin OCUPAR
//    int capacidadReservada; // por una ruta programada previa...
//    int capacidadParaReservar;
    String nombrePais;
    String nombreCiudad;
    String codigoAeropuertoEn4Letras;
    String codigoCiudadEn4Letras;

    // índices, no varían
    HashSet<Long> idsVuelosQueLoTienenComoDestino;
    HashSet<Long> idsVuelosQueLoTienenComoOrigen;
    HashSet<Long> idsPedidosConDestino;

    public AlmacenParaAlgoritmo(long id, boolean esInfinito, int capacidadMaxima, int capacidadOcupada,
                                String nombrePais, String nombreCiudad,
                                String codigoAeropuertoEn4Letras, String codigoCiudadEn4Letras,
                                HashSet<Long> idsVuelosQueLoTienenComoDestino,
                                HashSet<Long> idsVuelosQueLoTienenComoOrigen, HashSet<Long> idsPedidosConDestino) {
        this.id = id;
        this.esInfinito = esInfinito;
        this.capacidadMaxima = capacidadMaxima; // sí tienen una capacidad fija a pesar de ser infinitos!!
        this.capacidadOcupada = capacidadMaxima>=capacidadOcupada?capacidadOcupada:0;
        this.capacidadSinOcupar = capacidadMaxima - this.capacidadOcupada;
//        this.capacidadReservada = 0;
//        this.capacidadParaReservar = capacidadMaxima - capacidadReservada - capacidadOcupada;
        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;

        this.idsVuelosQueLoTienenComoDestino = (idsVuelosQueLoTienenComoDestino == null) ? new HashSet<>() : idsVuelosQueLoTienenComoDestino;
        this.idsVuelosQueLoTienenComoOrigen = (idsVuelosQueLoTienenComoOrigen == null) ? new HashSet<>() : idsVuelosQueLoTienenComoOrigen;
        this.idsPedidosConDestino = (idsPedidosConDestino == null) ? new HashSet<>() : idsPedidosConDestino;
    }


    public AlmacenParaAlgoritmo(long id, boolean esInfinito, int capacidadMaxima, int capacidadOcupada,
                                String nombrePais, String nombreCiudad,
                                String codigoAeropuertoEn4Letras, String codigoCiudadEn4Letras) {
        this.id = id;
        this.esInfinito = esInfinito;
        this.capacidadMaxima = capacidadMaxima; // sí tienen una capacidad fija a pesar de ser infinitos!!
        this.capacidadOcupada = capacidadMaxima>=capacidadOcupada?capacidadOcupada:0;
        this.capacidadSinOcupar = capacidadMaxima - this.capacidadOcupada;
//        this.capacidadReservada = 0;
//        this.capacidadParaReservar = capacidadMaxima - capacidadReservada - capacidadOcupada;
        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;

    }

    public AlmacenParaAlgoritmo(AlmacenParaAlgoritmo value) {
        this.id = value.id;
        this.esInfinito = value.esInfinito;
        this.capacidadMaxima = value.capacidadMaxima;
        this.capacidadOcupada = value.capacidadOcupada;
        this.capacidadSinOcupar = value.capacidadSinOcupar;
        this.nombrePais = value.nombrePais;
        this.nombreCiudad = value.nombreCiudad;
        this.codigoAeropuertoEn4Letras = value.codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = value.codigoCiudadEn4Letras;
        this.idsPedidosConDestino = value.idsPedidosConDestino;
        this.idsVuelosQueLoTienenComoOrigen = value.idsVuelosQueLoTienenComoOrigen;
        this.idsVuelosQueLoTienenComoDestino = value.idsVuelosQueLoTienenComoDestino;
    }

    public AlmacenParaAlgoritmo clone(){
//        AlmacenParaAlgoritmo almacenParaAlgoritmo = (AlmacenParaAlgoritmo) super.clone();
        return new AlmacenParaAlgoritmo(id,esInfinito,capacidadMaxima,capacidadOcupada,nombrePais,nombreCiudad,
                codigoAeropuertoEn4Letras,codigoCiudadEn4Letras,
                new HashSet<>(idsVuelosQueLoTienenComoDestino),new HashSet<>(idsVuelosQueLoTienenComoOrigen),
                 new HashSet<>(idsPedidosConDestino) );
    }

    public static AlmacenParaAlgoritmo desdeEntidad(Almacen a){
        return new AlmacenParaAlgoritmo(
        a.getId(),a.getEsInfinito(),a.getCapacidadMaxima(), a.getCapacidadOcupada(),a.getNombrePais(),
        a.getNombreCiudad(),a.getCodigoAeropuertoEn4Letras(), a.getCodigoCiudadEn4Letras(),null,null,null
        );
    }

    public static AlmacenParaAlgoritmo desdeEntidadYListas(Almacen a, HashSet<Long> idsVuelosQueLoTienenComoDestino,
                                                           HashSet<Long> idsVuelosQueLoTienenComoOrigen, HashSet<Long> idsPedidosConDestino){
        AlmacenParaAlgoritmo almacen = desdeEntidad(a);
        almacen.idsVuelosQueLoTienenComoDestino = idsVuelosQueLoTienenComoDestino;
        almacen.idsVuelosQueLoTienenComoOrigen = idsVuelosQueLoTienenComoOrigen;
        almacen.idsPedidosConDestino = idsPedidosConDestino;
        return almacen;
    }

    /**
     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas previas).
     * No consume reservas; decrementa capacidadSinOcupar y aumenta capacidadOcupada.
     *
     * @return true si se pudo ocupar la cantidad solicitada; false en otro caso.
     */
    public synchronized boolean ocuparCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        // capacidadSinOcupar = capacidadMaxima - capacidadOcupada (no incluye reservadas)
        if (capacidadSinOcupar >= cantidad) {
            capacidadOcupada += cantidad;
            // recalcular derivados
            recalcularDerivados();
            return true;
        }
        return false;
    }

    /**
     * Libera (desocupa) 'cantidad' unidades que estaban ocupadas.
     *
     * @return true si la operación se realizó (había al menos 'cantidad' ocupada), false si no.
     */
    public synchronized boolean desocuparCapacidad(int cantidad) {
        if (cantidad <= 0) return false;
        if (capacidadOcupada >= cantidad) {
            capacidadOcupada -= cantidad;
            // recalcular derivados
            recalcularDerivados();
            return true;
        }
        return false;
    }

    /**
     * Reserva 'cantidad' unidades para futuros envíos.
     * La reserva disminuye la capacidad disponible para nuevas reservas
     * (capacidadParaReservar), pero no afecta capacidadSinOcupar.
     *
     * @return true si la reserva fue aceptada; false si no hay suficiente espacio para reservar.
     */
//    public synchronized boolean reservarCapacidad(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadParaReservar >= cantidad) {
//            capacidadReservada += cantidad;
//            // recalcular derivados (capacidadParaReservar cambiará)
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }
//
//    /**
//     * Libera 'cantidad' unidades reservadas (sin convertirlas en ocupadas).
//     *
//     * @return true si existían al menos 'cantidad' reservadas y se liberaron; false si no.
//     */
//    public synchronized boolean liberarCapacidadReservada(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadReservada >= cantidad) {
//            capacidadReservada -= cantidad;
//            // recalcular derivados
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }

    /**
     * Confirma que una parte de las reservas se convierte en ocupación efectiva.
     * Mueve 'cantidad' de reservado -> ocupado.
     *
     * @return true si la conversión fue posible (había suficiente reservado), false si no.
//     */
//    public synchronized boolean confirmarReservaComoOcupada(int cantidad) {
//        if (cantidad <= 0) return false;
//        if (capacidadReservada >= cantidad) {
//            capacidadReservada -= cantidad;
//            capacidadOcupada += cantidad;
//            // recalcular derivados
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }

    /** Recalcula campos derivados a partir de capacidadMaxima, capacidadOcupada y capacidadReservada. */
    private void recalcularDerivados() {
        if (capacidadMaxima < 0) capacidadMaxima = 0; // por seguridad, aunque sería mejor validar antes
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
    }

    /**
     * Obtiene la cantidad actualmente disponible para reservar.
     * (método accesor si quieres usar en vez del campo directo).
     */
//    public synchronized int obtenerCapacidadParaReservar() {
//        // recalcular por seguridad antes de devolver
//        recalcularDerivados();
//        return capacidadParaReservar;
//    }

    /**
     * Obtiene la cantidad actualmente sin ocupar.
     */
    public synchronized int obtenerCapacidadSinOcupar() {
        recalcularDerivados();
        return capacidadSinOcupar;
    }

//    /**
//     * Capacidad disponible para ocupar inmediatamente (sin contar reservas).
//     * Generalmente = capacidadSinOcupar.
//     */
//    public int getAvailableForOccupation() {
//        if (esInfinito) return VIRTUAL_INF_CAP;
//        return Math.max(0, capacidadSinOcupar);
//    }

//    public static AlmacenParaAlgoritmo createFromEntity(Almacen almacen){
//        return AlmacenParaAlgoritmo.builder()
//                .id(almacen.getId())
//                .capacidadOcupada(almacen.getCapacidadOcupada())
//                .capacidadMaxima(almacen.getCapacidadTotal())
//                .capacidadReservadaPorEnvios(almacen.getCapacidadReservadaPorEnvios())
//                .esInfinito(almacen.getEsInfinito())
//                .codigoCiudadEn4Letras(almacen.getCodigoCiudadEn4Letras())
//                .build()
//                ;
//    }

    public synchronized int ocuparCapacidadIlegalmente(int cantidad) {
        if (cantidad <= 0) return 0;
        // capacidadSinOcupar = capacidadMaxima - capacidadOcupada (no incluye reservadas)
//        if (capacidadSinOcupar >= cantidad) {
            capacidadOcupada += cantidad;
            // recalcular derivados
            recalcularDerivados();
            return capacidadMaxima-capacidadOcupada;
//        }
//        return false;
    }
    public synchronized int desocuparCapacidadIlegalmente(int cantidad) {
        if (cantidad <= 0) return 0;
//        if (capacidadOcupada >= cantidad) {
            capacidadOcupada -= cantidad;
            // recalcular derivados
            recalcularDerivados();
            return capacidadMaxima-capacidadOcupada;
//        }
//        return false;
    }


    @Override
    public String toString() {
        return "AlmacenParaAlgoritmo{" +
                "id=" + id +
                ", capacidadMaxima=" + capacidadMaxima +
                ", capacidadOcupada=" + capacidadOcupada +
                ", capacidadSinOcupar=" + capacidadSinOcupar +
                ", nombrePais=" + nombrePais +
                ", nombreCiudad=" + nombreCiudad +
                ", esInfinito=" + esInfinito +
                '}';
    }
}
