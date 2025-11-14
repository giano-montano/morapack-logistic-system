package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;

import java.util.List;

@Repository
public interface VueloProgramadoRepository extends JpaRepository<VueloProgramado, Long> {
    // métodos custom si hacen falta
    @Query("select vp from VueloProgramado vp join fetch vp.almacenOrigen o join fetch vp.almacenDestino d where vp.activo = true")
    List<VueloProgramado> findAllActiveWithAlmacenes();

    List<VueloProgramado>  findByActivoTrue();
}
