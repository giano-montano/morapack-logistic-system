package pe.edu.pucp.inf.pddsbackend.algorithms.model;


import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record PlanificationProblemInput (

    @NotNull
    List<VueloForAlgorithm> vuelos,
    @NotNull
    List<AlmacenForAlgorithm> almacenes,
    @NotNull
    List<PedidoForAlgorithm> pedidos

//    List<EnvioForAlgorithm> envios,
    // otros parámetros relevantes del problema (maxTime, sla, restricciones)
//    int maxDays,
//    boolean allowBacktracking
){}
