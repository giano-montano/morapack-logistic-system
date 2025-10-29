package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;

/**
 * Clase base para todos los eventos de simulación enviados por WebSocket.
 * Usa polimorfismo JSON para que el frontend pueda identificar el tipo de evento.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "tipoEvento"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = EventoVueloSalidaDTO.class, name = "VUELO_SALIDA"),
    @JsonSubTypes.Type(value = EventoVueloLlegadaDTO.class, name = "VUELO_LLEGADA"),
    @JsonSubTypes.Type(value = EventoEntregaPedidoDTO.class, name = "ENTREGA_PEDIDO"),
    @JsonSubTypes.Type(value = EventoPlanificacionDTO.class, name = "PLANIFICACION"),
    @JsonSubTypes.Type(value = EventoEstadoSimulacionDTO.class, name = "ESTADO_SIMULACION")
})
public abstract class EventoSimulacionBaseDTO {
    
    private String idSimulacion;
    private LocalDateTime horaSimulacion;
    private LocalDateTime timestampReal;
    
    public EventoSimulacionBaseDTO() {
        this.timestampReal = LocalDateTime.now();
    }
    
    public EventoSimulacionBaseDTO(String idSimulacion, LocalDateTime horaSimulacion) {
        this.idSimulacion = idSimulacion;
        this.horaSimulacion = horaSimulacion;
        this.timestampReal = LocalDateTime.now();
    }

    // Getters y Setters
    public String getIdSimulacion() {
        return idSimulacion;
    }

    public void setIdSimulacion(String idSimulacion) {
        this.idSimulacion = idSimulacion;
    }

    public LocalDateTime getHoraSimulacion() {
        return horaSimulacion;
    }

    public void setHoraSimulacion(LocalDateTime horaSimulacion) {
        this.horaSimulacion = horaSimulacion;
    }

    public LocalDateTime getTimestampReal() {
        return timestampReal;
    }

    public void setTimestampReal(LocalDateTime timestampReal) {
        this.timestampReal = timestampReal;
    }
}
