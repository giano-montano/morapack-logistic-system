package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.dto.ProcessResult;

import java.io.InputStream;

public interface AlmacenService {
    /**
     * Procesa el InputStream del archivo (streaming) y persiste los almacenes.
     * Devuelve número de registros procesados.
     */
    public ProcessResult cargarAlmacenesEnBDDesdeArchivoDelProfe(InputStream inputStream);

}
