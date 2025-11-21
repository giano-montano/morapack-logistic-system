package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloSolucionDTO;

import java.util.List;

@Data
public class ProgramacionSolucionDTO
{
    // Long idRuta;
    PedidoSolucionDTO pedido;
    List<VueloSolucionDTO> vuelosDeRutaParaAtenderPedido;

}
