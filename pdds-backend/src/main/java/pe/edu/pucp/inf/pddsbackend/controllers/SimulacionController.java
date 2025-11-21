package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/simulaciones")
public class SimulacionController
{
    private final SimulacionService simulacionService;

    @PostMapping
    public ResponseEntity<?> iniciarSimulacion(
            @RequestBody SimulacionRequestDTO simulacionRequestDTO)
            throws ExecutionException, InterruptedException
    {
        System.out.println("QUE: " + simulacionRequestDTO);
        Simulacion saved = simulacionService.iniciarSimulacionAhora(simulacionRequestDTO);

        // ✅ Devolver un DTO con el ID inmediatamente para que el frontend se conecte al
        // WebSocket
        return ResponseEntity.ok().body(Map.of(
                "id", saved.getId(),
                "tipo", saved.getTipo(),
                "fechaHoraInicio", saved.getFechaHoraInicio()));
    }

    /**
     * ✅ Cancela una simulación en ejecución DELETE /api/simulaciones/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelarSimulacion(@PathVariable Long id)
    {
        System.out.println("🛑 Solicitud de cancelación de simulación ID: " + id);
        boolean cancelado = simulacionService.cancelarSimulacion(id);

        if (cancelado)
        {
            return ResponseEntity.ok().body(Map.of(
                    "mensaje", "Simulación cancelada exitosamente",
                    "idSimulacion", id));
        }
        else
        {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Simulación no encontrada o no está en ejecución",
                    "idSimulacion", id));
        }
    }

}
