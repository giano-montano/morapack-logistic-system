package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAxel;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

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
     * Función que asigna puntajes a pedidos según la siguiente formula: score =
     * urgenciaTiempo + urgenciaTamaño
     *
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a
     * 0 significa que el pedido es más urgente. Para pedidos iguales de urgentes,
     * el valor de score es de aproximadamente 6 y aumenta de forma logaritmica
     *
     */
    public static Map<Pedido, Double> asignarPuntajesPedidos_v2(List<Pedido> pedidos, Instant instanteActual)
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

}
