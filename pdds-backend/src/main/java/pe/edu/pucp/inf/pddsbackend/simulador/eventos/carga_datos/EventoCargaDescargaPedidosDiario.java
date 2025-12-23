package pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.SchedulerSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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

        /****/
        Instant manana = ctx.getAhora().plus(Duration.ofDays(1));

        boolean esDiaAdia = ctx.getParams().tipoSimulacion().equals(TipoSimulacion.TIEMPO_REAL) || ctx.getParams().factorDeVelocidad().equals(1.0);
                
        List<PedidoEntidad> pedidos = this.pedidoRepository
                .findByInstanteRegistroAfterAndInstanteRegistroBeforeFetchAlmacenAndDiaADia
                        (ctx.getAhora(), manana, esDiaAdia);

ctx.log("Se han tomado pedidos que " + (esDiaAdia?"SON":"NO SON") + " para el día a día.");
System.out.println("Se han tomado pedidos que " + (esDiaAdia?"SON":"NO SON") + " para el día a día.");

        List<Pedido> pedidosNuevos = pedidos.stream()
                .map(Pedido::desdeEntidad).collect(Collectors.toList());

        /* DEBIDO A QUE LA CONSTRUCCION DE LOS VUELOS NO CONSIDERA LAS REFERENCIAS, SE TIENE QUE SETEAR  */
        for (Pedido p : pedidosNuevos) {
            Almacen almDestino = ctx.getEstado().buscarAlmacenPorId(p.getAlmacenDestino().getId());
            p.setAlmacenDestino(almDestino);
        }
        /* */

        ctx.getEstado().agregarPedidosNuevos(pedidosNuevos);
        ctx.getEstado().borrarPedidosViejos(ctx.obtenerElAhora());

        SchedulerSimulacion motor = ctx.getScheduler();
        Instant siguienteProgramacion = this.instanteProgramadoCargarDescargarPedidos.plus(Duration.ofDays(Hiperparametros.INTERVALO_DIAS_AGREGAR_PEDIDOS));

        motor.programar(new EventoCargaDescargaPedidosDiario(
                UUID.randomUUID(),
                siguienteProgramacion,
                webSocketService,
                this.pedidoRepository));

        if (webSocketService != null) {
            List<PedidoListadoDTO> pedidosWS = ctx.getEstado().getPedidos().values().stream()
					.map(p -> new PedidoListadoDTO(
						p.getId(),
						"Cliente genérico",
						p.getAlmacenDestino().getNombreCiudad(),
						p.getCantidadProductos(),
						p.obtenerCantidadProductosEntregados(),
						0,
						p.obtenerCantidadProductosProgramados(),
						p.getEstado().name(),
						p.getInstanteRegistro().toString(),
						p.getInstanteLimite().toString(),
						p.obtenerSiPedidoEsIntercontinental(),
						null)
                    ).collect(Collectors.toList());

ctx.log("Enviando todos los pedidos del estado global a WS " + pedidosWS.size());

            webSocketService.enviarPedidosPeriodico(
                    ctx.getIdSimulacion().toString(),
                    ctx.obtenerElAhora(),
                    pedidosWS);
        }

        /****/

ctx.log("Se ha cargado los pedidos y eliminado los viejos: " + pedidosNuevos.size());
System.out.println("Se ha cargado los pedidos y eliminado los viejos: " + pedidosNuevos.size());
    }

    @Override
    public int getPriority()
    {
        return 2;
    }
}
