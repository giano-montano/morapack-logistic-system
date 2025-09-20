package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

@RequiredArgsConstructor
@Service
public class SimulacionServiceImpl implements SimulacionService {

    SimulacionRepository simulacionRepository;
    PlanificacionService planificacionService;
    ConfiguracionRepository configuracionRepository;

    @Transactional
    public void iniciarSimulacionAhora(SimulacionRequestDTO params) throws Exception {
        Simulacion simulacion = Simulacion.builder()
                .tipo(params.tipoSimulacion())
                .fechaHoraInicio(Instant.now())
                .fechaHoraFin(null)
                .razonColapso(null)
                .build();

        ConfiguracionParametrosSistemaDinamicos config = configuracionRepository.findById(1L).orElse(null);
        if(config == null) {
            config = new ConfiguracionParametrosSistemaDinamicos(0L, new BigDecimal("0.5"),false);
        }

        Simulacion saved = simulacionRepository.save(simulacion);
        RealizarPlanificacionDTO realizarPlanificacionDTO = RealizarPlanificacionDTO.builder()
                .idSimulacion(saved.getId())
                .estrategiaFija(config.getUsarPlanificacionRapida()? EstrategiaFija.RAPIDA: EstrategiaFija.PROFUNDA)
                .parametros(params.parametros())
                .build();


        PlanificacionResponseDTO res = planificacionService.realizarPlanificacionDePedidosActuales(realizarPlanificacionDTO); // usa la simulación también.
//        saved.setFechaHoraFin(res.fechaHoraFinPlanif());
    }
}
