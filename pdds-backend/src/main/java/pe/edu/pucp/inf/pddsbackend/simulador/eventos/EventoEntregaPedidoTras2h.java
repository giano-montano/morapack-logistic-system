package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
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
    Producto productoAEntregar;
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
        if (ctx.getEstado().entregarProductoEnPedido(idPedido, productoAEntregar)){}
            ctx.log("EventoEntregaPedido: Cantidad entregada de más "+1);
        Almacen almOrigen = ctx.getEstado().getAlmacenes().get(idAlmacenDestino);

        if ( ! almOrigen.quitarProducto(productoAEntregar) )
            throw new ColapsadoExceptionTemporal("EventoEntregaPedido: COLAPSO DE CAPACIDAD DE ALMACEN");
        ctx.log(String.format(
                "EventoEntregaPedido: El cliente recogió %d productos de su pedido con id %d del almacén %d",
                1, idPedido, idAlmacenDestino));
    }

    @Override
    public int getPriority() {
        return 0;
    }
}