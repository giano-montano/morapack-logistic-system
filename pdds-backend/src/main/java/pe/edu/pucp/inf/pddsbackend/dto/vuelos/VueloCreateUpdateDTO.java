package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record VueloCreateUpdateDTO(
        @NotBlank String codigo4Letras,
        @NotNull Long idAlmacenOrigen,
        @NotNull Long idAlmacenDestino,
        @NotNull Instant fechaHoraInicioUtc,
        @NotNull Instant fechaHoraFinUtc,
        @NotNull @Positive Integer capacidadMaxima,
        @NotNull @Min(0) Integer capacidadOcupada,
        @NotNull Boolean cancelado,
        @NotNull Boolean esIntercontinental,
        @NotNull Boolean activo
) {}
