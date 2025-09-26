package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
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
        VueloParaAlgoritmo vuelo = ctx.getEstadoGlobal().getVuelos().get(idVuelo);
        if (vuelo == null) {
            ctx.log("Vuelo no encontrado id=" + idVuelo);
            return;
        }
        AlmacenParaAlgoritmo almacenOrigen = ctx.getEstadoGlobal().getAlmacenFromId(vuelo.getIdAlmacenOrigen());
        if (almacenOrigen == null) {
            ctx.log("Almacen no encontrado id=" + vuelo.getIdAlmacenOrigen());
            return;
        }

        // Procesar rutas activas que usan este vuelo
        List<RutaProgramadaParaAlgoritmo> rutasConEsteVuelo =
                ctx.getEstadoGlobal().getRutasSolucionQueGeneraAlgoritmo().stream()
                        .filter(r -> r.isActivo() && r.getIdsVuelosEnOrden().contains(idVuelo))
                        .toList();

        int capacidadTotalACargar = 0;
        for (RutaProgramadaParaAlgoritmo ruta : rutasConEsteVuelo) {
            // Solo cargar si es el primer vuelo de la ruta
            if (ruta.getIdsVuelosEnOrden().getFirst().equals(idVuelo)) {
                capacidadTotalACargar += ruta.getCantidadTotalOParcial();

                // Actualizar pedido, productos en tránsito, PODRÍA SER!!!
//                PedidoParaAlgoritmo pedido = ctx.getEstadoGlobal()
//                        .getPedidos().get(ruta.getIdPedidoAsociado());
//                if (pedido != null) {
////                    pedido.incrementarProductosEnTransito(ruta.getCantidadTotalOParcial()); // Podría ser...
//                }
            }
        }

        // Liberar espacio en almacén origen
        almacenOrigen.desocuparCapacidad(capacidadTotalACargar);

        // Actualizar capacidad ocupada del vuelo
        vuelo.ocuparCapacidad(capacidadTotalACargar);

        ctx.log(String.format("Vuelo %d salió con %d productos desde almacén %d",
                idVuelo, capacidadTotalACargar, almacenOrigen.getId()));
    }
}
