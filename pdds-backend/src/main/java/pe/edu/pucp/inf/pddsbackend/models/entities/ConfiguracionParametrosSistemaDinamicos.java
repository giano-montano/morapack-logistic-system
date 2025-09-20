package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
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
