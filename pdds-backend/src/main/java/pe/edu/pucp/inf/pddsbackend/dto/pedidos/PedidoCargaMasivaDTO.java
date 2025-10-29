package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;

public record PedidoCargaMasivaDTO(
        Long idCliente,
        @NotNull Long idAlmacenDestino,
        @NotNull Integer cantProductos
) {
    public PedidoEntidad toEntity() {
        return PedidoEntidad.builder()
                .cantidadProductosPedidos(cantProductos)
                .cantidadProductosEntregados(0) // inicializar para no ser NULL
                .instanteRegistro(Instant.now())
                .build();
    }
}