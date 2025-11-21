package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.productos.ProductoSolucionDTO;

public record PedidoSolucionDTO(
        Long idPedido,
        Integer cantidadTotal,
        ProductoSolucionDTO productoEscogido,
        AlmacenSolucionDTO destino) {
}
