package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;

import java.io.InputStream;
import java.util.List;

public interface AlmacenService {
    /** Procesa el archivo masivo del profesor y persiste almacenes. */
    ProcessResult cargarAlmacenesEnBDDesdeArchivoDelProfe(InputStream inputStream);

    // CRUD
    AlmacenDTO crear(AlmacenCreateUpdateDTO dto);
    AlmacenDTO actualizar(Long id, AlmacenCreateUpdateDTO dto);
    AlmacenDTO obtener(Long id);
    Page<AlmacenDTO> listar(String q, Pageable pageable);
    List<AlmacenDTO> obtenerTodos(); // Sin paginación - para simulación
    void eliminar(Long id); // soft delete
}
