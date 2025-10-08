package pe.edu.pucp.inf.pddsbackend.dto;

import java.time.Instant;

public record VueloDTO(
        Long id,
        String codigo4Letras,
        Long idAlmacenOrigen,
        Long idAlmacenDestino,
        Instant fechaHoraInicioUtc,
        Instant fechaHoraFinUtc,
        Integer capacidadMaxima,
        Integer capacidadOcupada,
        Boolean cancelado,
        Boolean esIntercontinental,
        Boolean activo
) {}
