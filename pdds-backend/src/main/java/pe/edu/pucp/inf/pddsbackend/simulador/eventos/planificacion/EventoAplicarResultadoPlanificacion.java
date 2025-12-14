package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.PrettyPrinter;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evento que aplica los resultados de una planificación que se ejecutó de forma
 * asíncrona. Este evento se programa cuando el algoritmo de planificación
 * termina exitosamente.
 */
@Getter
@AllArgsConstructor
public class EventoAplicarResultadoPlanificacion implements EventoSimulacion
{

    private final UUID uuid;
    private final Instant instanteProgramado;
    private final ResultadoAlgoritmoDTO resultado;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramado;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception{
        System.out.println("\n📋 ========= APLICANDO RESULTADO DE PLANIFICACIÓN =========");
        System.out.println("⏰ Hora: " + instanteProgramado);
        System.out.println(
                "📦 Programaciones a aplicar: " + resultado.salida().getProgramaciones().size());
        System.out.println(
                "⚡ Tiempo que tomó el algoritmo: " + resultado.tiempoEjecucionMs() + " ms");
        System.out.println("📈 Fitness: " + resultado.fitness());
        System.out.println("===========================================================\n");

        ctx.log("📋 EventoAplicarResultadoPlanificacion: Aplicando resultado de planificación");

        // Desactivar programaciones anteriores
        // for (Programacion programacionActiva : programacionesActivasAntes) {
        // programacionActiva.setActivo(false);
        // }
        // programacionesActivasAntes.clear(); // <- mejor o no? Así evitamos confusiones futuras, mejor dsp

        ctx.setUltimaPlanificacion(instanteProgramado);
        
        // ✅ Incrementar contador de planificaciones
        ctx.setContadorPlanificaciones(ctx.getContadorPlanificaciones() + 1);

        SalidaProblemaPlanificacion salida = resultado.salida();
        if (salida == null)
        {
            ctx.log("⚠️ Salida de planificación es null, no hay nada que aplicar");
            return;
        }

        // Aplicar la solución al contexto
        aplicarSolucionEnContexto(ctx, salida);

        ctx.log("✅ Resultado de planificación aplicado exitosamente");

        System.out.println("✅ Resultado aplicado al contexto de simulación");
    }

    /**
     * Aplica la solución del algoritmo al contexto de la simulación
     */
    private void aplicarSolucionEnContexto(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida) throws Exception
    {

        // Verificar si hay colapso - MODIFICADO: Ya no se lanza excepción, se continúa la simulación
        if (salida.isColapsado()){
            ctx.log("⚠️ EventoAplicarResultadoPlanificacion: COLAPSO DETECTADO (continuando simulación)");
            System.out.println("\n⚠️ ========================================");
            System.out.println("⚠️ COLAPSO DETECTADO EN PLANIFICACIÓN (continuando)");
            System.out.println("⚠️ ========================================\n");
            if (salida.isHuboErrorEjecucion())
            {
                ctx.log("⚠️ EventoAplicarResultadoPlanificacion: ERROR en algoritmo (continuando): "
                        + salida.getError());
                // NO marcar como error fatal, solo como advertencia
                // ctx.setConError(true);
                // ctx.setErrorMsj(salida.getError());
            }
            // Ya NO se lanza excepción - la simulación continúa normalmente
            // throw new ColapsadoExceptionTemporal(
            //         "Colapso en planificación: no se pudo satisfacer todos los pedidos con los vuelos disponibles");
        }

        // Verificar si hay error
        if (salida.isHuboErrorEjecucion()){
            ctx.log("❌ EventoAplicarResultadoPlanificacion: ERROR en algoritmo: "
                    + salida.getError());
            ctx.setConError(true);
            ctx.setErrorMsj(salida.getError());
            return;
        }

        ctx.getEstado().getProgramaciones().clear(); // limpiar TODAS las progs previas
        // Agregar las programaciones a la lista de programaciones del estado
        ctx.getEstado().getProgramaciones().addAll(salida.getProgramaciones());
        ctx.log("📋 Programaciones agregadas al estado: " + salida.getProgramaciones().size());

        // Acumular la solución
        ctx.getSolucionesAcumuladas().add(salida);
        ctx.log("📊 Soluciones acumuladas: " + ctx.getSolucionesAcumuladas().size());

        // Si no hay rutas y no hay error/colapso, significa que todos los pedidos ya
        // fueron atendidos
        if (salida.getProgramaciones().isEmpty()){
            ctx.log("ℹ️ No se generaron nuevas programaciones (todos los pedidos ya atendidos)");
            return;
        }

        agregarProductosEnEstadoContexto(ctx, salida);
        ctx.log("RUTAS PROGRAMADAS:\n");
        ctx.log(PrettyPrinter.printList(ctx.getEstado().getProgramaciones()));

        int nuevosProdsProgramados = actualizarPedidosEnEstado(ctx, salida);

        // 📊 LOG DETALLADO DE VUELOS PROGRAMADOS
//        mostrarVuelosProgramados(ctx, salida);

        // LOG DE RUTAS PROGRAMADAS

    }

