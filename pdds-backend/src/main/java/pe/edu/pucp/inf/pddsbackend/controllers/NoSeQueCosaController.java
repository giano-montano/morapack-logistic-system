package pe.edu.pucp.inf.pddsbackend.controllers;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.services.implementations.ExperimentacionNumericaServiceImpl;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exp-num")
public class NoSeQueCosaController {

    private final ExperimentacionNumericaServiceImpl experimentacionNumericaService;

    @PostMapping
    public ResponseEntity<?> que(
            @RequestBody List<String> nombresArchivosPedidos) throws Exception {

        experimentacionNumericaService.correrPruebasDeAxel(nombresArchivosPedidos);

        return ResponseEntity.ok().build();
    }

}
