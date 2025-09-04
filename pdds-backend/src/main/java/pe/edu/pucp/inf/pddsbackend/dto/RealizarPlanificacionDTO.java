package pe.edu.pucp.inf.pddsbackend.dto;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;

@Data
public class RealizarPlanificacionDTO {

    EstrategiaFija estrategiaFija = EstrategiaFija.AUTO; //opcional
    // otros parámetros de la planificación...
}


