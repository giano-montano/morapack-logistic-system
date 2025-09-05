package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class  PlanificationSolutionOutput{
        List<EnvioSolution> envios;
        //otros metadatos de la ejecución del algoritmo:
//        double objectiveValue,
//        String strategyName,
//        long timeMillis
}

