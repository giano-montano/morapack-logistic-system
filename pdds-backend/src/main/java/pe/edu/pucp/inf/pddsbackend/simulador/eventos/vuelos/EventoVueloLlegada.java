package pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos.EventoEntregaPedidoTras2h;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloLlegada extends EventoSimulacion
{
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoLlegadaVuelo;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    private final int HORAS_QUE_SE_TARDA_EN_RECOGER_EL_CLIENTE = 2; // podría ser dinámico y
                                                                    // parametrizable?
    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramadoLlegadaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception{
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        if (vuelo == null){
            ctx.log("❌ EventoVueloLlegada: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        Almacen almacenAlQueLlego = vuelo.getAlmacenDestino();
//                ctx.getEstado()
//                .obtenerAlmacenPorId(vuelo.getAlmacenDestino());
        if (almacenAlQueLlego == null){
            ctx.log("❌ EventoVueloLlegada: Almacén destino no encontrado id="
                    + vuelo.getAlmacenDestino());
            return;
        }

        // verificar si colapsó.
        List<Producto> productosADescargar = vuelo.getInventario();
//                .getIdsProductosContenidos().stream()
//                .map(uuid1 -> ctx.getEstado().obtenerProductoPorUuid(uuid1)).toList();
        // ^^ del estado, lo real!

        int cantidadADescargar = vuelo.getInventario().size();
        // ^^ debería coincidir con productosADescargar.size()

        if (cantidadADescargar > 0){
            ctx.log(String.format(
                    "🛫 VUELO LLEGADA: ID=%d | Origen=%d → Destino=%d | Productos=%d | Inicio=%s | Fin(ahora)=%s",
                    idVuelo, vuelo.getAlmacenSalida().getId(), vuelo.getAlmacenDestino().getId(),
                    cantidadADescargar, vuelo.getInstanteSalida(), instanteProgramadoLlegadaVuelo));
            ctx.log("✅ Productos a descargar en vuelo ID=" + idVuelo + " ("
                    + vuelo.getInventario().size() + " prods): "
                    + vuelo.getInventario());
        }

        webSocketYLoggear(vuelo, ctx, cantidadADescargar, almacenAlQueLlego);

        if (cantidadADescargar > 0){ // importante para que no colapse de forma estúpida
            if (!almacenAlQueLlego.agregarVariosSimu(productosADescargar)){
                throw new ColapsadoExceptionTemporal(
                        "EventoVueloLlegada: El almacén no aguanta lo traído por el vuelo: " + vuelo
                                + "\nEl almacén es: " + almacenAlQueLlego
                                + "\nLos pedidos que estaría atendiendo son:\n"
                                + ctx.imprimirMinipedidosDeRutasDeVueloFinal(vuelo));
            }
            // ctx.log("Agregados varios, ¿el almacén está integro?: " // no da error
            // + almacenAlQueLlego.getInventario().size() + " - " +
            // almacenAlQueLlego.getIdsProductosExistentes().size());

            webSocket2(vuelo, almacenAlQueLlego, ctx);

            // Transaccionar en vuelo, almacén y productos:
            if (!vuelo.quitarVariosSimu(productosADescargar))
                throw new ColapsadoExceptionTemporal(
                        "EventoVueloLlegada: El vuelo "+vuelo+"\n no puede desocuparse los productos ("
                                + cantidadADescargar+"): " + productosADescargar);

            productosADescargar.forEach(producto -> {
                ctx.log("Producto descargado: " + producto);
            });

            // obtenemos los minipedidos que atiende este último vuelo según rutas.
            List<Programacion> rutasDondeElVueloEsFinal = ctx.getEstado() // Se supone que está cargado con lo nuevo
                    .getProgramaciones()
                    // SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL CUAL
                    .stream().filter(programacion -> {
                        Ruta vuelosEnOrden = programacion.getRuta();
                        return vuelosEnOrden.obtenerUltimoVuelo().getId() == vuelo.getId();
                    }).toList();

            // ctx.log("EventoVueloLlegada: Llegó el vuelo " + vuelo.getId() + " Rutas
            // asociadas donde es el último destino: " + rutasDondeElVueloEsFinal);

            // lógica de evento de liberación en 2h y entrega de pedido. Además, capacidad
            // descargada por ruta...
            for (Programacion prog : rutasDondeElVueloEsFinal){
                if (prog == null) {
                    throw new IllegalStateException("La programacion no puede ser nula en evento vuelo llegada");
//                    continue;
                }
                Producto prod = prog.getProducto();
//                        ctx.getEstado().obtenerProductoPorUuid(prog.getUuidProducto());
                ctx.log("El producto de la programación es :" + prod);
                // Pedido pedido = ctx.getEstado().getPedidos().get(prog.getIdPedido());
                ctx.programarEvento(new EventoEntregaPedidoTras2h(
                        prog.getPedido().getId(),
                        almacenAlQueLlego.getId(),
                        prod,
                        UUID.randomUUID(),
                        instanteProgramadoLlegadaVuelo.plus(
                                HORAS_QUE_SE_TARDA_EN_RECOGER_EL_CLIENTE, ChronoUnit.HOURS),
                        webSocketService));
                // La programación ha terminado, eliminarla del estado
//                ctx.getEstado().getProgramaciones().remove(prog);
//                  MENTIRA: PONEMOS COMO TERMINADA :V
                    prog.setTerminada(true);
            }
        }
    }

    private void webSocket2(Vuelo vuelo, Almacen almacenAlQueLlego, ContextoSimulacion ctx) {

        // ✅ Notificar cambio de capacidad del almacén destino SOLO si NO es infinito
        if (webSocketService != null && !almacenAlQueLlego.isInfinito()){
            try{
                webSocketService.enviarCambioCapacidadAlmacen(
                        String.valueOf(ctx.getIdSimulacion()),
                        almacenAlQueLlego.getId(),
                        almacenAlQueLlego.getInventario().size(),
                        almacenAlQueLlego.getCapacidad());
            }
            catch (Exception e){
                System.err.println(
                        "⚠️ Error al enviar cambio de capacidad de almacén: " + e.getMessage());
            }
        }
    }

    private void webSocketYLoggear(Vuelo vuelo, ContextoSimulacion ctx, int cantidadADescargar,
                                   Almacen almacenAlQueLlego) {

        // ✅ Enviar evento WebSocket SOLO si el vuelo tiene productos
        if (webSocketService != null && cantidadADescargar > 0){
            try{
                // 🛬 LOG Y WEBSOCKET
                vuelo.loggearLlegadaConsola(instanteProgramadoLlegadaVuelo);

                // ✅ Usar ID real de la simulación desde el contexto
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());

                // ✅ Enviar log simplificado
                String codigoVuelo = vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + idVuelo;
                String nombreDestino = almacenAlQueLlego.getNombreCiudad() != null
                        ? almacenAlQueLlego.getNombreCiudad()
                        : "Almacén " + almacenAlQueLlego.getId();

                System.out.println("📡 Enviando evento WebSocket para llegada de vuelo con productos: "
                        + codigoVuelo);

                webSocketService.enviarEventoVueloLlegada(
                        idSimulacion,
                        codigoVuelo,
                        nombreDestino,
                        cantidadADescargar,
                        instanteProgramadoLlegadaVuelo);
            }
            catch (Exception e){
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }
        else if (webSocketService != null && cantidadADescargar == 0){
            // System.out.println("⏭️ Vuelo ID=" + idVuelo + " llegó vacío - NO se envía por
            // WebSocket");
        }
    }

    @Override
    public int getPriority()
    {
        return 2; // después de cualquier salida de vuelo
    }
}
