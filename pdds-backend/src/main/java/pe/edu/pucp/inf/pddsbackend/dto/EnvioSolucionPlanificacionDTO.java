package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;


public record EnvioSolucionPlanificacionDTO(
    Long id,
    Integer cantidadProductosAEnviar,
    List<VueloDTO> vuelosParaHacerPosibleEnvio,

    List<PedidoDTO> pedidosOPartesDePedidoAAtender

){}
