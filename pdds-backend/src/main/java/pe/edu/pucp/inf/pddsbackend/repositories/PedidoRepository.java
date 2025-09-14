package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, RevisionRepository<Pedido, Long, Integer> {

    @Query("SELECT p FROM Pedido p WHERE p.estado ='POR_PROGRAMAR' OR p.cantidadProductosProgramados < p.cantidadProductosTotal")
    public List<Pedido> findPedidosAunNoProgramadosOProgramadosParcialmente();
}
