package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloLlegada implements  EventoSimulacion{
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoLlegadaVuelo;

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
        VueloParaAlgoritmo vueloParaAlgoritmo = ctx.getEstadoGlobal().getVuelos().get(idVuelo);
        if (vueloParaAlgoritmo == null) {
            ctx.log("Vuelo no encontrado id=" + idVuelo);
            return;
        }
        AlmacenParaAlgoritmo almacenAlQueLlego = ctx.getEstadoGlobal().getAlmacenFromId(vueloParaAlgoritmo.getIdAlmacenDestino());
        if (almacenAlQueLlego == null) {
            ctx.log("Almacen no encontrado id=" + vueloParaAlgoritmo.getIdAlmacenDestino());
            return;
        }
        //verificar si colapsó.
        almacenAlQueLlego.ocuparCapacidad(vueloParaAlgoritmo.getCapacidadOcupadaProductos());

    }
}
