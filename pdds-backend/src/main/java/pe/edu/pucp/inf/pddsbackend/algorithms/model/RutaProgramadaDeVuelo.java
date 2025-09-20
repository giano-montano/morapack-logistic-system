package pe.edu.pucp.inf.pddsbackend.algorithms.model;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RutaProgramadaDeVuelo {
    Long id;
    Long idPedidoQueAtiende;

}
