package pe.edu.pucp.inf.pddsbackend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;

public interface ConfiguracionRepository extends JpaRepository<ConfiguracionParametrosSistemaDinamicos, Long> {
}
