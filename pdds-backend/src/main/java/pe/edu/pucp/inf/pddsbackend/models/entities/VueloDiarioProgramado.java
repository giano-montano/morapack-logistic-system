package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;

import java.time.Instant;
import java.time.LocalTime;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@EqualsAndHashCode(callSuper = true) // q
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited(targetAuditMode = NOT_AUDITED)
public class VueloDiarioProgramado extends AuditableBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_origen_id") // codigoAeropuertoEn4letras
    private Almacen almacenOrigen;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private Almacen almacenDestino;

    private LocalTime  fechaHoraInicioUtc;

    private LocalTime fechaHoraFinUtc;

    @Enumerated(EnumType.STRING)  // Almacena como VARCHAR en la BD
    @Column(
            name = "estado",
            nullable = true)
    private EstadoVuelo estado;
    // posiblemente eliminado(transitivo con fechaHoras de inicio y fin) y solo atributo  "cancelado"


    Integer capacidadMaximaProductos;

    @ColumnDefault("0")
    Integer capacidadOcupadaProductos; // si el avión aún está en estado EN_ESPERA y esto tiene > 0; significa en reserva (?)

    @ColumnDefault("0")
    Integer capacidadReservadaProductos; // mejor su propio en reserva...
}
