package pe.edu.pucp.inf.pddsbackend.dto;

public record PedidoSolucionDTO(
        Long idPedido,
        Integer cantidadTotal,
        Integer cantidadAtendiendose,
        AlmacenSolucionDTO destino
)
{
}
