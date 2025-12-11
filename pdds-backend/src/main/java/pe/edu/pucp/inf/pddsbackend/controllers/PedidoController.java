package pe.edu.pucp.inf.pddsbackend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import java.util.Collections;
import java.util.Map;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200") // <--- permite Angular

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController
{

    private final PedidoService pedidoService;

    @PostMapping
    @Operation(summary = "Permite insertar pedido en BD; adicionalmente con param `paraMemoria` se" +
            " puede inyectar en memoria de contexto simulación" +
            "directamente, si es que hay simu corriendo, sino lanzará excepción." +
            "MANDAR EL INSTANTE DE REGISTRO EN UTC")
    public ResponseEntity<PedidoListadoDTO> insertarPedido(@RequestBody @Valid GuardarPedidoDTO dto)
    {
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  📡 PedidoController.insertarPedido - REQUEST        ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("📨 DTO recibido desde Frontend:");
        System.out.println("   → idCliente: " + dto.idCliente());
        System.out.println("   → idAlmacenDestino: " + dto.idAlmacenDestino());
        System.out.println("   → cantProductos: " + dto.cantProductos());
        System.out.println("   → instanteRegistro: " + dto.instanteRegistro());
        System.out.println("   → paraMemoria: " + dto.paraMemoria());
        
        PedidoListadoDTO pedidoListadoDTO = pedidoService.insertarUnPedido(dto);
        
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  📡 PedidoController.insertarPedido - RESPONSE       ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println("📤 Retornando al Frontend: " + pedidoListadoDTO);
        
        return ResponseEntity.ok(pedidoListadoDTO);
    }

    @PutMapping("/{id}/actualizar")
    public ResponseEntity<PedidoListadoDTO> actualizarPedido(
            @PathVariable Long id,
            @RequestBody @Valid GuardarPedidoDTO dto)
    {
        PedidoListadoDTO pedidoListadoDTO = pedidoService.actualizarUnPedido(id, dto);
        return ResponseEntity.ok(pedidoListadoDTO);
    }

    @GetMapping("/{id}/revisiones")
    public ResponseEntity<List<PedidoRevisionDto>> revisarPorId(
            @PathVariable Long id)
    {
        return ResponseEntity.ok(pedidoService.getAllRevisions(id));
    }

    // Listar todos los pedidos
    @GetMapping("/listar")
    public ResponseEntity<List<PedidoListadoDTO>> listarPedidos()
    {
        List<PedidoListadoDTO> pedidos = pedidoService.listarPedidos();
        return ResponseEntity.ok(pedidos);
    }

    // Obtener un pedido por ID
    @GetMapping("/{id}/obtenerPorId")
    public ResponseEntity<PedidoListadoDTO> obtenerPedidoPorId(@PathVariable Long id)
    {
        PedidoListadoDTO pedido = pedidoService.obtenerPedidoPorId(id);
        return ResponseEntity.ok(pedido);
    }

    // Eliminar un pedido
    @DeleteMapping("/{id}/eliminar")
    public ResponseEntity<Void> eliminarPedido(@PathVariable Long id)
    {
        pedidoService.eliminarPedido(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/archivo")
    public ResponseEntity<?> archivo(
            @RequestParam("file") MultipartFile file) // <- archivo legacy creo
    {
        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().body("Archivo vacío");
        try (InputStream is = file.getInputStream())
        {
            ProcessResult r = pedidoService.processOrders(is, 9, 2025);
            Map<String, Object> resp = new HashMap<>();
            resp.put("saved", r.getSavedCount());
            resp.put("skipped", r.getSkippedCount());
            resp.put("errors", r.getErrors());
            return ResponseEntity.ok(resp);
        }
        catch (Exception e)
        {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }

    }

    /* PUEDE AGREGAR TAMBIÉN A MEMORIA Y UNA VEZ AHÍ NO DISTINGUE ENTRE PEDIDOS PARA DIA A DIA O SIMU
    * Siempre agrega a BD, pero adicionalmente puede inyectar en memoria del contexto simulación para que la siguiente planif
    * lo considere en lugar de esperar carga diaria de pedidos desde BD
    * */
    @PostMapping("/carga-masiva-archivo") // <- archivo nuevo
    @Operation(summary = "Carga del archivo con formato Dávila (actual) para carga de pedidos en BD, también puede inyectarlo directamente" +
            "en memoria de la simulación para que se tengan en consideración en próxima planificación directamente.")
    public ResponseEntity<?> cargarPedidosMasivosArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "paraMemoria", defaultValue = "false") boolean paraMemoria
            ){
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No se envió ningún archivo");
        }

        try {
            if(paraMemoria)
                System.out.println("paraMemoria");
            else
                System.out.println("para BD");

            Integer pedidosGuardados = pedidoService.cargarPedidosDesdeArchivo(file, paraMemoria);

            // if (pedidosGuardados != null && pedidosGuardados.isEmpty()) {
            // return ResponseEntity.ok("Archivo procesado, no se guardaron pedidos (todos
            // excluidos o no válidos).");
            // }
            if (pedidosGuardados == null || pedidosGuardados <= 0) {
                return ResponseEntity.ok(
                        "Archivo procesado, no se guardaron pedidos (todos excluidos o no válidos).");
            }

            return ResponseEntity.ok(pedidosGuardados);
        }
        catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Error procesando archivo: " + e.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/filtrarPorDestino")
    @Operation(summary = "Filtrar por ID Destino")
    public ResponseEntity<?> filtrarPorDestino(@RequestParam("destino") String destino)
    {
        List<PedidoListadoDTO> pedidos = pedidoService.listarPedidosPorDestino(destino);
        if (pedidos.isEmpty())
        {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{id}/card")
    @Operation(summary = "Devolver info de card pedido durante simul")
    public ResponseEntity<PedidoCardDTO> dameCard(@PathVariable Long id)
    {
        PedidoCardDTO a = pedidoService.devolverCard(id);
        return ResponseEntity.ok(a);
    }

    @GetMapping("/simulados")
    @Operation(summary = "Listar pedidos simulados en memoria paginados")
    public Page<PedidoListadoDTO> listarSimulados(
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "cantProductosEntregados") Pageable pageable)
            throws ExcepcionLogica
    {

        return pedidoService.listarSimulados(q, pageable);
    }

}
