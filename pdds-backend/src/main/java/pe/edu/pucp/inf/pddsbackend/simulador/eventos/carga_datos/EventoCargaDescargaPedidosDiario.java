package pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.SchedulerSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos.EventoLlegadaPedido;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Getter
@AllArgsConstructor
public class EventoCargaDescargaPedidosDiario extends EventoSimulacion
{
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoCargarDescargarPedidos;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;
    private final PedidoRepository pedidoRepository;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramadoCargarDescargarPedidos;
    }

    @Override
    @Transactional(readOnly = true)
    public void procesar(ContextoSimulacion ctx) throws Exception
    {
        ctx.log("Comenzando a procesar EventoCargaDescargaPedidosDiario");
        System.out.println("Comenzando a procesar EventoCargaDescargaPedidosDiario");

        Instant manana = ctx.getAhora().plus(1, ChronoUnit.DAYS);

        boolean esDiaAdia = ctx.getParams().tipoSimulacion().equals(TipoSimulacion.TIEMPO_REAL)
                || ctx.getParams().factorDeVelocidad().equals(1.0)
                ;
        List<PedidoEntidad> pedidos = pedidoRepository
                .findByInstanteRegistroAfterAndInstanteRegistroBeforeFetchAlmacenAndDiaADia
                        (ctx.getAhora(), manana, esDiaAdia);

        ctx.log("Se han tomado pedidos que " + (esDiaAdia?"SON":"NO SON") + " para el día a día.");
        System.out.println("Se han tomado pedidos que " + (esDiaAdia?"SON":"NO SON") + " para el día a día.");

        List<Pedido> pedidosNuevos = pedidos
                .stream()
                .map(Pedido::desdeEntidad).toList();

        System.out.println("Los pedidos nuevos son: " + pedidosNuevos.size());
        ctx.getEstado().anadirPedidosNuevos(pedidosNuevos);

        if (!ctx.getEstado().limpiarPedidosViejosSegunInstante(ctx.obtenerElAhora()))
        {
            System.out.println("NO SE BORRÓ NINGÚN PEDIDO VIEJO DE HACE UNA SEMANA");
        }

        SchedulerSimulacion motor = ctx.getScheduler();
        for (Pedido p : pedidosNuevos)
        {
            motor.programar(
                    new EventoLlegadaPedido(p.getId(), UUID.randomUUID(), p.getInstanteRegistro()));
        }

        // Volverse a autoprogramar COMO BUENO
        motor.programar(new EventoCargaDescargaPedidosDiario(
                UUID.randomUUID(),
                instanteProgramadoCargarDescargarPedidos
                        .plus(Hiperparametros.INTERVALO_DIAS_AGREGAR_PEDIDOS, ChronoUnit.DAYS),
                webSocketService,
                pedidoRepository));

        if (webSocketService != null)
        {
            HashMap<Long, Almacen> alms = ctx.getEstado().getAlmacenes();

            List<PedidoListadoDTO> pedidosPaWS = ctx.getEstado().getPedidos().values().stream()
                    .map(p -> new PedidoListadoDTO(
                            p.getId(),
                            "Cliente genérico",
                            alms.get(p.getAlmacenDestino().getId()).getNombreCiudad(),
                            p.getCantidadProductos(),
                            p.getCantidadProductosSatisfechos(),
                            p.getCantidadProductos() - p.getCantidadProductosPendientes(),
                            p.getCantidadProductosPendientes(),//p.getCantidadProductosProgramados(),
                            p.getEstado().name(),
                            p.getInstanteRegistro().toString(),
                            p.getInstanteLimite().toString(),
                            p.isIntercontinentalAhora(),
                            null) // no supe...
                    )
                    .toList();

            ctx.log("Enviando todos los pedidos del estado global a WS " + pedidosPaWS.size());

            webSocketService.enviarPedidosPeriodico(
                    ctx.getIdSimulacion().toString(),
                    ctx.obtenerElAhora(),
                    pedidosPaWS);
        }

        ctx.log("Se ha cargado los pedidos y eliminado los viejos: " + pedidosNuevos.size());
        System.out.println(
                "Se ha cargado los pedidos y eliminado los viejos: " + pedidosNuevos.size());
    }

    @Override
    public int getPriority()
    {
        return 2;
    }
}
