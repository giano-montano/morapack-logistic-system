package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloSolucionDTO;

import java.util.List;

public record EnvioSolucionPlanificacionDTO(
        Long id,
        Integer cantidadProductosAEnviar,
        List<VueloSolucionDTO> vuelosParaHacerPosibleEnvio,

        List<PedidoSolucionDTO> pedidosOPartesDePedidoAAtender

) {
}
