package pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloSalida implements EventoSimulacion {
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
        
        List<Producto> productosACargar = ctx.obtenerProductosEnVueloIdParaCargarVuelo(idVuelo);
        int capacidadTotalACargar = productosACargar.size();

        // Log para archivo
        if(capacidadTotalACargar>0){
            ctx.log(String.format("🛫 VUELO SALIDA: ID=%d | Origen=%d %s → Destino=%d | Productos=%d | Inicio(ahora)=%s | Fin=%s",
                idVuelo, vuelo.getIdAlmacenOrigen(),
                    almacenOrigen.isEsInfinito()?"Infinito":"Intermedio"  ,vuelo.getIdAlmacenDestino(),
                capacidadTotalACargar, instanteProgramadoSalidaVuelo, vuelo.getFin()));
            ctx.log("✅ Productos a cargar en vuelo ID=" + idVuelo + " ("+productosACargar.size()+
                    "): " + productosACargar);
        }
        // ✅ Enviar eventos WebSocket SOLO si el vuelo tiene productos
        if (webSocketService != null && capacidadTotalACargar > 0) {
            System.out.println("\n=============== VUELO SALIENDO ===============");
            System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
            System.out.println("Fin: " + vuelo.getFin());
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
                    vuelo.getCapacidadMaxima(), // ✅ Agregar capacidad máxima
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
            System.out.println("      - UUIDs prods que tiene dentro ahora: " + almacenOrigen.getIdsProductosExistentes());
            System.out.println("   ✈️ Vuelo (ID=" + idVuelo + "):");
            System.out.println("      - Capacidad sin ocupar: " + vuelo.getCapacidadSinOcupar());
            System.out.println("      - Capacidad máxima: " + vuelo.getCapacidadMaxima());

            // Liberar espacio en almacén origen; PERO OJO, CASO DE ALMACÉN INFINITO!! SE TELETRANSPORTA NOMÁS
            if ( ! almacenOrigen.isEsInfinito() && ! almacenOrigen.quitarVarios(productosACargar) ) {
                System.out.println("❌ ¡COLAPSO! Almacén origen no tiene los productos para cargar: "+capacidadTotalACargar);
                ctx.log("✅ Productos que tiene el almacen origen con id " + almacenOrigen.getId()
                        + " ("+ almacenOrigen.getIdsProductosExistentes().size()+" prods): "
                        + almacenOrigen.getIdsProductosExistentes());
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: "+ almacenOrigen+"\nno tiene los productos para cargar que son: "
                        + capacidadTotalACargar + ". Solo tiene lleno: "+ almacenOrigen.getCapacidadOcupada() + " de: " + almacenOrigen.getCapacidadMaxima() );
            }
            
            // ✅ Notificar cambio de capacidad del almacén origen SOLO si NO es infinito
            if (webSocketService != null && !almacenOrigen.isEsInfinito()) {
                try {
                    webSocketService.enviarCambioCapacidadAlmacen(
                        String.valueOf(ctx.getIdSimulacion()),
                        almacenOrigen.getId(),
                        almacenOrigen.getCapacidadOcupada(),
                        almacenOrigen.getCapacidadMaxima()
                    );
                } catch (Exception e) {
                    System.err.println("⚠️ Error al enviar cambio de capacidad de almacén: " + e.getMessage());
                }
            }

            // Actualizar capacidad ocupada del vuelo
            if ( ! vuelo.agregarVarios(productosACargar)) {
                System.out.println("❌ ¡COLAPSO! Vuelo no tiene capacidad suficiente");
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Vuelo no tiene capacidad para llevar lo programado: "
                        + capacidadTotalACargar+" Solo tiene capacidad actual de: "+vuelo.getCapacidadSinOcupar()+" de max:"+vuelo.getCapacidadMaxima());
            }

            // CAMBIO DE DE ESTADO EN LOS PRODUCTOS QUE NO EXISTÍAN, AHORA SÍ EXISTIRÁN Y SE CeARGARÁN EN EL VUELO
            // ADEMÁS, SI ES EL ULTIMO VUELO DE UNA PROGRAMACION, EL PRODUCTO DEBE MARCARSE COMO PRONTO A ENTREGAR
            productosACargar.forEach(producto -> {
                if(!producto.isExiste()) producto.setExiste(true);
                producto.embarcarEnVuelo(idVuelo);
            });
            
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
