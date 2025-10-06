package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/vuelos")
public class VueloController {

    private final VueloService vueloService;

    @PostMapping("/planes/archivo")
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

}
