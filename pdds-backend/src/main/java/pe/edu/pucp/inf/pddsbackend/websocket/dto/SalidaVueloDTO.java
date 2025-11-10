package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO simple para notificar que un vuelo salió.
 * El frontend consultará los detalles del vuelo con otro endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalidaVueloDTO {
    private Long id; // ID del vuelo que salió
    private Integer cantidadProdsQueLleva;
}
