package pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloSalida extends EventoSimulacion
{
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoSalidaVuelo;

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
        return instanteProgramadoSalidaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception{
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        if (vuelo == null){
            ctx.log("❌ EventoVueloSalida: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        Almacen almacenOrigen = vuelo.getAlmacenSalida();
        if (almacenOrigen == null){
            ctx.log("❌ EventoVueloSalida: Almacén origen no encontrado id="+ vuelo.getAlmacenSalida().getId());
            return;
        }

        List<Programacion> prograsACargar = ctx.obtenerProgramacionesEnVueloIdParaCargarVuelo(idVuelo);

        List<Producto> prodsACargar = prograsACargar.stream().map(Programacion::getProducto).toList();
        int capacidadTotalACargar = prodsACargar.size();

        // loggear y web socket
        loggearyWebSocketVueloSalida(capacidadTotalACargar,vuelo,ctx,almacenOrigen, prograsACargar);

        // Liberar espacio en almacén origen; PERO OJO CON EL CASO DE ALMACÉN INFINITO!! AHÍ SE TELETRANSPORTA NOMÁS
        if (!almacenOrigen.isInfinito()){
            for(Programacion prog : prograsACargar){
                if( !almacenOrigen.quitarProdSimu(prog.getProducto()) ){
                    System.out.println("❌ ¡COLAPSO! Almacén origen no tiene los productos para cargar: "
                            + capacidadTotalACargar);
                    ctx.log("COLAPSO!: Productos que tiene el almacen origen con id " + almacenOrigen.getId()
                            + " (" + almacenOrigen.getInventario().size() + " prods): "
                            + almacenOrigen.getInventario());

                    throw new ColapsadoExceptionTemporal("EventoVueloSalida: " + almacenOrigen
                            + "\nno tiene los productos para cargar que son: "
                            + capacidadTotalACargar + ". Solo tiene lleno: "
                            + almacenOrigen.getInventario().size() + " de: "
                            + almacenOrigen.getCapacidad()); // COMENTADO PARA PRUEBAS - REVISAR LUEGO
                }
                // ACTUALIZACIÓN DEL PRODUCTO AL SALIR UN VUELO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                Producto productoAActualizar = prog.getProducto();
                if(prog.soloTiene1VueloYYaSalio(instanteProgramadoSalidaVuelo)){
                    if(prog.seriaIncancelable(instanteProgramadoSalidaVuelo)){
                        productoAActualizar.transPlanificadoExistenteAIncancelable();
                    }else{
                        // NO PASA NADA: ERA UN PROD DE ALM INTERMEDIO QUE YA EXISTÍA Y ESTABA PLANIFICADO, LO SIGUE ESTANDO
                    }
                }
            }
        } else {
            for(Programacion prog : prograsACargar){
                // ACTUALIZACIÓN DEL PRODUCTO AL SALIR UN VUELO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                Producto productoAActualizar = prog.getProducto();
                if(prog.soloTiene1VueloYYaSalio(instanteProgramadoSalidaVuelo)){
                    if(prog.seriaIncancelable(instanteProgramadoSalidaVuelo)){
                        productoAActualizar.transPlanificadoNoExistenteAIncancelable();
                    }else{
                        productoAActualizar.transPlanificadoNoExistenteAPlanificadoExistente();
                    }
                }
            }
        }

        loggearyWebSocketVueloSalida2(almacenOrigen, ctx, capacidadTotalACargar);

        // Actualizar capacidad ocupada del vuelo
        if (!vuelo.agregarVariosSimu(prodsACargar)){
            System.out.println("❌ ¡COLAPSO! Vuelo no tiene capacidad suficiente");
            throw new ColapsadoExceptionTemporal(
                    "EventoVueloSalida: Vuelo no tiene capacidad para llevar lo programado: "
                            + capacidadTotalACargar + " Solo tiene capacidad actual de: "
                            + ( vuelo.getCapacidad() - vuelo.getInventario().size() ) + " de max:"
                            + vuelo.getCapacidad());
        }
        if(!prodsACargar.isEmpty())
            System.out.println("✅ Productos cargados en el avión exitosamente");
    }

    private void loggearyWebSocketVueloSalida2(
            Almacen almacenOrigen,
            ContextoSimulacion ctx,
            int capacidadTotalACargar) {
        // ✅ Notificar cambio de capacidad del almacén origen SOLO si NO es infinito
        if (webSocketService != null && !almacenOrigen.isInfinito() && capacidadTotalACargar>0){
            try{
                webSocketService.enviarCambioCapacidadAlmacen(
                        String.valueOf(ctx.getIdSimulacion()),
                        almacenOrigen.getId(),
                        almacenOrigen.getInventario().size(),
                        almacenOrigen.getCapacidad());
            }
            catch (Exception e){
                System.err.println(
                        "⚠️ Error al enviar cambio de capacidad de almacén: " + e.getMessage());
            }
        }
    }

    private void loggearyWebSocketVueloSalida(int capacidadTotalACargar, Vuelo vuelo,
    ContextoSimulacion ctx, Almacen almacenOrigen ,List<Programacion> prograsACargar) {
        // Log para archivo
        if (capacidadTotalACargar > 0){
            ctx.log(String.format(
                    "🛫 VUELO SALIDA: ID=%d | Origen=%d %s → Destino=%d | Productos=%d | Inicio(ahora)=%s | Fin=%s",
                    idVuelo, vuelo.getAlmacenSalida().getId(),
                    almacenOrigen.isInfinito() ? "Infinito" : "Intermedio",
                    vuelo.getAlmacenDestino().getId(),
                    capacidadTotalACargar, instanteProgramadoSalidaVuelo, vuelo.getInstanteLlegada()));
            ctx.log("✅ Productos a actualizar (nuevos o existentes) en vuelo ID=" +
                    idVuelo + " (" + prograsACargar.size() +
                    "): " + prograsACargar);
        }

        // ✅ Enviar eventos WebSocket SOLO si el vuelo tiene productos
        if (webSocketService != null && capacidadTotalACargar > 0){
            vuelo.loggearSalidaConsola(instanteProgramadoSalidaVuelo, capacidadTotalACargar);
            try{
                Almacen almacenDestino = vuelo.getAlmacenDestino();
                String idSimulacion = String.valueOf(ctx.getIdSimulacion());
                String codigoVuelo = vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + idVuelo;
                String nombreOrigen = almacenOrigen.getNombreCiudad() != null
                        ? almacenOrigen.getNombreCiudad()
                        : "Almacén " + almacenOrigen.getId();
                String nombreDestino = almacenDestino != null
                        && almacenDestino.getNombreCiudad() != null
                        ? almacenDestino.getNombreCiudad()
                        : "Almacén " + vuelo.getAlmacenDestino();
                System.out.println(
                        "📡 Enviando evento WebSocket para vuelo con productos: " + codigoVuelo);
                // Enviar notificación de salida + log descriptivo
                webSocketService.enviarEventoVueloSalida(
                        idSimulacion,
                        idVuelo,
                        codigoVuelo,
                        nombreOrigen,
                        nombreDestino,
                        capacidadTotalACargar,
                        vuelo.getCapacidad(), // ✅ Agregar capacidad máxima
                        instanteProgramadoSalidaVuelo);
            }
            catch (Exception e){
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }else if (webSocketService != null && capacidadTotalACargar == 0){
            // System.out.println("⏭️ Vuelo ID=" + idVuelo + " sin productos - NO se envía por WebSocket");
        }
        if (capacidadTotalACargar > 0){ // importante para que no colapse de forma estúpida
            System.out.println("🔍 VERIFICANDO CAPACIDADES:");
            System.out.println("   📦 Productos a cargar: " + capacidadTotalACargar);
            System.out.println("   🏢 Almacén Origen (ID=" + almacenOrigen.getId() + "):");
            System.out.println("      - Es infinito: " + almacenOrigen.isInfinito());
            System.out.println("      - Capacidad ocupada: " + almacenOrigen.getInventario().size());
            System.out.println("      - Capacidad máxima: " + almacenOrigen.getCapacidad());
            System.out.println("      - Capacidad disponible: "+ (almacenOrigen.getCapacidad() - almacenOrigen.getInventario().size()));
            System.out.println("      - UUIDs prods que tiene dentro ahora: "+ almacenOrigen.getInventario());
            System.out.println("   ✈️ Vuelo (ID=" + idVuelo + "):");
            System.out.println("      - Capacidad sin ocupar: " + (vuelo.getCapacidad() - vuelo.getInventario().size()));
            System.out.println("      - Capacidad máxima: " + vuelo.getCapacidad());


        }
    }

    @Override
    public int getPriority(){
        return 1; // dsp de pickup/ entrega a cualquier cliente
    }
}

// Actualizar pedido, productos en tránsito, PODRÍA SER!!!
// Pedido pedido = ctx.getEstadoGlobal()
// .getPedidos().get(ruta.getIdPedidoAsociado());
// if (pedido != null) {
//// pedido.incrementarProductosEnTransito(ruta.getCantidadTotalOParcial()); //
// Podría ser...
// }
