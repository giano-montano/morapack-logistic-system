package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Audited // Crea tablas de auditoría con cada registro histórico, esto con el AuditableBase del AuditorAware nos dirá
//el histórico de qué cambio, cuándo, y quién sobre todo lo hizo.
public class PedidoEnvioProgramado extends AuditableBase  {
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
