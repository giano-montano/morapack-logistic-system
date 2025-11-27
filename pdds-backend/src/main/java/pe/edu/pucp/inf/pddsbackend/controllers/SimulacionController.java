package pe.edu.pucp.inf.pddsbackend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.EstadoSimulacionCompletoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Simulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.SimulacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/simulaciones")
public class SimulacionController
{
    private final SimulacionService simulacionService;

    @PostMapping
    public ResponseEntity<?> iniciarSimulacion(
            @RequestBody SimulacionRequestDTO simulacionRequestDTO)
            throws ExecutionException, InterruptedException
    {
        System.out.println("QUE: " + simulacionRequestDTO);
        Simulacion saved = simulacionService.iniciarSimulacionAhora(simulacionRequestDTO);

        // ✅ Devolver un DTO con el ID inmediatamente para que el frontend se conecte al
        // WebSocket
        return ResponseEntity.ok().body(Map.of(
                "id", saved.getId(),
                "tipo", saved.getTipo(),
                "fechaHoraInicio", saved.getFechaHoraInicio()));
    }

    /**
     * ✅ Cancela una simulación en ejecución DELETE /api/simulaciones/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelarSimulacion(@PathVariable Long id)
    {
        System.out.println("🛑 Solicitud de cancelación de simulación ID: " + id);
        boolean cancelado = simulacionService.cancelarSimulacion(id);

        if (cancelado)
        {
            return ResponseEntity.ok().body(Map.of(
                    "mensaje", "Simulación cancelada exitosamente",
                    "idSimulacion", id));
        }
        else
        {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Simulación no encontrada o no está en ejecución",
                    "idSimulacion", id));
        }
    }

    /**
     * ✅ Pausa la planificación de una simulación
     * POST /api/simulaciones/{id}/pausar-planificacion
     */
    @PostMapping("/{id}/pausar-planificacion")
    @Operation(summary = "Pausa la ejecución de planificaciones sin detener la simulación")
    public ResponseEntity<?> pausarPlanificacion(@PathVariable Long id)
    {
        System.out.println("⏸️  Solicitud de pausar planificación - Simulación ID: " + id);
        boolean pausado = simulacionService.pausarPlanificacion(id);

        if (pausado)
        {
            return ResponseEntity.ok().body(Map.of(
                    "mensaje", "Planificación pausada - la simulación continúa",
                    "idSimulacion", id,
                    "planificacionActiva", false));
        }
        else
        {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Simulación no encontrada o no está en ejecución",
                    "idSimulacion", id));
        }
    }

    /**
     * ✅ Reanuda la planificación de una simulación
     * POST /api/simulaciones/{id}/reanudar-planificacion
     */
    @PostMapping("/{id}/reanudar-planificacion")
    @Operation(summary = "Reanuda la ejecución de planificaciones")
    public ResponseEntity<?> reanudarPlanificacion(@PathVariable Long id)
    {
        System.out.println("▶️  Solicitud de reanudar planificación - Simulación ID: " + id);
        boolean reanudado = simulacionService.reanudarPlanificacion(id);

        if (reanudado)
        {
            return ResponseEntity.ok().body(Map.of(
                    "mensaje", "Planificación reanudada",
                    "idSimulacion", id,
                    "planificacionActiva", true));
        }
        else
        {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Simulación no encontrada o no está en ejecución",
                    "idSimulacion", id));
        }
    }

    /**
     * ✅ Obtiene el estado de la planificación
     * GET /api/simulaciones/{id}/estado-planificacion
     */
    @GetMapping("/{id}/estado-planificacion")
    @Operation(summary = "Verifica si la planificación está activa o pausada")
    public ResponseEntity<?> obtenerEstadoPlanificacion(@PathVariable Long id)
    {
        boolean pausada = simulacionService.estaPlanificacionPausada(id);
        
        return ResponseEntity.ok().body(Map.of(
                "idSimulacion", id,
                "planificacionPausada", pausada,
                "planificacionActiva", !pausada));
    }

    @GetMapping("/ejecutada")
    @Operation(summary = "Te dice si hay una simulación en tiempo real (op dia a dia) a la cual el frontend debe unirse inmediatamente"
    +" para poder ingresar pedidos, cancelar y demás. Si hay id de simu en curso, lo devuelve; si no, da not found" +
            ". Técnicamente te devuelve cualquier simu, pero su intención es para el caso de op día a día")
    public ResponseEntity<Long> haySimuCorriendo(){
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        if(ctx == null) return ResponseEntity.notFound().build();

        Long idSimu = ctx.getFormaRealizarPlanificacion().getIdSimulacion();
        return ResponseEntity.ok(idSimu);
    }

    @GetMapping
    @Operation(summary = "Recupera al completo el estado global de la simulación. El caso de uso" +
            "principal es que cuando otro cliente (máquina con navegador) entre a la plataforma y entre " +
            "a la pantalla de simulación el frontend revise si hay una simulación día a día corriendo y pueda llamar" +
            "a este endpoint para ponerse al día y recuperar todo el estado para cargar sus elementos de la barra lateral" +
            "y continuar la simu normalmente vía WS.")
    public ResponseEntity<EstadoSimulacionCompletoDTO> recuperarEstadoGlobalCompletoSimulacionDiaADia(){
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        if(ctx == null) throw new RuntimeException("Contexto no encontrado");

        EstadoGlobal estadoMemoria = ctx.getEstado();

        HashMap<Long, Almacen> almacenes = estadoMemoria.getAlmacenes();
        // DEVUELVO TAL COMO SE LO DABAN LOS EVENTOS PERIÓDICOS DE CARGA.
        EstadoSimulacionCompletoDTO simulacionDTO = new EstadoSimulacionCompletoDTO(
                estadoMemoria.getPedidos().values().stream().map(
                        pedido ->  PedidoListadoDTO.desdeDominio(pedido,almacenes.get(pedido.getIdAlmacenDestino()).getNombreCiudad() ))
                        .collect(Collectors.toList()),
                estadoMemoria.getVuelos().values().stream().map(VueloDTO::desdeDominio).toList(),
                estadoMemoria.getAlmacenes().values().stream().map(AlmacenDTO::desdeDominio).toList(),
                estadoMemoria.obtenerRutasProgramadas()

        );

        return ResponseEntity.ok().body(simulacionDTO);

    }

}
