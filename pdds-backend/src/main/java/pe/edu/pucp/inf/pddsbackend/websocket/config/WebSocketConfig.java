package pe.edu.pucp.inf.pddsbackend.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración de WebSocket para la simulación en tiempo real.
 * 
 * Endpoints disponibles:
 * - /ws-simulacion: Conexión WebSocket principal
 * 
 * Topics de suscripción:
 * - /topic/simulacion/{idSimulacion}: Eventos de una simulación específica
 * - /topic/simulacion/{idSimulacion}/estado: Estado general de la simulación
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilitar broker de mensajes en memoria
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefijo para mensajes desde el cliente
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint principal para WebSocket
        registry.addEndpoint("/ws-simulacion")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "*") // Para desarrollo
                .withSockJS(); // Fallback a SockJS si WebSocket no está disponible
    }
}
