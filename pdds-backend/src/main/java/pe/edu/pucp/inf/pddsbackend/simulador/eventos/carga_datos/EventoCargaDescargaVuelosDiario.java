package pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloProgramadoRepository;
import pe.edu.pucp.inf.pddsbackend.services.implementations.VueloServiceImpl;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.SchedulerSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoVueloLlegada;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoVueloSalida;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventoCargaDescargaVuelosDiario implements EventoSimulacion
{
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramadoCargarDescargarVuelos;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;
    private final VueloProgramadoRepository vueloProgramadoRepository;
    private final VueloService vueloService;
    private final AlmacenRepository almacenRepository;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramadoCargarDescargarVuelos;
    }

    @Override
    @Transactional(readOnly = true)
    public void procesar(ContextoSimulacion ctx) throws Exception
    {
        ctx.log("Comenzando a procesar EventoCargaDescargaVuelosDiario, hora ctx y de eveneto: " +
                ctx.getAhora() + " - " + instanteProgramadoCargarDescargarVuelos);
        System.out.println("Comenzando a procesar EventoCargaDescargaVuelosDiario");

        if (!ctx.getEstado().limpiarVuelosViejosSegunInstante(
                instanteProgramadoCargarDescargarVuelos /*
                                                         * más fiable que el ahora del contexto???
                                                         */))
        {
            System.out.println("NO SE BORRÓ NINGÚN VUELO VIEJO POR ALGUNA RAZÓN.");

        }

        List<VueloProgramado> planVuelo = vueloProgramadoRepository.findByActivoTrue(); // mejor
                                                                                        // mantenerlo
                                                                                        // en el
                                                                                        // contexto
                                                                                        // inicializado?
                                                                                        // Temporalmente
                                                                                        // dejemoslo
                                                                                        // asi
        if (planVuelo == null || planVuelo.isEmpty())
            return;

        // Map<Long, Almacen> almacenes = ctx.getEstado().getAlmacenes();
        Map<Long, AlmacenEntidad> almacenes = almacenRepository.findAlmacenByActivoTrue().stream()
                .collect(Collectors.toMap(AlmacenEntidad::getId, almacenEntidad -> almacenEntidad));

        Set<String> vuelosYaExistentes = ctx.getEstado().getVuelos().values().stream().map(
                Vuelo::getCodigo).collect(Collectors.toSet());

        VueloServiceImpl.GenerationResult gen = vueloService.generateFlightsInMemory(planVuelo,
                instanteProgramadoCargarDescargarVuelos, /*
                                                          * más fiable que el ahora del contexto???
                                                          */
                3, true, almacenes, vuelosYaExistentes);

        System.out.println("errores en vuelos:" + gen.getErrors());
        System.out.println("skipped en vuelos:" + gen.getSkipped());

        List<Vuelo> vuelosNuevos = gen.getVuelos();
        // vuelosNuevos.forEach(System.out::println);
        // Ahora tenemos solo los vuelos nuevos a agregar
        ctx.getEstado().anadirVuelosNuevos(vuelosNuevos);

        SchedulerSimulacion motor = ctx.getScheduler();
        for (Vuelo v : vuelosNuevos)
        {
            boolean existe = ctx.getEstado().getVuelos().containsKey(v.getId());
            if (!existe)
            {
                ctx.log("BUG DEBUG: vuelo agregado pero no encontrado en estado al marcarComoProgramado. idVuelo="
                        + v.getId() +
                        " totalVuelosEstado=" + ctx.getEstado().getVuelos().size() +
                        " primerosIds=" + ctx.getEstado().getVuelos().keySet().stream().limit(5)
                                .collect(Collectors.toList()));
            }

            motor.programar(new EventoVueloSalida(v.getId(), UUID.randomUUID(), v.getInicio(),
                    webSocketService));
            motor.programar(new EventoVueloLlegada(v.getId(), UUID.randomUUID(), v.getFin(),
                    webSocketService));
        }

        // Volverse a autoprogramar COMO BUENO
        motor.programar(new EventoCargaDescargaVuelosDiario(
                UUID.randomUUID(),
                instanteProgramadoCargarDescargarVuelos
                        .plus(Hiperparametros.INTERVALO_DIAS_AGREGAR_VUELOS, ChronoUnit.DAYS),
                webSocketService,
                vueloProgramadoRepository,
                vueloService,
                almacenRepository));

        if (webSocketService != null)
        {

            List<VueloDTO> vuelosPaWS = ctx.getEstado().getVuelos().values().stream()
                    .map(v -> new VueloDTO(
                            v.getId(),
                            v.getCodigo(),
                            v.getIdAlmacenOrigen(),
                            v.getIdAlmacenDestino(),
                            v.getInicio(),
                            v.getFin(),
                            v.getCapacidadMaxima(),
                            v.getCapacidadOcupada(),
                            v.isCancelado(),
                            v.isEsIntercontinental(),
                            v.isCancelado()))
                    .toList();

            ctx.log("Enviando todos los vuelos del estado global a WS " + vuelosPaWS.size());

            webSocketService.enviarVuelosPeriodico(
                    ctx.getIdSimulacion().toString(),
                    ctx.obtenerElAhora(),
                    vuelosPaWS);
        }

        ctx.log("Se ha cargado los vuelos y eliminado los viejos: " + vuelosNuevos.size());
        System.out.println(
                "Los vuelos nuevos son (también se eliminaron viejos): " + vuelosNuevos.size());

        ctx.log("el estado global luce tal que: " + ctx.getEstado());
        Instant init = ctx.getEstado().getVuelos().values().stream()
                .min(Comparator.comparing(Vuelo::getInicio))
                .map(Vuelo::getInicio)
                .orElse(null);
        Instant fin = ctx.getEstado().getVuelos().values().stream()
                .max(Comparator.comparing(Vuelo::getFin))
                .map(Vuelo::getFin)
                .orElse(null);
        ctx.log("el init es " + init + " el fin es " + fin);
    }

    @Override
    public int getPriority()
    {
        return 1;
    }
}
