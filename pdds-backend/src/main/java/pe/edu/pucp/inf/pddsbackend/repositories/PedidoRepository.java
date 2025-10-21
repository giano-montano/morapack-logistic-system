package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, RevisionRepository<Pedido, Long, Integer> {

//    @Query("SELECT p FROM Pedido p WHERE p.estado ='POR_PROGRAMAR' OR p.cantidadProductosProgramados < p.cantidadProductosTotal")
//    public List<Pedido> findPedidosAunNoProgramadosOProgramadosParcialmente();

    @Query("select p.id from Pedido p where p.almacenDestino.id = :idAlmacenDestino") // bug extraño que traía pedido en
    //vez de id me obligó a poner esta query manual
    public List<Long> findIdByAlmacenDestino_Id(Long idAlmacenDestino);

    @Query("""
    SELECT p FROM Pedido p WHERE p.cantidadProductosPedidos > p.cantidadProductosEntregados
    AND p.almacenDestino.esInfinito = false
""")
    public List<Pedido> listarPedidosNoAtendidosCompletamenteYNoDeAlmacenesInfinitos();
    @Query("""
    SELECT p FROM Pedido p
    LEFT JOIN FETCH p.almacenDestino a
    LEFT JOIN FETCH p.cliente c
    """)
    List<Pedido> findAllWithAlmacenAndCliente();

    @Query("SELECT p FROM Pedido p " +
            "JOIN FETCH p.almacenDestino " +
            "LEFT JOIN FETCH p.cliente " +
            "WHERE p.id = :id")
    Optional<Pedido> findByIdConRelaciones(@Param("id") Long id);

    @Query("SELECT p FROM Pedido p WHERE UPPER(p.almacenDestino.codigoCiudadEn4Letras) LIKE CONCAT('%', UPPER(:codigo), '%')")
    List<Pedido> findByDestino(@Param("codigo") String codigo);

}
