package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;

import java.util.Collection;

public record EstadoSimulacionCompletoDTO(
        Collection<PedidoListadoDTO> pedidos,
        Collection<VueloDTO> vuelos,
        Collection<AlmacenDTO> almacenes,
        Collection<RutaProgramadaListadaDTO> rutas // <- realmente necesario?
) {
}
