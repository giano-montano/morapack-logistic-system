package pe.edu.pucp.inf.pddsbackend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.history.Revision;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.inf.pddsbackend.dto.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.dto.RevisionResponse;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import java.util.List;
@CrossOrigin(origins = "http://localhost:4200") // <--- permite Angular
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

    @PutMapping("/{id}/actualizar")
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

    /// /////////
    // Listar todos los pedidos
    @GetMapping("/listar")
    public ResponseEntity<List<PedidoListadoDTO>> listarPedidos() {
        List<PedidoListadoDTO> pedidos = pedidoService.listarPedidos();
        return ResponseEntity.ok(pedidos);
    }

    // Obtener un pedido por ID
    @GetMapping("/{id}/obtenerPorId")
    public ResponseEntity<PedidoListadoDTO> obtenerPedidoPorId(@PathVariable Long id) {
        PedidoListadoDTO pedido = pedidoService.obtenerPedidoPorId(id);
        return ResponseEntity.ok(pedido);
    }

    // Eliminar un pedido
    @DeleteMapping("/{id}/eliminar")
    public ResponseEntity<Void> eliminarPedido(@PathVariable Long id) {
        pedidoService.eliminarPedido(id);
        return ResponseEntity.noContent().build();
    }

}
