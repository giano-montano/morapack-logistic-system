package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;

@Builder
@Data
public class RealizarPlanificacionDTO {

    EstrategiaFija estrategiaFija = EstrategiaFija.RAPIDA; //opcional
    ArrayList<Object> parametros;
    Instant instanteActual;
    Long idSimulacion;
    Long seed;
    String subCarpetaReportes;
    Boolean loggear;
    // otros parámetros de la planificación...
}


