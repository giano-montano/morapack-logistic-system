package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;

import java.util.List;

public interface VueloRepository extends JpaRepository<Vuelo, Long> {


    @Query("SELECT v FROM Vuelo v WHERE v.estado ='EN_ESPERA' or v.estado = 'EN_CURSO'")
    public List<Vuelo> findVuelosPorDespegarOEnCurso();

    //recordar habrá eventos periódicos en BD para manejar esta clase de estados automáticamente,
    //no necesario de hacer en la apliación.


}
