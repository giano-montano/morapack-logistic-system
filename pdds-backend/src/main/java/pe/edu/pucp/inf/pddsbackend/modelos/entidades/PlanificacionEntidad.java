package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;

@Entity
@Table(name = "planificacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanificacionEntidad
{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column
    private Instant instanteActual, inicioOperaciones;

    public PlanificacionEntidad(Planificacion dominio)
    {
        this.id = dominio.getId();
        this.instanteActual = dominio.getInstanteActual();
        this.inicioOperaciones = dominio.getInicioOperaciones();
    }

    public Planificacion convertirADominio()
    {
        return Planificacion.builder().
        id(id).instanteActual(instanteActual).inicioOperaciones(inicioOperaciones).build();
    }
}
