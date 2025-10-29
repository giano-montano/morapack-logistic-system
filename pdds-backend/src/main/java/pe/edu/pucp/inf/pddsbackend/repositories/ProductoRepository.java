package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ProductoEntidad;

import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<ProductoEntidad, Long> {
    Optional<ProductoEntidad> findByUuid(UUID uuidProducto);
}
