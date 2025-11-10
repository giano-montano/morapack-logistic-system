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

        
        // Log para archivo
        if(capacidadTotalACargar>0)
            ctx.log(String.format("🛫 VUELO SALIDA: ID=%d | Origen=%d → Destino=%d | Productos=%d | Hora=%s",
                idVuelo, vuelo.getIdAlmacenOrigen(), vuelo.getIdAlmacenDestino(), 
                capacidadTotalACargar, instanteProgramadoSalidaVuelo));
        
        // ✅ Enviar eventos WebSocket SOLO si el vuelo tiene productos
        if (webSocketService != null && capacidadTotalACargar > 0) {
            System.out.println("\n=============== VUELO SALIENDO ===============");
            System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
            System.out.println("ID Vuelo: " + idVuelo);
            System.out.println("Almacén Origen: ID=" + vuelo.getIdAlmacenOrigen());
            System.out.println("Almacén Destino: ID=" + vuelo.getIdAlmacenDestino());
            System.out.println("Cantidad Productos: " + capacidadTotalACargar);
            System.out.println("===============================================\n");
            try {
                Almacen almacenDestino = ctx.getEstado().obtenerAlmacenPorId(vuelo.getIdAlmacenDestino());
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());
                String codigoVuelo = vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + idVuelo;
                String nombreOrigen = almacenOrigen.getNombreCiudad() != null ? 
                        almacenOrigen.getNombreCiudad() : "Almacén " + almacenOrigen.getId();
                String nombreDestino = almacenDestino != null && almacenDestino.getNombreCiudad() != null ? 
                        almacenDestino.getNombreCiudad() : "Almacén " + vuelo.getIdAlmacenDestino();
                
                System.out.println("📡 Enviando evento WebSocket para vuelo con productos: " + codigoVuelo);
                
                // Enviar notificación de salida + log descriptivo
                webSocketService.enviarEventoVueloSalida(
                    idSimulacion,
                    idVuelo,
                    codigoVuelo,
                    nombreOrigen,
                    nombreDestino,
                    capacidadTotalACargar,
                    instanteProgramadoSalidaVuelo
                );
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        } else if (webSocketService != null && capacidadTotalACargar == 0) {
//            System.out.println("⏭️  Vuelo ID=" + idVuelo + " sin productos - NO se envía por WebSocket");
        }
        
        if(capacidadTotalACargar>0){ // importante para que no colapse de forma estúpida
            System.out.println("🔍 VERIFICANDO CAPACIDADES:");
            System.out.println("   📦 Productos a cargar: " + capacidadTotalACargar);
            System.out.println("   🏢 Almacén Origen (ID=" + almacenOrigen.getId() + "):");
            System.out.println("      - Es infinito: " + almacenOrigen.isEsInfinito());
            System.out.println("      - Capacidad ocupada: " + almacenOrigen.getCapacidadOcupada());
            System.out.println("      - Capacidad máxima: " + almacenOrigen.getCapacidadMaxima());
            System.out.println("      - Capacidad disponible: " + (almacenOrigen.getCapacidadMaxima() - almacenOrigen.getCapacidadOcupada()));
            System.out.println("   ✈️ Vuelo (ID=" + idVuelo + "):");
            System.out.println("      - Capacidad sin ocupar: " + vuelo.getCapacidadSinOcupar());
            System.out.println("      - Capacidad máxima: " + vuelo.getCapacidadMaxima());
            
            // Liberar espacio en almacén origen; PERO OJO, CASO DE ALMACÉN INFINITO!! SE TELETRANSPORTA NOMÁS
            if ( ! almacenOrigen.isEsInfinito() && ! almacenOrigen.quitarVarios(productosACargar) ) {
                System.out.println("❌ ¡COLAPSO! Almacén origen no tiene suficientes productos");
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Almacén no tiene cantidad para cargar lo programado, cantidad: "
                        + capacidadTotalACargar + " Solo tiene lleno: "+ almacenOrigen.getCapacidadOcupada() + " de: " + almacenOrigen.getCapacidadMaxima() );
            }

            // Actualizar capacidad ocupada del vuelo
            if ( ! vuelo.agregarVarios(productosACargar)) {
                System.out.println("❌ ¡COLAPSO! Vuelo no tiene capacidad suficiente");
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Vuelo no tiene capacidad para llevar lo programado: "
                        + capacidadTotalACargar+" Solo tiene capacidad actual de: "+vuelo.getCapacidadSinOcupar()+" de max:"+vuelo.getCapacidadMaxima());
            }

            // CAMBIO DE DE ESTADO EN LOS PRODUCTOS QUE NO EXISTÍAN, AHORA SÍ EXISTIRÁN
            productosACargar.stream().forEach(producto -> { if(!producto.isExiste()) producto.setExiste(true); });
            
            System.out.println("✅ Productos cargados exitosamente");
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
