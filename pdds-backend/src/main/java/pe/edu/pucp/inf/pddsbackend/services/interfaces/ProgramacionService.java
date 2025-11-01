package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RutaProgramadaResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaCardDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.util.LinkedList;
import java.util.List;

public interface ProgramacionService {
    List<RutaProgramadaResumenDTO> obtenerRutasProgramadasResumenSegunPedido(PedidoEntidad pedido);

    RutaProgramadaCardDTO devolverCardDeRutaProgramada(LinkedList<Long> idsVueloRuta);
}
