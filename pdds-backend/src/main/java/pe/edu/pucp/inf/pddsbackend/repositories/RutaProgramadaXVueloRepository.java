package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.models.entities.RutaProgramadaXVuelo;

import java.util.List;

public interface RutaProgramadaXVueloRepository extends JpaRepository<RutaProgramadaXVuelo,Long> {

    List<RutaProgramadaXVuelo> findByIdOrderByOrden(Long rutaProgramadaId);
    List<RutaProgramadaXVuelo> findByRutaProgramadaIdOrderByOrden(Long rutaProgramadaId);


}
