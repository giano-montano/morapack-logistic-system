package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para enviar mensajes de log de eventos de simulación.
 * Incluye el mensaje descriptivo y el timestamp del evento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogDTO {
    private String message;   // Mensaje descriptivo del evento (ej: "El vuelo V-123 salió desde Lima a México con 25 productos")
    private Instant timestamp; // Momento en que ocurrió el evento en la simulación
}
