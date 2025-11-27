package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para enviar mensajes de error durante la simulación.
 * Se usa para notificar al frontend sobre problemas como timeouts,
 * errores de planificación, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDTO
{
    private String tipoError; // Tipo de error (ej: "TIMEOUT_PLANIFICACION", "ERROR_ALGORITMO")
    private String mensaje; // Mensaje descriptivo del error
    private Instant timestamp; // Momento en que ocurrió el error
    private String accionTomada; // Acción tomada (ej: "Planificación pausada automáticamente")
}
