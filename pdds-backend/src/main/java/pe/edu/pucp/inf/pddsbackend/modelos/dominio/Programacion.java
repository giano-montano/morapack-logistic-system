package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
public class Programacion implements Serializable
{
    private final Pedido pedido;
    private final Producto producto;
    Ruta ruta;

    // Constructor principal para programaciones que se vayan haciendo en el
    // algoritmo, o sea programaciones nuevas; esta es la única manera de que
    // se creen programaciones oficialmente.
    public Programacion(
            Pedido pedido,
            Producto producto,
            Ruta ruta) {
        this.pedido = pedido;
        this.producto = producto;
        this.ruta = ruta;

    }

    public Programacion(Programacion original) {
        this.pedido = new Pedido( original.pedido );
        this.producto = new Producto( original.producto );
        this.ruta = new Ruta ( original.ruta );

    }

    public boolean estaEnUltimoVueloCirculante(Instant instante) {
        return !instante.isBefore(this.ruta.getVuelosRuta().getLast().getInstanteSalida())
                && !instante.isAfter(this.ruta.getVuelosRuta().getLast().getInstanteLlegada());
    }

    public boolean estaEnUltimoAlmacen(Instant instante) {
        Instant finVueloFinal = this.ruta.getVuelosRuta().getLast().getInstanteLlegada();
        return instante.isAfter(finVueloFinal)
                && !instante.isAfter(finVueloFinal.plus(Hiperparametros.HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS));
    }

    public boolean seriaIncancelable(Instant instante) {
        return this.estaEnUltimoVueloCirculante(instante) || this.estaEnUltimoAlmacen(instante);
        // de otro modo o es INTERMEDIO o debería ser ELIMINADO
    }

    public boolean soloTiene1VueloYYaSalio(Instant instante) {
        Vuelo primerVuelo = this.ruta.getVuelosRuta().getFirst();
        if( this.ruta.getVuelosRuta().size()> 1){
            // sí considera al propio instante
            return primerVuelo.yaPartio(instante);
        }
        return false;
    }

    @Override
    public String toString() {
        return "Programacion{" +
                "idPedido=" + pedido.getId() +
                ", uuidProducto=" + producto.getId() +
                ", idsVueloRuta=" + ruta.getVuelosRuta() +
                '}';
    }
}
