package pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoEntregaPedidoTras2h extends EventoSimulacion {
    // DEUDA TÉCNICA: HACER QUE CHUPE VARIOS PRODUCTOS Y NO SOLO UNO, YA QUE LOS
    // VUELOS LLEGAN CON VARIOS A LA VEZ
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
    public UUID getId(){
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instante2hDespuesDeLlegadosProductosAAlmacenDestino;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        Pedido pedido = ctx.getEstado().getPedidos().get(idPedido);
        Almacen almDestino = ctx.getEstado().getAlmacenes().get(idAlmacenDestino);

        // 📦 LOG DETALLADO DE ENTREGA DE PEDIDO
        System.out.println("\n📦 ============= ENTREGA DE PEDIDO (SIMBÓLICO) =============");
        System.out.println("Hora: " + instante2hDespuesDeLlegadosProductosAAlmacenDestino);
        System.out.println("ID Pedido: " + idPedido);
        System.out.println("Producto a entregar: " + productoAEntregar);
        System.out.println("===============================================\n");

        pedido.registrarProductoEntregado(productoAEntregar);
        // MOVIDO NUEVAMENTE, EL PRODUCTO SE ENTREGA CUANDO DEBE ENTREGARSE, O SEA CUANDO EL CLIENTE LO RECOGE

        webSocketYLog(idPedido, ctx);

        // Buscar y transicionar la programación asociada de I (Incancelable) a T (Terminado)
        ctx.getEstado().getProgramaciones().stream()
                .filter(prog -> prog.getProducto().getId().equals(productoAEntregar.getId()))
                .findFirst()
                .ifPresent(programacion -> {
                    ctx.log("Programación que ha llegado a recogerse: " + programacion);
                    ctx.log("Prod de programación que ha llegado a recogerse: " + programacion.getProducto());
                    if (programacion.validarIncancelable_I(instante2hDespuesDeLlegadosProductosAAlmacenDestino)) {
                        programacion.transIncancelable_I_Terminada_T();
                    } else {
                        String msj = String.format("Programación no incancelable llegó a entrega de pedido");
                        String prog = "\nProgramación que ha llegado a recogerse: " + programacion;
                        String prod ="\nProd de programación que ha llegado a recogerse: " + programacion.getProducto();
                        ctx.log(msj+prog+prod);
                        lanzarExcepcion("procesar", msj+prog+prod);
                    }
                });
        
        // Quitar producto del almacén
        if (!almDestino.borrarProductoSincronizado(productoAEntregar)){
            ctx.log("\n❌ EventoEntregaPedido: ERROR AL QUITAR PRODUCTO DE " + almDestino);
            ctx.log("Producto que dio falla: " + productoAEntregar);
            throw new ColapsadoExceptionTemporal(
                    "EventoEntregaPedido: COLAPSO DE CAPACIDAD DE ALMACEN" + almDestino);
        }

        webSocketYLog2(almDestino, ctx, idPedido, idAlmacenDestino, productoAEntregar);

        // Actualizar el producto
        ctx.getEstado().getProductos().remove(productoAEntregar.getId());
    }

    private void webSocketYLog2(
            Almacen almOrigen,
            ContextoSimulacion ctx,
            long idPedido,
            long idAlmacenDestino,
            @NotNull Producto productoAEntregar) {
        // ✅ Notificar cambio de capacidad del almacén SOLO si NO es infinito
        if (webSocketService != null && !almOrigen.isInfinito()){
            try{
                webSocketService.enviarCambioCapacidadAlmacen(
                        String.valueOf(ctx.getIdSimulacion()),
                        almOrigen.getId(),
                        almOrigen.getInventario().size(),
                        almOrigen.getCapacidad());
            }
            catch (Exception e){
                System.err.println(
                        "⚠️ Error al enviar cambio de capacidad de almacén: " + e.getMessage());
            }
        }

        ctx.log(String.format(
                "📦 ENTREGA: Cliente recogió producto del pedido ID=%d desde almacén ID=%d | Hora=%s",
                idPedido, idAlmacenDestino, instante2hDespuesDeLlegadosProductosAAlmacenDestino));
        ctx.log("    -> Producto entregado: " + productoAEntregar);
    }

    private void webSocketYLog(long idPedido, ContextoSimulacion ctx) {
        // ✅ Enviar evento WebSocket simplificado
        if (webSocketService != null){
            try{
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());

                // Contar productos del pedido (asumiendo que cada pedido tiene 1 producto por
                // simplicidad)
                // Si necesitas el conteo real, deberás buscarlo en el contexto
                int cantidadProductos = 1; // Ajustar si es necesario

                webSocketService.enviarEventoEntregaPedido(
                        idSimulacion,
                        idPedido,
                        cantidadProductos,
                        instante2hDespuesDeLlegadosProductosAAlmacenDestino);
            }
            catch (Exception e){
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }

    }

    @Override
    public int getPriority()
    {
        return 0;
    }
}
