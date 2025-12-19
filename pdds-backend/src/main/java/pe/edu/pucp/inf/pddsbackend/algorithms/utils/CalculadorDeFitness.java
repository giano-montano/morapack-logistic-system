package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.apache.commons.lang3.tuple.Pair;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAxel;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.PESO_APTITUD_TEMPORAL;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.PESO_APTITUD_LOGISTICA;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class CalculadorDeFitness
{
    private static final int exponente = 2; // experimental
    private static final double factor = 1; // experimental


    private CalculadorDeFitness()
    {
        throw new AssertionError("No se inicializa la CalculadorDeFitness");
    }

    /*
     * Función que asigna puntajes a rutas según la siguiente formula: score = alfa1
     * * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial
     *
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a
     * 0 significa que la ruta es mejor valorada. Falta tunear los coeficientes
     * alfa1 y alfa2 También podría evaluar qué tan cargados estén los vuelos (esto
     * no esta ni implementado ni modelado en la ecuación). También podría evaluar
     * qué tanto espacio va a ocupar en almacenes escala (esto no esta ni
     * implementado ni modelado en la ecuación).
     *
     */
    public static Double asignarPuntajesRutas(Ruta ruta, Instant instanteActual, Instant instanteMaximoEntrega, EstadoGlobal estado)
    {
        Double score, aptitudTemporal, aptitudLogística, aptitudEspacial;
        Pair<Double, Double> aptitudes;

        aptitudTemporal = calcularAptitudTemporal(ruta, instanteActual, instanteMaximoEntrega);
        aptitudes = calcularAptitudLogisticaYEspacial(ruta, estado);
        aptitudLogística = aptitudes.getLeft();
        aptitudEspacial = aptitudes.getRight();

        score = PESO_APTITUD_TEMPORAL * aptitudTemporal + PESO_APTITUD_LOGISTICA * aptitudLogística * aptitudEspacial;

        return score;
    }

    /*
     * Calcula segun la formula: (instantePrimerVuelo - instanteActual) /
     * (instanteMaximoParaEntregar - instanteUltimoVuelo)
     *
     * Las ruta asume que el primer vuelo todavía no sale
     *
     * instantePrimerVuelo -> instante de salida del primer vuelo de la ruta
     * instanteUltimoVuelo -> instante de llegada del ultimo vuelo dela ruta
     * instanteActual -> instante en el que se solicito la planificación
     * instanteMaximoParaEntregar -> instante de entrega máximo
     *
     */
    private static Double calcularAptitudTemporal(Ruta ruta, Instant instanteActual, Instant instanteMaximoEntrega)
    {
        Double tiempoPartida, tiempoSobrante;
        Instant instantePrimerVuelo, instanteUltimoVuelo;

        instantePrimerVuelo = ruta.obtenerPrimerVuelo().getInstanteSalida();
        instanteUltimoVuelo = ruta.obtenerUltimoVuelo().getInstanteLlegada();
        tiempoPartida = Duration.between(instanteActual, instantePrimerVuelo).toMillis() / 1000.0;
        tiempoSobrante = Duration.between(instanteUltimoVuelo, instanteMaximoEntrega)
                .toMillis() / 1000.0;

        return (tiempoPartida) / (tiempoSobrante);
    }

    /*
     * Calcula según la formula: aptitudLogística = nVuelos / sum(sqrt(tiempoVuelo_i
     * ^ 2 + tiempoEspera ^ 2_i)) aptitudEspacial = (capacidadOcupada ) /
     * capacidadTotal
     *
     * nVuelos -> cantidad de vuelos que posee la ruta tiempoVuelo_i -> duración del
     * vuelo i-ésimo de la ruta evaluada tiempoEspera_i -> duración de la espera
     * i-ésima antes de abordar el siguiente vuelo capacidadOcupada -> capacidad
     * ocupada del almacén capacidadTotal capacidadTotal -> capacidad máxima del
     * almacén
     *
     */
    private static Pair<Double, Double> calcularAptitudLogisticaYEspacial(Ruta ruta, EstadoGlobal estado)
    {
        Integer nVuelos;
        Double tiempoVuelo, tiempoEspera, espacioAlmacen, aptitudLogística, aptitudEspacial;
        Instant instanteSalida, instanteLlegada;
        Vuelo vueloAnterior;
        Almacen almacenLlegada;

        nVuelos = 0;
        aptitudLogística = aptitudEspacial = tiempoEspera = 0D;

        for (Vuelo vuelo : ruta.getVuelos())
        {
            instanteSalida = vuelo.getInstanteSalida();
            instanteLlegada = vuelo.getInstanteLlegada();
            almacenLlegada = vuelo.getAlmacenDestino();
            tiempoVuelo = Duration.between(instanteSalida, instanteLlegada).getSeconds() / 3600.0;
            espacioAlmacen = (double) almacenLlegada.getInventario().size()
                    / almacenLlegada.getCapacidad();

            if (nVuelos > 0)
            {
                vueloAnterior = ruta.getVuelos().get(nVuelos - 1);
                instanteSalida = vuelo.getInstanteSalida();
                instanteLlegada = vueloAnterior.getInstanteLlegada();
                tiempoEspera = Duration.between(instanteLlegada, instanteSalida).getSeconds()
                        / 3600.0;
            }

            aptitudLogística += Math.sqrt(Math.pow(tiempoVuelo, 2) + Math.pow(tiempoEspera, 2));
            aptitudEspacial += espacioAlmacen;
            nVuelos++;
        }

        aptitudLogística = nVuelos / aptitudLogística;
        aptitudEspacial = aptitudEspacial / nVuelos;

        return Pair.of(aptitudLogística, aptitudEspacial);
    }

    /*
     * Función que asigna puntaje a un pedido según la siguiente formula: score =
     * urgenciaTiempo + urgenciaTamaño
     *
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a 0 significa que el pedido es más urgente. Para pedidos iguales de urgentes, el valor de score es de aproximadamente 6 y aumenta de forma logaritmica
     *
     * Remplazo de asignarPuntajesPedidos
     */
    public static Double asignarPuntajesPedidos(Pedido pedido, Instant instanteActual) {
        Double puntaje;

        puntaje = calcularUrgenciaTiempo(pedido, instanteActual) + calcularUrgenciaTamano(pedido);

        return puntaje;
    }  

    /*
     * Calcula segun la formula: urgenciaTiempo = (instanteMaximoParaEntregar -
     * instanteActual) / ( instanteMaximoParaEntregar - instanteRegistro)
     *
     * instanteMaximoParaEntregar -> instante de entrega máximo instanteActual ->
     * instante en el que se solicito la planificacion instanteRegistro -> instante
     * de registro del pedido
     *
     */
    private static Double calcularUrgenciaTiempo(Pedido pedido, Instant instanteActual) {
        Double urgenciaTiempo, tiempoRestante, tiempoMaximoParaEntregar;
        Instant instanteRegistro, instanteMaximoParaEntregar;

        instanteRegistro = pedido.getInstanteRegistro();
        instanteMaximoParaEntregar = pedido.getInstanteLimite();
        tiempoRestante = Duration.between(instanteActual, instanteMaximoParaEntregar).toMillis()
                / 1000.0;
        tiempoMaximoParaEntregar = Duration.between(instanteRegistro, instanteMaximoParaEntregar)
                .toMillis() / 1000.0;
        urgenciaTiempo = tiempoRestante / tiempoMaximoParaEntregar;

        return urgenciaTiempo;
    }

    /*
     * Calcula segun al formula: ln((1 + productosTotales) / (1 +
     * productosEntregados))
     *
     * productosTotales -> cantidad de productos que compone el pedido
     * productosEntregados -> cantidad de productos entregados
     *
     */
    private static Double calcularUrgenciaTamano(Pedido pedido) {
        Integer productosTotales, productosEntregados;
        Double urgenciaTamano;

        productosEntregados = pedido.obtenerCantidadProductosFaltantes();
        productosTotales = pedido.getCantidadProductos();
        urgenciaTamano = (productosTotales + 1.0) / (productosEntregados + 1.0);
        urgenciaTamano = Math.log(urgenciaTamano);

        return urgenciaTamano;
    }

    /**
     * Calcula el fitness de la solución de planificación.
     * El fitness es un promedio ponderado del fitness de cada pedido programado.
     * Un fitness cercano a 0 indica una mejor solución.
     */
    public static double calcularFitnessSalidaProblema(
            SalidaProblemaPlanificacion salidaObtenida,
            EntradaProblemaPlanificacion input) {
        
        EstadoGlobal estadoGlobal = input.getEstadoGlobalCopia();
        Map<Long, Pedido> pedidosDelEstado = estadoGlobal.getPedidos();
        List<Programacion> programaciones = salidaObtenida.getProgramaciones();
        
        if (pedidosDelEstado.isEmpty()) {
            return 0.0;
        }
        
        double fitnessPlanificacion = 0.0;
        int cantidadPedidos = 0;
        
        // Agrupar programaciones por pedido
        Map<Long, List<Programacion>> programacionesPorPedido = new HashMap<>();
        for (Programacion programacion : programaciones) {
            Long idPedido = programacion.getPedido().getId();
            programacionesPorPedido.computeIfAbsent(idPedido, k -> new LinkedList<>()).add(programacion);
        }
        
        // Calcular fitness para cada pedido con programaciones
        for (Map.Entry<Long, List<Programacion>> entry : programacionesPorPedido.entrySet()) {
            Long idPedido = entry.getKey();
            List<Programacion> programacionesDelPedido = entry.getValue();
            Pedido pedido = pedidosDelEstado.get(idPedido);
            
            if (pedido != null) {
                double fitnessPedido = calcularFitnessPedido(pedido, programacionesDelPedido, estadoGlobal);
                int cantidadProductosPedido = pedido.getCantidadProductos();
                fitnessPlanificacion += fitnessPedido * cantidadProductosPedido;
                cantidadPedidos++;
            }
        }
        
        fitnessPlanificacion = fitnessPlanificacion / cantidadPedidos;
        
        return fitnessPlanificacion;
    }

    /**
     * Calcula el fitness de un pedido basándose en sus programaciones.
     * Considera el número de programaciones, la urgencia y la eficiencia de las rutas.
     */
    private static double calcularFitnessPedido(
            Pedido pedido,
            List<Programacion> programacionesDelPedido,
            EstadoGlobal estadoGlobal) {
        
        int cantidadProductosPedido = pedido.getCantidadProductos();
        int cantidadProgramaciones = programacionesDelPedido.size();
        
        double fitnessPedido = 0.0;
        
        for (Programacion programacion : programacionesDelPedido) {
            double fitnessProgramacion = calcularFitnessProgramacion(programacion, estadoGlobal);
            double proporcionProgramacion = 1.0 / (double) cantidadProductosPedido;
            fitnessPedido += proporcionProgramacion * fitnessProgramacion;
        }
        
        // Penalizar por tener más programaciones (más escalas)
        double penalizacionProgramaciones = Math.pow(cantidadProgramaciones, exponente) / (double) cantidadProductosPedido;
        fitnessPedido = penalizacionProgramaciones * fitnessPedido;
        
        return fitnessPedido;
    }

    /**
     * Calcula el fitness de una programación individual basándose en su ruta.
     * Considera el número de escalas y el tiempo total de viaje.
     */
    private static double calcularFitnessProgramacion(
            Programacion programacion,
            EstadoGlobal estadoGlobal) {
        
        Ruta ruta = programacion.getRuta();
        int cantidadEscalas = ruta.getVuelos().size();
        
        double fitnessRuta = 0.0;
        
        for (Vuelo vuelo : ruta.getVuelos()) {
            double tiempoViajeHoras = calcularTiempoDeViaje(vuelo);
            fitnessRuta += Math.pow(tiempoViajeHoras, 2);
        }
        
        // Preferir rutas con menos escalas y menos tiempo de viaje
        fitnessRuta = factor * cantidadEscalas / fitnessRuta;
        
        return fitnessRuta;
    }

    public static double calcularTiempoDeViaje(Vuelo vuelo)
    {
        Duration duracion;
        double tiempoDeViaje;

        duracion = Duration.between(vuelo.getInstanteSalida(), vuelo.getInstanteLlegada());
        tiempoDeViaje = duracion.toMillis() / 1000.0 / 3600.0;

        return tiempoDeViaje;
    }

    /*
     * Función que asigna puntajes a pedidos según la siguiente formula: score =
     * urgenciaTiempo + urgenciaTamaño
     *
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a
     * 0 significa que el pedido es más urgente. Para pedidos iguales de urgentes,
     * el valor de score es de aproximadamente 6 y aumenta de forma logaritmica
     *
     */
    public static Map<Pedido, Double> asignarPuntajesPedidos(List<Pedido> pedidos, Instant instanteActual)
    {
        Double puntaje;
        Map<Pedido, Double> puntajes = new HashMap<>();

        for (Pedido pedido : pedidos)
        {
            puntaje = calcularUrgenciaTiempo(pedido, instanteActual)
                    + calcularUrgenciaTamano(pedido);
            puntajes.put(pedido, puntaje);
        }

        return puntajes;
    }    
}
