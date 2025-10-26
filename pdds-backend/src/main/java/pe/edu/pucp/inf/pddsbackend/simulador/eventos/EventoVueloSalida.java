package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.ArrayList;
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
        Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
        ctx.log("EventoVueloSalida: VUELO EN SÍ: "+vuelo); //debug
        if (vuelo == null) {
            ctx.log("EventoVueloSalida: VueloEntidad no encontrado id=" + idVuelo);
            return;
        }
        Almacen almacenOrigen = ctx.getEstado().obtenerAlmacenPorId(vuelo.getIdAlmacenOrigen());
        if (almacenOrigen == null) {
            ctx.log("EventoVueloSalida: AlmacenEntidad no encontrado id=" + vuelo.getIdAlmacenOrigen());
            return;
        }
        List<Producto> productosACargar = ctx.obtenerProductosEnVueloId(idVuelo);
        int capacidadTotalACargar = productosACargar.size();
        if(capacidadTotalACargar>0){ // importante para que no colapse de forma estúpida
            // Liberar espacio en almacén origen; PERO OJO, CASO DE ALMACÉN INFINITO!! SE TELETRANSPORTA NOMÁS
            if ( ! almacenOrigen.isEsInfinito() && ! almacenOrigen.quitarVarios(productosACargar) )
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: Almacén no tiene cantidad para cargar lo programado, cantidad: "
                        + capacidadTotalACargar + " Solo tiene lleno: "+ almacenOrigen.getCapacidadOcupada() + " de: " + almacenOrigen.getCapacidadMaxima() );

            // Actualizar capacidad ocupada del vuelo
            if ( ! vuelo.agregarVarios(productosACargar))
                throw new ColapsadoExceptionTemporal("EventoVueloSalida: VueloEntidad no tiene capacidad para llevar lo programado: "
                        + capacidadTotalACargar+" Solo tiene capacidad actual de: "+vuelo.getCapacidadSinOcupar()+" de max:"+vuelo.getCapacidadMaxima());
        }
            ctx.log(String.format("EventoVueloSalida: VueloEntidad %d salió con %d productos desde almacén %d",
                idVuelo, capacidadTotalACargar, almacenOrigen.getId()));
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
