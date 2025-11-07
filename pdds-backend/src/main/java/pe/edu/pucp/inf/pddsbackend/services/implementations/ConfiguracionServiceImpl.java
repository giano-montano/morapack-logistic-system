package pe.edu.pucp.inf.pddsbackend.services.implementations;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;

@Service
@RequiredArgsConstructor
public class ConfiguracionServiceImpl implements ConfiguracionService {
    private final ConfiguracionRepository configuracionRepository;


    @Setter
    @Getter
    public static double FACTOR_DE_VELOCIDAD_POR_DEFECTO = 400.0;

    @Setter
    @Getter
    public static long MINUTOS_REALES_ENTRE_PLANIFS_POR_DEFECTO = 60;

    // GUARDA o retorna config (TRANSACCIÓN CORTA)
    @Transactional
    @Override
    public ConfiguracionParametrosSistemaDinamicos crearYAsegurarConfig(SimulacionRequestDTO params) {
        ConfiguracionParametrosSistemaDinamicos c = configuracionRepository.findById(1L).orElse(null);
        
        // ✅ Si ya existe, ACTUALIZAR con los nuevos parámetros
        if (c != null) {
            boolean cambios = false;
            
            // Actualizar factor de velocidad si viene en params
            if (params.factorDeVelocidad() != null && !params.factorDeVelocidad().equals(c.getFactorDeVelocidad())) {
                c = ConfiguracionParametrosSistemaDinamicos.builder()
                        .id(c.getId())
                        .factorDeVelocidad(params.factorDeVelocidad())
                        .minutosRealesEntrePlanificaciones(
                            params.minutosRealesEntrePlanificaciones() != null ? 
                                params.minutosRealesEntrePlanificaciones() : c.getMinutosRealesEntrePlanificaciones()
                        )
                        .usarPlanificacionRapida(c.getUsarPlanificacionRapida())
                        .build();
                cambios = true;
                System.out.println("🔧 Actualizando configuración: factorDeVelocidad " + 
                    c.getFactorDeVelocidad() + " → " + params.factorDeVelocidad());
            }
            
            // Actualizar minutos entre planificaciones si viene en params
            if (params.minutosRealesEntrePlanificaciones() != null && 
                !params.minutosRealesEntrePlanificaciones().equals(c.getMinutosRealesEntrePlanificaciones())) {
                c = ConfiguracionParametrosSistemaDinamicos.builder()
                        .id(c.getId())
                        .factorDeVelocidad(c.getFactorDeVelocidad())
                        .minutosRealesEntrePlanificaciones(params.minutosRealesEntrePlanificaciones())
                        .usarPlanificacionRapida(c.getUsarPlanificacionRapida())
                        .build();
                cambios = true;
                System.out.println("🔧 Actualizando configuración: minutosEntrePlanif " + 
                    c.getMinutosRealesEntrePlanificaciones() + " → " + params.minutosRealesEntrePlanificaciones());
            }
            
            if (cambios) {
                return configuracionRepository.save(c);
            }
            
            System.out.println("ℹ️ Usando configuración existente: factorVelocidad=" + c.getFactorDeVelocidad());
            return c;
        }
        
        // ✅ Si NO existe, crear nueva
        c = ConfiguracionParametrosSistemaDinamicos.builder()
                .factorDeVelocidad(params.factorDeVelocidad()!=null?
                        params.factorDeVelocidad() :  FACTOR_DE_VELOCIDAD_POR_DEFECTO)
                .minutosRealesEntrePlanificaciones(params.minutosRealesEntrePlanificaciones()!=null?
                        params.minutosRealesEntrePlanificaciones():MINUTOS_REALES_ENTRE_PLANIFS_POR_DEFECTO)
                .usarPlanificacionRapida(false)
                .build();
        System.out.println("✨ Creando nueva configuración: factorVelocidad=" + c.getFactorDeVelocidad());
        return configuracionRepository.save(c);
    }

    @Override
    public ConfiguracionParametrosSistemaDinamicos obtenerConfig() {

        return configuracionRepository.findById(1L).orElse(null);
    }


}
