package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;

import java.time.LocalTime;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private LocalTime  horaInicioUtc;

    @Column(nullable = false)
    private LocalTime horaFinUtc;

    @Column(nullable = false)
    Integer capacidadMaxima;

    @ColumnDefault("true")
    @Column(nullable = false)
    Boolean activo; // PORSIA
}
