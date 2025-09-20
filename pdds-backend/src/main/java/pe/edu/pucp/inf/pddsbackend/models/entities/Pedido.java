package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import org.hibernate.envers.Audited;

import java.time.Instant;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "almacenDestino")
// Crea tablas de auditoría con cada registro histórico, esto con el BaseAuditable del AuditorAware nos dirá
//el histórico de qué cambio, cuándo, y quién sobre todo lo hizo.
@Audited(targetAuditMode = NOT_AUDITED) // NOT_AUDITED PARA QUE NO SE LOQUEE CON EL ALMACÉN NO AUDITADO CON AUDITED (independiente del auditableBase creo)
public class Pedido  extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private Almacen almacenDestino;

    @Column(nullable = false)
    private Integer cantidadProductosPedidos;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer cantidadProductosEntregados=0;

    @Column(nullable = false)
    private Instant instanteRegistro;

    @Column(nullable = false)
    private Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar, nullable.
//^^ si esto obtiene valor, sabremos que ya ha sido programado.
}
