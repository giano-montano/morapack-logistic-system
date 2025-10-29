package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para eventos de salida de vuelo.
 * Contiene información sobre qué productos se cargan en qué vuelo.
 */
public class EventoVueloSalidaDTO extends EventoSimulacionBaseDTO {
    
    private String idVuelo;
    private String codigoVuelo;
    private Long idAlmacenOrigen;
    private String nombreAlmacenOrigen;
    private Long idAlmacenDestino;
    private String nombreAlmacenDestino;
    private int capacidadVuelo;
    private int capacidadOcupada;
    private List<String> productosUUIDs;
    
    public EventoVueloSalidaDTO() {
        super();
    }
    
    public EventoVueloSalidaDTO(String idSimulacion, LocalDateTime horaSimulacion,
                                String idVuelo, String codigoVuelo,
                                Long idAlmacenOrigen, String nombreAlmacenOrigen,
                                Long idAlmacenDestino, String nombreAlmacenDestino,
                                int capacidadVuelo, int capacidadOcupada,
                                List<String> productosUUIDs) {
        super(idSimulacion, horaSimulacion);
        this.idVuelo = idVuelo;
        this.codigoVuelo = codigoVuelo;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.nombreAlmacenOrigen = nombreAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.nombreAlmacenDestino = nombreAlmacenDestino;
        this.capacidadVuelo = capacidadVuelo;
        this.capacidadOcupada = capacidadOcupada;
        this.productosUUIDs = productosUUIDs;
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

    public Long getIdAlmacenOrigen() {
        return idAlmacenOrigen;
    }

    public void setIdAlmacenOrigen(Long idAlmacenOrigen) {
        this.idAlmacenOrigen = idAlmacenOrigen;
    }

    public String getNombreAlmacenOrigen() {
        return nombreAlmacenOrigen;
    }

    public void setNombreAlmacenOrigen(String nombreAlmacenOrigen) {
        this.nombreAlmacenOrigen = nombreAlmacenOrigen;
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

    public int getCapacidadVuelo() {
        return capacidadVuelo;
    }

    public void setCapacidadVuelo(int capacidadVuelo) {
        this.capacidadVuelo = capacidadVuelo;
    }

    public int getCapacidadOcupada() {
        return capacidadOcupada;
    }

    public void setCapacidadOcupada(int capacidadOcupada) {
        this.capacidadOcupada = capacidadOcupada;
    }

    public List<String> getProductosUUIDs() {
        return productosUUIDs;
    }

    public void setProductosUUIDs(List<String> productosUUIDs) {
        this.productosUUIDs = productosUUIDs;
    }
}
