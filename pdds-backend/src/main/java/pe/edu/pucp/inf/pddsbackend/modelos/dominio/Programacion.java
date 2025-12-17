package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
public class Programacion implements Serializable {

    private final Pedido pedido;
    private final Producto producto;
    private Ruta ruta;

    @Setter
    private boolean terminada;

    public Programacion(Pedido pedido, Producto producto, Ruta ruta) {
        /* Crea una programación nueva asociando pedido, producto y ruta. */
        this.pedido = pedido;
        this.producto = producto;
        this.ruta = ruta;
    }

    public Programacion(Programacion original) {
        /* Crea una copia de programación copiando sus componentes. */
        this.pedido = new Pedido(original.pedido);
        this.producto = new Producto(original.producto);
        this.ruta = new Ruta(original.ruta);
    }

    public boolean estaEnUltimoVueloCirculante(Instant instante) {
        /* Verifica si el instante está dentro del intervalo del último vuelo de la ruta. */
        Instant salida = this.ruta.obtenerInstanteSalidaUltimoVuelo();
        Instant llegada = this.ruta.obtenerInstanteLlegadaUltimoVuelo();
        return !instante.isBefore(salida) && !instante.isAfter(llegada);
    }

    public boolean estaEnUltimoAlmacen(Instant instante) {
        /* Verifica si el instante está dentro de la ventana de espera tras el último vuelo. */
        Instant finVueloFinal = this.ruta.obtenerInstanteLlegadaUltimoVuelo();
        return instante.isAfter(finVueloFinal)
                && !instante.isAfter(finVueloFinal.plus(Hiperparametros.HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS));
    }

    public boolean seriaIncancelable(Instant instante) {
        /* Indica si la programación está en un estado donde ya no debería poder cancelarse. */
        return this.estaEnUltimoVueloCirculante(instante) || this.estaEnUltimoAlmacen(instante);
    }

    public boolean soloTiene1VueloYYaSalio(Instant instante) {
        /* Indica si la ruta tiene exactamente un vuelo y ese vuelo ya partió. */
        if (this.ruta.cantidadVuelos() == 1) {
            return this.ruta.obtenerPrimerVuelo().yaPartio_v2(instante);
        }
        return false;
    }

    @Override
    public String toString() {
        /* Retorna una representación textual de la programación para depuración. */
        return "Programacion{" +
                "idPedido=" + this.pedido.getId() +
                ", uuidProducto=" + this.producto.getId() +
                ", idsVueloRuta=" + this.ruta.getUuid() + 
                '}';
    }
}
