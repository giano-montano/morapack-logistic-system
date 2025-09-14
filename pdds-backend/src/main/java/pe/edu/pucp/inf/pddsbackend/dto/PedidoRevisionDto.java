package pe.edu.pucp.inf.pddsbackend.dto;


import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoPedido;
import java.time.Instant;



public record PedidoRevisionDto(
        Long id,
        Integer cantidadProductosTotal,
        Integer cantidadProductosEntregados,
        Integer cantidadProductosProgramados,
        EstadoPedido estado,
        Instant instanteRegistro,
        AlmacenRefDto almacenDestino, // solo referencia reducida
        Number revisionNumber,
        Instant revisionInstant,
        String revisionUsername,
        String revisionType
) {

    public record AlmacenRefDto(Long id, String nombre) {}

}

