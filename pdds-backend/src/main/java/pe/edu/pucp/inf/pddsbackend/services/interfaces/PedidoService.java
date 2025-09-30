package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import io.micrometer.common.lang.Nullable;
import org.springframework.data.history.Revision;
import pe.edu.pucp.inf.pddsbackend.dto.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.dto.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.io.InputStream;
import java.util.List;

public interface PedidoService {

    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto);

    PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto);

    List<Revision<Integer, Pedido>> listarRevisionesPedidosPorIdPedido(Long idPedido);

    List<PedidoRevisionDto> getAllRevisions(Long pedidoId);

    PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber);

    // Método principal para PedidoServiceImpl
    ProcessResult processOrders(InputStream inputStream, int month, int year);

//    public List<PedidoListadoDTO> insertarPedidosDesdeArchivoCsv(/*algo*/)
}
