package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;

import java.time.Instant;

@Data
@AllArgsConstructor
@Builder
public class VueloForAlgorithm {
    Long id;

    Instant inicio;
    Instant fin;

    /*AlmacenForAlgorithm*/ Long idAlmacenOrigen;
    /*AlmacenForAlgorithm */ Long idAlmacenDestino;

    Integer capacidadMaximaProductos;
    Integer capacidadOcupadaProductos;
    Integer capacidadReservadaProductos;

    EstadoVuelo estado; // en curso, cancelado, en espera, finalizado

    public static VueloForAlgorithm createFromEntity(Vuelo vuelo){
        return VueloForAlgorithm.builder()
                .id(vuelo.getId())
                .capacidadReservadaProductos(vuelo.getCapacidadReservadaProductos())
                .capacidadOcupadaProductos(vuelo.getCapacidadOcupadaProductos())
                .capacidadMaximaProductos(vuelo.getCapacidadMaximaProductos())
                .estado(vuelo.getEstado())
                .idAlmacenDestino(vuelo.getAlmacenDestino().getId())
                .idAlmacenOrigen(vuelo.getAlmacenOrigen().getId())
                .inicio(vuelo.getFechaHoraInicio())
                .fin(vuelo.getFechaHoraFin())
                .build();
    }
}
