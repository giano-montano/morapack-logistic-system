package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class RutaProgramadaSolucionDTO {
    Long idRuta;
    PedidoSolucionDTO pedido;
    List<VueloSolucionDTO> vuelosDeRutaParaAtenderPedido;

}
