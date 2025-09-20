package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

import java.util.ArrayList;

@Data
public class RealizarPlanificacionDTO {

    EstrategiaFija estrategiaFija = EstrategiaFija.RAPIDA; //opcional
    ArrayList<Object> parametros;
    // otros parámetros de la planificación...
}


