package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO simple para notificar que un vuelo salió. Incluye información de
 * capacidad para que el frontend pueda colorear el ícono.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalidaVueloDTO
{
    private Long id; // ID del vuelo que salió
    private Integer cantidadProdsQueLleva; // Productos que lleva actualmente
    private Integer capacidadMaxima; // Capacidad máxima del vuelo
}
