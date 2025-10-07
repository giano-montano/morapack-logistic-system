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
    public static PedidoListadoDTO fromEntity(Pedido pedido) {
        Long idAlmacen = pedido.getAlmacenDestino() != null ? pedido.getAlmacenDestino().getId() : null;
        String nombreAlmacen = pedido.getAlmacenDestino() != null
                ? pedido.getAlmacenDestino().getCodigoCiudadEn4Letras()
                : null;

        Long idCliente = pedido.getCliente() != null ? pedido.getCliente().getId() : null;
        String nombreCliente = pedido.getCliente() != null
                ? pedido.getCliente().getNombre()   //  aquí tomamos el nombre del cliente
                : null;

        return new PedidoListadoDTO(
                pedido.getId(),                        // id
                idCliente,                             // idCliente
                nombreCliente,                         // nombreCliente
                idAlmacen,                             // idAlmacenDestino
                nombreAlmacen,                         // nombreAlmacenDestino
                pedido.getCantidadProductosPedidos(),  // cantProductosTotales
                pedido.getCantidadProductosEntregados(), // cantProductosAtendidos
                0                                      // cantProductosProgramados
        );
    }

}
