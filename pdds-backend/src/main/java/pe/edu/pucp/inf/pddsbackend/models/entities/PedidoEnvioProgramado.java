package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoEnvioProgramado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    private EnvioProgramado envioQueSatisfaceParteOTodoPedido;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = true // TAL VEZ Y SOLO TAL VEZ, EL ALGORITMO PROGRAME ENVÍOS SOLO PARA LIBERAR ALMACENES
    )
    private Pedido pedidoQueElEnvioEstaAtendiendo;

//    private Integer ordenDelVueloEnEnvioParaAtenderPedido;

    private Integer cantidadDeProductosDelPedidoAtendiendose; // Pueden ser todos o una parte del pedido
}
