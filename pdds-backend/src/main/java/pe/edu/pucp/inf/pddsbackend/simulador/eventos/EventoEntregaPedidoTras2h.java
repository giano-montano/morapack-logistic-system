package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoEntregaPedidoTras2h implements  EventoSimulacion{
    @NotNull
    long idPedido;
    @NotNull
    long idAlmacenDestino;
    @NotNull
    Producto productoAEntregar;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instante2hDespuesDeLlegadosProductosAAlmacenDestino;
    
    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instante2hDespuesDeLlegadosProductosAAlmacenDestino;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        Pedido pedido = ctx.getEstado().getPedidos().get(idPedido);
        Almacen almOrigen = ctx.getEstado().getAlmacenes().get(idAlmacenDestino);
        
        // 📦 LOG DETALLADO DE ENTREGA DE PEDIDO
        System.out.println("\n📦 ============= ENTREGA DE PEDIDO =============");
        System.out.println("⏰ Hora: " + instante2hDespuesDeLlegadosProductosAAlmacenDestino);
        System.out.println("📋 ID Pedido: " + idPedido);
        System.out.println("🎯 Almacén Destino: ID=" + idAlmacenDestino);
        System.out.println("📦 Producto UUID: " + productoAEntregar.getUuid());
        if (pedido != null) {
            System.out.println("📊 Estado Pedido: " + pedido.getCantidadProductosEntregados() + 
                             "/" + pedido.getCantidadProductosPedidos() + " entregados");
        }
        System.out.println("===============================================\n");
        
        // Entregar producto al pedido
        boolean exitoso = ctx.getEstado().entregarProductoEnPedido(idPedido, productoAEntregar);
        String mensaje;
        
        if (exitoso) {
            ctx.log("✅ EventoEntregaPedido: Producto entregado al cliente - Pedido ID=" + idPedido);
            mensaje = "Producto entregado exitosamente";
        } else {
            ctx.log("⚠️ EventoEntregaPedido: No se pudo entregar producto - Pedido ID=" + idPedido);
            mensaje = "Error: No se pudo entregar el producto";
        }
        
        // Enviar evento WebSocket
        if (webSocketService != null) {
            try {
                // SIEMPRE usar "sim-default" para facilitar testing
                String idSimulacion = "sim-default";
                
                webSocketService.enviarEventoEntregaPedido(
                    idSimulacion,
                    LocalDateTime.ofInstant(instante2hDespuesDeLlegadosProductosAAlmacenDestino, ZoneId.systemDefault()),
                    idPedido,
                    productoAEntregar.getUuid().toString(),
                    idAlmacenDestino,
                    almOrigen != null && almOrigen.getNombreCiudad() != null ? 
                        almOrigen.getNombreCiudad() : "Almacén " + idAlmacenDestino,
                    exitoso,
                    mensaje
                );
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }

        // Quitar producto del almacén
        if ( ! almOrigen.quitarProducto(productoAEntregar) )
            throw new ColapsadoExceptionTemporal("EventoEntregaPedido: COLAPSO DE CAPACIDAD DE ALMACEN");
        
        ctx.log(String.format(
                "📦 ENTREGA: Cliente recogió producto del pedido ID=%d desde almacén ID=%d | Hora=%s",
                idPedido, idAlmacenDestino, instante2hDespuesDeLlegadosProductosAAlmacenDestino));
    }

    @Override
    public int getPriority() {
        return 0;
    }
}