package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.exceptions.ErrorDuranteAlgoritmoException;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.*;

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
        // 0) preparar DTO para planner
        RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
                .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
                .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
                .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
                .seed(ctx.getFormaRealizarPlanificacion().getSeed())
                .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
                .build();
//        ctx.log("Creé DTO de planif: " + dto);

        // 1) construir EntradaProblemaPlanificacion desde el estado en memoria:
        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .almacenes(new HashMap<>(ctx.getEstadoGlobalSimuladoNoAlgoritmo().getAlmacenes()))
                .vuelos(new HashMap<>(ctx.getEstadoGlobalSimuladoNoAlgoritmo().getVuelos()))
                .pedidos(new HashMap<>(ctx.getEstadoGlobalSimuladoNoAlgoritmo().getPedidos()))
                .seed(dto.getSeed())
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();
//        ctx.log("Creé entrada de planif: " + entrada);

        // 2) ejecutar planner con timeout (mismo hilo del motor usando Executor para timeout)
        ExecutorService exec = Executors.newSingleThreadExecutor();
//        ctx.log("Creé exec: " + exec);
        Future<SalidaProblemaPlanificacion> futuraSalida = exec.submit(
                () -> planificacionService.realizarPlanificacionConEntrada(dto, entrada));
        SalidaProblemaPlanificacion salida = null;
        try {
            salida = futuraSalida
                    .get(ctx.getParams().maximoTimeOutSegundosPorPlanif()!=null?
                            ctx.getParams().maximoTimeOutSegundosPorPlanif()
                            :MAXIMO_ESPERA_ALGORITMO_SEGUNDOS, TimeUnit.SECONDS);
            ctx.log("salida del futuro size: " + salida.getRutasProgramadasParaSatisfacerTodoPedido().size());
        } catch (TimeoutException te) {
            futuraSalida.cancel(true);
            ctx.log("Planner TIMEOUT en " + ctx.obtenerElAhora());
            // registrar métrica / marcar evento
        } catch (Exception ex) {
            ctx.log("Planner ERROR: " + ex.getMessage());
        } finally {
            ctx.log("Finally ");
            exec.shutdownNow();
        }

        if (salida == null) {
            // nada que aplicar
            return;
        }

        // 3) validar salida contra ctx (capacidad aún disponible)
        boolean ok = !salida.isColapsado(); /*validarSalidaContraContexto(salida, ctx);*/
        boolean errorEjecucion = salida.isHuboErrorEjecucion(); /*validarSalidaContraContexto(salida, ctx);*/
        ctx.log("Ok? y error?: " + ok + " "+ errorEjecucion);
        if (!ok) {
//            ctx.getScheduler().programar(new EventoTriggerColapsado() );
            ctx.log("COLAPSO DETECTADO en " + ctx.obtenerElAhora());
            ctx.registrarMetrica("tiempo_hasta_colapso_minutos",
                    Duration.between(ctx.getReloj().instant(), ctx.getAhora()).toMinutes());
            ctx.setColapsado(true);
            throw new ColapsadoExceptionTemporal("Morí.");// TEMPORAL!! DEBERÍA HABER UNA BANDERA?, YA QUE ES ERROR DE LÓGICA DE NEGOCIO
        }
        if(errorEjecucion) throw new ErrorDuranteAlgoritmoException(salida.getError());

        // 4) aplicar la salida en memoria (reservas, marcar pedidos programados)
        aplicarSalidaEnContexto(salida, ctx);
        ctx.log("Apliqué salida en contexto, num rutas salida: " + salida.getRutasProgramadasParaSatisfacerTodoPedido().size());
        // 5) guardar en ctx.solucionesAcumuladas (para reportes)
//        ctx.getSolucionesAcumuladas().add(salida);
        ctx.log("Solus acumuladas: "+ctx.getSolucionesAcumuladas().size() );
        // 6) checkpoint opcional: si tocó (cada N triggers o tiempo)
//        if (ctx.shouldCheckpointNow()) {
//            persistirCheckpoint(ctx);
//        }
    }

    public void aplicarSalidaEnContexto(SalidaProblemaPlanificacion salida, ContextoSimulacion ctx) {
        if(salida.getRutasProgramadasParaSatisfacerTodoPedido().isEmpty()){
            ctx.log("Salida obtenida no tiene rutas ni está colapsada o con error, todos pedidos ya atendidos");
        }else {
            ctx.getSolucionesAcumuladas().add(salida); // ESTO ES LO EFECTIVO!
        }
        // desactivar las anteriores:
//        for(RutaProgramadaParaAlgoritmo ruta:  ctx.getEstadoGlobalSimuladoNoAlgoritmo().getRutasSolucionQueGeneraAlgoritmo()){
//           ruta.setActivo(false);
//        }
//        ctx.log("Rutas viejas a desechar (puestas en false): " + ctx.getEstadoGlobalSimuladoNoAlgoritmo().getRutasSolucionQueGeneraAlgoritmo());
        //poner las nuevas que de por sí son true
//        for(RutaProgramadaParaAlgoritmo ruta: salida.getRutasProgramadasParaSatisfacerTodoPedido()){
////            ctx.log("Intentando anadir esta ruta al contexto: \n" + ruta.);
////            ctx.getEstadoGlobal().anadirRutaSolucion(ruta); // ESTO NO!! EL ESTADO GLOBAL DEL ALGORITMO SE REINICIA.
//        }
//        ctx.log("Rutas nuevas (en true)" + salida.getRutasProgramadasParaSatisfacerTodoPedido().size());

    }
}
// intentar reparacion simple o loggear si !ok
//            SalidaProblemaPlanificacion reparada = intentarReparacionLocal(salida, ctx);
//            if (reparada == null) {
//                ctx.log("No se pudo aplicar solucion: conflictos detectados");
//                return;
//            }
//            salida = reparada;