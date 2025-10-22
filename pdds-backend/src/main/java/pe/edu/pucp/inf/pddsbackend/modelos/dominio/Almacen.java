package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;

import java.util.*;

@Getter // creo que normal
public class Almacen {
    // propios del dominio:
    private long id;
    private boolean esInfinito;
    private int capacidadMaxima;
    private int capacidadOcupada;
    private int capacidadSinOcupar;
    private String nombrePais;
    private String nombreCiudad;
    private String codigoAeropuertoEn4Letras;
    private String codigoCiudadEn4Letras;
    private Continente continente;

    private List<UUID>idsProductosExistentes; // solo para facilitar, no deberíamos persistir desde acá, solo desde program.
    //^^^ Pero sí es muy ventajoso mantener esto.

    // índices:

    // Constructor principal
    public Almacen(long id,
                   boolean esInfinito,
                   int capacidadMaxima,
                   int capacidadOcupada,
                   String nombrePais,
                   String nombreCiudad,
                   String codigoAeropuertoEn4Letras,
                   String codigoCiudadEn4Letras,
                   List<UUID> idsProductosExistentes
                   ) {
        this.id = id;
        this.esInfinito = esInfinito;
        this.capacidadMaxima = capacidadMaxima; // sí tienen una capacidad fija a pesar de ser infinitos!!
        this.capacidadOcupada = capacidadMaxima>=capacidadOcupada?capacidadOcupada:0;
        this.capacidadSinOcupar = capacidadMaxima - this.capacidadOcupada;
        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;

        this.idsProductosExistentes = idsProductosExistentes!=null?
                new LinkedList<>(idsProductosExistentes):
                new LinkedList<>();
    }

    // clone
    public Almacen(Almacen value) {
        this.id = value.id;
        this.esInfinito = value.esInfinito;
        this.capacidadMaxima = value.capacidadMaxima;
        this.capacidadOcupada = value.capacidadOcupada;
        this.capacidadSinOcupar = value.capacidadSinOcupar;
        this.nombrePais = value.nombrePais;
        this.nombreCiudad = value.nombreCiudad;
        this.codigoAeropuertoEn4Letras = value.codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = value.codigoCiudadEn4Letras;
        this.continente = value.continente;

        this.idsProductosExistentes = value.idsProductosExistentes;
    }

    /** Recalcula campos derivados a partir de capacidadMaxima, capacidadOcupada y capacidadReservada. */
    private void recalcularDerivados() {
        if (capacidadMaxima < 0) capacidadMaxima = 0; // por seguridad, aunque sería mejor validar antes
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
    }

    /* Intenta ocupar inmediatamente, true si pudo; false si es inconsistente*/
    public boolean agregarProducto(Producto producto) {
        if (capacidadSinOcupar >= 1) { // un solo productito
            capacidadOcupada += 1;
            recalcularDerivados();
            idsProductosExistentes.add(producto.getUuid());
            return true;
        }
        return false;
    }

    /* Intenta desocupar inmediatamente, true si pudo; false si es inconsistente*/
    public boolean quitarProducto(Producto producto) {
        if (capacidadOcupada >= 1) {
            capacidadOcupada -= 1;
            recalcularDerivados();
            idsProductosExistentes.remove(producto.getUuid());
            return true;
        }
        return false;
    }

    /**
     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas previas).
     * No consume reservas; decrementa capacidadSinOcupar y aumenta capacidadOcupada.
     *
     * @return true si se pudo ocupar la cantidad solicitada; false en otro caso.
     */
//    public synchronized boolean ocuparCapacidad(int cantidad) {
//        if (cantidad <= 0) return false;
//        // capacidadSinOcupar = capacidadMaxima - capacidadOcupada (no incluye reservadas)
//        if (capacidadSinOcupar >= cantidad) {
//            capacidadOcupada += cantidad;
//            // recalcular derivados
//            recalcularDerivados();
//            return true;
//        }
//        return false;
//    }

}
