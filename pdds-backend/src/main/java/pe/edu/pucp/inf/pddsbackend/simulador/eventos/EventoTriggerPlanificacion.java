package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.exceptions.ErrorDuranteAlgoritmoException;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

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
                .build();

        // 1) construir EntradaProblemaPlanificacion desde el estado en memoria:
        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .almacenes(new HashMap<>(ctx.getEstadoGlobal().getAlmacenes()))
                .vuelos(new HashMap<>(ctx.getEstadoGlobal().getVuelos()))
                .pedidos(new HashMap<>(ctx.getEstadoGlobal().getPedidos()))
                .seed(dto.getSeed())
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();

        // 2) ejecutar planner con timeout (mismo hilo del motor usando Executor para timeout)
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<SalidaProblemaPlanificacion> future = exec.submit(() -> planificacionService.realizarPlanificacionConEntrada(dto, entrada));
        SalidaProblemaPlanificacion salida = null;
        try {

            salida = future.get(ctx.getParams().maximoTimeOutSegundos()!=null?ctx.getParams().maximoTimeOutSegundos():300, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            ctx.log("Planner TIMEOUT en " + ctx.obtenerElAhora());
            // registrar métrica / marcar evento
        } catch (Exception ex) {
            ctx.log("Planner ERROR: " + ex.getMessage());
        } finally {
            exec.shutdownNow();
        }

        if (salida == null) {
            // nada que aplicar
            return;
        }

        // 3) validar salida contra ctx (capacidad aún disponible)
        boolean ok = !salida.isColapsado(); /*validarSalidaContraContexto(salida, ctx);*/
        boolean errorEjecucion = !salida.isHuboErrorEjecucion(); /*validarSalidaContraContexto(salida, ctx);*/
        if (!ok) {
//            ctx.getScheduler().programar(new EventoTriggerColapsado() );
            throw new ColapsadoExceptionTemporal("Morí.");// TEMPORAL!! DEBERÍA HABER UNA BANDERA?, YA QUE ES ERROR DE LÓGICA DE NEGOCIO
        }
        if(errorEjecucion) throw new ErrorDuranteAlgoritmoException(salida.getError());

        // 4) aplicar la salida en memoria (reservas, marcar pedidos programados)
        aplicarSalidaEnContexto(salida, ctx);

        // 5) guardar en ctx.solucionesAcumuladas (para reportes)
        ctx.getSolucionesAcumuladas().add(salida);

        // 6) checkpoint opcional: si tocó (cada N triggers o tiempo)
//        if (ctx.shouldCheckpointNow()) {
//            persistirCheckpoint(ctx);
//        }
    }

    public void aplicarSalidaEnContexto(SalidaProblemaPlanificacion salida, ContextoSimulacion ctx) {
        ctx.getSolucionesAcumuladas().add(salida);
        // desactivar las anteriores:
        for(RutaProgramadaParaAlgoritmo ruta:  ctx.getEstadoGlobal().getRutasSolucionQueGeneraAlgoritmo()){
           ruta.setActivo(false);
        }
        ctx.log("Rutas viejas a desechar (puestas en false): " + ctx.getEstadoGlobal().getRutasSolucionQueGeneraAlgoritmo());
        //poner las nuevas que de por sí son true
        for(RutaProgramadaParaAlgoritmo ruta: salida.getRutasProgramadasParaSatisfacerTodoPedido()){
            ctx.getEstadoGlobal().anadirRutaSolucion(ruta);
        }
        ctx.log("Rutas nuevas (en true)" + salida.getRutasProgramadasParaSatisfacerTodoPedido());

    }


}
// intentar reparacion simple o loggear si !ok
//            SalidaProblemaPlanificacion reparada = intentarReparacionLocal(salida, ctx);
//            if (reparada == null) {
//                ctx.log("No se pudo aplicar solucion: conflictos detectados");
//                return;
//            }
//            salida = reparada;