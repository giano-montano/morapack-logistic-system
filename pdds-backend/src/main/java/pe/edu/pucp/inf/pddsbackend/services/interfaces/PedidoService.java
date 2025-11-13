package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.history.Revision;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.*;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;

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

    Integer cargarPedidosMasivos(List<PedidoCargaMasivaDTO> pedidosDTO);
    List<PedidoCargaMasivaDTO> leerPedidosDesdeExcel(MultipartFile file);
    List<PedidoCargaMasivaDTO> leerPedidosDesdeArchivo(MultipartFile file); // detecta tipo
    Integer cargarPedidosDesdeArchivo(MultipartFile file); // lee + guarda y NO devuelve DTOs, solo la cuenta

    List<PedidoResumenDTO> obtenerResumenPedidosParaAlmacen(AlmacenEntidad almacen);

    List<PedidoResumenDTO> obtenerResumenPedidosEnVuelo(VueloEntidad vuelo);

    @Transactional(readOnly = true)
    PedidoCardDTO devolverCard(Long id);
}
