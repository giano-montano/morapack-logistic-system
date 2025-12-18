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
public class EventoAplicarResultadoPlanificacion extends EventoSimulacion {

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
        ctx.setUltimaPlanificacion(instanteProgramado);
        ctx.setContadorPlanificaciones(ctx.getContadorPlanificaciones() + 1);

        SalidaProblemaPlanificacion salida = resultado.salida();

        if (salida != null) {
            /* Lo importante */
            aplicarSolucionEnContexto(ctx, salida);
            /* */

            ctx.getSolucionesAcumuladas().add(salida);
            ctx.log("📊 Soluciones acumuladas: " + ctx.getSolucionesAcumuladas().size());
            ctx.log("✅ Resultado de planificación aplicado exitosamente");
            System.out.println("✅ Resultado aplicado al contexto de simulación");
            
        }else{
            ctx.log("⚠️ Salida de planificación es null, no hay nada que aplicar");
        }
    }

    /*
     * Aplica la solución del algoritmo al contexto de la simulación. Elimina todas las programaciones y las remplaza por las que el algoritmo calculó. Se ejecuta siempre en instanteAlgoritmo, por lo que todos los eventos que deberían haber sucedido, ya sucedieron. VERIFICAR PRIORIDAD BAJA PARA EVENTOS EN EL MISMO INSTANTE.
     */
    private void aplicarSolucionEnContexto(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida) throws Exception {
        if (salida.isColapsado()) {
            ctx.log("⚠️ EventoAplicarResultadoPlanificacion: COLAPSO DETECTADO");
            System.out.println("\n🚨 ========================================");
            System.out.println("🚨 COLAPSO DETECTADO EN PLANIFICACIÓN");
            System.out.println("🚨 ========================================\n");
            if (salida.isHuboErrorEjecucion()) {
                ctx.log("❌ EventoAplicarResultadoPlanificacion: ERROR en algoritmo: "
                        + salida.getError());
                ctx.setConError(true);
                ctx.setErrorMsj(salida.getError());
            }
            throw new ColapsadoExceptionTemporal(
                    "Colapso en planificación: no se pudo satisfacer todos los pedidos con los vuelos disponibles");
        }

        ctx.getEstado().getProgramaciones().clear();
        ctx.getEstado().getProgramaciones().addAll(salida.getProgramaciones());

        // Si hay programaciones, se deben actualizar los productos en el estado del contexto
        if (!salida.getProgramaciones().isEmpty()){
            agregarProductosEnEstadoContexto(ctx, salida);
            
            ctx.log("📋 Programaciones agregadas al estado: " + salida.getProgramaciones().size());
            ctx.log("RUTAS PROGRAMADAS:\n");
            ctx.log(PrettyPrinter.printList(ctx.getEstado().getProgramaciones()));            
        }else{
            ctx.log("ℹ️ No se generaron nuevas programaciones (todos los pedidos ya atendidos)");
        }
    }

    /*
     * Se convierte todos los productosReales a tipo A y luego se compara con los productosPlanificacion si han cambiado de estado a tipo D. La cantidad de productos tipo B debe coincidir entre reales y planificados. Se añade los nuevos productos a la lista de productosProgramados de cada pedido.
     */
    private void agregarProductosEnEstadoContexto(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida) {
        Map<UUID, Producto> productosPlanificacion = salida.getProductos();
        Map<UUID, Producto> productosReales = ctx.getEstado().getProductos();

        List<Producto> productosBReales = new ArrayList<>();
        List<Producto> productosBPlanificacion = new ArrayList<>();

        productosBReales = productosReales.values().stream()
                .filter(Producto::validarIncancelable_B)
                .toList();
        productosBPlanificacion = productosPlanificacion.values().stream()
                .filter(Producto::validarIncancelable_B)
                .toList();

        // Vaciar la lista de productosProgramados de los pedidos
        ctx.getEstado().getPedidos().values().forEach(pedido -> {
            pedido.getProductosProgramados().clear();
        });

        // La cantidad de productos tipo B debe coincidir entre reales y planificados        
        if (productosBReales.size() == productosBPlanificacion.size()) {
            ctx.getEstado().getProgramaciones().forEach(pg -> {
                Producto productoReal = productosReales.get(pg.getProducto().getId());
                pg.setProducto(productoReal);
            });
            
        }else{
            lanzarError(ctx, String.format(
                "La cantidad de productos de tipo B no coincide entre el estado y la planificación (Estado: %d, Planificación: %d)", 
                productosBReales.size(), productosBPlanificacion.size()));
        }

        // Convierte todos los productos existentes de tipo D a tipo A, para luego verificar si alguna nueva programación los vuelve tipo D nuevamente
        productosReales.forEach((uuid1, producto) -> {
            if(producto.validarPlanificadoExistente_D()){
                producto.transPlanificadoExistente_D_NoPlanificado_A();
            }
        });

        ctx.getEstado().getProgramaciones().forEach(pg -> {
            Producto productoPlanificacion = pg.getProducto();
            UUID idProductoPlanificacion = productoPlanificacion.getId();
            Pedido pedido = pg.getPedido();

            // Se actualiza la lista de productos programados del pedido
            

            if (!productosReales.containsKey(idProductoPlanificacion)) {
                // Producto nuevo, debería ser de tipo C
                if(productoPlanificacion.validarPlanificadoNoExistente_C()){
                    productosReales.put(idProductoPlanificacion, productoPlanificacion);
                    pedido.registrarProductoProgramado(productoPlanificacion);
                }else{
                    lanzarError(ctx, "Se intentó agregar un producto que no es de tipo C como nuevo producto");
                }
            } else { 
                // Producto ya existente, actualmente de tipo A 
                Producto productoReal = productosReales.get(idProductoPlanificacion);

                if(productoReal.validarNoPlanificado_A()){
                    // Producto existente, debería ser de tipo D
                    if(productoPlanificacion.validarPlanificadoExistente_D()){
                        productoReal.transNoPlanificado_A_PlanificadoExistente_D();
                        pg.setProducto(productoReal);
                        pedido.registrarProductoProgramado(productoReal);
                    } else {
                        lanzarError(ctx, "El producto planificado no es de tipo D");
                    }
                }
            }
        });
    }

    /*
     * Método helper para loguear y lanzar excepción de estado ilegal
     */
    private void lanzarError(ContextoSimulacion ctx, String mensaje) {
        String errorCompleto = "ERROR (AplicarResultadoPlanificacion): " + mensaje;
        ctx.log(errorCompleto);
        throw new IllegalStateException(errorCompleto);
    }

    @Override
    public int getPriority(){
        return 3; // Prioridad 3: después de llegadas de vuelo (2) pero antes de trigger de
                  // planificación (4)
    }
}
