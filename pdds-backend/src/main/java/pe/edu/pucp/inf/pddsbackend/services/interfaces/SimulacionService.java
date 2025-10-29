package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;

import java.util.concurrent.ExecutionException;

@Validated
public interface SimulacionService {

    @Transactional
    Simulacion iniciarSimulacionAhora(@Valid SimulacionRequestDTO params) throws ExecutionException, InterruptedException;

}
