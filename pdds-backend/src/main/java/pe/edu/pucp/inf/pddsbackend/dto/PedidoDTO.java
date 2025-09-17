package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;

public record PedidoDTO(
        Long idPedido,
        Integer cantidadTotal,
        Integer cantidadAtendiendose,

        AlmacenDTO destino


)
{
}
