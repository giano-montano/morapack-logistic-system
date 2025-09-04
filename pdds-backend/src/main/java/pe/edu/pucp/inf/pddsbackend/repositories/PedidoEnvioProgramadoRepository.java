package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.PedidoEnvioProgramado;

@Repository
public interface PedidoEnvioProgramadoRepository extends JpaRepository<PedidoEnvioProgramado, Long> {


}
