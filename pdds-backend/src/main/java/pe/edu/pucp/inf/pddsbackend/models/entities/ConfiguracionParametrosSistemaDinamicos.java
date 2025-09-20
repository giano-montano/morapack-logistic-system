package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@AllArgsConstructor
@Entity
@NoArgsConstructor
@Getter
public class ConfiguracionParametrosSistemaDinamicos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @ColumnDefault("0.5")
    private BigDecimal equivalenciaMinutoEnSegundos;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean usarPlanificacionRapida=false;


}
