package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;

import java.io.InputStream;
import java.time.LocalDate;

public interface VueloService {
    ProcessResult procesarArchivoPlanesVueloDelProfe(InputStream inputStream);
    @Transactional ProcessResult createConcreteFlights(LocalDate startDate, int days, boolean skipIfExists);

    // CRUD
    VueloDTO crear(VueloCreateUpdateDTO dto);
    VueloDTO actualizar(Long id, VueloCreateUpdateDTO dto);
    VueloDTO obtener(Long id);
    Page<VueloDTO> listar(String q, Pageable pageable);
    java.util.List<VueloDTO> obtenerTodos(); // ✅ NUEVO: Para simulación
    void eliminar(Long id); // soft

    VueloCardDTO devolverCard(Long id);
}