    private void agregarProductosEnEstadoContexto(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida){
        Map<UUID, Producto> productosPlanificacion = salida.getProductos();
        List<Producto> nuevosProductos = new ArrayList<>();

        EstadoGlobal estadoReal = ctx.getEstado();
        Map<UUID, Producto> productosRealesSimu = estadoReal.getProductos();
        salida.getProgramaciones().forEach(prog -> {
            UUID uuid = prog.getUuidProducto();

            if (!productosRealesSimu.containsKey(uuid)){
                Producto prodPlanificado = productosPlanificacion.get(uuid);
                if (prodPlanificado == null)
                    ctx.log("Prod nulo con uuid?? " + uuid);
                productosRealesSimu.put(uuid, prodPlanificado);
                nuevosProductos.add(prodPlanificado);
                // ctx.log("Llevado al estado el producto planificado nuevo: " +
                // prodPlanificado);
            }
        });

//        ctx.log("📋 Productos agregados al estado: " + nuevosProductos.size() + ":" +
//                PrettyPrinter.printList(nuevosProductos));
    }

    // Solo actualiza que aparezcan los prods programados actuales en los pedidos de
    // la simu para que el cliente los pueda consumir mejor; sin embargo, estos se eliminarán cuando comience una
    // nueva planificación para volver a poblarse. Retorna productos agregados programados
    private int actualizarPedidosEnEstado(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida){
        EstadoGlobal estadoReal = ctx.getEstado();
        Map<Long, Pedido> pedidos = estadoReal.getPedidos();

        pedidos.forEach((aLong, pedido) -> {

            pedido.restablecerProductosProgramadosParaAlgoritmo(); // para poner los nuevos-
                });

        AtomicInteger prodsAgregados = new AtomicInteger(); // <- que es esto jajajaj, Java eres raro a veces
        salida.getProgramaciones().forEach(programacion -> {
            Pedido pedido = pedidos.get(programacion.getIdPedido());
            Producto prod = estadoReal.obtenerProductoPorUuid(programacion.getUuidProducto());
            if (pedido.agregarProductoProgramadoEnSimu(prod)){
//                ctx.log("Pedido actualizando estado: " + pedido);
                prodsAgregados.getAndIncrement();
            }else{
                ctx.log("\n⚠️ ADVERTENCIA - Pedido con posible exceso: "+pedido+"\nProd que causaría exceso: "+prod + " (continuando simulación)");
                // ✅ Ya NO se lanza excepción - la simulación continúa mágicamente
                // throw new IllegalStateException(
                //         "¿Cómo el algoritmo hizo que un producto programado excede a lo pedido en total?");
            }
        });

        ctx.log("📋 Productos programados totales a pedidos: " + prodsAgregados.get());

        return prodsAgregados.get();
    }

    /**
     * Muestra un resumen detallado de los vuelos que tienen programaciones
     */
    private void mostrarVuelosProgramados(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida){
        // Recopilar todos los vuelos únicos de las programaciones con sus horarios
        Map<Long, Instant> vuelosConHorarios = new LinkedHashMap<>();

        for (Programacion prog : salida.getProgramaciones()){
            if (prog.getIdsVueloRuta() == null || prog.getIdsVueloRuta().isEmpty()){
                continue;
            }

            for (Long idVuelo : prog.getIdsVueloRuta()){
                if (!vuelosConHorarios.containsKey(idVuelo)){
                    Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);
                    if (vuelo != null){
                        vuelosConHorarios.put(idVuelo, vuelo.getInicio());
                    }
                }
            }
        }

        // Ordenar por hora de salida
        List<Map.Entry<Long, Instant>> vuelosOrdenados = vuelosConHorarios.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList();

        System.out.println("\n✈️  ========= VUELOS CON PROGRAMACIONES =========");
        System.out.println("📦 Total programaciones: " + salida.getProgramaciones().size());
        System.out.println("✈️  Total vuelos únicos programados: " + vuelosConHorarios.size());
        System.out.println("📋 Detalle de vuelos (ordenados por hora de salida):");

        for (Map.Entry<Long, Instant> entry : vuelosOrdenados){
            Long idVuelo = entry.getKey();
            Instant horaSalida = entry.getValue();
            Vuelo vuelo = ctx.getEstado().getVuelos().get(idVuelo);

            String codigoVuelo = vuelo != null && vuelo.getCodigo() != null
                    ? vuelo.getCodigo()
                    : "V-" + idVuelo;

             System.out.println(String.format(" 🛫 ID: %-6d | Código: %-10s | Salida: %s",
             idVuelo, codigoVuelo, horaSalida));
        }
        System.out.println("================================================\n");
    }

    @Override
    public int getPriority(){
        return 3; // Prioridad 3: después de llegadas de vuelo (2) pero antes de trigger de
                  // planificación (4)
    }
}
