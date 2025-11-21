package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository
        extends
            JpaRepository<PedidoEntidad, Long>,
            RevisionRepository<PedidoEntidad, Long, Integer>
{

    // @Query("SELECT p FROM PedidoEntidad p WHERE p.estado ='POR_PROGRAMAR' OR
    // p.cantidadProductosProgramados < p.cantidadProductosTotal")
    // public List<PedidoEntidad>
    // findPedidosAunNoProgramadosOProgramadosParcialmente();

    @Query("select p.id from PedidoEntidad p where p.almacenDestino.id = :idAlmacenDestino") // bug
                                                                                             // extraño
                                                                                             // que
                                                                                             // traía
                                                                                             // pedido
                                                                                             // en
    // vez de id me obligó a poner esta query manual
    public List<Long> findIdByAlmacenDestino_Id(Long idAlmacenDestino);

    @Query("""
                SELECT p FROM PedidoEntidad p WHERE p.cantidadProductosPedidos > p.cantidadProductosEntregados
                AND p.almacenDestino.esInfinito = false
            """)
    public List<PedidoEntidad> listarPedidosNoAtendidosCompletamenteYNoDeAlmacenesInfinitos();

    @Query("""
            SELECT p FROM PedidoEntidad p
            LEFT JOIN FETCH p.almacenDestino a
            LEFT JOIN FETCH p.cliente c
            """)
    List<PedidoEntidad> findAllWithAlmacenAndCliente();

    @Query("""
            SELECT p FROM PedidoEntidad p
            LEFT JOIN FETCH p.almacenDestino a
            LEFT JOIN FETCH p.cliente c
            WHERE
                p.cantidadProductosPedidos > p.cantidadProductosEntregados
                AND p.almacenDestino.esInfinito = false
                AND p.instanteRegistro > :fechaMinima
                AND p.instanteRegistro < :fechaMaxima
            """)
    List<PedidoEntidad> listarPedidosNoAtendidosCompletamenteYNoDeAlmacenesInfinitosEntreMedio(
            @Param("fechaMinima") Instant topeInferior, @Param("fechaMaxima") Instant topeSuperior);

    @Query("SELECT p FROM PedidoEntidad p " +
            "JOIN FETCH p.almacenDestino " +
            "LEFT JOIN FETCH p.cliente " +
            "WHERE p.id = :id")
    Optional<PedidoEntidad> findByIdConRelaciones(@Param("id") Long id);

    @Query("SELECT p FROM PedidoEntidad p WHERE UPPER(p.almacenDestino.codigoCiudadEn4Letras) LIKE CONCAT('%', UPPER(:codigoCiudadEn4Letras), '%')")
    List<PedidoEntidad> findByDestino(@Param("codigo") String codigoCiudadEn4Letras);

    List<PedidoEntidad> findAllByInstanteRegistroAfter(Instant instante);

    @Query("select p from PedidoEntidad p join fetch p.almacenDestino a where p.instanteRegistro >= :from and p.instanteRegistro < :to")
    List<PedidoEntidad> findByInstanteRegistroAfterAndInstanteRegistroBeforeFetchAlmacen(
            @Param("from") Instant from, @Param("to") Instant to);
}
