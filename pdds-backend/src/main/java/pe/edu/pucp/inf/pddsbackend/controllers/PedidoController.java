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

}
