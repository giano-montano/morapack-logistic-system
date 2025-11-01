package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoResumenDTO;

import java.time.Instant;
import java.util.List;

public record VueloCardDTO (
        Long id,
        String codigo,
        Integer capacidadOcupada,
        Integer capacidadMaxima,
        String ciudadSalida,
        String ciudadLlegada,
        Instant inicio,
        Instant fin,
        String estado,
        List<PedidoResumenDTO> pedidos
)
{
}
