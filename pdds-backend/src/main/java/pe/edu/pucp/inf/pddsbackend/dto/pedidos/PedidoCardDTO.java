package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RutaProgramadaResumenDTO;

import java.time.Instant;
import java.util.List;

public record PedidoCardDTO(
        Long id,
//        String almacenOrigen,
        String almacenDestino,
        Integer cantidadProductosEntregados,
        Integer cantidadProductosSinEntregar,
        String cliente,
//        Boolean reprogramado, <- esto es irrelevante, siempre pero siempre se replanifica sobre lo que antes era válido
        Instant fechaRegistro,
        String estado,
        String politicaAplicada,

        List<RutaProgramadaResumenDTO> rutas
) {
}
