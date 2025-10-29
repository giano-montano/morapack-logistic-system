package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;

import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/simulaciones")
public class SimulacionController {
    private final SimulacionService simulacionService;

    @PostMapping
    public ResponseEntity<?> iniciarSimulacion(
            @RequestBody SimulacionRequestDTO simulacionRequestDTO
    ) throws ExecutionException, InterruptedException {
        System.out.println("QUE: "+ simulacionRequestDTO);
        Simulacion saved =simulacionService.iniciarSimulacionAhora(simulacionRequestDTO);
        return ResponseEntity.ok().body(saved);
    }

}
