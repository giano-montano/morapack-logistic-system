package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@AllArgsConstructor
@Entity
@NoArgsConstructor
@Getter
@Builder
public class ConfiguracionParametrosSistemaDinamicos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @ColumnDefault("800.0")
    private Double factorDeVelocidad;

    @Column(nullable = false)
    @ColumnDefault("60")
    private Long minutosRealesEntrePlanificaciones;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean usarPlanificacionRapida=false;


}
