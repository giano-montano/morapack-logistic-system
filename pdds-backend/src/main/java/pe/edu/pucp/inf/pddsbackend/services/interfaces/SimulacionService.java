package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.models.entities.Simulacion;

import java.util.concurrent.ExecutionException;

@Validated
public interface SimulacionService {

    @Transactional
    Simulacion iniciarSimulacionAhora(@Valid SimulacionRequestDTO params) throws ExecutionException, InterruptedException;

}
