package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PlanificationProblemInput;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;

import java.util.List;

@Component
@Primary // SI en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class GraspAndGeneticAlgorithmStrategy implements PlanificationStrategy {

//    @Bean
    @Override
    public PlanificacionResponseDTO planificar(PlanificationProblemInput parametrosAlgoritmo) {
        List<PedidoForAlgorithm> pedidos = parametrosAlgoritmo.pedidos();
        List<VueloForAlgorithm> vuelos = parametrosAlgoritmo.vuelos();
        List<AlmacenForAlgorithm> almacenes = parametrosAlgoritmo.almacenes();


        return null;
    }
}
