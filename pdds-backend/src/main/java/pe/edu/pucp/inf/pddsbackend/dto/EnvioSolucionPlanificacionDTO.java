package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;


public record EnvioSolucionPlanificacionDTO(
    Long id,
    List<VueloDTO> vuelosParaHacerPosibleEnvio,
    Integer cantidadProductosAEnviar,
    List<PedidoDTO> pedidosOPartesDePedidoAAtender

){}
