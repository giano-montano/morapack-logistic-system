package pe.edu.pucp.inf.pddsbackend.servicios.interfaces;

import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionParametrosDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;

public interface PlanificacionServicio
{
    Planificacion persistir(Planificacion planificacion);

    void planificar(PlanificacionParametrosDTO parametrosDTO) throws Exception;
}
