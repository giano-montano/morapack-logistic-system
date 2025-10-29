package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteRepository extends JpaRepository<pe.edu.pucp.inf.pddsbackend.modelos.entidades.Cliente, Long> {
    // No necesitas agregar nada más; hereda métodos como findById, getReferenceById, save, etc.
}
