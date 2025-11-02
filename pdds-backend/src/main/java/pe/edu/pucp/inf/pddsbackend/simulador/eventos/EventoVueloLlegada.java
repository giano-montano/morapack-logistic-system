package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventoVueloLlegada implements  EventoSimulacion{
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoLlegadaVuelo;
    
    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    private final int HORAS_QUE_SE_TARDA_EN_RECOGER_EL_CLIENTE = 2; //podría ser dinámico y parametrizable?
    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramadoLlegadaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        if (vuelo == null) {
            ctx.log("❌ EventoVueloLlegada: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        Almacen almacenAlQueLlego = ctx.getEstado().obtenerAlmacenPorId(vuelo.getIdAlmacenDestino());
        if (almacenAlQueLlego == null) {
            ctx.log("❌ EventoVueloLlegada: Almacén destino no encontrado id=" + vuelo.getIdAlmacenDestino());
            return;
        }
        
        //verificar si colapsó.
        List<Producto> productosADescargar = vuelo.getIdsProductosContenidos().stream()
                .map(uuid1 -> ctx.getEstado().obtenerProductoPorUuid(uuid1)).toList(); // del estado, lo real!
        int cantidadADescargar = vuelo.getCapacidadOcupada(); // debería coincidir con productosADescargar.size()

        // 🛬 LOG Y WEBSOCKET - SIEMPRE, INCLUSO SI LLEGA VACÍO
        System.out.println("\n🛬 =============== VUELO LLEGANDO ===============");
        System.out.println("⏰ Hora: " + instanteProgramadoLlegadaVuelo);
        System.out.println("✈️  ID Vuelo: " + idVuelo);
        System.out.println("📍 Almacén Origen: ID=" + vuelo.getIdAlmacenOrigen());
        System.out.println("🎯 Almacén Destino: ID=" + vuelo.getIdAlmacenDestino() + 
                         " (Ocupado: " + almacenAlQueLlego.getCapacidadOcupada() + "/" + almacenAlQueLlego.getCapacidadMaxima() + ")");
        System.out.println("📊 Cantidad Productos: " + cantidadADescargar);
        
        if (cantidadADescargar > 0) {
            // Imprimir productos individuales que están llegando
            System.out.println("📦 Productos en este vuelo:");
            for (Producto producto : productosADescargar) {
                System.out.println("   • Producto UUID: " + producto.getUuid() + 
                                 " | Entregado: " + producto.isEntregado());
            }
        } else {
            System.out.println("📭 Vuelo vacío (sin productos)");
        }
        System.out.println("===============================================\n");

        ctx.log("¿Coincide cant ocupada y cant de productos contenidos en vuelo al llegar?: "
                + productosADescargar.size() + " - " + cantidadADescargar);
        
        // Enviar evento WebSocket (antes de modificar el estado) - SIEMPRE, incluso si llega vacío
        if (webSocketService != null) {
            try {
                List<String> productosUUIDs = productosADescargar.stream()
                        .map(p -> p.getUuid().toString())
                        .collect(Collectors.toList());
                
                // ✅ Usar ID real de la simulación desde el contexto
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());
                
                // Calcular cuántos productos van a entrega inmediata (destino final)
                int entregasInmediatas = 0;
                int productosEnTransito = 0;
                
                if (cantidadADescargar > 0 && !ctx.getSolucionesAcumuladas().isEmpty()) {
                    List<Programacion> rutasDondeElVueloEsFinal = ctx.getSolucionesAcumuladas().getLast().getProgramaciones()
                            .stream().filter(prog -> {
                                LinkedList<Long> vuelosEnOrden = prog.getIdsVueloRuta();
                                return vuelosEnOrden.getLast() == vuelo.getId();
                            }).toList();
                    entregasInmediatas = rutasDondeElVueloEsFinal.size();
                    productosEnTransito = cantidadADescargar - entregasInmediatas;
                }
                
                webSocketService.enviarEventoVueloLlegada(
                    idSimulacion,
                    LocalDateTime.ofInstant(instanteProgramadoLlegadaVuelo, ZoneId.systemDefault()),
                    String.valueOf(idVuelo),
                    vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + idVuelo,
                    vuelo.getIdAlmacenDestino(),
                    almacenAlQueLlego.getNombreCiudad() != null ? 
                        almacenAlQueLlego.getNombreCiudad() : "Almacén " + almacenAlQueLlego.getId(),
                    cantidadADescargar,
                    productosUUIDs,
                    entregasInmediatas,
                    productosEnTransito
                );
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }

        if(cantidadADescargar>0) { // importante para que no colapse de forma estúpida
            if (!almacenAlQueLlego.agregarVarios(productosADescargar))
                throw new ColapsadoExceptionTemporal("EventoVueloLlegada: El almacén no aguanta lo traído por el vuelo: " + vuelo
                +"\nEl almacén es: "+almacenAlQueLlego+"\nLos pedidos que estaría atendiendo son:\n"+ctx.imprimirMinipedidosDeRutasDeVueloFinal(vuelo));
            if (!vuelo.quitarVarios(productosADescargar))
                throw new ColapsadoExceptionTemporal("EventoVueloLlegada: El vuelo no puede desocuparse la cantidad: "
                        + cantidadADescargar+", vuelo: " + vuelo);
            //obtenemos los minipedidos que atiende este último vuelo según rutas.
            List<Programacion> rutasDondeElVueloEsFinal = ctx.getSolucionesAcumuladas().getLast().getProgramaciones()
                    //SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL CUAL
                    .stream().filter(Programacion -> {
                        LinkedList<Long> vuelosEnOrden = Programacion.getIdsVueloRuta();
                        if (vuelosEnOrden.getLast() == vuelo.getId()) return true;
                        return false;
                    }).toList();
            ctx.log("EventoVueloLlegada: Llegó el vuelo " + vuelo.getId() + " Rutas asociadas donde es el último destino: " + rutasDondeElVueloEsFinal);
            //lógica de evento de liberación en 2h y entrega de pedido. Además, capacidad descargada por ruta...
            for (Programacion prog : rutasDondeElVueloEsFinal) {

                ctx.programarEvento(new EventoEntregaPedidoTras2h(prog.getIdPedido(), almacenAlQueLlego.getId(),
                        ctx.getEstado().obtenerProductoPorUuid(prog.getUuidProducto()),
                        UUID.randomUUID(), instanteProgramadoLlegadaVuelo.plus(HORAS_QUE_SE_TARDA_EN_RECOGER_EL_CLIENTE, ChronoUnit.HOURS),
                        webSocketService));
            }
        }
    }

    @Override
    public int getPriority() {
        return 2; // después de cualquier salida de vuelo
    }
}
