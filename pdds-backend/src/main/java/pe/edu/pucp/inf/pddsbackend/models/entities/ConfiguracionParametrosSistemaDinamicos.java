package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

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
    @ColumnDefault("0.5")
    private Double factorDeVelocidad;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean usarPlanificacionRapida=false;


}
