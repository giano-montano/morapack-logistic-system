package pe.edu.pucp.inf.pddsbackend.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PlanificacionEntidad;

@Repository
public interface PlanificacionRepositorio extends JpaRepository<PlanificacionEntidad, Long>
{
}
