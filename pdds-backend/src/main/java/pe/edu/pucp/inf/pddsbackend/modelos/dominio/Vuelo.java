package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;
import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Getter
public class Vuelo implements Serializable
{
    private static int correlativo = 1;
    @Setter private boolean cancelado = false;
    private final long id;
    private final String codigo;
    private final int capacidad;
    private final Instant instanteSalida;
    private final Instant instanteLlegada;
    private final Almacen almacenSalida;
    private final Almacen almacenDestino;
    private List<Producto> inventario;

    /*
     * Constructor principal. Para uso en simulación
     */
    public Vuelo(Almacen almacenSalida, Almacen almacenDestino, String codigo, Instant instanteSalida, Instant instanteLlegada, int capacidad, boolean cancelado) {
        this.cancelado = false;
        this.id = correlativo;
        this.codigo = codigo;
        this.capacidad = capacidad;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.almacenSalida = almacenSalida;
        this.almacenDestino = almacenDestino;
        this.inventario = new ArrayList<>();

        correlativo++;
    }

    /*
     * Constructor para la BD
     */
    public Vuelo(long id, Almacen almacenSalida, Almacen almacenDestino, String codigo, Instant instanteSalida, Instant instanteLlegada, int capacidad, int capacidadOcupada, boolean intercontinental, boolean cancelado) {
        this.id = id;
        this.almacenSalida = almacenSalida;
        this.almacenDestino = almacenDestino;
        this.codigo = codigo;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.capacidad = capacidad;
        this.inventario = new ArrayList<>();
        this.cancelado = cancelado;
    }

    /*
     * Constructor copia profunda usando serialización
     */
    public Vuelo(Vuelo other) {
        Vuelo copia = deepCopy(other);
        this.id = copia.id;
        this.instanteSalida = copia.instanteSalida;
        this.instanteLlegada = copia.instanteLlegada;
        this.almacenSalida = copia.almacenSalida;
        this.almacenDestino = copia.almacenDestino;
        this.codigo = copia.codigo;
        this.capacidad = copia.capacidad;
        this.inventario = copia.inventario;
        this.cancelado = copia.cancelado;
    }

    /*
     * Convierte una entidad de vuelo a un dominio de vuelo
     */
    public static Vuelo desdeEntidad(VueloEntidad v) {
        return new Vuelo(
                v.getId(),
                Almacen.desdeEntidad( v.getAlmacenOrigen() ),
                Almacen.desdeEntidad( v.getAlmacenDestino() ),
                v.getCodigo4Letras(),
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),
                v.getEsIntercontinental(),
                v.getCancelado());
    }

    /*
     * Obtiene la capacidad vacía del vuelo
     */
    public int obtenerEspacioVacio() {
        return this.capacidad - this.inventario.size();
    }

    /*
     * En base a un instante, devuelve si el vuelo ya partió
     */
    public boolean verificarSalida(Instant instanteActual) {
        return !instanteSalida.isAfter(instanteActual);
    }
    
    /*
     * En base a un instante, devuelve si el vuelo ya llegó
     */
    public boolean verificarLlegada(Instant instanteActual) {
        return !instanteLlegada.isAfter(instanteActual);
    }

    /*
     * Registra una lista de productos al inventario
     */
    public boolean registrarProducto(List<Producto> productos) {
        int inventarioTotal;

        inventarioTotal = this.inventario.size() + productos.size();

        if(inventarioTotal <= this.capacidad) {
            this.inventario.addAll(productos);

            return true;
        }

        return false;
    }

    /*
     * Registra un solo producto al inventario
     */
    public boolean registrarProducto(Producto producto)
    {
        int inventarioTotal;
        
        inventarioTotal = this.inventario.size() + 1;

        if(inventarioTotal <= this.capacidad)
        {
            this.inventario.add(producto);

            return true;
        }

        return false;
    }

    /*
     * Registra un solo producto al inventario. Sincronizado
     */
    public synchronized boolean registrarProductoSincronizado(Producto producto){
        return registrarProducto(producto);
    }

    /*
     * Registra una lista de productos al inventario. Sincronizado
     */
    public synchronized boolean registrarProductoSincronizado(List<Producto> productos){
        return registrarProducto(productos);
    }

    /*
     * Borra un producto del inventario. Sincronizado
     */
    public synchronized boolean borrarProductoSincronizado(Producto producto) {
        if(!this.inventario.isEmpty()) {
            if(this.inventario.remove(producto)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Borra una lista de productos al inventario. Sincronizado
     */
    public synchronized boolean borrarProductoSincronizado(List<Producto> productos) {
        if(!this.inventario.isEmpty()) {
            if(this.inventario.removeAll(productos)) {  
                return true;
            }
        }
        return true;
    }

    /*
     * Obtener el estado del vuelo (string) en base al instante actual
     */
    public String obtenerEstado(Instant instanteActual) {
        if (!verificarSalida(instanteActual) && !verificarLlegada(instanteActual)) {
            return "Por salir";
        }
        if (verificarSalida(instanteActual) && !verificarLlegada(instanteActual)) {
            return "En curso";
        }
        return "Finalizado";
    }

    @Override
    public String toString() {
        return "Vuelo{" +
                "id=" + id +
                ", codigo='" + codigo + '\'' +
                ", capacidad=" + capacidad +
                ", instanteSalida=" + instanteSalida +
                ", instanteLlegada=" + instanteLlegada +
                ", almacenSalida=" + almacenSalida +
                ", almacenDestino=" + almacenDestino +
                ", inventario=" + inventario.size() +
                ", cancelado=" + cancelado +
                '}';
    }

    public void loggearSalidaConsola(@NotNull Instant instanteProgramadoSalidaVuelo, int capacidadTotalACargar) {
        System.out.println("\n=============== VUELO SALIENDO ===============");
        System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
        System.out.println("instanteLlegada: " + instanteLlegada);
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + almacenSalida.getId());
        System.out.println("Almacén Destino: ID=" + getAlmacenDestino());
        System.out.println("Cantidad Productos: " + capacidadTotalACargar);
        System.out.println("Cantidad Productos objetos: " + inventario.size());
        System.out.println("===============================================\n");
    }

    public void loggearLlegadaConsola(@NotNull Instant instanteProgramadoLlegadaVuelo) {
        System.out.println("\n=============== VUELO LLEGANDO ===============");
        System.out.println("Hora: " + instanteProgramadoLlegadaVuelo);
        System.out.println("Salio a las: " + instanteSalida);
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + almacenSalida.getId());
        System.out.println("Almacén Destino: ID=" + getAlmacenSalida());
        System.out.println("UUIDs productos que lleva: " + inventario);
        System.out.println("===============================================\n");
    }
}
