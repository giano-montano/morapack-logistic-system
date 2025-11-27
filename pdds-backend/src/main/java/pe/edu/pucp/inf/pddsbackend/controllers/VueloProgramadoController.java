package pe.edu.pucp.inf.pddsbackend.controllers;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloProgramadoService;

@RestController
@RequestMapping("/api/vuelos-programados")
@RequiredArgsConstructor
public class VueloProgramadoController {

    private final VueloProgramadoService vueloProgramadoService;

    /**
     * GET /api/vuelos-programados?page=0&size=20&sort=id,asc&soloActivos=true
     */
    @GetMapping("/paginados")
    @Operation(summary = "Listar vuelos programados paginados")
    public ResponseEntity<Page<VueloDTO>> listar(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(name = "soloActivos", defaultValue = "true") boolean soloActivos
    ) {
        Page<VueloDTO> page = vueloProgramadoService.listarVuelosProgramados(pageable, soloActivos);
        return ResponseEntity.ok(page);
    }
}
