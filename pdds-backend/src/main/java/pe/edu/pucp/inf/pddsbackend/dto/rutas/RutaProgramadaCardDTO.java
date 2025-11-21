package pe.edu.pucp.inf.pddsbackend.dto.rutas;

import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoMiniResumenDTO;

import java.time.Instant;
import java.util.List;

public record RutaProgramadaCardDTO(
        int cantTotalAEntregar,
        String almacenDestinoFinal,
        Instant fechaHoraPlanificadaLlegadaMax,
        int numPedidosAtender,
        int numVuelosATomar,
        // boolean reprogramado, <- sin sentido
        boolean cumplido,
        Instant fechaHoraPlanificacion,

        List<String> nombresCiudades,
        List<String> codigosVuelosProgramados,
        List<PedidoMiniResumenDTO> pedidosQueAtendera

) {
}
