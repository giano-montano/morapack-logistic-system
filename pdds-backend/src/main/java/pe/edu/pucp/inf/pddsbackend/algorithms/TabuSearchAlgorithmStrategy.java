package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobalMutableProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;

import java.util.*;

@Component
public class TabuSearchAlgorithmStrategy implements PlanificationStrategy {

    @Autowired
    private LoggedHeuristicAlgorithmStrategy heuristicAlgorithm;

    // Parámetros del algoritmo Tabu Search optimizados para ALMACORP
    private static final int MAX_ITERATIONS = 500;
    private static final int TABU_LIST_SIZE = 30;
    private static final int MAX_NO_IMPROVEMENT = 50;
    private static final int NEIGHBORHOOD_SIZE = 15;

    // Constantes del dominio ALMACORP
    private static final double COLLAPSE_PENALTY = -1000.0;
    private static final double DELAY_PENALTY = -100.0;
    private static final double DELIVERY_REWARD = 100.0;
    private static final double ROUTE_EFFICIENCY_FACTOR = -2.0;

    private LoggingReport loggingReport = new LoggingReport();
    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) {
        // 1) Construir la mesa de trabajo (estado global mutable)
        EstadoGlobalMutableProblemaPlanificacion mesaTrabajo = EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(parametrosAlgoritmo);
        mesaTrabajo.setLoggingReport(loggingReport);
        // 2) Generar rutas candidatas por BFS (ya implementado en mesaTrabajo)
        //    Nota: estas rutas NO tienen pedido ni cantidad aún.
        List<RutaProgramadaParaAlgoritmo> rutasCandidatas = mesaTrabajo.generarTodasRutasPosiblesATodosDestinos();

        // 3) Ordenar pedidos por deadline (si existe) para dar prioridad básica
        List<PedidoParaAlgoritmo> pedidos = new ArrayList<>(mesaTrabajo.getPedidos().values());
        pedidos.sort(Comparator.comparing(PedidoParaAlgoritmo::getInstanteMaximoParaEntregar, Comparator.nullsLast(Comparator.naturalOrder())));

        // 4) Búsqueda en amplitud inicial (greedy con BFS de rutas):
        //    Para cada pedido, seguir asignando rutas factibles hasta satisfacerlo o agotar opciones.
        for (PedidoParaAlgoritmo pedido : pedidos) {
            if (pedido == null) continue;
            if (pedido.getCantidadRestanteDeEntregaYProgram() <= 0) continue;

            // Evitar ciclos infinitos: como máximo intentamos tantas veces como rutas candidatas existen
            int intentos = 0;
            int maxIntentos = Math.max(1, rutasCandidatas.size());

            while (pedido.getCantidadRestanteDeEntregaYProgram() > 0 && intentos < maxIntentos) {
                intentos++;
                RutaProgramadaParaAlgoritmo ruta = encontrarPrimeraRutaBFSFactibleParaPedido(pedido, rutasCandidatas, mesaTrabajo);
                if (ruta == null) break; // no hay más rutas factibles para este pedido

                int cantidad = decidirCantidadAsignable(pedido, ruta, mesaTrabajo);
                if (cantidad <= 0) break; // no se puede asignar más en estado actual

                // Crear una NUEVA ruta para no mutar la ruta candidata compartida
                RutaProgramadaParaAlgoritmo rutaAsignada = new RutaProgramadaParaAlgoritmo(
                        new LinkedList<>(ruta.getIdsVuelosEnOrden()),
                        pedido.getId(),
                        cantidad
                );

                // aplicar en la mesa (actualiza capacidades de vuelos y programado del pedido)
                mesaTrabajo.anadirRutaSolucion(rutaAsignada);

                // si quedó completamente satisfecho, limpiar de la mesa y salir
                if (mesaTrabajo.eliminarPedidoYaSatisfecho(pedido.getId())) {
                    break;
                }
                // de lo contrario, continuar buscando una nueva ruta factible para el remanente
            }
        }

        // 5) Construir salida con las rutas añadidas en la mesa
        return SalidaProblemaPlanificacion.builder()
                .rutasProgramadasParaSatisfacerTodoPedido(mesaTrabajo.getRutasSolucionQueGeneraAlgoritmo())
                .huboErrorEjecucion(false)
                .colapsado(false)
                .build();
    }


    // Encuentra la primera ruta (en el orden en que fueron generadas por el BFS) que:
    // - termine en el almacén destino del pedido
    // - sea factible según el estado actual de la mesa
    private RutaProgramadaParaAlgoritmo encontrarPrimeraRutaBFSFactibleParaPedido(
            PedidoParaAlgoritmo pedido,
            List<RutaProgramadaParaAlgoritmo> rutasCandidatas,
            EstadoGlobalMutableProblemaPlanificacion mesaTrabajo
    ) {
        if (pedido == null || rutasCandidatas == null || rutasCandidatas.isEmpty()) return null;
        Long destPedido = pedido.getIdAlmacenDestino();
        for (RutaProgramadaParaAlgoritmo ruta : rutasCandidatas) {
            if (ruta == null || ruta.getIdsVuelosEnOrden() == null || ruta.getIdsVuelosEnOrden().isEmpty()) continue;
            // último vuelo de la ruta
            List<Long> ids = ruta.getIdsVuelosEnOrden();
            Long idUltimoVuelo = ids.get(ids.size() - 1);
            VueloParaAlgoritmo ultimo = mesaTrabajo.getVueloFromId(idUltimoVuelo);
            if (ultimo == null) continue;
            if (!Objects.equals(ultimo.getIdAlmacenDestino(), destPedido)) continue;

            // factibilidad conservadora en el estado actual
            if (mesaTrabajo.esFactibleLlevarPedidoEnRuta(pedido.getId(), ruta)) {
                return ruta;
            }
        }
        return null;
    }

    // Cálculo conservador de la cantidad asignable en una ruta actualmente factible.
    private int decidirCantidadAsignable(PedidoParaAlgoritmo pedido,
                                         RutaProgramadaParaAlgoritmo ruta,
                                         EstadoGlobalMutableProblemaPlanificacion mesaTrabajo) {
        if (pedido == null || ruta == null) return 0;
        int remaining = pedido.getCantidadRestanteDeEntregaYProgram();
        if (remaining <= 0) return 0;

        // capacidad mínima entre los vuelos de la ruta (considerando ocupados actuales)
        int capacidadMinRuta = mesaTrabajo.obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
        if (capacidadMinRuta <= 0) return 0;

        // stock disponible en el almacén de origen del primer vuelo
    List<Long> ids = ruta.getIdsVuelosEnOrden();
    Long idPrimerVuelo = ids.get(0);
        VueloParaAlgoritmo primer = mesaTrabajo.getVueloFromId(idPrimerVuelo);
        if (primer == null) return 0;
        long idAlmacenOrigen = primer.getIdAlmacenOrigen();
        int disponibleOrigen;
        {
            // buscar el almacén origen en la mesa
            // Nota: almacenes es un HashMap<Long, AlmacenParaAlgoritmo>
            var alm = mesaTrabajo.getAlmacenes().get(idAlmacenOrigen);
            if (alm == null) {
                disponibleOrigen = 0;
            } else if (alm.isEsInfinito()) {
                disponibleOrigen = Integer.MAX_VALUE / 4; // suficiente para no limitar
            } else {
                // usamos la capacidad ocupada como "stock" aproximado disponible de salida
                disponibleOrigen = Math.max(0, alm.getCapacidadOcupada());
            }
        }

        int asignable = Math.min(remaining, Math.min(capacidadMinRuta, disponibleOrigen));
        return Math.max(0, asignable);
    }
        
}
