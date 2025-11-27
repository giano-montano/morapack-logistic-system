package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import java.time.Instant;

public record CancelarVueloDTO(
        Instant fechaHoraCancelacionUTC

) {

}
