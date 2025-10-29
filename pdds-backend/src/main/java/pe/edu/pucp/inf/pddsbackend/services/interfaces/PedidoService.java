package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.history.Revision;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoCargaMasivaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;

import java.io.InputStream;
import java.util.List;

public interface PedidoService {

    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto);

    PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto);

    @Transactional
    int destruirTodosPedidos();

    List<Revision<Integer, PedidoEntidad>> listarRevisionesPedidosPorIdPedido(Long idPedido);

    List<PedidoRevisionDto> getAllRevisions(Long pedidoId);

    PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber);

    // Método principal para PedidoServiceImpl
    ProcessResult processOrders(InputStream inputStream, int month, int year);

//    public List<PedidoListadoDTO> insertarPedidosDesdeArchivoCsv(/*algo*/)
    List<PedidoListadoDTO> listarPedidos(); //

    PedidoListadoDTO obtenerPedidoPorId(Long idPedido); //

    void eliminarPedido(Long idPedido); //
    List<PedidoListadoDTO> listarPedidosPorDestino(String codigoDestino);

    List<PedidoListadoDTO> cargarPedidosMasivos(List<PedidoCargaMasivaDTO> pedidosDTO);
    List<PedidoCargaMasivaDTO> leerPedidosDesdeExcel(MultipartFile file);
    List<PedidoCargaMasivaDTO> leerPedidosDesdeArchivo(MultipartFile file); // detecta tipo
    List<PedidoListadoDTO> cargarPedidosDesdeArchivo(MultipartFile file); // lee + guarda y devuelve DTOs
}
