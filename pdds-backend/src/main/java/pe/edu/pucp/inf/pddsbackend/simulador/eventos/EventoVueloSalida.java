package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloSalida implements  EventoSimulacion{
    @NotNull long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull Instant instanteProgramadoSalidaVuelo;

    @Override
    public UUID getId() {return uuid;}

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramadoSalidaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        VueloParaAlgoritmo vuelo = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getVuelos().get(idVuelo);
        ctx.log("EventoVueloSalida: VUELO EN SÍ: "+vuelo); //debug
        if (vuelo == null) {
            ctx.log("EventoVueloSalida: Vuelo no encontrado id=" + idVuelo);
            return;
        }
        AlmacenParaAlgoritmo almacenOrigen = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenFromId(vuelo.getIdAlmacenOrigen());
        if (almacenOrigen == null) {
            ctx.log("EventoVueloSalida: Almacen no encontrado id=" + vuelo.getIdAlmacenOrigen());
            return;
        }
        SalidaProblemaPlanificacion ultimaSolucion = ctx.getSolucionesAcumuladas().getLast();
        // Procesar rutas activas que usan este vuelo
        List<RutaProgramadaParaAlgoritmo> rutasConEsteVuelo =
                ultimaSolucion.getRutasProgramadasParaSatisfacerTodoPedido().stream()
                        .filter(r -> r.isActivo() && r.getIdsVuelosEnOrden().contains(idVuelo))
                        .toList();
        ctx.log("EventoVueloSalida: Rutas con este vuelo "+ idVuelo +" a procesar: " + rutasConEsteVuelo);
        int capacidadTotalACargar = 0;
        for (RutaProgramadaParaAlgoritmo ruta : rutasConEsteVuelo) {
            // Solo cargar si es el primer vuelo de la ruta
            if (ruta.getIdsVuelosEnOrden().getFirst().equals(idVuelo)) {
                capacidadTotalACargar += ruta.getCantidadTotalOParcial();
            }
        }

        if(capacidadTotalACargar>0){ // importante para que no colapse de forma estúpida
            // Liberar espacio en almacén origen; PERO OJO, CASO DE ALMACÉN INFINITO!! SE TELETRANSPORTA NOMÁS
            if ( ! almacenOrigen.isEsInfinito() && ! almacenOrigen.desocuparCapacidad(capacidadTotalACargar) )
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Almacén no tiene cantidad para cargar lo programado, cantidad: "
                        + capacidadTotalACargar + " Solo tiene lleno: "+ almacenOrigen.getCapacidadOcupada() + " de: " + almacenOrigen.getCapacidadMaxima() );

            // Actualizar capacidad ocupada del vuelo
            if ( ! vuelo.ocuparCapacidad(capacidadTotalACargar))
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Vuelo no tiene capacidad para llevar lo programado: "
                        + capacidadTotalACargar+" Solo tiene capacidad actual de: "+vuelo.getCapacidadSinOcupar()+" de max:"+vuelo.getCapacidadMaximaProductos());
        }
            ctx.log(String.format("EventoVueloSalida: Vuelo %d salió con %d productos desde almacén %d",
                idVuelo, capacidadTotalACargar, almacenOrigen.getId()));
    }

    @Override
    public int getPriority() {
        return 1; // dsp de pickup/ entrega a cualquier cliente
    }
}


// Actualizar pedido, productos en tránsito, PODRÍA SER!!!
//                PedidoParaAlgoritmo pedido = ctx.getEstadoGlobal()
//                        .getPedidos().get(ruta.getIdPedidoAsociado());
//                if (pedido != null) {
////                    pedido.incrementarProductosEnTransito(ruta.getCantidadTotalOParcial()); // Podría ser...
//                }
