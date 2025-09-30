package pe.edu.pucp.inf.pddsbackend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.history.Revision;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoListadoDTO> insertarPedido( @RequestBody @Valid GuardarPedidoDTO dto) {
        PedidoListadoDTO pedidoListadoDTO = pedidoService.insertarUnPedido(dto);
        return ResponseEntity.ok(pedidoListadoDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PedidoListadoDTO> actualizarPedido(
            @PathVariable Long id,
            @RequestBody @Valid GuardarPedidoDTO dto) {
        PedidoListadoDTO pedidoListadoDTO = pedidoService.actualizarUnPedido(id, dto);
        return ResponseEntity.ok(pedidoListadoDTO);
    }

    @GetMapping("/{id}/revisiones")
    public ResponseEntity<List<PedidoRevisionDto>>  revisarPorId(
            @PathVariable Long id
    ){
        return ResponseEntity.ok(pedidoService.getAllRevisions(id));
    }

    @GetMapping("/archivo")
    public ResponseEntity<?>  archivo(
            @RequestParam("file") MultipartFile file
    ){
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Archivo vacío");
        try (InputStream is = file.getInputStream()){
            ProcessResult r = pedidoService.processOrders(is,9, 2025);
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
