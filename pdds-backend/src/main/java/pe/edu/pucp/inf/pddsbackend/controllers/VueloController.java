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
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/vuelos")
@Tag(name = "Vuelos", description = "CRUD y procesamiento de vuelos")
public class VueloController {

    private final VueloService vueloService;

    @PostMapping("/planes/archivo")
    @Operation(summary = "Carga archivo de planes de vuelo del profesor")
    public ResponseEntity<?> uploadFlightPlan(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Archivo vacío");
        try (InputStream is = file.getInputStream()) {
            ProcessResult r = vueloService.procesarArchivoPlanesVueloDelProfe(is);
            Map<String,Object> resp = new HashMap<>();
            resp.put("saved", r.getSavedCount());
            resp.put("skipped", r.getSkippedCount());
            resp.put("errors", r.getErrors());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }


    @PostMapping("/semana")
    @Operation(summary = "Generar vuelos concretos desde programados (semana)")
    public ResponseEntity<?> cargarVuelosDesdeVuelosProgramados() {
        try  {
            ProcessResult r = vueloService.createConcreteFlights(LocalDate.now(),9,false);
            Map<String,Object> resp = new HashMap<>();
            resp.put("saved", r.getSavedCount());
            resp.put("skipped", r.getSkippedCount());
            resp.put("errors", r.getErrors());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // ---------------- CRUD ----------------
    @PostMapping
    @Operation(summary = "Crear vuelo")
    public ResponseEntity<VueloDTO> crear(@RequestBody @jakarta.validation.Valid VueloCreateUpdateDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(vueloService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar vuelo")
    public VueloDTO actualizar(@PathVariable Long id, @RequestBody @jakarta.validation.Valid VueloCreateUpdateDTO dto){
        return vueloService.actualizar(id,dto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener vuelo por id")
    public VueloDTO obtener(@PathVariable Long id){
        return vueloService.obtener(id);
    }

    @GetMapping
    @Operation(summary = "Listar vuelos paginados")
    public Page<VueloDTO> listar(@RequestParam(value = "q", required = false) String q,
                                 @PageableDefault(size=20, sort = "fechaHoraInicioUtc") Pageable pageable){
        return vueloService.listar(q,pageable);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (soft) vuelo")
    public ResponseEntity<Void> eliminar(@PathVariable Long id){
        vueloService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}
