package pe.edu.pucp.inf.pddsbackend.dto.almacenes;

import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoResumenDTO;

import java.util.List;

public record AlmacenCardDTO(
        Long id,
        String nombreCiudad,
        Integer capacidadOcupada,
        Integer capacidadTotal,
        List<PedidoResumenDTO> pedidos
) {
}
