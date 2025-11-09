package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RutaProgramadaResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.util.LinkedList;
import java.util.List;

public interface ProgramacionService {
    List<RutaProgramadaResumenDTO> obtenerRutasProgramadasResumenSegunPedido(PedidoEntidad pedido);

    RutaProgramadaCardDTO devolverCardDeRutaProgramada(LinkedList<Long> idsVueloRuta);

    Page<RutaProgramadaListadaDTO> listarRutasProgramadas(int page, int size, String sortBy, boolean sortDir);
}
