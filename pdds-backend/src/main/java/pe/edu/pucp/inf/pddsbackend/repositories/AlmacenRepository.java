package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;

import java.util.List;

@Repository
public interface AlmacenRepository extends JpaRepository<Almacen, Long> {

    @Query("SELECT a FROM Almacen a WHERE a.capacidadOcupada < a.capacidadTotal OR a.esInfinito = true")
    public List<Almacen> findAlmacenesNoLlenosOInfinitos();

    // PERSISTIR SOLUCIÓN DE ALGORITMO EN BD
    @Modifying
    @Query("update Almacen a set a.capacidadOcupada = a.capacidadOcupada - :delta where a.id = :id and a.esInfinito = false")
    int decrementarCapacidadOcupadaSiFinito(@Param("id") Long id, @Param("delta") Integer delta);



}
