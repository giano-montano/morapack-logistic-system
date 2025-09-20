package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;

@Builder
@Data
public class RealizarPlanificacionDTO {

    EstrategiaFija estrategiaFija = EstrategiaFija.RAPIDA; //opcional
    ArrayList<Object> parametros;
    Long idSimulacion;
    // otros parámetros de la planificación...
}


