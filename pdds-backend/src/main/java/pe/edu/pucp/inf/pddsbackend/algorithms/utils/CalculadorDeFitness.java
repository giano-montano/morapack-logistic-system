package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAxel;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@Component
public class CalculadorDeFitness {
    Integer exponente = 2; //experimental
    double factor = 1; //experimental

    // esto debería ser fijo, o hacemos strategies para calcular fitness de soluciones genéricas?
    public static double calcularFitnessSalidaProblema(SalidaProblemaPlanificacion salidaObtenida, EntradaProblemaPlanificacion input) {
        CalculadorDeFitness calc = new CalculadorDeFitness();
        EstadoGlobal estadoGlobal = input.getEstadoGlobalCopia();

        double fitnessPlanificacion = 0.0;
        HashMap<Long, PedidoParaAxel> pedidos = EstadoGlobal.pedidosDesdeEstadoGlobal(estadoGlobal, salidaObtenida.getProgramaciones());

        for (PedidoParaAxel pedido : pedidos.values()) {
            fitnessPlanificacion += calc.calcularFitnessPedido(pedido, estadoGlobal) * pedido.getCantidad();
        }

        fitnessPlanificacion = fitnessPlanificacion / pedidos.size();

        return fitnessPlanificacion;
    }

    private double calcularFitnessPedido(PedidoParaAxel pedido, EstadoGlobal estadoGlobal) {
        Integer cantidadProductos, largoMinipedidos;
        double[] tiempoEntrega; //[tiempoEntrega, tiempoPolitica] xdd
        double fitnessPedido = 0.0;
        List<Programacion> minipedidos;

        minipedidos = pedido.getMiniPedidos();
        tiempoEntrega = calularTiempoEntrega(minipedidos, estadoGlobal);
        cantidadProductos = pedido.getCantidad();
        largoMinipedidos = minipedidos.size();

        for (Programacion minipedido : minipedidos) {
            fitnessPedido += ((/*minipedido.getCantidadProductosEscogidosYaExistentes()*/ 1 / (double) cantidadProductos)
                    // xd!!! lo puse así pa que funque, corregir creo
                    * calcularFitnessMinipedido(minipedido, estadoGlobal));
        }

        fitnessPedido = (Math.pow(largoMinipedidos, exponente) / (double) cantidadProductos)
                //* ((tiempoEntrega[1] - tiempoEntrega[0]) / tiempoEntrega[1]) ESTE ES EL TERMINO QUE NECESITA LA FUNCION calularTiempoEntrega
                * fitnessPedido;

        return fitnessPedido;
    }

    private double calcularFitnessMinipedido(Programacion minipedido, EstadoGlobal estadoGlobal) {
        Integer cantidadDeEscalas;
        double fitnessRuta = 0.0, tiempoDeVuelo;
        LinkedList<Long> idsVuelo;

        idsVuelo = minipedido.getIdsVueloRuta();
        cantidadDeEscalas = idsVuelo.size();

        for (Long id : idsVuelo) {
            Vuelo vuelo = estadoGlobal.getVuelos().get(id);

            fitnessRuta += Math.pow(calcularTiempoDeViaje(vuelo), 2);
        }

        fitnessRuta = factor *  cantidadDeEscalas / fitnessRuta;

        return fitnessRuta;
    }

    public double[] calularTiempoEntrega(List<Programacion> minipedidos, EstadoGlobal estadoGlobal) {
        double tiempoEntrega = 0, tiempoPolitica = 0;
        // AQUI DEBERIAS ENCONTRAR EL TIEMPO EN EL QUE SE ENTREGO (tiempoEntrega) Y EL TIEMPO QUE LE ASIGNA POR LA POLITICA (tiempoPolitica: 2 o 3 dias), pero no se como jaja
        for (Programacion minipedido : minipedidos) {
            LinkedList<Long> idsVuelo;

            idsVuelo = minipedido.getIdsVueloRuta();
            for (Long id : idsVuelo) {
                Vuelo vuelo = estadoGlobal.getVuelos().get(id);

            }
        }

        return new double[]{tiempoEntrega, tiempoPolitica};
    }

    public double calcularTiempoDeViaje(Vuelo vuelo){
        Duration duracion;
        double tiempoDeViaje;

        duracion = Duration.between(vuelo.getInicio(), vuelo.getFin());
        tiempoDeViaje = duracion.toMillis() / 1000.0 / 3600.0;

        return tiempoDeViaje;
    }

}
