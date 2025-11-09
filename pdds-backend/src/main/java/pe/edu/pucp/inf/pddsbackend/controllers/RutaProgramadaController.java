package pe.edu.pucp.inf.pddsbackend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ProgramacionService;

import java.util.LinkedList;

@RestController
@RequestMapping("/api/rutas-programadas")
@RequiredArgsConstructor
@Tag(name = "Rutas programadas (abstracción)", description = "Rutas programadas en base a programaciones individuales")
public class RutaProgramadaController {

    private final ProgramacionService programacionService;

    @GetMapping("/cards") // Estoy tratando de ser RESTful, no me mates Andrea 😭
    @Operation(summary = "Devolver info de card de la ruta programada durante simul, necesita los ids de vuelos que"+
    "componen la ruta")
    public ResponseEntity<RutaProgramadaCardDTO> dameCard(
            @RequestParam(required = true) LinkedList<Long> idsVueloRuta
    ){
        RutaProgramadaCardDTO a= programacionService.devolverCardDeRutaProgramada(idsVueloRuta);
        return ResponseEntity.ok(a);
    }

    @GetMapping()
    @Operation(summary = "Devolver info de card de la ruta programada durante simul, necesita los ids de vuelos que"+
            "componen la ruta")
    public ResponseEntity<Page<RutaProgramadaListadaDTO>> listarRutasProgramadas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "true") boolean sortDir
    ) {
        return ResponseEntity.ok(
                programacionService.listarRutasProgramadas(page,size,sortBy,sortDir)
        );
    }
}
