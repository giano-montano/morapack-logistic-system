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
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class EventoTriggerPlanificacion implements EventoSimulacion {

    @NotNull UUID uuid;
    @NotNull Instant instanteProgramado ;

    private final PlanificacionService planificacionService;
    
    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;
    
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
        
        // 📋 LOG INICIO DE PLANIFICACIÓN
        System.out.println("\n📋 =========== TRIGGER PLANIFICACIÓN ===========");
        System.out.println("⏰ Hora: " + instanteProgramado); // <- se supone que es lo mismo que ctx.getAhora no?
        System.out.println("🔢 Número de planificación: " + (ctx.getContadorPlanificaciones() + 1));
        System.out.println("📊 Pedidos pendientes: " + ctx.getEstado().contarPedidosPendientes());
        System.out.println("===============================================\n");
        
        ctx.log("📋 EventoTriggerPlanificacion: Comenzando planificación #" + (ctx.getContadorPlanificaciones() + 1));
        
        // ✅ Enviar log simplificado de planificación
        String idSimulacion = String.valueOf(ctx.getIdSimulacion());
        
        if (webSocketService != null) {
            try {
                webSocketService.enviarEventoPlanificacion(
                    idSimulacion,
                    instanteProgramado
                );
            } catch (Exception e) {
                System.err.println("⚠️ Error al enviar evento WebSocket: " + e.getMessage());
            }
        }
        
        // 0) preparar DTO para planner
        // ✅ CRÍTICO: Pasar el instante actual de la simulación para obtener vuelos correctos
        RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
                .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
                .instanteActual(ctx.getAhora()) // ✅ Usar tiempo actual de la simulación
                .instanteDesdeTomarPedidos( ctx.getInicioSimulacion() )
                .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
                .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
                .seed(ctx.getFormaRealizarPlanificacion().getSeed())
                .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
                .usarModoMock(ctx.getFormaRealizarPlanificacion().getUsarModoMock()) // ⚠️ IMPORTANTE: pasar el flag de modo mock
                .build();
        ctx.log("EventoTriggerPlanificacion: DTO creado - Modo Mock: " + dto.getUsarModoMock());

        Map<Long, Vuelo> vuelosBase = ctx.getEstado().getVuelos();
        Map<Long, Vuelo> vuelosParaAlgoritmo = vuelosBase.entrySet().stream()
                .filter(longVueloEntry ->
                        !vuelosBase.get(longVueloEntry).isCancelado()
                        && vuelosBase.get(longVueloEntry).getFin().isBefore(
                                instanteProgramado.plus(3, ChronoUnit.DAYS))
                        && !vuelosBase.get(longVueloEntry).getInicio().isBefore(ctx.getInicioSimulacion())
                        // El vuelo no está cancelado y llega antes del instante en que se planificará más 3 días
                        // (ya que se toman los pedidos solo hasta ahora! Si eso cambia, acá también deberíamos cambiar)
                        // Además se toman solo los vuelos desde que inició la simulación (posiblemente en curso y que
                        // traerán productos interesantes)
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Vuelo(e.getValue()) // copy constructor
                ));
        Map<Long, Almacen> almacenesParaAlgoritmo = ctx.getEstado()
                .getAlmacenes().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Almacen(e.getValue()) // copy constructor
                ));

        Map<Long, Pedido> pedidosBase = ctx.getEstado().getPedidos();
        Map<Long, Pedido> pedidosParaAlgoritmo = pedidosBase.entrySet().stream()
                .filter(longPedidoEntry ->
                        !pedidosBase.get(longPedidoEntry).getInstanteRegistro().isBefore(ctx.getInicioSimulacion())
                        && pedidosBase.get(longPedidoEntry).getInstanteRegistro().isBefore(instanteProgramado)
                        // pedido que se haya registrado
                        // después o igual al inicio de la simu pero antes del instante en que se planifica.
                ).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue)
                ); // Referencia directa, no copia... estará bien?

        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(new EstadoGlobal(almacenesParaAlgoritmo, vuelosParaAlgoritmo, pedidosParaAlgoritmo,null))
                .semilla(dto.getSeed())
                .instanteActual( ctx.obtenerElAhora() !=null ? ctx.obtenerElAhora() : Instant.now() )
                .parametrosOpcionalesPersonalizados(dto.getParametros())
                .build();

        ExecutorService exec = Executors.newSingleThreadExecutor();

        // Guardamos las programaciones activas hasta ahora para desactivarlas si es que la planif sale bien.
        List<Programacion> programacionesActivas = ctx.getEstado().getProgramaciones().stream()
                .filter(Programacion::isActivo).toList();

        Future<ResultadoAlgoritmoDTO> futuraSalida = exec.submit(
                () -> planificacionService.realizarPlanificacionConEntrada(dto, entrada));
        ResultadoAlgoritmoDTO res = null;
        try {
            res = futuraSalida
                    .get(ctx.getParams().maximoTimeOutSegundosPorPlanif()!=null?
                            ctx.getParams().maximoTimeOutSegundosPorPlanif()
                            :MAXIMO_ESPERA_ALGORITMO_SEGUNDOS, TimeUnit.SECONDS);
            if(res != null) { // cambios necesarios en el estado/contexto
                for(Programacion programacionActiva:programacionesActivas){
                    programacionActiva.setActivo(false); // Las programaciones no activas de planifs pasadas ya no se
                    // toman en cuenta
                }
                ctx.setUltimaPlanificacion(ctx.obtenerElAhora());
            }
            
            // ✅ LOG RESULTADO DE PLANIFICACIÓN
            System.out.println("\n✅ ========= PLANIFICACIÓN COMPLETADA =========");
            System.out.println("⏰ Hora: " + ctx.obtenerElAhora());
            System.out.println("📦 Programaciones generadas: " + res.salida().getProgramaciones().size());
            System.out.println("⚡ Tiempo ejecución: " + res.tiempoEjecucionMs() + " ms");
            System.out.println("📈 Fitness: " + res.fitness());
            System.out.println("===============================================\n");
            
            ctx.log("✅ EventoTriggerPlanificacion: Planificación exitosa - " + res.salida().getProgramaciones().size() + " programaciones");
            
            // Ya se envió el log al inicio, no es necesario enviar más eventos
        } catch (TimeoutException te) {
            futuraSalida.cancel(true);
            System.out.println("\n⏱️  ========= TIMEOUT PLANIFICACIÓN =========");
            System.out.println("⏰ Hora: " + ctx.obtenerElAhora());
            System.out.println("⚠️  El algoritmo excedió el tiempo máximo");
            System.out.println("===============================================\n");
            ctx.log("⏱️  EventoTriggerPlanificacion: TIMEOUT en " + ctx.obtenerElAhora());
            
            // Ya se envió el log al inicio, no enviar eventos adicionales
            
            // registrar métrica / marcar evento
        } catch (Exception ex) {
            System.out.println("\n❌ ========= ERROR PLANIFICACIÓN =========");
            System.out.println("⏰ Hora: " + ctx.obtenerElAhora());
            System.out.println("❌ Error: " + ex.getMessage());
            System.out.println("===============================================\n");
            ctx.log("❌ EventoTriggerPlanificacion: ERROR: " + ex.getMessage());
            
            // Ya se envió el log al inicio, no enviar eventos adicionales
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

        // 📊 IMPRIMIR SOLUCIÓN RECIBIDA
        System.out.println("\n📊 ========= SOLUCIÓN RECIBIDA =========");
        System.out.println("⏰ Hora: " + ctx.obtenerElAhora());
        System.out.println("📦 Total Programaciones: " + salida.getProgramaciones().size());
        System.out.println("🔍 Detalle de Programaciones:");
        
        int contador = 1;
        for (Programacion prog : salida.getProgramaciones()) {
            System.out.println("  " + contador + ") Pedido ID=" + prog.getIdPedido() + 
                             " | Producto UUID=" + prog.getUuidProducto() + 
                             " | Ruta (vuelos): " + prog.getIdsVueloRuta());
            contador++;
        }
        System.out.println("=========================================\n");

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