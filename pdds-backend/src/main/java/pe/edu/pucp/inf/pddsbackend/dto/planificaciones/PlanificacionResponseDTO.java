package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import java.time.Instant;
import java.util.List;

public record PlanificacionResponseDTO (
    Long idPlanificacion,
    Instant fechaHoraFinPlanif,
    Boolean colapsado,
    Double fitnessConseguido, // NO PONER TODAVÍA
    Long duracionEjecucionMiliSegundos,
    List<ProgramacionSolucionDTO> programaciones,
    Boolean conError,
    String error
){}

