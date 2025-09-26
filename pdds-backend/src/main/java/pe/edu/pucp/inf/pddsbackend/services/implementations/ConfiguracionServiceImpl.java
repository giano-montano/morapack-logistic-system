package pe.edu.pucp.inf.pddsbackend.services.implementations;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;

@Service
@RequiredArgsConstructor
public class ConfiguracionServiceImpl implements ConfiguracionService {
    private final ConfiguracionRepository configuracionRepository;


    @Setter
    @Getter
    public static double FACTOR_DE_VELOCIDAD_POR_DEFECTO = 30.0;

    // GUARDA o retorna config (TRANSACCIÓN CORTA)
    @Transactional
    @Override
    public ConfiguracionParametrosSistemaDinamicos crearYAsegurarConfig() {
        ConfiguracionParametrosSistemaDinamicos c = configuracionRepository.findById(1L).orElse(null);
        if (c == null) {
            c = ConfiguracionParametrosSistemaDinamicos.builder()
//                    .id(1L) // da problemas no se pq
                    .factorDeVelocidad(FACTOR_DE_VELOCIDAD_POR_DEFECTO)
                    .usarPlanificacionRapida(false)
                    .build();
            return configuracionRepository.save(c);
        }
        return c;
    }



}
