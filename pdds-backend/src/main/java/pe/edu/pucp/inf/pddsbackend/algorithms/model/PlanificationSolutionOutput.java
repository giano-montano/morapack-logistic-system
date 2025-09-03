package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import java.util.List;

public record PlanificationSolutionOutput(
        List<EnvioSolution> envios,
        double objectiveValue,
        String strategyName,
        long timeMillis
) {}

