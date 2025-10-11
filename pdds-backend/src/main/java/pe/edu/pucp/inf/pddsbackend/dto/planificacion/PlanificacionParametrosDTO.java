package pe.edu.pucp.inf.pddsbackend.dto.planificacion;

import java.time.Instant;
import java.util.ArrayList;

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

//    EstrategiaFija estrategiaFija = EstrategiaFija.PROFUNDA;
    ArrayList<Object> parametros;
    Long semilla;

//    String subCarpetaReportes;
//    Boolean loggear;
    //    Long idSimulacion;
    public Planificacion converitrADominio()
    {
        return Planificacion.builder().instanteActual(this.instanteActual).build();
    }
}
