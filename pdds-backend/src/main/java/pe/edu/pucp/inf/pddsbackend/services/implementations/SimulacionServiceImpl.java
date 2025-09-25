package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.EjecutorSimulacion;

import java.time.Instant;

@RequiredArgsConstructor
@Service
public class SimulacionServiceImpl implements SimulacionService {

    SimulacionRepository simulacionRepository;
    PlanificacionService planificacionService;
    ConfiguracionRepository configuracionRepository;
    EjecutorSimulacion ejecutorSimulacion;

    @Transactional
    public void iniciarSimulacionAhora(SimulacionRequestDTO params) throws Exception {
        Simulacion simulacion = Simulacion.builder()
                .tipo(params.tipoSimulacion())
                .fechaHoraInicio(Instant.now())
                .fechaHoraFin(null)
                .razonFin(null)
                .build();
        ConfiguracionParametrosSistemaDinamicos config = configuracionRepository.findById(1L).orElse(null);
        if(config == null) {
            config = ConfiguracionParametrosSistemaDinamicos.builder()
                    .id(0L)
                    .factorDeVelocidad(60.0)
                    .usarPlanificacionRapida(false)
                    .build();
        }
        Simulacion saved = simulacionRepository.save(simulacion);

        RealizarPlanificacionDTO realizarPlanificacionDTO = RealizarPlanificacionDTO.builder()
                .idSimulacion(saved.getId())
                .estrategiaFija(config.getUsarPlanificacionRapida()? EstrategiaFija.RAPIDA: EstrategiaFija.PROFUNDA)
                .parametros(params.parametros())
                .build();

        ejecutorSimulacion.startSimulation(saved,params, config, realizarPlanificacionDTO);

        saved.setFechaHoraFin(Instant.now());
    }
}
