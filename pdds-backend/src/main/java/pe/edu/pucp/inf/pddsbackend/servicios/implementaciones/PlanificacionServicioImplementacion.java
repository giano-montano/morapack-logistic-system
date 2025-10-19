package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import pe.edu.pucp.inf.pddsbackend.algoritmo.Estado;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Lectora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PlanificacionEntidad;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

@Service
@RequiredArgsConstructor
public class PlanificacionServicioImplementacion implements PlanificacionServicio
{
    private final PlanificacionRepositorio planificacionRepositorio;

    @Override
    public Planificacion persistir(Planificacion planificacion)
    {
        PlanificacionEntidad planificacionEntidad;

        planificacionEntidad = new PlanificacionEntidad(planificacion);
        planificacionEntidad = planificacionRepositorio.save(planificacionEntidad);
        planificacion = planificacionEntidad.convertirADominio();

        return planificacion;
    }

    @Override
    public Boolean planificar(Planificacion planificacion)
    {
        Lectora lectora = new Lectora();
        Estado estadoInicial = new Estado();

        System.out.println("todo bien mano");
        // this.obtenerEstado();

        return false;
    }
}
