package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO para enviar el estado general de la simulación.
 * Se puede usar para actualizaciones periódicas del estado global.
 */
public class EventoEstadoSimulacionDTO extends EventoSimulacionBaseDTO {
    
    private int totalVuelosActivos;
    private int totalPedidosPendientes;
    private int totalPedidosEntregados;
    private Map<Long, Integer> productosEnAlmacenes; // idAlmacen -> cantidad productos
    private double porcentajeCompletado; // 0.0 a 100.0
    
    public EventoEstadoSimulacionDTO() {
        super();
    }
    
    public EventoEstadoSimulacionDTO(String idSimulacion, LocalDateTime horaSimulacion,
                                     int totalVuelosActivos, int totalPedidosPendientes,
                                     int totalPedidosEntregados,
                                     Map<Long, Integer> productosEnAlmacenes,
                                     double porcentajeCompletado) {
        super(idSimulacion, horaSimulacion);
        this.totalVuelosActivos = totalVuelosActivos;
        this.totalPedidosPendientes = totalPedidosPendientes;
        this.totalPedidosEntregados = totalPedidosEntregados;
        this.productosEnAlmacenes = productosEnAlmacenes;
        this.porcentajeCompletado = porcentajeCompletado;
    }

    // Getters y Setters
    public int getTotalVuelosActivos() {
        return totalVuelosActivos;
    }

    public void setTotalVuelosActivos(int totalVuelosActivos) {
        this.totalVuelosActivos = totalVuelosActivos;
    }

    public int getTotalPedidosPendientes() {
        return totalPedidosPendientes;
    }

    public void setTotalPedidosPendientes(int totalPedidosPendientes) {
        this.totalPedidosPendientes = totalPedidosPendientes;
    }

    public int getTotalPedidosEntregados() {
        return totalPedidosEntregados;
    }

    public void setTotalPedidosEntregados(int totalPedidosEntregados) {
        this.totalPedidosEntregados = totalPedidosEntregados;
    }

    public Map<Long, Integer> getProductosEnAlmacenes() {
        return productosEnAlmacenes;
    }

    public void setProductosEnAlmacenes(Map<Long, Integer> productosEnAlmacenes) {
        this.productosEnAlmacenes = productosEnAlmacenes;
    }

    public double getPorcentajeCompletado() {
        return porcentajeCompletado;
    }

    public void setPorcentajeCompletado(double porcentajeCompletado) {
        this.porcentajeCompletado = porcentajeCompletado;
    }
}
