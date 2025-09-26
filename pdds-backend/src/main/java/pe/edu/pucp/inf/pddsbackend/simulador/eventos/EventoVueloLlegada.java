package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@AllArgsConstructor
public class EventoVueloLlegada implements  EventoSimulacion{
    @NotNull
    long idVuelo;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoLlegadaVuelo;

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
        VueloParaAlgoritmo vuelo = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getVuelos().get(idVuelo);
        if (vuelo == null) {
            ctx.log("Vuelo no encontrado id=" + idVuelo);
            return;
        }
        AlmacenParaAlgoritmo almacenAlQueLlego = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenFromId(vuelo.getIdAlmacenDestino());
        if (almacenAlQueLlego == null) {
            ctx.log("Almacen no encontrado id=" + vuelo.getIdAlmacenDestino());
            return;
        }
        //verificar si colapsó.
        almacenAlQueLlego.ocuparCapacidad(vuelo.getCapacidadOcupadaProductos());
        //obtenemos los minipedidos que atiende este último vuelo según rutas.
        List<RutaProgramadaParaAlgoritmo> rutasDondeElVueloEsFinal = ctx.getSolucionesAcumuladas().getLast().getRutasProgramadasParaSatisfacerTodoPedido()
                //SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL CUAL
                .stream().filter(rutaProgramadaParaAlgoritmo -> {
                    LinkedList<Long> vuelosEnOrden = rutaProgramadaParaAlgoritmo.getIdsVuelosEnOrden();
                    if (vuelosEnOrden.getLast() == vuelo.getId()) return true;
                    return false;
                }).toList();
        ctx.log("Llegó el vuelo " + vuelo.getId() + " Rutas asociadas donde es el último destino: " + rutasDondeElVueloEsFinal);
        //lógica de evento de liberación en 2h y entrega de pedido
        for(RutaProgramadaParaAlgoritmo rutita : rutasDondeElVueloEsFinal ){
            ctx.programarEvento(new EventoEntregaPedidoTras2h(rutita.getIdPedidoAsociado(), almacenAlQueLlego.getId(),
                    rutita.getCantidadTotalOParcial(),
                    UUID.randomUUID(), instanteProgramadoLlegadaVuelo.plus(HORAS_QUE_SE_TARDA_EN_RECOGER_EL_CLIENTE, ChronoUnit.HOURS )));
        }

    }
}
