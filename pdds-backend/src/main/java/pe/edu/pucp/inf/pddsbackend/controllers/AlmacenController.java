package pe.edu.pucp.inf.pddsbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.AlmacenService;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/almacenes")
@RequiredArgsConstructor
public class AlmacenController {

    private final AlmacenService almacenService;


    @PostMapping("/archivo")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo vacio");
        }

        try (InputStream is = file.getInputStream()) {
            ProcessResult result = almacenService.cargarAlmacenesEnBDDesdeArchivoDelProfe(is);
            Map<String, Object> resp = new HashMap<>();
            resp.put("saved", result.getSavedCount());
            resp.put("skipped", result.getSkippedCount());
            resp.put("errors", result.getErrors());
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Error procesando: " + ex.getMessage());
        }
    }
}