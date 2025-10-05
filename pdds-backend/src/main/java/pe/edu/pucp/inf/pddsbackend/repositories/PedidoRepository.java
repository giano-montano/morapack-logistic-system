package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, RevisionRepository<Pedido, Long, Integer> {

//    @Query("SELECT p FROM Pedido p WHERE p.estado ='POR_PROGRAMAR' OR p.cantidadProductosProgramados < p.cantidadProductosTotal")
//    public List<Pedido> findPedidosAunNoProgramadosOProgramadosParcialmente();

    @Query("select p.id from Pedido p where p.almacenDestino.id = :idAlmacenDestino") // bug extraño que traía pedido en
    //vez de id me obligó a poner esta query manual
    public List<Long> findIdByAlmacenDestino_Id(Long idAlmacenDestino);
    @Query("SELECT p FROM Pedido p JOIN FETCH p.almacenDestino")
    List<Pedido> findAllWithAlmacen();

    @Query("""
    SELECT p FROM Pedido p WHERE p.cantidadProductosPedidos > p.cantidadProductosEntregados
    AND p.almacenDestino.esInfinito = false
""")
    public List<Pedido> listarPedidosNoAtendidosCompletamenteYNoDeAlmacenesInfinitos();
}
