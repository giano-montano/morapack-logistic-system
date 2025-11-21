package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para sincronizar el reloj del frontend con el backend al inicio de la simulación.
 * Permite que el frontend calcule la hora simulada actual usando la misma fórmula
 * que el RelojEnganado del backend.
 *
 * Fórmula de sincronización:
 * horaSimulada = horaSimuladaInicio + (tiempoRealTranscurrido * factorVelocidad)
 *
 * Donde:
 * - tiempoRealTranscurrido = Instant.now() - horaRealArranque
 *
 * Este DTO se envía una sola vez al inicio de la simulación para que el frontend
 * pueda mantener su propio reloj sincronizado sin depender de eventos constantes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SincronizacionSimulacionDTO
{
    /**
     * ID de la simulación en ejecución
     */
    private Long idSimulacion;

    /**
     * Momento en tiempo real cuando arrancó la simulación (System.currentTimeMillis)
     * Usado como referencia para calcular el tiempo transcurrido
     */
    private Instant horaRealArranque;

    /**
     * Hora inicial del mundo simulado (ej: 2025-01-15T08:00:00Z)
     * Punto de partida para el cálculo de la hora simulada
     */
    private Instant horaSimuladaInicio;

    /**
     * Factor de velocidad de la simulación (ej: 800.0 significa 800x más rápido)
     * 1.0 = tiempo real, 60.0 = 1 minuto real = 1 hora simulada
     */
    private Double factorVelocidad;

    /**
     * Minutos reales entre ejecuciones de planificación
     */
    private Long minutosEntrePlanificaciones;
}
