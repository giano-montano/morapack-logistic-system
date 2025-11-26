package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Builder
public record PedidoCargaMasivaDTO(
        // Long idPedido, // nuevo, si el profe pide que se respete el ID que él
        // designó, sería necesario. Y la BD ya no lo maneja secuencialmente
        Long idCliente,
        @NotNull Long idAlmacenDestino,
        @NotNull Integer cantProductos,
        @NotNull Instant instanteRegistro) {
    public PedidoEntidad toEntity(boolean paraMemoria){
        return PedidoEntidad.builder()
                // .id(idPedido) // nuevo, si el profe pide que se respete el ID que él designó,
                // sería necesario. Y la BD ya no lo maneja secuencialmente
                .cantidadProductosPedidos(cantProductos)
                .cantidadProductosEntregados(0) // evitar NULL
                .instanteRegistro(instanteRegistro
                        .atZone(ZoneId.systemDefault())
                        .toInstant())
                .instanteMaximoParaEntregar(
                        instanteRegistro.plus(Hiperparametros.DIAS_CONTINENTAL, ChronoUnit.DAYS))
                .esIntercontinental(false) // requerido en tu entidad (nullable = false)
                .esParaOperacionesDiaADia(paraMemoria) // <- NUEVO: asumimos que si es para memoria se qudará en día a día
                .build();
    }
}
