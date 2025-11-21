package pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.CancelacionVuelo;
import pe.edu.pucp.inf.pddsbackend.repositories.CancelacionVueloRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoCancelacionVuelo;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventoCargaCancelacionesUnico implements EventoSimulacion
{

    @NotNull
    UUID uuid;
    @NotNull
    Instant cuando;

    private final CancelacionVueloRepository cancelacionVueloRepository;
    private final PlanificacionService planificacionService;
    private final SimulacionWebSocketService webSocketService;
    private final ConfiguracionService configuracionService;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return cuando;
    }

    @Override
    public void procesar(ContextoSimulacion ctx)
    {
        List<CancelacionVuelo> cancelaciones = cancelacionVueloRepository.findAll();
        EstadoGlobal estadoGlobal = ctx.getEstado();

        Map<String, Vuelo> vuelos = estadoGlobal.getVuelos().values().stream().collect(
                Collectors.toMap(Vuelo::getCodigo, vuelo -> vuelo));

        for (CancelacionVuelo cancelacion : cancelaciones)
        {
            Vuelo vueloACancelar = vuelos.get(cancelacion.getCodigoGeneradoCoincidenteConVuelo());

            if (vueloACancelar == null)
                continue;
            ctx.log("Se ha programado la cancelación de un vuelo: " + vueloACancelar + "\n"
                    + "para la fecha " + cancelacion.getFechaCancelacion());

            ctx.getScheduler()
                    .programar(new EventoCancelacionVuelo(vueloACancelar.getId(), UUID.randomUUID(),
                            cancelacion.getFechaCancelacion(),
                            planificacionService, webSocketService, configuracionService));
        }

    }

    @Override
    public int getPriority()
    {
        return 3;
    }

}
