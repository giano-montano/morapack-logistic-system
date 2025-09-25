package pe.edu.pucp.inf.pddsbackend.dto;

import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;

import java.util.ArrayList;

public record SimulacionRequestDTO (
        TipoSimulacion tipoSimulacion,
        ArrayList<Object> parametros,
        Long maximoTimeOutSegundos
){
}
