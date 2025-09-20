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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Audited(targetAuditMode = NOT_AUDITED) // Crea tablas de auditoría con cada registro histórico, esto con el BaseAuditable del AuditorAware nos dirá
//el histórico de qué cambio, cuándo, y quién sobre todo lo hizo.
// PONEMOS NOT_AUDITED PARA QUE NO SE WEBEE CON LAS ENTIDADES RELACIONADAS, SI NO, DA ERROR
public class Vuelo extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_origen_id")
    private Almacen almacenOrigen;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private Almacen almacenDestino;

    @Column(nullable = false)
    private Instant fechaHoraInicioUtc; // Ya en UTC

    @Column(nullable = false)
    private Instant fechaHoraFinUtc;

    @Column(nullable = false)
    Integer capacidadMaxima;

    @Column(nullable = false)
    @ColumnDefault("0")
    Integer capacidadOcupada; // si el avión aún está en estado EN_ESPERA y esto tiene > 0; significa en reserva (?)

    @Column(nullable = false)
    @ColumnDefault("false")
    Boolean cancelado;

    @Column(nullable = false)
    Boolean esIntercontinental; // también es transitivo ahora que lo veo bien...

    @ColumnDefault("true")
    @Column(nullable = false)
    Boolean activo; // PORSIA
}
// La razón por la que usamos wrappers es para que todo pueda ser nulo y nos facilite la construcción o instanciacion
// objetos (relaciones lazy), sin embargo, en algoritmo sí conviene más primitivos.