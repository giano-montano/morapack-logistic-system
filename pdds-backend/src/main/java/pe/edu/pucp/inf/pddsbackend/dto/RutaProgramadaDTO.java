package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class RutaProgramadaDTO {
    Long idRuta;
    PedidoDTO pedido;

    List<VueloDTO> vuelosDeRutaParaAtenderPedido;

}
