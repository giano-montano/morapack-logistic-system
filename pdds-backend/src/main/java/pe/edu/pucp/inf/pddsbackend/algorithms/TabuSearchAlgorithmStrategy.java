package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class TabuSearchAlgorithmStrategy implements PlanificationStrategy {

    @Override
    public PlanificationSolutionOutput planificar(PlanificationProblemInput parametrosAlgoritmo) {

        // Leer input (defensivo: crear copias mutables)
        List<PedidoForAlgorithm> pedidos = parametrosAlgoritmo.pedidos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.pedidos());
        List<VueloForAlgorithm> vuelos = parametrosAlgoritmo.vuelos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.vuelos());
        List<AlmacenForAlgorithm> almacenes = parametrosAlgoritmo.almacenes() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.almacenes());


        /*
        * Aquí comiencen a codear el algoritmo
        * */

        return null;
    }
}
