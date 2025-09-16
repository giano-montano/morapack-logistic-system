package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;

import java.util.ArrayList;
import java.util.List;

@Component
public class TabuSearchAlgorithmStrategy implements PlanificationStrategy {
    // pa logs
    private LoggingReport loggingReport = new LoggingReport();
    @Override
    public PlanificationSolutionOutput planificar(PlanificationProblemInput parametrosAlgoritmo) throws Exception {

        // Leer input (defensivo: crear copias mutables)
        List<PedidoForAlgorithm> pedidos = parametrosAlgoritmo.pedidos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.pedidos());
        List<VueloForAlgorithm> vuelos = parametrosAlgoritmo.vuelos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.vuelos());
        List<AlmacenForAlgorithm> almacenes = parametrosAlgoritmo.almacenes() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.almacenes());


        /*
        * Aquí comiencen a codear el algoritmo
        * */

        // Ejemplo de hacer log en reporte externo [IMPORTANTE PARA QUE LE HAGAS SEGUIMIENTO A TU ALGORITMO]
        loggingReport.appendReport("Inicio de planificacion con algoritmo tabú. Pedidos: " +
                pedidos.size() + ", vuelos=" + vuelos.size() + ", almacenes=" + almacenes.size());

        // Ejemplo de guardar el reporte en /reports
        loggingReport.writeReportFile("tabu-report-final");

        return null;
    }
}
