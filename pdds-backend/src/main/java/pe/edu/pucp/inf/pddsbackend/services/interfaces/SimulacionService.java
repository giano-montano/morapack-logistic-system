package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;

import java.util.concurrent.ExecutionException;

@Validated
public interface SimulacionService
{

    @Transactional
    Simulacion iniciarSimulacionAhora(@Valid SimulacionRequestDTO params)
            throws ExecutionException, InterruptedException;

    /**
     * ✅ Cancela una simulación en ejecución
     */
    boolean cancelarSimulacion(Long idSimulacion);

    /**
     * ✅ Pausa la planificación sin detener la simulación
     */
    boolean pausarPlanificacion(Long idSimulacion);

    /**
     * ✅ Reanuda la planificación
     */
    boolean reanudarPlanificacion(Long idSimulacion);

    /**
     * ✅ Verifica si la planificación está pausada
     */
    boolean estaPlanificacionPausada(Long idSimulacion);

    /**
     * ✅ Envía la sincronización del reloj a usuarios que se conectan a una simulación existente
     */
    boolean solicitarSincronizacion(Long idSimulacion);

}
