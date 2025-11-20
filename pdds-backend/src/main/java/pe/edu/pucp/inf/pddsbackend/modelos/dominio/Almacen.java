package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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
                   List<UUID> idsProductosExistentes,
                   Continente continente
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

        this.continente = continente;
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

        this.idsProductosExistentes =  new ArrayList<>(value.idsProductosExistentes);
    }

    public static Almacen desdeEntidad(AlmacenEntidad a){
        // ✅ NO cargamos productosActuales desde BD para evitar LazyInitializationException
        // En simulación, los productos se manejan en el EstadoGlobal del contexto (en memoria)
        // No necesitamos cargar la colección lazy de productos desde la entidad JPA
        return new Almacen(
                a.getId(),
                a.getEsInfinito(),
                a.getCapacidadMaxima(),
                a.getCapacidadOcupada(),
                a.getNombrePais(),
                a.getNombreCiudad(),
                a.getCodigoAeropuertoEn4Letras(),
                a.getCodigoCiudadEn4Letras(),
                new ArrayList<>(), // ← Lista vacía: productos se manejan en EstadoGlobal de simulación
                a.getContinente()
        );
    }

//    public static Almacen desdeEntidadYListas(AlmacenEntidad a, HashSet<Long> idsVuelosQueLoTienenComoDestino,
//                                                           HashSet<Long> idsVuelosQueLoTienenComoOrigen, HashSet<Long> idsPedidosConDestino){
//        Almacen almacen = desdeEntidad(a);
//        almacen.idsVuelosQueLoTienenComoDestino = idsVuelosQueLoTienenComoDestino;
//        almacen.idsVuelosQueLoTienenComoOrigen = idsVuelosQueLoTienenComoOrigen;
//        almacen.idsPedidosConDestino = idsPedidosConDestino;
//        return almacen;
//    }

    /** Recalcula campos derivados a partir de capacidadMaxima, capacidadOcupada y capacidadReservada. */
    private void recalcularDerivados() {
        if (capacidadMaxima < 0) capacidadMaxima = 0; // por seguridad, aunque sería mejor validar antes
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
    }

    /* Intenta ocupar inmediatamente, true si pudo; false si es inconsistente*/
    public boolean agregarProducto(Producto producto) {
        if (producto == null) return false;
        if (idsProductosExistentes.contains(producto.getUuid())) {
            return false; // ya estaba
        }
        if (capacidadSinOcupar >= 1) {
            idsProductosExistentes.add(producto.getUuid());
            capacidadOcupada += 1;
            recalcularDerivados();
            return true;
        }
        return false;

//        if (capacidadSinOcupar >= 1) { // un solo productito
//            capacidadOcupada += 1;
//            recalcularDerivados();
//            idsProductosExistentes.add(producto.getUuid());
//            return true;
//        }
//        return false;
    }

    /* Intenta desocupar inmediatamente, true si pudo; false si es inconsistente*/
    public boolean quitarProducto(Producto producto) {
        if (producto == null) return false;
        boolean removed = idsProductosExistentes.remove(producto.getUuid());
        if (removed) {
            capacidadOcupada = Math.max(0, capacidadOcupada - 1);
            recalcularDerivados();
            return true;
        }
        return false;

//        if (capacidadOcupada >= 1) {
//            capacidadOcupada -= 1;
//            recalcularDerivados();
//            idsProductosExistentes.remove(producto.getUuid());
//            return true;
//        }
//        return false;
    }

    public boolean agregarVarios(List<Producto> productos) {
        for(Producto producto: productos) {
            if (!agregarProducto(producto)) return false;
        }
        return true;
    }

    public boolean quitarVarios(List<Producto> productos) {
        for(Producto producto: productos) {
            if (!quitarProducto(producto)) return false;
        }
        return true;
    }

    /* Intenta ocupar incluso si es inconsistente*/
    public boolean agregarProductoIlegalmente(Producto producto) {

        capacidadOcupada += 1;
        capacidadSinOcupar -=1;
        idsProductosExistentes.add(producto.getUuid());

        if (capacidadSinOcupar >= 1) { // un solo productito
            return true;
        }
        return false;
    }

    /* Intenta desocupar incluso si es inconsistente*/
    public boolean quitarProductoIlegalmente(Producto producto) {

        capacidadOcupada -= 1;
        capacidadSinOcupar +=1;
        idsProductosExistentes.remove(producto.getUuid());

        if (capacidadOcupada >= 1) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Almacen{" +
                "id=" + id +
                ", esInfinito=" + esInfinito +
                ", capacidadMaxima=" + capacidadMaxima +
                ", capacidadOcupada=" + capacidadOcupada +
                ", capacidadSinOcupar=" + capacidadSinOcupar +
                ", nombrePais='" + nombrePais + '\'' +
                ", nombreCiudad='" + nombreCiudad + '\'' +
                ", codigoAeropuertoEn4Letras='" + codigoAeropuertoEn4Letras + '\'' +
                ", codigoCiudadEn4Letras='" + codigoCiudadEn4Letras + '\'' +
                ", continente=" + continente +
                ", idsProductosExistentes (numero de uuids)=" + idsProductosExistentes.size() +
                '}';
    }
}