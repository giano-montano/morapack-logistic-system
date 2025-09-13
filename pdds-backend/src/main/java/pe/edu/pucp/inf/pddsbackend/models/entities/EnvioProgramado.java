package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@EqualsAndHashCode(callSuper = true) // esto qué?xd
@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Audited // Crea tablas de auditoría con cada registro histórico, esto con el AuditableBase del AuditorAware nos dirá
//el histórico de qué cambio, cuándo, y quién sobre todo lo hizo.
public class EnvioProgramado extends AuditableBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

//    @ManyToOne(
//            fetch = FetchType.LAZY,
//            optional = false
//    )
//    @JoinColumn(name = "almacen_destino_id")
//    private Almacen almacenDestino; esto se infiere desde el último vuelo del envío
    private Integer cantProductosAEnviar; //puede atender varios o partes de pedidos a la vez.

//    private Boolean fechaHoraLlegada; es transitivo
    private Boolean cumplido; // si ya se cumplió o no

    private Boolean reprogramado; //true: se reprogramó, este ya no tiene validez; false: no se reprogramó

}
