package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Builder
public record PedidoCargaMasivaDTO(
        Long idCliente,
        @NotNull Long idAlmacenDestino,
        @NotNull Integer cantProductos,
        @NotNull LocalDateTime instanteRegistro
) {
    public PedidoEntidad toEntity() {
        return PedidoEntidad.builder()
                .cantidadProductosPedidos(cantProductos)
                .cantidadProductosEntregados(0) // evitar NULL
                .instanteRegistro(instanteRegistro
                        .atZone(ZoneId.systemDefault())
                        .toInstant())
                .esIntercontinental(false) // requerido en tu entidad (nullable = false)
                .build();
    }
}