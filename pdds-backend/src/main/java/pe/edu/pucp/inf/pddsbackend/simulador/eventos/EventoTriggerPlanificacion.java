package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.exceptions.ErrorDuranteAlgoritmoException;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class EventoTriggerPlanificacion implements EventoSimulacion {

    @NotNull UUID uuid;
    @NotNull Instant instanteProgramado ;

    private final PlanificacionService planificacionService;
    private static final int MAXIMO_ESPERA_ALGORITMO_SEGUNDOS = 300;

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return instanteProgramado;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        ctx.log("EventoTriggerPlanificacion: comenzando a planificar!" /*+ planificacionService.obtenerMetaDatos()*/);
        // 0) preparar DTO para planner
        RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
                .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
                .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
                .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
                .seed(ctx.getFormaRealizarPlanificacion().getSeed())
                .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
                .build();
        ctx.log("EventoTriggerPlanificacion: Creé DTO de planif (forma realizar planificación): " + dto);

        Map<Long, Vuelo> vuelosCopy = ctx.getEstado()
                .getVuelos().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Vuelo(e.getValue()) // copy constructor
                ));
        Map<Long, Almacen> almacenesCopy = ctx.getEstado()
                .getAlmacenes().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Almacen(e.getValue()) // copy constructor
                ));
        // 1) construir EntradaProblemaPlanificacion desde el estado en memoria:
        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(new EstadoGlobal(almacenesCopy, vuelosCopy, ctx.getEstado().getPedidos(),null))
//                .almacenes(new HashMap<>(almacenesCopy)) // qué?... ... ... :(
//                .vuelos(new HashMap<>(vuelosCopy)) //PASABA QUE LE PASABA OBJETOS MUTABLES, NECESITO DEEP COPY,
                //EN CUANTO A ALMACENES Y PEDIDOS, NO IMPORTA PORQUE NO MUTO NADA RELEVANTE PERO EN VUELOS
                //TOMABA LA OCUPADA XDDDDDDDDDDDDDDDD
//                .pedidos(new HashMap<>(ctx.getEstado().getPedidos()))
                .semilla(dto.getSeed())
                .instanteActual(Instant.now())
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();
//        ctx.log("Creé entrada de planif: " + entrada);
//        Long someId = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenes().keySet().iterator().next();
//        boolean same = ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenes().get(someId) ==
//                entrada.getAlmacenes().get(someId);
//        ctx.log("¿Misma instancia? " + same); // sí pes, siempre, es necesario deep copy


        // 2) ejecutar planner con timeout (mismo hilo del motor usando Executor para timeout)
        ExecutorService exec = Executors.newSingleThreadExecutor();
//        ctx.log("Creé exec: " + exec);
        Future<ResultadoAlgoritmoDTO> futuraSalida = exec.submit(
                () -> planificacionService.realizarPlanificacionConEntrada(dto, entrada));
        ResultadoAlgoritmoDTO res = null;
        try {
            res = futuraSalida
                    .get(ctx.getParams().maximoTimeOutSegundosPorPlanif()!=null?
                            ctx.getParams().maximoTimeOutSegundosPorPlanif()
                            :MAXIMO_ESPERA_ALGORITMO_SEGUNDOS, TimeUnit.SECONDS);
            ctx.log("EventoTriggerPlanificacion: res planificación num programs: " + res.salida().getProgramaciones().size());
        } catch (TimeoutException te) {
            futuraSalida.cancel(true);
            ctx.log("EventoTriggerPlanificacion: Planner TIMEOUT en " + ctx.obtenerElAhora());
            // registrar métrica / marcar evento
        } catch (Exception ex) {
            ctx.log("EventoTriggerPlanificacion: Planner ERROR: " + ex.getMessage());
        } finally {
//            ctx.log("Finally ");
            exec.shutdownNow();
        }
        assert res != null;
        SalidaProblemaPlanificacion salida = res.salida();
        if (salida == null) {
            // nada que aplicar
            return;
        }

        // 3) validar salida contra ctx (capacidad aún disponible)
        boolean ok = !salida.isColapsado(); /*validarSalidaContraContexto(salida, ctx);*/
        boolean errorEjecucion = salida.isHuboErrorEjecucion(); /*validarSalidaContraContexto(salida, ctx);*/
//        ctx.log("Ok? y error?: " + ok + " "+ errorEjecucion);
        if (!ok) {
//            ctx.getScheduler().programar(new EventoTriggerColapsado() );
            ctx.log("EventoTriggerPlanificacion: COLAPSO DETECTADO en " + ctx.obtenerElAhora());
            ctx.registrarMetrica("tiempo_hasta_colapso_minutos",
                    Duration.between(ctx.getReloj().instant(), ctx.getAhora()).toMinutes());
            ctx.setColapsado(true);
            throw new ColapsadoExceptionTemporal("Morí.");// TEMPORAL!! DEBERÍA HABER UNA BANDERA?, YA QUE ES ERROR DE LÓGICA DE NEGOCIO
        }
        if(errorEjecucion) throw new ErrorDuranteAlgoritmoException(salida.getError());

        // 4) aplicar la salida en memoria (reservas, marcar pedidos programados)
        aplicarSalidaEnContexto(salida, ctx);
        ctx.log("EventoTriggerPlanificacion: Apliqué salida en contexto, num programs salida: " + salida.getProgramaciones().size());
        // 5) guardar en ctx.solucionesAcumuladas (para reportes)
//        ctx.getSolucionesAcumuladas().add(salida);
        ctx.log("EventoTriggerPlanificacion: Solus acumuladas: "+ctx.getSolucionesAcumuladas().size() );
        // 6) checkpoint opcional: si tocó (cada N triggers o tiempo)
//        if (ctx.shouldCheckpointNow()) {
//            persistirCheckpoint(ctx);
//        }
    }

    public void aplicarSalidaEnContexto(SalidaProblemaPlanificacion salida, ContextoSimulacion ctx) {
        if(salida.getProgramaciones().isEmpty()){
            ctx.log("EventoTriggerPlanificacion: Salida obtenida no tiene rutas ni está colapsada o con error, todos pedidos ya atendidos");
        }else {
            ctx.getSolucionesAcumuladas().add(salida); // ESTO ES LO EFECTIVO!
        }
        // desactivar las anteriores:
//        for(Programacion ruta:  ctx.getEstadoGlobalSimuladoNoAlgoritmo().getRutasSolucionQueGeneraAlgoritmo()){
//           ruta.setActivo(false);
//        }
//        ctx.log("Rutas viejas a desechar (puestas en false): " + ctx.getEstadoGlobalSimuladoNoAlgoritmo().getRutasSolucionQueGeneraAlgoritmo());
        //poner las nuevas que de por sí son true
//        for(Programacion ruta: salida.getRutasProgramadasParaSatisfacerTodoPedido()){
////            ctx.log("Intentando anadir esta ruta al contexto: \n" + ruta.);
////            ctx.getEstadoGlobal().anadirRutaSolucion(ruta); // ESTO NO!! EL ESTADO GLOBAL DEL ALGORITMO SE REINICIA.
//        }
//        ctx.log("Rutas nuevas (en true)" + salida.getRutasProgramadasParaSatisfacerTodoPedido().size());

    }
    @Override
    public int getPriority() {
        return 4; // después de cualquier llegada de avión.
    }
}
// intentar reparacion simple o loggear si !ok
//            SalidaProblemaPlanificacion reparada = intentarReparacionLocal(salida, ctx);
//            if (reparada == null) {
//                ctx.log("No se pudo aplicar solucion: conflictos detectados");
//                return;
//            }
//            salida = reparada;