package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ProgramacionEntidad;

public interface ProgramacionRepository extends JpaRepository<ProgramacionEntidad,Long> {
}
