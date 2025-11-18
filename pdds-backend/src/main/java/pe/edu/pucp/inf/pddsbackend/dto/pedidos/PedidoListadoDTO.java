package pe.edu.pucp.inf.pddsbackend.dto.pedidos;

import lombok.Builder;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Builder
public record PedidoListadoDTO(
        Long id,
        String nombreCliente,
        String nombreAlmacenDestino,
        Integer cantProductosTotales,       // = cantidadProductosPedidos
        Integer cantProductosEntregados,    // de la entidad (inicializado a 0 en BD)
        Integer cantProductosAtendidos,     // placeholder para el front (0)
        Integer cantProductosProgramados,   // placeholder para el front (0)
        String estado,                      // placeholder para el front ("-")
        String instanteRegistro,            // ISO local string (desde Instant)
        String instanteMaximoParaEntregar,  // ISO local string (desde Instant)
        Boolean esIntercontinental          // de la entidad
) {

    public static PedidoListadoDTO fromEntity(PedidoEntidad pedido) {
        String nombreAlmacen =
                pedido.getAlmacenDestino() != null
                        ? // usa el código que expongas en la UI (ciudad o aeropuerto)
                        firstNonNull(
                                pedido.getAlmacenDestino().getCodigoCiudadEn4Letras(),
                                pedido.getAlmacenDestino().getCodigoAeropuertoEn4Letras()
                        )
                        : null;

        String nombreCliente =
                pedido.getCliente() != null ? pedido.getCliente().getNombre() : null;

        Integer total       = nz(pedido.getCantidadProductosPedidos());
        Integer entregados  = nz(pedido.getCantidadProductosEntregados());

        return PedidoListadoDTO.builder()
                .id(pedido.getId())
                .nombreCliente(nombreCliente)
                .nombreAlmacenDestino(nombreAlmacen)
                .cantProductosTotales(total)
                .cantProductosEntregados(entregados)
                // placeholders para que tu tabla Angular siempre tenga números
                .cantProductosAtendidos(0)
                .cantProductosProgramados(0)
                .estado("-")
                .instanteRegistro(toIsoLocal(pedido.getInstanteRegistro()))
                .instanteMaximoParaEntregar(toIsoLocal(pedido.getInstanteMaximoParaEntregar()))
                .esIntercontinental(Boolean.TRUE.equals(pedido.getEsIntercontinental()))
                .build();
    }

    // ==== helpers ====

    private static Integer nz(Integer v) { return v != null ? v : 0; }

    private static String toIsoLocal(Instant instant) {
        if (instant == null) return null;
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private static String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}