package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.algorithms.GraspAndGeneticAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.PlanificationStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PlanificationProblemInput;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.EnvioProgramadoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;

import java.util.List;


@Service
@RequiredArgsConstructor
public class PlanificacionServiceImpl implements PlanificacionService {

    private PlanificationStrategy planificationStrategy; // podría variar la estrategia con el tiempo?
    private final AlmacenRepository almacenRepository;
    private final PedidoRepository pedidoRepository;
    private final VueloRepository vueloRepository;
    private final EnvioProgramadoRepository envioProgramadoRepositoryRepository;
    // ver planificaciones pasadas sería relevante para el algo.

    private void escogerEstrategiaInicial(EstrategiaFija estrategiaFija){
        switch(estrategiaFija){
            case AUTO -> planificationStrategy = new GraspAndGeneticAlgorithmStrategy();
            case PROFUNDA ->  planificationStrategy = new GraspAndGeneticAlgorithmStrategy();
            case RAPIDA ->   planificationStrategy = new GraspAndGeneticAlgorithmStrategy();
        }
    }

    private PlanificationProblemInput obtenerDatosParaAlgoritmo(){
        //ineficiente pero probemos
        //Podemos restringir de por sí la data para el algoritmo,
        //de manera que le ahorramos ver opciones inválidas, ejm:
        //almacenes llenos, vuelos terminados o ya cancelados, pedidos ya enviados
        List<AlmacenForAlgorithm> almacenesParaAlgoritmo =
                almacenRepository.findAlmacenesNoLlenos().stream().map(
                        AlmacenForAlgorithm::createFromEntity
                ).toList();
        List<VueloForAlgorithm> vuelosParaAlgoritmo =
                vueloRepository.findVuelosPorDespegarOEnCurso().stream().map(
                        VueloForAlgorithm::createFromEntity
                ).toList();
        List<PedidoForAlgorithm> pedidosParaAlgoritmo =
                pedidoRepository.findPedidosAunNoProgramados().stream().map(
                        PedidoForAlgorithm::createFromEntity
                ).toList();
        // Como estamos agarrando pedidos por programar, creo que no es necesario obtener los envíos
        // de antes, ya que esos ya habrían hecho que los pedidos figuren con estado "PROGRAMADO"
        return PlanificationProblemInput.builder()
                .almacenes(almacenesParaAlgoritmo)
                .pedidos(pedidosParaAlgoritmo)
                .vuelos(vuelosParaAlgoritmo)
                .build();
    }

    @Override
    public PlanificacionResponseDTO realizarPlanificacionDePedidosActuales(RealizarPlanificacionDTO params) {

        escogerEstrategiaInicial(params.estrategiaFija()); // la elección de estrategia puede ser derivada
        // a una clase o método aun mpas especializado que use por ejemplo, el PlanificationProblemInput para
        // determinar mejor la estrategia si es que el usuario puso AUTO
        PlanificationProblemInput dataEntradaAlgoritmo =  obtenerDatosParaAlgoritmo();
        planificationStrategy.planificar(dataEntradaAlgoritmo);

        return null;
    }
}
