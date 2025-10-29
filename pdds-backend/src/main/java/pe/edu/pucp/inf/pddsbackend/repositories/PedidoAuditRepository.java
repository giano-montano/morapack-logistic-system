package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

@Repository
public interface PedidoAuditRepository extends RevisionRepository<PedidoEntidad, Long, Integer> {
}
