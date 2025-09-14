package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

@Repository
public interface PedidoAuditRepository extends RevisionRepository<Pedido, Long, Integer> {
}
