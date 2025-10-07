package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.inf.pddsbackend.models.entities.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<pe.edu.pucp.inf.pddsbackend.models.entities.Cliente, Long> {
    // No necesitas agregar nada más; hereda métodos como findById, getReferenceById, save, etc.
}
