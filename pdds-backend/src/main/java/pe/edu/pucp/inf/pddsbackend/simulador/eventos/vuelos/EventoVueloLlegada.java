package pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos.EventoEntregaPedidoTras2h;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventoVueloLlegada extends EventoSimulacion {
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoLlegadaVuelo;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

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
    public void procesar(ContextoSimulacion ctx) throws Exception {
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        if (vuelo == null){
            ctx.log("❌ EventoVueloLlegada: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        
        Almacen almacenDestino = vuelo.getAlmacenDestino();
        if (almacenDestino == null){
            ctx.log("❌ EventoVueloLlegada: Almacén destino no encontrado id="
                    + vuelo.getAlmacenDestino());
            return;
        }

        // Obtener productos a descargar
        List<Producto> productosADescargar = vuelo.getInventario();

        loggeo(ctx, vuelo, productosADescargar);
        webSocketYLoggear(vuelo, ctx, productosADescargar.size(), almacenDestino);

        // Empieza el proceso de descargue
        if (!productosADescargar.isEmpty()) {
            // Agrega los productos al inventario del almacén destino
            if (!almacenDestino.registrarProductov2(productosADescargar)){
                throw new ColapsadoExceptionTemporal(
                        "EventoVueloLlegada: El almacén no aguanta lo traído por el vuelo: " + vuelo
                                + "\nEl almacén es: " + almacenDestino
                                + "\nLos pedidos que estaría atendiendo son:\n"
                                + ctx.imprimirMinipedidosDeRutasDeVueloFinal(vuelo));
            }

            webSocket2(vuelo, almacenDestino, ctx);

            // Transaccionar en vuelo, almacén y productos:
            if (!vuelo.borrarProductoSincronizado(productosADescargar)) {
                throw new ColapsadoExceptionTemporal("EventoVueloLlegada: El vuelo "+vuelo+"\n no puede desocuparse los productos (" + productosADescargar.size() + "): " + productosADescargar);
            }

            // Obtener las programaciones donde este vuelo es el último
            List<Programacion> programacionesFinales = ctx.getEstado().getProgramaciones().stream()
                    .filter(pg -> {
                        Ruta ruta = pg.getRuta();
                        return ruta.verificarUltimoVuelo(vuelo) && pg.validarIncancelable_I(instanteProgramadoLlegadaVuelo);
                    }).collect(Collectors.toList());

            // Procesar las programacionesFinales. Realmente el evento de entrega es solo para liberar el espacio del almacen
            Instant instanteEntregaPedido = instanteProgramadoLlegadaVuelo.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
            
            for (Programacion pg : programacionesFinales) {
                Producto producto = pg.getProducto();

                ctx.programarEvento(new EventoEntregaPedidoTras2h(
                        pg.getPedido().getId(),
                        almacenDestino.getId(),
                        producto,
                        UUID.randomUUID(),
                        instanteEntregaPedido,
                        webSocketService));
            }
        }
    }

    private void loggeo(ContextoSimulacion ctx, Vuelo vuelo, List<Producto> productosADescargar) {
        int cantidadADescargar = productosADescargar.size();

        if (cantidadADescargar > 0){
            ctx.log(String.format(
                    "🛫 VUELO LLEGADA: ID=%d | Origen=%d → Destino=%d | Productos=%d | Inicio=%s | Fin(ahora)=%s",
                    idVuelo, vuelo.getAlmacenSalida().getId(), vuelo.getAlmacenDestino().getId(),
                    cantidadADescargar, vuelo.getInstanteSalida(), instanteProgramadoLlegadaVuelo));
            ctx.log("✅ Productos a descargar en vuelo ID=" + idVuelo + " ("
                    + vuelo.getInventario().size() + " prods): ");
//                    + vuelo.getInventario());
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
