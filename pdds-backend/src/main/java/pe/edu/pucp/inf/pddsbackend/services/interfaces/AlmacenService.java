package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;

import java.io.InputStream;
import java.util.List;

public interface AlmacenService
{
    /** Procesa el archivo masivo del profesor y persiste almacenes. */
    ProcessResult cargarAlmacenesEnBDDesdeArchivoDelProfe(InputStream inputStream);

    // CRUD
    AlmacenDTO crear(AlmacenCreateUpdateDTO dto);

    AlmacenDTO actualizar(Long id, AlmacenCreateUpdateDTO dto);

    AlmacenDTO obtener(Long id);

    Page<AlmacenDTO> listar(String q, Pageable pageable);

    @Transactional(readOnly = true)
    Page<AlmacenDTO> listarSimulados(String q, Pageable pageable) throws ExcepcionLogica;

    List<AlmacenDTO> obtenerTodos(); // Sin paginación - para simulación

    void eliminar(Long id); // soft delete

    AlmacenCardDTO devolverCardAlmacen(Long id);
}
