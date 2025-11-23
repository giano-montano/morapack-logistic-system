package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO que contiene toda la información sobre el fin de una simulación
 * Incluye la razón del fin y la última planificación exitosa
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinSimulacionDTO
{
    /**
     * Instante en que finalizó o colapsó la simulación
     */
    private Instant instanteFin;

    /**
     * Razón por la cual finalizó la simulación
     */
    private RazonFinSimulacion razonFin;

    /**
     * Mensaje descriptivo del motivo del fin (especialmente útil para colapsos)
     */
    private String mensajeDetalle;

    /**
     * Instante de la última planificación exitosa
     */
    private Instant instanteUltimaPlanificacion;

    /**
     * Estructura de rutas agrupadas por pedido de la última planificación exitosa
     * Puede ser null si nunca hubo planificación exitosa
     */
    private List<RutaPorPedidoDTO> rutasPorPedido;

    /**
     * Total de planificaciones realizadas durante la simulación
     */
    private Integer totalPlanificaciones;

    /**
     * ¿La simulación completó todos los pedidos? (solo true si terminó por tiempo)
     */
    private Boolean pedidosCompletados;
}
