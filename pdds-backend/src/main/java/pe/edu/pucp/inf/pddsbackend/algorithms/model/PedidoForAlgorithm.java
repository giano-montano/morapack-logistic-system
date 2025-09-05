package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Builder;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.time.Instant;

@Data
@Builder
public class PedidoForAlgorithm{
    Long id;
    //cliente
    Long idAlmacenDestino; // No clase Almacen, no necesario
    Integer cantidadProductosPedidos;
    Integer cantidadProductosEntregados=0;

    Instant instanteRegistro;
    Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar.

    // No muy interesantes para el algoritmo
//    Boolean atendidoCompletamente; // cuando cantEntregados >= cantPedidos
//    Boolean colapsado=false; // si es que pasa el instante máximo para entregar y cantEntregados < cantPedidos
    public static PedidoForAlgorithm createFromEntity(Pedido p){
        return PedidoForAlgorithm.builder()
                .cantidadProductosEntregados(p.getCantidadProductosEntregados())
                .cantidadProductosPedidos(p.getCantidadProductosTotal())
                .id(p.getId())
                .instanteMaximoParaEntregar(p.getInstanteMaximoParaEntregar())
                .instanteRegistro(p.getInstanteRegistro())
                .idAlmacenDestino(p.getAlmacenDestino().getId())
                .build();
    }

}
