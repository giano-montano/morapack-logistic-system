package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlmacenRepository extends JpaRepository<AlmacenEntidad, Long>
{

    @Query("SELECT a FROM AlmacenEntidad a WHERE a.capacidadOcupada < a.capacidadMaxima OR a.esInfinito = true")
    public List<AlmacenEntidad> listarTodosAlmacenes();

    List<AlmacenEntidad> findAlmacenByActivoTrue(); // O que sea una List???

    // PERSISTIR SOLUCIÓN DE ALGORITMO EN BD
    @Modifying
    @Query("update AlmacenEntidad a set a.capacidadOcupada = a.capacidadOcupada - :delta where a.id = :id and a.esInfinito = false")
    int decrementarCapacidadOcupadaSiFinito(@Param("id") Long id, @Param("delta") Integer delta);

    Optional<AlmacenEntidad> findByCodigoAeropuertoEn4LetrasIgnoreCase(String code);

    // Optional<AlmacenEntidad> findByCodigoAeropuertoEn4LetrasIgnoreCase(String
    // codigo);
}
