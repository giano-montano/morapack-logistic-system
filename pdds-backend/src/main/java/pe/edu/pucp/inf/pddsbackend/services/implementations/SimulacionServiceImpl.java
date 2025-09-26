package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.EjecutorSimulacion;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
@Service
public class SimulacionServiceImpl implements SimulacionService {

    private final SimulacionRepository simulacionRepository;
//    PlanificacionService planificacionService;
    private final ConfiguracionRepository configuracionRepository;
    private final EjecutorSimulacion ejecutorSimulacion;

    @Override
    @Transactional
    public Simulacion iniciarSimulacionAhora(SimulacionRequestDTO params) throws ExecutionException, InterruptedException {
        System.out.println("He recibido: "+params);
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
                .seed(params.seed()!=null? params.seed() : new Random().nextLong())
                .parametros(params.parametros())
                .build();

        saved = ejecutorSimulacion.startSimulation(saved, params, config, realizarPlanificacionDTO)
                .get();

        saved.setFechaHoraFin(Instant.now());
        return simulacionRepository.save(saved);
    }
}
