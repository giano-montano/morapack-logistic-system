package pe.edu.pucp.inf.pddsbackend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCardDTO;
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

}
