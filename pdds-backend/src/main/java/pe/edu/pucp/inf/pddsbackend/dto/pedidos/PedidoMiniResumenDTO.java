package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

public record PedidoMiniResumenDTO(
        long id,
        int cantPedida,
        int cantEntregada,
        long cantQueSeAtendera

) {
}
