package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventoVueloSalida implements  EventoSimulacion{
    @NotNull long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull Instant instanteProgramadoSalidaVuelo;
    
    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    @Override
    public UUID getId() {return uuid;}

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramadoSalidaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        if (vuelo == null) {
            ctx.log("❌ EventoVueloSalida: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        Almacen almacenOrigen = ctx.getEstado().obtenerAlmacenPorId(vuelo.getIdAlmacenOrigen());
        if (almacenOrigen == null) {
            ctx.log("❌ EventoVueloSalida: Almacén origen no encontrado id=" + vuelo.getIdAlmacenOrigen());
            return;
        }
        
        List<Producto> productosACargar = ctx.obtenerProductosEnVueloId(idVuelo);
        int capacidadTotalACargar = productosACargar.size();
        
        // 🛫 LOG Y WEBSOCKET - SIEMPRE, INCLUSO SI VA VACÍO
        System.out.println("\n🛫 =============== VUELO SALIENDO ===============");
        System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
        System.out.println("ID Vuelo: " + idVuelo);
        System.out.println("📦 Almacén Origen: ID=" + vuelo.getIdAlmacenOrigen() + 
                         " (Ocupado: " + almacenOrigen.getCapacidadOcupada() + "/" + almacenOrigen.getCapacidadMaxima() + ")");
        System.out.println("🎯 Almacén Destino: ID=" + vuelo.getIdAlmacenDestino());
        System.out.println("📊 Cantidad Productos: " + capacidadTotalACargar + 
                         " (Capacidad vuelo: " + vuelo.getCapacidadSinOcupar() + "/" + vuelo.getCapacidadMaxima() + ")");
        
        if (capacidadTotalACargar > 0) {
            // Imprimir productos individuales que están saliendo
            System.out.println("📦 Productos en este vuelo:");
            for (Producto producto : productosACargar) {
                System.out.println("   • Producto UUID: " + producto.getUuid() + 
                                 " | Entregado: " + producto.isEntregado() +
                                 " | Existe: " + producto.isExiste());
            }
        } else {
            System.out.println("📭 Vuelo vacío (sin productos asignados)");
        }
        System.out.println("===============================================\n");
        
        // Log para archivo
        ctx.log(String.format("🛫 VUELO SALIDA: ID=%d | Origen=%d → Destino=%d | Productos=%d | Hora=%s",
                idVuelo, vuelo.getIdAlmacenOrigen(), vuelo.getIdAlmacenDestino(), 
                capacidadTotalACargar, instanteProgramadoSalidaVuelo));
        
        // Enviar evento WebSocket - SIEMPRE, incluso si va vacío
        if (webSocketService != null) {
            try {
                Almacen almacenDestino = ctx.getEstado().obtenerAlmacenPorId(vuelo.getIdAlmacenDestino());
                List<String> productosUUIDs = productosACargar.stream()
                        .map(p -> p.getUuid().toString())
                        .collect(Collectors.toList());
                
                // ✅ Usar ID real de la simulación desde el contexto
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());
                
                webSocketService.enviarEventoVueloSalida(
                    idSimulacion,
                    LocalDateTime.ofInstant(instanteProgramadoSalidaVuelo, ZoneId.systemDefault()),
                    String.valueOf(idVuelo),
                    vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + idVuelo,
                    vuelo.getIdAlmacenOrigen(),
                    almacenOrigen.getNombreCiudad() != null ? almacenOrigen.getNombreCiudad() : "Almacén " + almacenOrigen.getId(),
                    vuelo.getIdAlmacenDestino(),
                    almacenDestino != null && almacenDestino.getNombreCiudad() != null ? 
                        almacenDestino.getNombreCiudad() : "Almacén " + vuelo.getIdAlmacenDestino(),
                    vuelo.getCapacidadMaxima(),
                    capacidadTotalACargar,
                    productosUUIDs
                );
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }
        
        if(capacidadTotalACargar>0){ // importante para que no colapse de forma estúpida
            // Liberar espacio en almacén origen; PERO OJO, CASO DE ALMACÉN INFINITO!! SE TELETRANSPORTA NOMÁS
            if ( ! almacenOrigen.isEsInfinito() && ! almacenOrigen.quitarVarios(productosACargar) )
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Almacén no tiene cantidad para cargar lo programado, cantidad: "
                        + capacidadTotalACargar + " Solo tiene lleno: "+ almacenOrigen.getCapacidadOcupada() + " de: " + almacenOrigen.getCapacidadMaxima() );

            // Actualizar capacidad ocupada del vuelo
            if ( ! vuelo.agregarVarios(productosACargar))
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Vuelo no tiene capacidad para llevar lo programado: "
                        + capacidadTotalACargar+" Solo tiene capacidad actual de: "+vuelo.getCapacidadSinOcupar()+" de max:"+vuelo.getCapacidadMaxima());
        }
    }

    @Override
    public int getPriority() {
        return 1; // dsp de pickup/ entrega a cualquier cliente
    }
}


// Actualizar pedido, productos en tránsito, PODRÍA SER!!!
//                Pedido pedido = ctx.getEstadoGlobal()
//                        .getPedidos().get(ruta.getIdPedidoAsociado());
//                if (pedido != null) {
////                    pedido.incrementarProductosEnTransito(ruta.getCantidadTotalOParcial()); // Podría ser...
//                }
