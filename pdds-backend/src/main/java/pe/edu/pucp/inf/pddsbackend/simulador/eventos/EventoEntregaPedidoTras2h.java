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
        System.out.println("Hora: " + instante2hDespuesDeLlegadosProductosAAlmacenDestino);
        System.out.println("ID Pedido: " + idPedido);
        System.out.println("===============================================\n");
        
        // Entregar producto al pedido
        boolean exitoso = ctx.getEstado().entregarProductoEnPedido(idPedido, productoAEntregar); // <- muta estado del pedido.
        
        if (exitoso) {
            ctx.log("✅ EventoEntregaPedido: Producto entregado al cliente - Pedido ID=" + idPedido);
        } else {
            ctx.log("⚠️ EventoEntregaPedido: No se pudo entregar producto - Pedido ID=" + idPedido);
        }
        
        // ✅ Enviar evento WebSocket simplificado
        if (webSocketService != null) {
            try {
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());
                
                // Contar productos del pedido (asumiendo que cada pedido tiene 1 producto por simplicidad)
                // Si necesitas el conteo real, deberás buscarlo en el contexto
                int cantidadProductos = 1; // Ajustar si es necesario
                
                webSocketService.enviarEventoEntregaPedido(
                    idSimulacion,
                    idPedido,
                    cantidadProductos,
                    instante2hDespuesDeLlegadosProductosAAlmacenDestino
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