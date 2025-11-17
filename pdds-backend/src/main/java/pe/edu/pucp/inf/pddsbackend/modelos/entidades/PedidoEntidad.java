package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

//@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "almacenDestino")
// Crea tablas de auditoría con cada registro histórico, esto con el BaseAuditable del AuditorAware nos dirá
//el histórico de qué cambio, cuándo, y quién sobre todo lo hizo.
//@Audited(targetAuditMode = NOT_AUDITED) // NOT_AUDITED PARA QUE NO SE LOQUEE CON EL ALMACÉN NO AUDITADO CON AUDITED (independiente del auditableBase creo)
@Table(name = "pedido",indexes = {
        @Index(name = "indice_por_fecha_hora_registro_para_extraccion",columnList = "instante_registro")
})
public class PedidoEntidad extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // <- safa si el profe quiere proveer el id manual y directamente
    private Long id; // Puede que incluso la carga se haga más eficiente con el id provisto por el usuario /app y no por la BD / proveedor hibernate

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private AlmacenEntidad almacenDestino;

    @Column(nullable = false)
    private Integer cantidadProductosPedidos;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer cantidadProductosEntregados=0;

    @Column(nullable = false)
    private Instant instanteRegistro;

    @Column(nullable = true)
    private Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar, nullable.

    @Column(nullable = false)
    private Boolean esIntercontinental;

    @ManyToOne(fetch = FetchType.LAZY, optional = true, cascade = CascadeType.PERSIST) // es nullable;
//    @JoinColumn(
//
//    )
    private Cliente cliente;



}
