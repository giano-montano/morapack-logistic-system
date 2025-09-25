package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoVueloSalida implements  EventoSimulacion{
    @NotNull long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull Instant instanteProgramadoSalidaVuelo;

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramadoSalidaVuelo;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        VueloParaAlgoritmo vueloParaAlgoritmo = ctx.getEstadoGlobal().getVuelos().get(idVuelo);
        if (vueloParaAlgoritmo == null) {
            ctx.log("Vuelo no encontrado id=" + idVuelo);
            return;
        }
        AlmacenParaAlgoritmo almacenDelQueSale = ctx.getEstadoGlobal().getAlmacenFromId(vueloParaAlgoritmo.getIdAlmacenOrigen());
        if (almacenDelQueSale == null) {
            ctx.log("Almacen no encontrado id=" + vueloParaAlgoritmo.getIdAlmacenOrigen());
            return;
        }
        // retirar productos

    }
}
