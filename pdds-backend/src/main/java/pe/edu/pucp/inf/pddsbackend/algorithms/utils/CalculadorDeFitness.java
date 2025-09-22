package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.util.HashMap;
import java.util.Map;

@Component
public class CalculadorDeFitness {



    // esto debería ser fijo, o hacemos strategies para calcular fitness de soluciones genéricas?
    public static double calcularFitnessSalidaProblema(SalidaProblemaPlanificacion salidaObtenida, EntradaProblemaPlanificacion input){
        EstadoGlobalMutableProblemaPlanificacion estadoGlobal =
                EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(input);
        estadoGlobal.setRutasSolucionQueGeneraAlgoritmo(salidaObtenida.getRutasProgramadasParaSatisfacerTodoPedido());
        /*
        * estadoGlobal: son los almacenes, vuelos y pedidos tal como están AHORITA EN LA VIDA REAL.
        * También contiene rutasSolucionQueGeneraAlgoritmo que estamos inicializando con las rutas que YA
        * generó el planificador/algoritmo/ejecución.
        * Tu responsabilidad es hallar el fitness apoyando de las funciones que NO MUTAN el estadoGlobal
        * por ejm: puedes usar la función obtenerAlmacenEnInstante(...)
        * */
        // OJO: RUTA_PROGRAMADA = MINIPEDIDO + LISTA DE VUELOS EN ORDEN
        HashMap<Long, PedidoParaAxel> pedidosMap = EstadoGlobalMutableProblemaPlanificacion.pedidosDesdeEstadoGlobal(estadoGlobal);
        System.out.println("pedidos para axel: \n" + PrettyPrinter.printMap(pedidosMap));

        // Ejemplo simple: fitness = proporción de demanda cubierta (sum(cubierto) / sum(demanda))
        int totalDemand = 0;
        int totalCubierto = 0;
        for (Map.Entry<Long, PedidoParaAxel> e : pedidosMap.entrySet()) {
            PedidoParaAxel ppa = e.getValue();
            PedidoParaAlgoritmo pedido = ppa.getPedidoObjeto();
            if (pedido == null) continue;
            int demanda = Math.max(0, pedido.getCantidadProductosPedidos());
            int cubierto = ppa.getCantidadTotalProgramadaEnMiniPedidos(); // suma de cantidades de sus rutas
            totalDemand += demanda;
            totalCubierto += Math.min(demanda, cubierto); // no doblecontar más de lo pedido
        }
        double fitness = (totalDemand == 0) ? 1.0 : (double) totalCubierto / (double) totalDemand;
        return fitness;

//        return 1.0;
    }
}
