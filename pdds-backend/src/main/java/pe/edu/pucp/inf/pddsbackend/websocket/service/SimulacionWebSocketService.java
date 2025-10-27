package pe.edu.pucp.inf.pddsbackend.websocket.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Servicio para enviar eventos de simulación a través de WebSocket.
 * 
 * Los clientes deben suscribirse a:
 * - /topic/simulacion/{idSimulacion} para recibir todos los eventos
 * - /topic/simulacion/{idSimulacion}/estado para recibir solo actualizaciones de estado
 */
@Service
public class SimulacionWebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public SimulacionWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Envía un evento genérico de simulación.
     */
    private void enviarEvento(String idSimulacion, EventoSimulacionBaseDTO evento) {
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("🔴 WebSocket: Enviando a " + destination + " - Tipo: " + evento.getClass().getSimpleName());
        messagingTemplate.convertAndSend(destination, evento);
        System.out.println("✅ WebSocket: Enviado correctamente");
    }
    
    /**
     * Envía evento de salida de vuelo.
     */
    public void enviarEventoVueloSalida(String idSimulacion, LocalDateTime horaSimulacion,
                                        String idVuelo, String codigoVuelo,
                                        Long idAlmacenOrigen, String nombreAlmacenOrigen,
                                        Long idAlmacenDestino, String nombreAlmacenDestino,
                                        int capacidadVuelo, int capacidadOcupada,
                                        List<String> productosUUIDs) {
        EventoVueloSalidaDTO evento = new EventoVueloSalidaDTO(
            idSimulacion, horaSimulacion,
            idVuelo, codigoVuelo,
            idAlmacenOrigen, nombreAlmacenOrigen,
            idAlmacenDestino, nombreAlmacenDestino,
            capacidadVuelo, capacidadOcupada,
            productosUUIDs
        );
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de llegada de vuelo.
     */
    public void enviarEventoVueloLlegada(String idSimulacion, LocalDateTime horaSimulacion,
                                         String idVuelo, String codigoVuelo,
                                         Long idAlmacenDestino, String nombreAlmacenDestino,
                                         int cantidadDescargada, List<String> productosDescargados,
                                         int entregasInmediatas, int productosEnTransito) {
        EventoVueloLlegadaDTO evento = new EventoVueloLlegadaDTO(
            idSimulacion, horaSimulacion,
            idVuelo, codigoVuelo,
            idAlmacenDestino, nombreAlmacenDestino,
            cantidadDescargada, productosDescargados,
            entregasInmediatas, productosEnTransito
        );
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de entrega de pedido.
     */
    public void enviarEventoEntregaPedido(String idSimulacion, LocalDateTime horaSimulacion,
                                          Long idPedido, String productoUUID,
                                          Long idAlmacen, String nombreAlmacen,
                                          boolean exitoso, String mensaje) {
        EventoEntregaPedidoDTO evento = new EventoEntregaPedidoDTO(
            idSimulacion, horaSimulacion,
            idPedido, productoUUID,
            idAlmacen, nombreAlmacen,
            exitoso, mensaje
        );
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de inicio de planificación.
     */
    public void enviarEventoPlanificacionInicio(String idSimulacion, LocalDateTime horaSimulacion,
                                                int pedidosPendientes) {
        EventoPlanificacionDTO evento = new EventoPlanificacionDTO(
            idSimulacion, horaSimulacion,
            "INICIO", pedidosPendientes
        );
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de planificación completada.
     */
    public void enviarEventoPlanificacionCompletada(String idSimulacion, LocalDateTime horaSimulacion,
                                                    int pedidosPendientes, int programacionesGeneradas,
                                                    Long duracionMs,
                                                    List<EventoPlanificacionDTO.ProgramacionInfoDTO> programaciones) {
        EventoPlanificacionDTO evento = new EventoPlanificacionDTO(
            idSimulacion, horaSimulacion,
            "COMPLETADA", pedidosPendientes
        );
        evento.setProgramacionesGeneradas(programacionesGeneradas);
        evento.setDuracionMs(duracionMs);
        evento.setProgramaciones(programaciones);
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de planificación con timeout.
     */
    public void enviarEventoPlanificacionTimeout(String idSimulacion, LocalDateTime horaSimulacion,
                                                 int pedidosPendientes, Long duracionMs) {
        EventoPlanificacionDTO evento = new EventoPlanificacionDTO(
            idSimulacion, horaSimulacion,
            "TIMEOUT", pedidosPendientes
        );
        evento.setDuracionMs(duracionMs);
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía evento de error en planificación.
     */
    public void enviarEventoPlanificacionError(String idSimulacion, LocalDateTime horaSimulacion,
                                               int pedidosPendientes, String mensajeError) {
        EventoPlanificacionDTO evento = new EventoPlanificacionDTO(
            idSimulacion, horaSimulacion,
            "ERROR", pedidosPendientes
        );
        evento.setMensajeError(mensajeError);
        enviarEvento(idSimulacion, evento);
    }
    
    /**
     * Envía actualización del estado general de la simulación.
     */
    public void enviarEstadoSimulacion(String idSimulacion, LocalDateTime horaSimulacion,
                                       int totalVuelosActivos, int totalPedidosPendientes,
                                       int totalPedidosEntregados,
                                       Map<Long, Integer> productosEnAlmacenes,
                                       double porcentajeCompletado) {
        EventoEstadoSimulacionDTO evento = new EventoEstadoSimulacionDTO(
            idSimulacion, horaSimulacion,
            totalVuelosActivos, totalPedidosPendientes,
            totalPedidosEntregados, productosEnAlmacenes,
            porcentajeCompletado
        );
        
        // Enviar tanto al canal principal como al canal de estado
        enviarEvento(idSimulacion, evento);
        messagingTemplate.convertAndSend("/topic/simulacion/" + idSimulacion + "/estado", evento);
    }
}
