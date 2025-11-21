package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import java.time.Instant;

public record PedidoRevisionDto(
        Long id,
        Integer cantidadProductosTotal,
        Integer cantidadProductosEntregados,
        Instant instanteRegistro,
        AlmacenRefDto almacenDestino, // solo referencia reducida
        Number revisionNumber,
        Instant revisionInstant,
        String revisionUsername,
        String revisionType) {

    public record AlmacenRefDto(Long id, String nombre) {
    }

}
