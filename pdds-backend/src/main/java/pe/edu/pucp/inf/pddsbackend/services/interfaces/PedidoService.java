package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import io.micrometer.common.lang.Nullable;
import org.springframework.data.history.Revision;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.io.InputStream;
import java.util.List;

public interface PedidoService {

    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto);

    PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto);

    @Transactional
    int destruirTodosPedidos();

    List<Revision<Integer, Pedido>> listarRevisionesPedidosPorIdPedido(Long idPedido);

    List<PedidoRevisionDto> getAllRevisions(Long pedidoId);

    PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber);

    // Método principal para PedidoServiceImpl
    ProcessResult processOrders(InputStream inputStream, int month, int year);

//    public List<PedidoListadoDTO> insertarPedidosDesdeArchivoCsv(/*algo*/)
    List<PedidoListadoDTO> listarPedidos(); //

    PedidoListadoDTO obtenerPedidoPorId(Long idPedido); //

    void eliminarPedido(Long idPedido); //
    List<Pedido> cargarPedidosMasivos(List<PedidoCargaMasivaDTO> pedidosDTO);
    List<PedidoCargaMasivaDTO> leerPedidosDesdeExcel(MultipartFile file);

}
