package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;

@Getter
public class Ruta implements Serializable {
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
     * Constructor copia profunda usando serialización
     */
    public Ruta(Ruta ruta) {
        Ruta copia = deepCopy(ruta);
        this.uuid = copia.uuid;
        this.vuelos = copia.vuelos;
        this.puntaje = copia.puntaje;
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
        // Tener cuidado con si tomar o no el instante actual o el tLimiteRecojo
    }

    /*
     * Verifica que t_llegada + HORAS_ESPERA_PARA_RECOJO <= t_actual
     */
    public boolean verificarRutaFinalizada(Instant instanteActual) {
        Instant tLlegada = obtenerInstanteLlegada();
        Instant tLimiteRecojo = tLlegada.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
        
        return !tLimiteRecojo.isAfter(instanteActual);
    }

    /*
     * Verificar que sea su ultimo vuelo
     */
    public boolean verificarUltimoVuelo(Vuelo vuelo) {
        return obtenerUltimoVuelo().getId() == vuelo.getId();
        // Con id es más seguro
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

    public boolean tieneVuelo(long idVuelo) {
        return vuelos.stream().anyMatch(vuelo -> vuelo.getId() == idVuelo);
    }

    @Override
    public String toString() {
        return "Ruta{IMPRESION NO IMPLEMENTADA";
    }

}
