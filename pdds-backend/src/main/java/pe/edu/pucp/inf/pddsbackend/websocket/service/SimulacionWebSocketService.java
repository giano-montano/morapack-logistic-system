package pe.edu.pucp.inf.pddsbackend.websocket.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.CambioCapacidadAlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.LogDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.SalidaVueloDTO;

import java.time.Instant;

/**
 * Servicio simplificado para enviar eventos de simulación a través de WebSocket.
 * 
 * Los clientes deben suscribirse a:
 * - /topic/simulacion/{idSimulacion} para recibir todos los eventos
 * 
 * Solo se usan 2 tipos de DTOs:
 * - SalidaVueloDTO: Notifica que un vuelo salió (solo con ID)
 * - LogDTO: Mensajes descriptivos de eventos (vuelos, entregas, planificaciones)
 */
@Service
public class SimulacionWebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public SimulacionWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Envía un objeto genérico por WebSocket.
     */
    private void enviarEvento(String idSimulacion, Object evento) {
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("🔴 WebSocket: Enviando a " + destination + " - Tipo: " + evento.getClass().getSimpleName());
        messagingTemplate.convertAndSend(destination, evento);
        System.out.println("✅ WebSocket: Enviado correctamente");
    }
    
    /**
     * Envía notificación de salida de vuelo con información de capacidad.
     */
    public void enviarSalidaVuelo(String idSimulacion, Long idVuelo, int cantidadProductos, int capacidadMaxima) {
        SalidaVueloDTO dto = new SalidaVueloDTO(idVuelo, cantidadProductos, capacidadMaxima);
        enviarEvento(idSimulacion, dto);
    }
    
    /**
     * Envía notificación de cambio de capacidad en un almacén.
     * ⚠️ Solo debe llamarse si el almacén NO es infinito
     */
    public void enviarCambioCapacidadAlmacen(String idSimulacion, Long idAlmacen, 
                                             int capacidadOcupada, int capacidadMaxima) {
        CambioCapacidadAlmacenDTO dto = new CambioCapacidadAlmacenDTO(
            idAlmacen, capacidadOcupada, capacidadMaxima
        );
        enviarEvento(idSimulacion, dto);
    }
    
    /**
     * Envía un mensaje de log descriptivo.
     */
    public void enviarLog(String idSimulacion, String mensaje, Instant timestamp) {
        LogDTO dto = new LogDTO(mensaje, timestamp);
        enviarEvento(idSimulacion, dto);
    }
    
    // ============================================
    // MÉTODOS DE CONVENIENCIA PARA EVENTOS ESPECÍFICOS
    // ============================================
    
    /**
     * Envía evento de salida de vuelo (notificación + log).
     */
    public void enviarEventoVueloSalida(String idSimulacion, Long idVuelo, 
                                        String codigoVuelo, String nombreOrigen, 
                                        String nombreDestino, int cantidadProductos,
                                        int capacidadMaxima, Instant timestamp) {
        // 1. Notificar que el vuelo salió con info de capacidad
        enviarSalidaVuelo(idSimulacion, idVuelo, cantidadProductos, capacidadMaxima);
        
        // 2. Enviar log descriptivo
        String mensaje = String.format("El vuelo %s salió desde %s hacia %s con %d/%d productos",
                codigoVuelo, nombreOrigen, nombreDestino, cantidadProductos, capacidadMaxima);
        enviarLog(idSimulacion, mensaje, timestamp);
    }
    
    /**
     * Envía evento de llegada de vuelo (solo log).
     */
    public void enviarEventoVueloLlegada(String idSimulacion, String codigoVuelo,
                                         String nombreDestino, int cantidadProductos,
                                         Instant timestamp) {
        String mensaje = String.format("El vuelo %s llegó a %s con %d productos",
                codigoVuelo, nombreDestino, cantidadProductos);
        enviarLog(idSimulacion, mensaje, timestamp);
    }
    
    /**
     * Envía evento de entrega de pedido (solo log).
     */
    public void enviarEventoEntregaPedido(String idSimulacion, Long idPedido,
                                          int cantidadProductos, Instant timestamp) {
        String mensaje = String.format("El cliente recogió su pedido #%d con %d productos",
                idPedido, cantidadProductos);
        enviarLog(idSimulacion, mensaje, timestamp);
    }
    
    /**
     * Envía evento de planificación (solo log).
     */
    public void enviarEventoPlanificacion(String idSimulacion, Instant timestamp) {
        String mensaje = "El algoritmo volvió a planificar";
        enviarLog(idSimulacion, mensaje, timestamp);
    }
}
