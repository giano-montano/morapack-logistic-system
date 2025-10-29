package pe.edu.pucp.inf.pddsbackend.modelos.entidades;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Planificacion extends BaseAuditable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    private Instant fechaHoraFinPlanif;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean colapsado=false;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean reprogramado=false;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private Simulacion simulacion;

    private Double fitnessConseguido;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean huboErrorEjecucion=false;

    private String razonErrorEjecucion;

    private Long duracionEjecucionAlgoritmo;
}
