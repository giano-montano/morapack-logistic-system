package pe.edu.pucp.inf.pddsbackend.algorithms;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.PlanificationProblemInput;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;


public interface PlanificationStrategy {

    public PlanificacionResponseDTO planificar(PlanificationProblemInput parametrosAlgoritmo);

    // otros métodos que podrían variar por cada estrategia y que puedan ser utilizados en medio de algo
}
