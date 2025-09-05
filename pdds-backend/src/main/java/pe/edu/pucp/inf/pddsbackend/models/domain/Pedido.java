package pe.edu.pucp.inf.pddsbackend.models.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class Pedido {
    Long id;
    //cliente
    Almacen destino;
    Integer cantidadProductosPedidos;
    Integer cantidadProductosEntregados=0;

    Instant instanteRegistro;
    Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar.


    Boolean atendidoCompletamente; // cuando cantEntregados >= cantPedidos
    Boolean colapsado=false; // si es que pasa el instante máximo para entregar y cantEntregados < cantPedidos
}
