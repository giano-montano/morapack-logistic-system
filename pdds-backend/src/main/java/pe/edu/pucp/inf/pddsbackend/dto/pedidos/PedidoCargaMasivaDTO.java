package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Constantes;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

@Builder
public record PedidoCargaMasivaDTO(
//        Long idPedido, // nuevo, si el profe pide que se respete el ID que él designó, sería necesario. Y la BD ya no lo maneja secuencialmente
        Long idCliente,
        @NotNull Long idAlmacenDestino,
        @NotNull Integer cantProductos,
        @NotNull Instant instanteRegistro
) {
    public PedidoEntidad toEntity() {
        return PedidoEntidad.builder()
//                .id(idPedido) // nuevo,  si el profe pide que se respete el ID que él designó, sería necesario. Y la BD ya no lo maneja secuencialmente
                .cantidadProductosPedidos(cantProductos)
                .cantidadProductosEntregados(0) // evitar NULL
                .instanteRegistro(instanteRegistro
                        .atZone(ZoneId.systemDefault())
                        .toInstant())
                .instanteMaximoParaEntregar(instanteRegistro.plus(Constantes.DIAS_CONTINENTAL, ChronoUnit.DAYS ))
                .esIntercontinental(false) // requerido en tu entidad (nullable = false)
                .build();
    }
}