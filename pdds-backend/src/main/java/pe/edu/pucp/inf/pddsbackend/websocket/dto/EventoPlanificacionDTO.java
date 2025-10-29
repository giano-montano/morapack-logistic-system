package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para eventos de planificación.
 * Contiene información sobre el inicio y fin de ciclos de planificación.
 */
public class EventoPlanificacionDTO extends EventoSimulacionBaseDTO {
    
    private String fase; // "INICIO", "COMPLETADA", "TIMEOUT", "ERROR"
    private int pedidosPendientes;
    private int programacionesGeneradas;
    private Long duracionMs; // Duración del algoritmo en milisegundos
    private String mensajeError; // En caso de error
    private List<ProgramacionInfoDTO> programaciones; // Detalle de las programaciones
    
    public EventoPlanificacionDTO() {
        super();
    }
    
    public EventoPlanificacionDTO(String idSimulacion, LocalDateTime horaSimulacion,
                                  String fase, int pedidosPendientes) {
        super(idSimulacion, horaSimulacion);
        this.fase = fase;
        this.pedidosPendientes = pedidosPendientes;
    }

    // Clase interna para detalles de programaciones
    public static class ProgramacionInfoDTO {
        private Long idPedido;
        private String productoUUID;
        private List<String> rutaVuelos; // Lista de códigos de vuelos
        
        public ProgramacionInfoDTO() {}
        
        public ProgramacionInfoDTO(Long idPedido, String productoUUID, List<String> rutaVuelos) {
            this.idPedido = idPedido;
            this.productoUUID = productoUUID;
            this.rutaVuelos = rutaVuelos;
        }

        // Getters y Setters
        public Long getIdPedido() {
            return idPedido;
        }

        public void setIdPedido(Long idPedido) {
            this.idPedido = idPedido;
        }

        public String getProductoUUID() {
            return productoUUID;
        }

        public void setProductoUUID(String productoUUID) {
            this.productoUUID = productoUUID;
        }

        public List<String> getRutaVuelos() {
            return rutaVuelos;
        }

        public void setRutaVuelos(List<String> rutaVuelos) {
            this.rutaVuelos = rutaVuelos;
        }
    }

    // Getters y Setters
    public String getFase() {
        return fase;
    }

    public void setFase(String fase) {
        this.fase = fase;
    }

    public int getPedidosPendientes() {
        return pedidosPendientes;
    }

    public void setPedidosPendientes(int pedidosPendientes) {
        this.pedidosPendientes = pedidosPendientes;
    }

    public int getProgramacionesGeneradas() {
        return programacionesGeneradas;
    }

    public void setProgramacionesGeneradas(int programacionesGeneradas) {
        this.programacionesGeneradas = programacionesGeneradas;
    }

    public Long getDuracionMs() {
        return duracionMs;
    }

    public void setDuracionMs(Long duracionMs) {
        this.duracionMs = duracionMs;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }

    public List<ProgramacionInfoDTO> getProgramaciones() {
        return programaciones;
    }

    public void setProgramaciones(List<ProgramacionInfoDTO> programaciones) {
        this.programaciones = programaciones;
    }
}
