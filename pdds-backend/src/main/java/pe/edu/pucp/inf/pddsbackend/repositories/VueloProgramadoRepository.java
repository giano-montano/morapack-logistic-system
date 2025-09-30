package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.VueloProgramado;

@Repository
public interface VueloProgramadoRepository extends JpaRepository<VueloProgramado, Long> {
    // métodos custom si hacen falta
}
