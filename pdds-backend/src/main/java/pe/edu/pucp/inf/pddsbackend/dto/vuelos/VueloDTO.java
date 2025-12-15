package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

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
        Boolean activo) {

    public static VueloDTO desdeDominio(Vuelo vuelo) {
        return new VueloDTO(
                vuelo.getId(),
                vuelo.getCodigo(),
                vuelo.getIdAlmacenOrigen(),
                vuelo.getAlmacenDestino(),
                vuelo.getInicio(),
                vuelo.getFin(),
                vuelo.getCapacidad(),
                vuelo.getCapacidadOcupada(),
                vuelo.isCancelado(),
                vuelo.isIntercontinental(),
                vuelo.isCancelado()
        );
    }
}
