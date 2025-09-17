package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;

public record PlanificacionResponseDTO (
        // esta wa como tal no tiene un ID, salvo lo definamos en la BD para mantener persistencia
        // de cada planificacion hecha.
    List<RutaProgramadaDTO> rutasProgramadas
){}

