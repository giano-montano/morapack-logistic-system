package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;

public record GuardarPedidoDTO(
        Long idCliente,

        @NotNull Long idAlmacenDestino,

        @NotNull Integer cantProductos,

        Instant instanteRegistro // no necesariamente es cuando se registra en el sistema

) {
    // podría ir en un mapper separado tal vez
    public PedidoEntidad toEntity()
    {
        // veamos almacén de destino... mejor en el service

        PedidoEntidad pedido = PedidoEntidad.builder()
                // NOTA: no seteamos almacenDestino aquí
                .cantidadProductosPedidos(cantProductos)
                .instanteRegistro(instanteRegistro != null ? instanteRegistro : Instant.now())
                .build();
        return pedido;
    }
}
