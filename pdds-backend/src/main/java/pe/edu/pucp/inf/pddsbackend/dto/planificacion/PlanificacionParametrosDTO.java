package pe.edu.pucp.inf.pddsbackend.dto.planificacion;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanificacionParametrosDTO
{
    Instant instanteActual, inicioOperaciones;

    public Planificacion converitrADominio()
    {
        return Planificacion.builder().
        instanteActual(this.instanteActual).
        inicioOperaciones(this.inicioOperaciones).build();
    }
}
