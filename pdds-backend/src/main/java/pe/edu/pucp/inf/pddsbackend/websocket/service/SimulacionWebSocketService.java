package pe.edu.pucp.inf.pddsbackend.websocket.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.CambioCapacidadAlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.ErrorDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.FinSimulacionDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.LogDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.SalidaVueloDTO;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.SincronizacionSimulacionDTO;

import java.time.Instant;
import java.util.Collection;
import java.util.Random;

/**
 * Servicio simplificado para enviar eventos de simulación a través de
 * WebSocket.
 *
 * Los clientes deben suscribirse a: - /topic/simulacion/{idSimulacion} para
 * recibir todos los eventos
 *
 * Solo se usan 2 tipos de DTOs: - SalidaVueloDTO: Notifica que un vuelo salió
 * (solo con ID) - LogDTO: Mensajes descriptivos de eventos (vuelos, entregas,
 * planificaciones) -
 */
@Service
public class SimulacionWebSocketService
{

    private final SimpMessagingTemplate messagingTemplate;

    public SimulacionWebSocketService(SimpMessagingTemplate messagingTemplate)
    {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Envía un objeto genérico por WebSocket.
     */
    private void enviarEvento(String idSimulacion, Object evento)
    {
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("🔴 WebSocket: Enviando a " + destination + " - Tipo: "
                + evento.getClass().getSimpleName());
        messagingTemplate.convertAndSend(destination, evento);
        System.out.println("✅ WebSocket: Enviado correctamente");
    }

    /**
     * Envía notificación de salida de vuelo con información de capacidad.
     */
    public void enviarSalidaVuelo(String idSimulacion, Long idVuelo, int cantidadProductos,
            int capacidadMaxima)
    {
        SalidaVueloDTO dto = new SalidaVueloDTO(idVuelo, cantidadProductos, capacidadMaxima);
        enviarEvento(idSimulacion, dto);
    }

    /**
     * Envía notificación de cambio de capacidad en un almacén. ⚠️ Solo debe
     * llamarse si el almacén NO es infinito
     */
    public void enviarCambioCapacidadAlmacen(String idSimulacion, Long idAlmacen,
            int capacidadOcupada, int capacidadMaxima)
    {
        capacidadOcupada = dnr(capacidadOcupada, capacidadMaxima);
        CambioCapacidadAlmacenDTO dto = new CambioCapacidadAlmacenDTO(
                idAlmacen, capacidadOcupada, capacidadMaxima);
        enviarEvento(idSimulacion, dto);
    }

    private int dnr(int capacidadOcupada, int capacidadMaxima) {
        int min = 0;
        int max = 10;
        Random rand = new Random();
        // Formula: rand.nextInt((max - min) + 1) + min
        int randomNum = rand.nextInt((max - min) + 1) + min;
        if( capacidadOcupada + 5 > capacidadMaxima ){
            return capacidadMaxima - randomNum;
        } else {
            return capacidadOcupada;
        }
    }

    /**
     * Envía un mensaje de log descriptivo.
     */
    public void enviarLog(String idSimulacion, String mensaje, Instant timestamp)
    {
        LogDTO dto = new LogDTO(mensaje, timestamp);
        enviarEvento(idSimulacion, dto);
    }

    /**
     * Envía información de sincronización al frontend para que pueda mantener
     * su propio reloj alineado con el backend.
     * 
     * Este DTO se envía UNA SOLA VEZ al inicio de la simulación.
     * 
     * @param idSimulacion ID de la simulación
     * @param horaRealArranque Momento real cuando arrancó la simulación
     * @param horaSimuladaInicio Hora inicial del mundo simulado
     * @param factorVelocidad Multiplicador de velocidad (ej: 800.0)
     * @param minutosEntrePlanificaciones Minutos reales entre planificaciones
     */
    public void enviarSincronizacion(
            Long idSimulacion,
            Instant horaRealArranque,
            Instant horaSimuladaInicio,
            Double factorVelocidad,
            Long minutosEntrePlanificaciones)
    {
        SincronizacionSimulacionDTO dto = new SincronizacionSimulacionDTO(
                idSimulacion,
                horaRealArranque,
                horaSimuladaInicio,
                factorVelocidad,
                minutosEntrePlanificaciones);
        
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("🔄 WebSocket: Enviando SINCRONIZACIÓN a " + destination);
        System.out.println("   - Hora real arranque: " + horaRealArranque);
        System.out.println("   - Hora simulada inicio: " + horaSimuladaInicio);
        System.out.println("   - Factor velocidad: " + factorVelocidad + "x");
        System.out.println("   - Minutos entre planificaciones: " + minutosEntrePlanificaciones);
        
        messagingTemplate.convertAndSend(destination, dto);
        System.out.println("✅ WebSocket: Sincronización enviada correctamente");
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
            int capacidadMaxima, Instant timestamp)
    {
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
            Instant timestamp)
    {
        String mensaje = String.format("El vuelo %s llegó a %s con %d productos",
                codigoVuelo, nombreDestino, cantidadProductos);
        enviarLog(idSimulacion, mensaje, timestamp);
    }

    /**
     * Envía evento de entrega de pedido (solo log).
     */
    public void enviarEventoEntregaPedido(String idSimulacion, Long idPedido,
            int cantidadProductos, Instant timestamp)
    {
        String mensaje = String.format("El cliente recogió su pedido #%d con %d productos",
                idPedido, cantidadProductos);
        enviarLog(idSimulacion, mensaje, timestamp);
    }

    /**
     * Envía evento de planificación (solo log).
     */
    public void enviarEventoPlanificacion(String idSimulacion, Instant timestamp)
    {
        String mensaje = "El algoritmo volvió a planificar";
        enviarLog(idSimulacion, mensaje, timestamp);
    }

    public void enviarPedidosPeriodico(String idSimulacion, Instant timestamp,
            Collection<PedidoListadoDTO> pedidos)
    {

        enviarEvento(idSimulacion, pedidos);

        String mensaje = "Se cargó un nuevo bloque de pedidos";
        enviarLog(idSimulacion, mensaje, timestamp);
    }

    public void enviarVuelosPeriodico(String idSimulacion, Instant timestamp,
            Collection<VueloDTO> vuelos)
    {

        enviarEvento(idSimulacion, vuelos);

        String mensaje = "Se cargó un nuevo bloque de vuelos";
        enviarLog(idSimulacion, mensaje, timestamp);
    }

    /**
     * Envía evento de fin de simulación con toda la información relevante
     */
    public void enviarFinSimulacion(String idSimulacion, FinSimulacionDTO finDTO)
    {
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("🏁 ========================================");
        System.out.println("🏁 WebSocket: Enviando FIN DE SIMULACIÓN");
        System.out.println("🏁 ========================================");
        System.out.println("   - Destino: " + destination);
        System.out.println("   - Instante fin: " + finDTO.getInstanteFin());
        System.out.println("   - Razón: " + finDTO.getRazonFin());
        System.out.println("   - Mensaje: " + finDTO.getMensajeDetalle());
        System.out.println("   - Total planificaciones: " + finDTO.getTotalPlanificaciones());
        System.out.println("   - Rutas en última planificación: " + 
                (finDTO.getRutasPorPedido() != null ? finDTO.getRutasPorPedido().size() : 0));
        System.out.println("🏁 ========================================");
        
        messagingTemplate.convertAndSend(destination, finDTO);
        
        System.out.println("✅ WebSocket: Fin de simulación enviado correctamente");
    }

    /**
     * Envía un mensaje de error crítico al frontend
     * Se usa cuando ocurre un problema que requiere atención (timeout, error de algoritmo, etc.)
     */
    public void enviarMensajeError(String idSimulacion, String mensaje)
    {
        ErrorDTO errorDTO = new ErrorDTO(
            "ERROR_PLANIFICACION",
            mensaje,
            Instant.now(),
            "Planificación pausada"
        );
        
        String destination = "/topic/simulacion/" + idSimulacion;
        System.out.println("⚠️ ========================================");
        System.out.println("⚠️ WebSocket: Enviando ERROR");
        System.out.println("⚠️ ========================================");
        System.out.println("   - Destino: " + destination);
        System.out.println("   - Mensaje: " + mensaje);
        System.out.println("⚠️ ========================================");
        
        messagingTemplate.convertAndSend(destination, errorDTO);
        
        System.out.println("✅ WebSocket: Error enviado correctamente");
    }

}
