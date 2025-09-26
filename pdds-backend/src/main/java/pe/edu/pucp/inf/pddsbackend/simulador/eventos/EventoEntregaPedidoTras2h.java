package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAlgoritmo;
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
        pedidoEnCuestion.agregarCantidadEntregada(cantidad);
        AlmacenParaAlgoritmo almOrigen = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenes().get(idAlmacenDestino);
        almOrigen.desocuparCapacidad(cantidad);
        ctx.log(String.format("El cliente recogió %d productos de su pedido con id %d del almacén %d", cantidad, idPedido, idAlmacenDestino));
    }
}