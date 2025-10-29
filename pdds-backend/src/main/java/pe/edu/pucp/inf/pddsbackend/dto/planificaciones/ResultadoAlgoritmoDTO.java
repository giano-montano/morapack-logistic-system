package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;

public record ResultadoAlgoritmoDTO(
    SalidaProblemaPlanificacion salida,
    double fitness,
    long tiempoEjecucionMs
){}
