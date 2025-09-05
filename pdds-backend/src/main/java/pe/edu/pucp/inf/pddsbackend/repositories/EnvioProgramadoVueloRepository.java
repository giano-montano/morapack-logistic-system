package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.EnvioProgramado;
import pe.edu.pucp.inf.pddsbackend.models.entities.EnvioProgramadoVuelo;

@Repository
public interface EnvioProgramadoVueloRepository extends JpaRepository<EnvioProgramadoVuelo, Long> {

}
