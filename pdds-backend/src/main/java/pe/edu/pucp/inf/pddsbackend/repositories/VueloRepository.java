package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
@Repository
public interface VueloRepository extends JpaRepository<VueloEntidad, Long> {


//    @Query("SELECT v FROM VueloEntidad v WHERE v.estado ='EN_ESPERA' or v.estado = 'EN_CURSO'")
    public List<VueloEntidad> findByActivoTrueAndFechaHoraInicioUtcAfter(Instant despegaDespuesDe);

    public List<VueloEntidad> findByActivoTrueAndFechaHoraInicioUtcAfterAndFechaHoraFinUtcBefore(Instant despegaDespuesDe, Instant instanteMaximoLlegada);

    // ✅ NUEVO: Para obtener todos los vuelos activos sin filtros
    public List<VueloEntidad> findByActivoTrue();

    @Query("select v.id from VueloEntidad v where v.almacenDestino.id = :idAlmacenDestino") // bug extraño que traía pedido en
    public List<Long> findIdByActivoTrueAndAlmacenDestino_Id(Long idAlmacenDestino);

    @Query("select v.id from VueloEntidad v where v.almacenOrigen.id = :idAlmacenOrigen")
    public List<Long> findIdByActivoTrueAndAlmacenOrigen_Id(Long idAlmacenOrigen);

    boolean existsByAlmacenOrigenAndAlmacenDestinoAndFechaHoraInicioUtc(AlmacenEntidad origen, AlmacenEntidad destino, Instant fechaHoraInicioUtc);


    List<VueloEntidad> findByAlmacenOrigen_IdInAndAlmacenDestino_IdInAndFechaHoraInicioUtcBetween(
            Collection<Long> origenIds,
            Collection<Long> destinoIds,
            Instant startInclusive,
            Instant endInclusive);

}