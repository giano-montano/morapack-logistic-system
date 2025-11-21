package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VueloCargaMasivaConcretosDTO(
        @NotNull Integer numDiasCargar,
        LocalDate fechaInicioLocal) {
}
