package pe.edu.pucp.inf.pddsbackend.dto.planificacion;

import java.time.Instant;
import java.util.UUID;

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
    Instant instanteActual;

    public Planificacion converitrADominio()
    {
        return Planificacion.builder()
                .id(UUID.randomUUID())
                .instanteActual(this.instanteActual)
                .build();
    }
}
