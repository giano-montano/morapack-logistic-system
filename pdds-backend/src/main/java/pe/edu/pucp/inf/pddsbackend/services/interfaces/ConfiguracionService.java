package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;

public interface ConfiguracionService
{
    // GUARDA o retorna config (TRANSACCIÓN CORTA)
    @Transactional
    ConfiguracionParametrosSistemaDinamicos crearYAsegurarConfig(SimulacionRequestDTO params);

    ConfiguracionParametrosSistemaDinamicos obtenerConfig();
}
