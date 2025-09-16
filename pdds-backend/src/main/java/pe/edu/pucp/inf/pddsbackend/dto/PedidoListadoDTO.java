package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

@Builder // why not
public record PedidoListadoDTO (

        Long id,

        Long idCliente,
        String nombreCliente,

        Long idAlmacenDestino,
        String nombreAlmacenDestino,

        Integer cantProductosTotales,
        Integer cantProductosAtendidos,
        Integer cantProductosProgramados

){
    public static PedidoListadoDTO fromEntity(Pedido pedidoBD) {
        Long idAlmacen = null;
        String nombreAlmacen = null;
        if (pedidoBD.getAlmacenDestino() != null) {
            idAlmacen = pedidoBD.getAlmacenDestino().getId();
            nombreAlmacen = pedidoBD.getAlmacenDestino().getCodigoCiudadEn4Letras();
        }

        return new PedidoListadoDTO(
                pedidoBD.getId(),
                0L, // idCliente (rellena si lo tienes)
                "",
                idAlmacen,
                nombreAlmacen,
                pedidoBD.getCantidadProductosTotal(),
                pedidoBD.getCantidadProductosEntregados(),
                pedidoBD.getCantidadProductosProgramados()
        );
    }
}
