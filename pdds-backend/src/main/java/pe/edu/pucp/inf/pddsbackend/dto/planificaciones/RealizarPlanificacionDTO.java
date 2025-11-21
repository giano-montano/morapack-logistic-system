package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;

@Builder
@Data
public class RealizarPlanificacionDTO
{

    @Builder.Default
    EstrategiaFija estrategiaFija = EstrategiaFija.RAPIDA; // opcional
    ArrayList<Object> parametros;
    Instant instanteActual;
    Instant instanteDesdeTomarPedidos;
    Long idSimulacion;
    Long seed;
    String subCarpetaReportes;
    Boolean loggear;
    Boolean usarModoMock; // true = usa planificación mock, false/null = usa GRASP real
    // otros parámetros de la planificación...
}
