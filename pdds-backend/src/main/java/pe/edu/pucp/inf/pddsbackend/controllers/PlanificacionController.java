package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;

import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanificacionController {

    @PostMapping("/planificaciones")
    public ResponseEntity<String> realizarPlanificacionDeEnvios(
            @RequestBody RealizarPlanificacionDTO params
    ) {


        return ResponseEntity.of(Optional.of(" No implementado aún"));
    }
}
