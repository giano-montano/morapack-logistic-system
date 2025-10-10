package pe.edu.pucp.inf.pddsbackend.servicios.interfaces;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;

public interface PlanificacionServicio
{
    Planificacion persistir(Planificacion planificacion);
}
