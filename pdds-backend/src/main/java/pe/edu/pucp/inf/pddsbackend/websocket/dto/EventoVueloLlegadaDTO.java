package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para eventos de llegada de vuelo.
 * Contiene información sobre qué productos se descargan en qué almacén.
 */
public class EventoVueloLlegadaDTO extends EventoSimulacionBaseDTO {
    
    private String idVuelo;
    private String codigoVuelo;
    private Long idAlmacenDestino;
    private String nombreAlmacenDestino;
    private int cantidadDescargada;
    private List<String> productosDescargados;
    private int entregasInmediatas; // Productos que van directo al cliente
    private int productosEnTransito; // Productos que esperan siguiente vuelo
    
    public EventoVueloLlegadaDTO() {
        super();
    }
    
    public EventoVueloLlegadaDTO(String idSimulacion, LocalDateTime horaSimulacion,
                                 String idVuelo, String codigoVuelo,
                                 Long idAlmacenDestino, String nombreAlmacenDestino,
                                 int cantidadDescargada, List<String> productosDescargados,
                                 int entregasInmediatas, int productosEnTransito) {
        super(idSimulacion, horaSimulacion);
        this.idVuelo = idVuelo;
        this.codigoVuelo = codigoVuelo;
        this.idAlmacenDestino = idAlmacenDestino;
        this.nombreAlmacenDestino = nombreAlmacenDestino;
        this.cantidadDescargada = cantidadDescargada;
        this.productosDescargados = productosDescargados;
        this.entregasInmediatas = entregasInmediatas;
        this.productosEnTransito = productosEnTransito;
    }

    // Getters y Setters
    public String getIdVuelo() {
        return idVuelo;
    }

    public void setIdVuelo(String idVuelo) {
        this.idVuelo = idVuelo;
    }

    public String getCodigoVuelo() {
        return codigoVuelo;
    }

    public void setCodigoVuelo(String codigoVuelo) {
        this.codigoVuelo = codigoVuelo;
    }

    public Long getIdAlmacenDestino() {
        return idAlmacenDestino;
    }

    public void setIdAlmacenDestino(Long idAlmacenDestino) {
        this.idAlmacenDestino = idAlmacenDestino;
    }

    public String getNombreAlmacenDestino() {
        return nombreAlmacenDestino;
    }

    public void setNombreAlmacenDestino(String nombreAlmacenDestino) {
        this.nombreAlmacenDestino = nombreAlmacenDestino;
    }

    public int getCantidadDescargada() {
        return cantidadDescargada;
    }

    public void setCantidadDescargada(int cantidadDescargada) {
        this.cantidadDescargada = cantidadDescargada;
    }

    public List<String> getProductosDescargados() {
        return productosDescargados;
    }

    public void setProductosDescargados(List<String> productosDescargados) {
        this.productosDescargados = productosDescargados;
    }

    public int getEntregasInmediatas() {
        return entregasInmediatas;
    }

    public void setEntregasInmediatas(int entregasInmediatas) {
        this.entregasInmediatas = entregasInmediatas;
    }

    public int getProductosEnTransito() {
        return productosEnTransito;
    }

    public void setProductosEnTransito(int productosEnTransito) {
        this.productosEnTransito = productosEnTransito;
    }
}
