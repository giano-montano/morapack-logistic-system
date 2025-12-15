package pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoLlegadaPedido implements EventoSimulacion
{
    @NotNull
    long idPedido;
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteRegistroPedido;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteRegistroPedido;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception
    {
        // 1) obtener pedido desde estado global (ya existe)
        Pedido pedido = ctx.getEstado().getPedidos().get(idPedido); // me da referencia :D
        if (pedido == null)
        {
            ctx.log("EventoLlegadaPedido: PedidoEntidad no encontrado id=" + idPedido);
            return;
        }
        // ctx.log(String.format("EventoLlegadaPedido: PedidoEntidad %d ha llegado: %d
        // productos hacia almacén %d",
        // idPedido, pedido.getCantidadProductosPedidos(),
        // pedido.getAlmacenDestino()));

        // 2) marcar como 'disponible' en el pool (ya está en map pero podemos cambiar
        // flags)
        // pedido.setInstanteRegistro(this.instanteRegistroPedido); // aseguramos el
        // instante
        // pedido.setCantidadProgramada(0);
        // (si tenías un map de pendientes mantenlo actualizado)
        // ctx.getEstadoGlobal().getPedidos().put(idPedido, pedido);

        // 3) política: ¿disparar planner?
        // boolean dispararPorUmbral = ctx.getParams().getPendingThreshold() != null
        // && ctx.getEstadoGlobal().countPedidosPendientes() >=
        // ctx.getParams().getPendingThreshold();

        // if (dispararPorUmbral) {
        // Instant when = ctx.obtenerElAhora().plusMillis(1); // epsilon
        // ctx.programarEvento(new EventoTriggerPlanificacion(when, UUID.randomUUID(),
        // "umbral"));
        // }

        // 4) opcional: si quieres simular notificación al cliente, marcarComoProgramado
        // EventoNotificacion

    }

    @Override
    public int getPriority()
    {
        return 3; // después decualquier asdasfs
    }
}
