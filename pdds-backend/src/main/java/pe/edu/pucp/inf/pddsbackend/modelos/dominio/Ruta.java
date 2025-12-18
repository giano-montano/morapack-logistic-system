package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter
public class Ruta {
    private final UUID uuid;
    private final LinkedList<Vuelo> vuelos;
    @Setter private double puntaje;

     /*
      * Constructor principal. Crea una ruta validando la coherencia de su ruta
      */
    public Ruta(LinkedList<Vuelo> vuelos) {
        if(vuelos == null || vuelos.isEmpty()) {
            String error = String.format("ERROR (Ruta): La ruta no tiene vuelos");
            Bitacora.escribir(error);
            throw new IllegalStateException(error);
        }
        verificarCoherenciaRuta(vuelos);
        this.vuelos = new LinkedList<>(vuelos);
        this.uuid = UUID.randomUUID();
        this.puntaje = 0.0;
    }

    /*
     * Crea una copia de Ruta. No debería poder modificarse la lista de vuelos después de creada
     */
    public Ruta(Ruta ruta) {
        this.uuid = ruta.uuid;
        this.vuelos = new LinkedList<>(ruta.vuelos);
        this.puntaje = ruta.puntaje;
    }

    /*
     * Obtener la cantidad de vuelos de la ruta
     */
    public int obtenerCantidadVuelos() {
        verificarNoVacia();
        return this.vuelos.size();
    }

    /*
     * Obtener el primer vuelo de la ruta
     */
    public Vuelo obtenerPrimerVuelo() {
        verificarNoVacia();
        return this.vuelos.getFirst();
    }

    /*
     * Obtener el último vuelo de la ruta
     */
    public Vuelo obtenerUltimoVuelo() {
        verificarNoVacia();
        return this.vuelos.getLast();
    }

    /*
     * Obtener el instante de salida del último vuelo de la ruta
     */
    public Instant obtenerInstanteSalida() {
        return obtenerPrimerVuelo().getInstanteSalida();
    }

    /*
     * Obtener el instante de salida del último vuelo de la ruta, osea, el momento donde ya no se puede cancelar
     */
    public Instant obtenerInstanteIncancelable() {
        return obtenerUltimoVuelo().getInstanteSalida();
    }

    /*
     * Obtener el instante de llegada del último vuelo de la ruta
     */
    public Instant obtenerInstanteLlegada() {
        return obtenerUltimoVuelo().getInstanteLlegada();
    }

    /*
     * Obtener el instante de llegada del último vuelo de la ruta
     */
    public Instant obtenerInstanteRecojo() {
        return obtenerUltimoVuelo().getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
    }

    /*
     * Verifica que un vuelo esté en la ruta
     */
    public boolean verificarVueloEnRuta(Vuelo vuelo) {
        return vuelos.stream()
                .anyMatch(v -> v.equals(vuelo));
    }

    /*
     * Verifica que t_salida <= t_actual < t_incancelable
     */
    public boolean verificarRutaEnIntermedios(Instant instanteActual) {
        Instant tSalida = obtenerInstanteSalida();
        Instant tIncancelable = obtenerInstanteIncancelable();
        
        return !tSalida.isAfter(instanteActual) && instanteActual.isBefore(tIncancelable);
    }

    /*
     * Verifica que t_actual < t_salida
     */
    public boolean verificarRutaNoEmpieza(Instant instanteActual) {
        Instant tSalida = obtenerInstanteSalida();
        
        return instanteActual.isBefore(tSalida);
    }

    /*
     * Verifica que t_incancelable <= t_actual < t_llegada + HORAS_ESPERA_PARA_RECOJO
     */
    public boolean verificarRutaEnUltimoTramo(Instant instanteActual) {
        Instant tIncancelable = obtenerInstanteIncancelable();
        Instant tLlegada = obtenerInstanteLlegada();
        Instant tLimiteRecojo = tLlegada.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
        
        return !tIncancelable.isAfter(instanteActual) && instanteActual.isBefore(tLimiteRecojo);
    }

    /*
     * Verifica que t_llegada + HORAS_ESPERA_PARA_RECOJO <= t_actual
     */
    public boolean verificarRutaFinalizada(Instant instanteActual) {
        Instant tLlegada = obtenerInstanteLlegada();
        Instant tLimiteRecojo = tLlegada.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
        
        return !tLimiteRecojo.isAfter(instanteActual);
    }

    /* Métodos de ayuda */

    /*
     * Lanza una excepción si la ruta no tiene vuelos
     */
    private void verificarNoVacia() {
        if(this.vuelos.isEmpty()) {
            String error = String.format("ERROR (Ruta): La ruta no tiene vuelos");
            Bitacora.escribir(error);
            throw new IllegalStateException(error);
        }
    }

    /*
     * Verifica que la ruta cumpla con las restricciones de coherencia:
     * - Cada vuelo debe partir después de la llegada del vuelo anterior
     * - Cada vuelo debe partir del almacén de llegada del vuelo anterior
     */
    private void verificarCoherenciaRuta(LinkedList<Vuelo> vuelos) {
        for (int i = 1; i < vuelos.size(); i++) {
            Vuelo vueloAnterior = vuelos.get(i - 1);
            Vuelo vueloActual = vuelos.get(i);
            
            if (!vueloActual.getInstanteSalida().isAfter(vueloAnterior.getInstanteLlegada())) {
                String error = String.format("ERROR (Ruta): Incoherencia temporal - Vuelo %d sale antes o al mismo tiempo que llega vuelo %d", i, i - 1);
                Bitacora.escribir(error);
                throw new IllegalStateException(error);
            }
            
            if (!vueloActual.getAlmacenSalida().equals(vueloAnterior.getAlmacenDestino())) {
                String error = String.format("ERROR (Ruta): Incoherencia espacial - Vuelo %d no parte del almacén de llegada del vuelo %d", i, i - 1);
                Bitacora.escribir(error);
                throw new IllegalStateException(error);
            }
        }
    }

    @Override
    public String toString() {
        return "Ruta{IMPRESION NO IMPLEMENTADA";
    }

}
