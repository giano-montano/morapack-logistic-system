package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAxel;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
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
    public static Double asignarPuntajesRutas_v2(LinkedList<Vuelo> ruta, Instant instanteActual, Instant instanteMaximoEntrega, EstadoGlobal estado)
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
    private static Double calcularAptitudTemporal(List<Vuelo> ruta, Instant instanteActual, Instant instanteMaximoEntrega)
    {
        Double tiempoPartida, tiempoSobrante;
        Instant instantePrimerVuelo, instanteUltimoVuelo;

        instantePrimerVuelo = ruta.get(0).getInicio();
        instanteUltimoVuelo = ruta.get(ruta.size() - 1).getFin();
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
    private static Pair<Double, Double> calcularAptitudLogisticaYEspacial(List<Vuelo> ruta, EstadoGlobal estado)
    {
        Integer nVuelos;
        Double tiempoVuelo, tiempoEspera, espacioAlmacen, aptitudLogística, aptitudEspacial;
        Instant instanteSalida, instanteLlegada;
        Vuelo vueloAnterior;
        Almacen almacenLlegada;

        nVuelos = 0;
        aptitudLogística = aptitudEspacial = tiempoEspera = 0D;

        for (Vuelo vuelo : ruta)
        {
            instanteSalida = vuelo.getInicio();
            instanteLlegada = vuelo.getFin();
            almacenLlegada = estado.buscarAlmacen(vuelo.getIdAlmacenDestino());
            tiempoVuelo = Duration.between(instanteSalida, instanteLlegada).getSeconds() / 3600.0;
            espacioAlmacen = (double) almacenLlegada.getCapacidadOcupada()
                    / almacenLlegada.getCapacidadMaxima();

            if (nVuelos > 0)
            {
                vueloAnterior = ruta.get(nVuelos - 1);
                instanteSalida = vuelo.getInicio();
                instanteLlegada = vueloAnterior.getFin();
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
    public static Double asignarPuntajesPedidos_v2(Pedido pedido, Instant instanteActual)
    {
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
    private static Double calcularUrgenciaTiempo(Pedido pedido, Instant instanteActual)
    {
        Double urgenciaTiempo, tiempoRestante, tiempoMaximoParaEntregar;
        Instant instanteRegistro, instanteMaximoParaEntregar;

        instanteRegistro = pedido.getInstanteRegistro();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
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
    private static Double calcularUrgenciaTamano(Pedido pedido)
    {
        Integer productosTotales, productosEntregados;
        Double urgenciaTamano;

        productosEntregados = pedido.getCantidadProductosEntregados();
        productosTotales = pedido.getCantidadProductosPedidos();
        urgenciaTamano = (productosTotales + 1.0) / (productosEntregados + 1.0);
        urgenciaTamano = Math.log(urgenciaTamano);

        return urgenciaTamano;
    }




/* Legacy */
    private static final int exponente = 2; // experimental
    private static final double factor = 1; // experimental

    // esto debería ser fijo, o hacemos strategies para calcular fitness de
    // soluciones genéricas?
    public static double calcularFitnessSalidaProblema(SalidaProblemaPlanificacion salidaObtenida,
            EntradaProblemaPlanificacion input)
    {
        EstadoGlobal estadoGlobal = input.getEstadoGlobalCopia();

        double fitnessPlanificacion = 0.0;
        HashMap<Long, PedidoParaAxel> pedidos = EstadoGlobal.pedidosDesdeEstadoGlobal(estadoGlobal,
                salidaObtenida.getProgramaciones());

        for (PedidoParaAxel pedido : pedidos.values())
        {
            fitnessPlanificacion += calcularFitnessPedido(pedido, estadoGlobal)
                    * pedido.getCantidad();
        }

        fitnessPlanificacion = fitnessPlanificacion / pedidos.size();

        return fitnessPlanificacion;
    }

    private static double calcularFitnessPedido(PedidoParaAxel pedido, EstadoGlobal estadoGlobal)
    {
        Integer cantidadProductos, largoMinipedidos;
        double[] tiempoEntrega; // [tiempoEntrega, tiempoPolitica] xdd
        double fitnessPedido = 0.0;
        List<Programacion> minipedidos;

        minipedidos = pedido.getMiniPedidos();
        tiempoEntrega = calularTiempoEntrega(minipedidos, estadoGlobal);
        cantidadProductos = pedido.getCantidad();
        largoMinipedidos = minipedidos.size();

        for (Programacion minipedido : minipedidos)
        {
            fitnessPedido += ((/* minipedido.getCantidadProductosEscogidosYaExistentes() */ 1
                    / (double) cantidadProductos)
                    // xd!!! lo puse así pa que funque, corregir creo
                    * calcularFitnessMinipedido(minipedido, estadoGlobal));
        }

        fitnessPedido = (Math.pow(largoMinipedidos, exponente) / (double) cantidadProductos)
                // * ((tiempoEntrega[1] - tiempoEntrega[0]) / tiempoEntrega[1]) ESTE ES EL
                // TERMINO QUE NECESITA LA FUNCION calularTiempoEntrega
                * fitnessPedido;

        return fitnessPedido;
    }

    private static double calcularFitnessMinipedido(Programacion minipedido, EstadoGlobal estadoGlobal)
    {
        Integer cantidadDeEscalas;
        double fitnessRuta = 0.0, tiempoDeVuelo;
        LinkedList<Long> idsVuelo;

        idsVuelo = minipedido.getIdsVueloRuta();
        cantidadDeEscalas = idsVuelo.size();

        for (Long id : idsVuelo)
        {
            ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
            Vuelo vuelo;
            if (ctx!=null)
                vuelo = ctx.getEstado().getVuelos().get(id);
            else
                vuelo = estadoGlobal.getVuelos().get(id);

            fitnessRuta += Math.pow(calcularTiempoDeViaje(vuelo), 2);
        }

        fitnessRuta = factor * cantidadDeEscalas / fitnessRuta;

        return fitnessRuta;
    }

    public static double[] calularTiempoEntrega(List<Programacion> minipedidos, EstadoGlobal estadoGlobal)
    {
        double tiempoEntrega = 0, tiempoPolitica = 0;
        // AQUI DEBERIAS ENCONTRAR EL TIEMPO EN EL QUE SE ENTREGO (tiempoEntrega) Y EL
        // TIEMPO QUE LE ASIGNA POR LA POLITICA (tiempoPolitica: 2 o 3 dias), pero no se
        // como jaja
        for (Programacion minipedido : minipedidos)
        {
            LinkedList<Long> idsVuelo;

            idsVuelo = minipedido.getIdsVueloRuta();
            for (Long id : idsVuelo)
            {
                Vuelo vuelo = estadoGlobal.getVuelos().get(id);

            }
        }

        return new double[]
        {tiempoEntrega, tiempoPolitica};
    }

    public static double calcularTiempoDeViaje(Vuelo vuelo)
    {
        Duration duracion;
        double tiempoDeViaje;

        duracion = Duration.between(vuelo.getInicio(), vuelo.getFin());
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
