package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;

public record PlanificacionResponseDTO (
    List<EnvioSolucionPlanificacionDTO> envios
){}

