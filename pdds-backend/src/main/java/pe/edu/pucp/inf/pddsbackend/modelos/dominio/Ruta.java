package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class Ruta {

    @Getter
    private UUID uuid;

    @Getter
    private LinkedList<Vuelo> vuelosRuta;

    @Getter
    private Double puntaje;

    public Ruta(LinkedList<Vuelo> vuelos) {
        /* Crea una ruta nueva con una lista de vuelos y valores iniciales por defecto. */
        this.vuelosRuta = new LinkedList<>(vuelos);
        this.uuid = UUID.randomUUID();
        this.puntaje = 0.0;
    }

    public Ruta(Ruta ruta) {
        /* Crea una copia de Ruta sin hacer deep copy de los objetos Vuelo. */
        this.uuid = ruta.uuid;
        this.vuelosRuta = new LinkedList<>(ruta.vuelosRuta);
        this.puntaje = ruta.puntaje;
    }

    public int cantidadVuelos() {
        /* Retorna la cantidad de vuelos de la ruta. */
        return this.vuelosRuta.size();
    }

    public boolean estaVacia() {
        /* Indica si la ruta no contiene vuelos. */
        return this.vuelosRuta.isEmpty();
    }

    public Vuelo obtenerPrimerVuelo() {
        /* Retorna el primer vuelo de la ruta. */
        this.validarNoVacia();
        return this.vuelosRuta.getFirst();
    }

    public Vuelo obtenerUltimoVuelo() {
        /* Retorna el último vuelo de la ruta. */
        this.validarNoVacia();
        return this.vuelosRuta.getLast();
    }

    public Instant obtenerInstanteSalidaUltimoVuelo() {
        /* Retorna el instante de salida del último vuelo de la ruta. */
        return this.obtenerUltimoVuelo().getInstanteSalida();
    }

    public Instant obtenerInstanteLlegadaUltimoVuelo() {
        /* Retorna el instante de llegada del último vuelo de la ruta. */
        return this.obtenerUltimoVuelo().getInstanteLlegada();
    }

    private void validarNoVacia() {
        /* Lanza una excepción si la ruta no tiene vuelos. */
        if (this.vuelosRuta.isEmpty()) {
            throw new IllegalStateException("La ruta no contiene vuelos.");
        }
    }

    public boolean tieneVuelo(long idVuelo){
        return vuelosRuta.stream().anyMatch(vuelo -> vuelo.getId() == idVuelo);
    }

    public LinkedList<Long> getIdsVuelos(){
        return new LinkedList<>( vuelosRuta.stream().map(Vuelo::getId).toList() );
    }

}
