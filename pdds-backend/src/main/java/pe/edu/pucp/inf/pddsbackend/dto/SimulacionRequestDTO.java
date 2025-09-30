package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;

import java.util.ArrayList;


public record SimulacionRequestDTO (
        @NotNull
        TipoSimulacion tipoSimulacion,
        ArrayList<Object> parametros,
        Long maximoTimeOutSegundosPorPlanif,
        Double factorDeVelocidad, // todavía no hago que sea dinámico
        Long minutosRealesEntrePlanificaciones,
        Long seed
){
}
