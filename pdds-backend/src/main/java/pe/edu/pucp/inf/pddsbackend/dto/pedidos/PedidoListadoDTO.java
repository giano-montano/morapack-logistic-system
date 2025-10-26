package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import lombok.Builder;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

@Builder // why not
public record PedidoListadoDTO (
        Long id,
        String nombreCliente,
        String nombreAlmacenDestino,
        Integer cantProductosTotales
        //String estado

){
    public static PedidoListadoDTO fromEntity(PedidoEntidad pedido) {
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
