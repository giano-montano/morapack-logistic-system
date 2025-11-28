package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;
import pe.edu.pucp.inf.pddsbackend.services.implementations.VueloServiceImpl;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VueloService
{
    ProcessResult procesarArchivoPlanesVueloDelProfe(InputStream inputStream);

    @Transactional
    ProcessResult createConcreteFlights(LocalDate startDate, int days, boolean skipIfExists);

    VueloServiceImpl.GenerationResult generateFlightsInMemory(
            List<VueloProgramado> programados,
            Instant referenceDate,
            int days,
            boolean skipIfExists,
            Map<Long, AlmacenEntidad> almacenById,
            Set<String> existingKeys);

    // CRUD
    VueloDTO crear(VueloCreateUpdateDTO dto);

    VueloDTO actualizar(Long id, VueloCreateUpdateDTO dto);

    VueloDTO obtener(Long id);
    List<VueloDTO> buscarVuelosSimulados(String q) throws ExcepcionLogica;

    Page<VueloDTO> listar(String q, Pageable pageable);

    java.util.List<VueloDTO> obtenerTodos(); // ✅ NUEVO: Para simulación

    void eliminar(Long id); // soft

    VueloCardDTO devolverCard(Long id);

    Page<VueloDTO> listarVuelosSimulados(String q, Pageable pageable) throws ExcepcionLogica;

    ProcessResult procesarArchivoDeCancelados(MultipartFile file, LocalDate referenceDate, boolean paraMemoria)
            throws Exception;

    boolean agregarCanceladoMemoria(Long id, Instant instante);
}
