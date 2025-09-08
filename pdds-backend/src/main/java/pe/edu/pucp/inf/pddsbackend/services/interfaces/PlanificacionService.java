package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.models.domain.Planificacion;

public interface PlanificacionService {

    public PlanificacionResponseDTO realizarPlanificacionDePedidosActuales(RealizarPlanificacionDTO params) throws Exception;

}
