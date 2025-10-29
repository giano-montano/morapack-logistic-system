package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import java.time.LocalDateTime;

/**
 * DTO para eventos de entrega de pedido al cliente.
 * Se dispara 2 horas después de que el producto llegue al almacén destino.
 */
public class EventoEntregaPedidoDTO extends EventoSimulacionBaseDTO {
    
    private Long idPedido;
    private String productoUUID;
    private Long idAlmacen;
    private String nombreAlmacen;
    private boolean exitoso;
    private String mensaje; // Mensaje de éxito o error
    
    public EventoEntregaPedidoDTO() {
        super();
    }
    
    public EventoEntregaPedidoDTO(String idSimulacion, LocalDateTime horaSimulacion,
                                  Long idPedido, String productoUUID,
                                  Long idAlmacen, String nombreAlmacen,
                                  boolean exitoso, String mensaje) {
        super(idSimulacion, horaSimulacion);
        this.idPedido = idPedido;
        this.productoUUID = productoUUID;
        this.idAlmacen = idAlmacen;
        this.nombreAlmacen = nombreAlmacen;
        this.exitoso = exitoso;
        this.mensaje = mensaje;
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

    public Long getIdAlmacen() {
        return idAlmacen;
    }

    public void setIdAlmacen(Long idAlmacen) {
        this.idAlmacen = idAlmacen;
    }

    public String getNombreAlmacen() {
        return nombreAlmacen;
    }

    public void setNombreAlmacen(String nombreAlmacen) {
        this.nombreAlmacen = nombreAlmacen;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public void setExitoso(boolean exitoso) {
        this.exitoso = exitoso;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
