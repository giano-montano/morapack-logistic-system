package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ProgramacionXVuelo;

import java.util.List;

public interface ProgramacionXVueloRepository extends JpaRepository<ProgramacionXVuelo, Long>
{

    List<ProgramacionXVuelo> findByIdOrderByOrden(Long rutaProgramadaId);

    List<ProgramacionXVuelo> findByRutaProgramadaIdOrderByOrden(Long rutaProgramadaId);

}
