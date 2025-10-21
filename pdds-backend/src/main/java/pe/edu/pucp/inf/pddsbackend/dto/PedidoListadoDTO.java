package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

@Builder // why not
public record PedidoListadoDTO (
        Long id,
        String nombreCliente,
        String nombreAlmacenDestino,
        Integer cantProductosTotales
        //String estado

){
    public static PedidoListadoDTO fromEntity(Pedido pedido) {
        String nombreAlmacen = pedido.getAlmacenDestino() != null
                ? pedido.getAlmacenDestino().getCodigoCiudadEn4Letras()
                : null;

        String nombreCliente = pedido.getCliente() != null
                ? pedido.getCliente().getNombre()
                : null;

        return new PedidoListadoDTO(
                pedido.getId(),
                nombreCliente,
                nombreAlmacen,
                pedido.getCantidadProductosPedidos()
                //pedido.getEstado() != null ? pedido.getEstado().name() : "SIN_ESTADO"
        );
    }

}
