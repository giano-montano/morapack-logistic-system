package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;

public interface ConfiguracionService {
    // GUARDA o retorna config (TRANSACCIÓN CORTA)
    @Transactional
    ConfiguracionParametrosSistemaDinamicos crearYAsegurarConfig();
}
