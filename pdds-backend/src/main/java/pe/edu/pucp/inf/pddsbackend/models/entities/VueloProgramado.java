package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;

import java.time.LocalTime;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Audited(targetAuditMode = NOT_AUDITED)
public class VueloProgramado extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_origen_id") // ¿deberían llamarse a FKs en esta "plantilla" abstracta?
    private Almacen almacenOrigen;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id") // podría bastar con el id numérico sin relación fk como tal.
    private Almacen almacenDestino;

    @Column(nullable = false)
    Boolean esIntercontinental;

    @Column(nullable = false)
    private LocalTime horaInicioEnPropioHuso;

    @Column(nullable = false)
    private LocalTime horaFinEnPropioHuso;

    @Column(nullable = false)
    Integer capacidadMaxima;

    @ColumnDefault("true")
    @Column(nullable = false)
    Boolean activo; // PORSIA
}
