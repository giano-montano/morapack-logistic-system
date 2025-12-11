package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;

import java.util.Optional;

public interface SimulacionRepository extends JpaRepository<Simulacion, Long>
{
    /**
     * ✅ Busca la primera simulación TIEMPO_REAL que aún no ha terminado
     * (fechaHoraFin es null)
     */
    Optional<Simulacion> findFirstByTipoAndFechaHoraFinIsNullOrderByFechaHoraInicioDesc(
            TipoSimulacion tipo);
}
