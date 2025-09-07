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
    Integer cantidadProductosProgramados=0;

    Instant instanteRegistro;
    Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar.

    //el estado depende mucho de los envíos (transitivo), mejor consultar los envíos por sobre este estado en sí, luego vemos qué hacemos,
    //por ahora, lo dejo sin atributo estado aquí

    Boolean atendidoCompletamente; // cuando cantEntregados >= cantPedidos
    Boolean colapsado=false; // si es que pasa el instante máximo para entregar y cantEntregados < cantPedidos
}
