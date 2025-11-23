package pe.edu.pucp.inf.pddsbackend.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO que representa una ruta programada agrupada por pedido
 * Incluye información de los vuelos que componen la ruta
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RutaPorPedidoDTO
{
    /**
     * ID del pedido que se atiende con esta ruta
     */
    private Long idPedido;

    /**
     * Cantidad total de productos a entregar en esta ruta
     */
    private Integer cantidadProductos;

    /**
     * Cantidad de productos ya entregados del pedido
     */
    private Integer cantidadEntregada;

    /**
     * Cantidad de productos programados en esta planificación
     */
    private Integer cantidadProgramada;

    /**
     * Almacén destino final (ciudad)
     */
    private String almacenDestinoFinal;

    /**
     * Fecha y hora planificada de llegada del último vuelo
     */
    private Instant fechaHoraLlegadaMax;

    /**
     * Número de vuelos que componen esta ruta
     */
    private Integer numVuelos;

    /**
     * Nombres de las ciudades en orden (incluye origen y destinos intermedios)
     */
    private List<String> nombresCiudades;

    /**
     * Códigos de los vuelos programados en orden
     */
    private List<String> codigosVuelos;

    /**
     * IDs de los vuelos que componen la ruta
     */
    private List<Long> idsVuelos;
}
