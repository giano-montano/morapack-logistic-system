package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.AlmacenService;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/almacenes")
@RequiredArgsConstructor
@Tag(name = "Almacenes", description = "CRUD y carga masiva de almacenes")
public class AlmacenController
{

    private final AlmacenService almacenService;

    @PostMapping("/archivo")
    @Operation(summary = "Carga masiva desde archivo del profesor")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return ResponseEntity.badRequest().body("Archivo vacio");
        }

        try (InputStream is = file.getInputStream())
        {
            ProcessResult result = almacenService.cargarAlmacenesEnBDDesdeArchivoDelProfe(is);
            Map<String, Object> resp = new HashMap<>();
            resp.put("saved", result.getSavedCount());
            resp.put("skipped", result.getSkippedCount());
            resp.put("errors", result.getErrors());
            return ResponseEntity.ok(resp);
        }
        catch (Exception ex)
        {
            return ResponseEntity.status(500).body("Error procesando: " + ex.getMessage());
        }
    }

    // ---------------- CRUD ----------------
    @PostMapping
    @Operation(summary = "Crear almacén")
    public ResponseEntity<AlmacenDTO> crear(
            @RequestBody @jakarta.validation.Valid AlmacenCreateUpdateDTO dto)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(almacenService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar almacén")
    public AlmacenDTO actualizar(@PathVariable Long id,
            @RequestBody @jakarta.validation.Valid AlmacenCreateUpdateDTO dto)
    {
        return almacenService.actualizar(id, dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener almacén por id")
    public AlmacenDTO obtener(@PathVariable Long id)
    {
        return almacenService.obtener(id);
    }

    @GetMapping
    @Operation(summary = "Listar almacenes paginados")
    public Page<AlmacenDTO> listar(@RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "codigoAeropuertoEn4Letras") Pageable pageable)
    {
        return almacenService.listar(q, pageable);
    }

    @GetMapping("/todos")
    @Operation(summary = "Obtener TODOS los almacenes sin paginación (para simulación)")
    public ResponseEntity<java.util.List<AlmacenDTO>> obtenerTodos()
    {
        return ResponseEntity.ok(almacenService.obtenerTodos());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft) almacén")
    public ResponseEntity<Void> eliminar(@PathVariable Long id)
    {
        almacenService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/card")
    @Operation(summary = "Devolver info de card almacén durante simul")
    public ResponseEntity<AlmacenCardDTO> dameCard(@PathVariable Long id)
    {
        AlmacenCardDTO a = almacenService.devolverCardAlmacen(id);
        return ResponseEntity.ok(a);
    }

    @GetMapping("/simulados")
    @Operation(summary = "Listar almacenes simulados en memoria paginados")
    public Page<AlmacenDTO> listarSimulados(
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "codigoAeropuertoEn4Letras") Pageable pageable)
            throws ExcepcionLogica
    {
        return almacenService.listarSimulados(q, pageable);
    }

}
