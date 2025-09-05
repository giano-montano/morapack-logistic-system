package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary // SI en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class GraspAndGeneticAlgorithmStrategy implements PlanificationStrategy {

//    @Bean
    @Override
    public PlanificationSolutionOutput planificar(PlanificationProblemInput parametrosAlgoritmo) {

        List<PedidoForAlgorithm> pedidos = parametrosAlgoritmo.pedidos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.pedidos());
        List<VueloForAlgorithm> vuelos = parametrosAlgoritmo.vuelos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.vuelos());
        List<AlmacenForAlgorithm> almacenes = parametrosAlgoritmo.almacenes() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.almacenes());



        return null;
    }
}
