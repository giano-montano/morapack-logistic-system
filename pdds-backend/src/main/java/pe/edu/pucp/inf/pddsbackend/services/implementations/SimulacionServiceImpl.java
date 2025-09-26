package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.ConfiguracionParametrosSistemaDinamicos;
import pe.edu.pucp.inf.pddsbackend.models.entities.RazonFin;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.ConfiguracionRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.SimulacionRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.EjecutorSimulacion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static pe.edu.pucp.inf.pddsbackend.utils.LoggingReport.TS_FMT;

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
        String nombreSubCarpeta = "Simulación_"+saved.getId()+"_"+LocalDateTime.now().format(TS_FMT);
        RealizarPlanificacionDTO realizarPlanificacionDTO = RealizarPlanificacionDTO.builder()
                .idSimulacion(saved.getId())
                .estrategiaFija(config.getUsarPlanificacionRapida()? EstrategiaFija.RAPIDA: EstrategiaFija.PROFUNDA)
                .seed(params.seed()!=null? params.seed() : new Random().nextLong())
                .subCarpetaReportes(nombreSubCarpeta)
                .parametros(params.parametros())
                .build();

        ContextoSimulacion contextoSimulacionActualizado = ejecutorSimulacion.startSimulation(saved, params, config, realizarPlanificacionDTO,nombreSubCarpeta)
                .get();
        saved.setFechaHoraFin(Instant.now());
        if(contextoSimulacionActualizado != null) {
            if(contextoSimulacionActualizado.isColapsado()){
                saved.setRazonFin(RazonFin.POR_COLAPSO);
            }
            if(contextoSimulacionActualizado.isConError()){
                saved.setRazonFin(RazonFin.ERROR_INTERNO);
            }
            List<SalidaProblemaPlanificacion> planOut = contextoSimulacionActualizado.getSolucionesAcumuladas(); // si recogiste soluciones
            if(planOut!=null ){
                System.out.println("planOut = " + planOut.size());
                saved.setRazonFin(RazonFin.NATURAL); // no colapso Fin Normal
            }
        }
        return simulacionRepository.save(saved);
    }
}
