package pe.edu.pucp.inf.pddsbackend.dto;

import java.time.Instant;
import java.util.List;

public record PlanificacionResponseDTO (
    Long idPlanificacion,
    Instant fechaHoraFinPlanif,
    Boolean colapsado,
    Double fitnessConseguido, // NO PONER TODAVÍA
    List<RutaProgramadaSolucionDTO> rutasProgramadas,
    Boolean conError,
    String error,
    Long duracionEjecucionMiliSegundos
){}

