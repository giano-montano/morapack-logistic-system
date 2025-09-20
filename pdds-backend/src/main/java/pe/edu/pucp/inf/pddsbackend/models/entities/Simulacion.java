package pe.edu.pucp.inf.pddsbackend.models.entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Simulacion extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    private Instant fechaHoraInicio = Instant.now();

    private Instant fechaHoraFin;

    @Enumerated(EnumType.STRING)
    private TipoSimulacion tipo;

    @Enumerated(EnumType.STRING)
    private RazonFin razonColapso;

}
