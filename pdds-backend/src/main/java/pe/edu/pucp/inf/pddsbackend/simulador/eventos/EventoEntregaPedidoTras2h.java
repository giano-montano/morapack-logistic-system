package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoEntregaPedidoTras2h implements  EventoSimulacion{
    @NotNull
    long idPedido;
    @NotNull
    long idAlmacenDestino;
    @NotNull
    int cantidad;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instante2hDespuesDeLlegadosProductosAAlmacenDestino;

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instante2hDespuesDeLlegadosProductosAAlmacenDestino;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        PedidoParaAlgoritmo pedidoEnCuestion = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getPedidos().get(idPedido);
        if (pedidoEnCuestion.agregarCantidadEntregada(cantidad)) ctx.log("EventoEntregaPedido: Cantidad entregada de más "+cantidad);
        AlmacenParaAlgoritmo almOrigen = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenes().get(idAlmacenDestino);
        if ( ! almOrigen.desocuparCapacidad(cantidad) ) throw new ColapsadoExceptionTemporal("EventoEntregaPedido: COLAPSO DE CAPACIDAD DE ALMACEN");
        ctx.log(String.format("EventoEntregaPedido: El cliente recogió %d productos de su pedido con id %d del almacén %d", cantidad, idPedido, idAlmacenDestino));
    }

    @Override
    public int getPriority() {
        return 0;
    }
}