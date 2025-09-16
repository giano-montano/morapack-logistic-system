package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.history.Revision;
import pe.edu.pucp.inf.pddsbackend.dto.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.util.List;

public interface PedidoService {

    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto);

    PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto);

    List<Revision<Integer, Pedido>> listarRevisionesPedidosPorIdPedido(Long idPedido);

    List<PedidoRevisionDto> getAllRevisions(Long pedidoId);

    PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber);

//    public List<PedidoListadoDTO> insertarPedidosDesdeArchivoCsv(/*algo*/)
}
