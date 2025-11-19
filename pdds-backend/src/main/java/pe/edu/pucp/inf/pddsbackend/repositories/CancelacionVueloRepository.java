package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.CancelacionVuelo;

public interface CancelacionVueloRepository extends JpaRepository<CancelacionVuelo, Long> {
}
