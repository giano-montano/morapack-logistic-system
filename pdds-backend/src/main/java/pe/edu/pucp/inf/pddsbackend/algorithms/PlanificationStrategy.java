package pe.edu.pucp.inf.pddsbackend.algorithms;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.PlanificationProblemInput;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PlanificationSolutionOutput;


public interface PlanificationStrategy {

    public PlanificationSolutionOutput planificar(PlanificationProblemInput parametrosAlgoritmo) throws Exception;

    // otros métodos que podrían variar por cada estrategia y que puedan ser utilizados en medio de algo
}
