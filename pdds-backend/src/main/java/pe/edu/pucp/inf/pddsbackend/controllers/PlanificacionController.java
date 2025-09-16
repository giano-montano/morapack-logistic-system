package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;

import java.util.Optional;

@RestController
@RequestMapping("/api/planificaciones")
@RequiredArgsConstructor
public class PlanificacionController {

    private final PlanificacionService planificacionService;

    @PostMapping
    public ResponseEntity<PlanificacionResponseDTO> realizarPlanificacionDeEnvios(
            @RequestBody RealizarPlanificacionDTO params
    ) throws Exception {
        PlanificacionResponseDTO res = planificacionService.realizarPlanificacionDePedidosActuales(params);

        return ResponseEntity.of(Optional.of(res));
    }



}
