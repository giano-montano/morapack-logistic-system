package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;

import java.time.Instant;
import java.util.List;
@Repository
public interface VueloRepository extends JpaRepository<Vuelo, Long> {


//    @Query("SELECT v FROM Vuelo v WHERE v.estado ='EN_ESPERA' or v.estado = 'EN_CURSO'")
    public List<Vuelo> findByActivoTrueAndFechaHoraInicioUtcAfter(Instant despegaDespuesDe);

    @Query("select v.id from Vuelo v where v.almacenDestino.id = :idAlmacenDestino") // bug extraño que traía pedido en
    public List<Long> findIdByActivoTrueAndAlmacenDestino_Id(Long idAlmacenDestino);

    @Query("select v.id from Vuelo v where v.almacenOrigen.id = :idAlmacenOrigen")
    public List<Long> findIdByActivoTrueAndAlmacenOrigen_Id(Long idAlmacenOrigen);

}