package pe.edu.pucp.inf.pddsbackend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.history.Revision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;
import java.util.Map;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
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
        //return ResponseEntity.status(HttpStatus.CREATED).body(pedidoListadoDTO);
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
    @PostMapping("/carga-masiva-archivo")
    public ResponseEntity<?> cargarPedidosMasivosArchivo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No se envió ningún archivo");
        }

        try {
            // Leer pedidos desde Excel
            List<PedidoCargaMasivaDTO> pedidosDTO = pedidoService.leerPedidosDesdeExcel(file);

            if (pedidosDTO.isEmpty()) {
                return ResponseEntity.badRequest().body("El archivo no contiene pedidos válidos");
            }

            // Guardar pedidos masivos
            List<Pedido> pedidosGuardados = pedidoService.cargarPedidosMasivos(pedidosDTO);

            return ResponseEntity.ok(pedidosGuardados);
        } catch (Exception e) {
            e.printStackTrace(); // log en consola para debugging
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar el archivo: " + e.getMessage());
        }
    }


}

