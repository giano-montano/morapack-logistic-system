package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;

@Repository
public interface VueloProgramadoRepository extends JpaRepository<VueloProgramado, Long> {
    // métodos custom si hacen falta
}
