package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Planificacion;

public interface PlanificacionRepository extends JpaRepository<Planificacion,Long> {
}
