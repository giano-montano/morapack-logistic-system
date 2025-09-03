package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;

import java.util.List;

public interface AlmacenRepository extends JpaRepository<Almacen, Long> {

    @Query("SELECT a FROM Almacen a WHERE a.capacidadOcupada >= a.capacidadTotal")
    public List<Almacen> findAlmacenesNoLlenos();

}
