package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.time.Instant;

public record PedidoCargaMasivaDTO(
        Long idCliente,
        @NotNull Long idAlmacenDestino,
        @NotNull Integer cantProductos
) {
    public Pedido toEntity() {
        return Pedido.builder()
                .cantidadProductosPedidos(cantProductos)
                .cantidadProductosEntregados(0) // inicializar para no ser NULL
                .instanteRegistro(Instant.now())
                .build();
    }
}