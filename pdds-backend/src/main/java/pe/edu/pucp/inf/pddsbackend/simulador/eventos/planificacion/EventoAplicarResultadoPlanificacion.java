package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ColapsadoExceptionTemporal;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.PrettyPrinter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Testeador;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Ruta;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos.EventoEntregaPedidoTras2h;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    /*
     * Prioridad 3: después de llegadas de vuelo (2) pero antes de trigger de planificación (4)
     */
    @Override
    public int getPriority(){
        return 3; 
    }

    /*
     * Aplica lasp programaciones 
     */
    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        System.out.println("🔄 EventoAplicarResultadoPlanificacion nro: " + ctx.getSolucionesAcumuladas().size()+1);
        ctx.log("🔄 EventoAplicarResultadoPlanificacion nro: " + ctx.getSolucionesAcumuladas().size()+1);

Bitacora.escribir("============ APLICAR RESULTADO PLANIFICACION ============");
        SalidaProblemaPlanificacion salida;

        ctx.setUltimaPlanificacion(instanteProgramado);
        ctx.setContadorPlanificaciones(ctx.getContadorPlanificaciones() + 1);

        salida = resultado.salida();

        if (salida.isColapsado() || salida.isHuboErrorEjecucion()) {
            // Caso hubo colapso
            if (salida.isHuboErrorEjecucion()) {
                Bitacora.escribir(" ERROR en algoritmo: " + salida.getError());
                ctx.setConError(true);
                ctx.setErrorMsj(salida.getError());
            }

            throw new ColapsadoExceptionTemporal("Colapso en planificación: no se pudo satisfacer todos los pedidos con los vuelos disponibles o hubo un error en ejecución" +
                    " error(si es que hay): " + salida.getError());
//            lanzarExcepcion("procesar", "Colapso en planificación: no se pudo satisfacer todos los pedidos con los vuelos disponibles o hubo un error en ejecución");
        }else{
            // Caso sin colapso
            if (!salida.getProgramaciones().isEmpty()){
//Testeador.cantidadProgramacionesIncancelablesConsistenteTEST(ctx.getEstado(), salida);

mostrarDiferenciaIncancelablesPrevioNuevo(ctx, salida.getProgramaciones());
                limpiarProductosProgramadosPedidos(ctx);
                procesarPedidos(ctx, salida);
                procesarProgramacionesPrevias(ctx);
                procesarProgramacionesSalida(ctx, salida);
                //GIANO DICE QUE
//Testeador.paraUnEkCualquiera(instanteProgramado, ctx.getEstado(), ctx.getParams().fechaHoraInicioSimulacion());
Bitacora.escribir(ctx.getEstado(), "Estado del ctx con resultado aplicado");
            }else{
                Bitacora.escribir("ERROR: No hay programaciones que aplicar (todos los pedidos ya atendidos, o hay otra posibilidad?)");
            }

            ctx.getSolucionesAcumuladas().add(salida);
        }
Bitacora.escribir("============ FIN EVENTO ============");
    }

    private void procesarPedidos(ContextoSimulacion ctx, SalidaProblemaPlanificacion salida) {
        EstadoGlobal estado = ctx.getEstado();
        for(Long id : salida.getIdsPedidosImportantes()){
            estado.getPedidos().remove(id); // *

            Iterator<Programacion> it = estado.getProgramaciones().iterator();
            while (it.hasNext()) {
                Programacion programacion = it.next();
                if (programacion.getPedido().getId() == id) {
                    it.remove();                                        // *
                    Producto productoPedido = programacion.getProducto();
                    if (productoPedido.validarIncancelable_B()
                            || productoPedido.validarPlanificadoExistente_D()) {
                        productoPedido.transPlanificadoExistente_D_NoPlanificado_Av2();
                    }
                }
            }

            ctx.anadir(salida.getIdsPedidosImportantes());                  // *

            System.out.println("PRocesando pedido: " + id);
        }
    }

    private void mostrarDiferenciaIncancelablesPrevioNuevo(ContextoSimulacion ctx, List<Programacion> programaciones) {
        int prograsIncancelablesPrevias = (int) ctx.getEstado().getProgramaciones().stream()
                .filter(programacion -> programacion.validarIncancelable_I(instanteProgramado)).count();

        int prograsIncancelablesNuevas = (int) programaciones.stream()
                .filter(programacion -> programacion.validarIncancelable_I(instanteProgramado)).count();

        Bitacora.escribir("Programaciones incancelables previas: "+prograsIncancelablesPrevias);
        Bitacora.escribir("Programaciones incancelables nuevas: "+prograsIncancelablesNuevas);
        Bitacora.escribir("Diferencia: "+ (prograsIncancelablesNuevas - prograsIncancelablesPrevias));
    }

    /*
     * Limpia la lista de productosProgramados de todos los pedidos en el contexto, manteniendo los productos incancelables  
     */
    private List<Programacion> limpiarProductosProgramadosPedidos(ContextoSimulacion ctx) {
        List<Programacion> programacionesIncancelables = new ArrayList<>();
        
        for (Pedido pedido : ctx.getEstado().getPedidos().values()) {
            for (Producto producto : pedido.getProductosProgramados()) {
                if (producto.validarIncancelable_B()) {
                    for (Programacion programacion : ctx.getEstado().getProgramaciones()) {
                        if (programacion.getProducto().equals(producto) && programacion.getPedido().equals(pedido)) {
                            programacionesIncancelables.add(programacion);
                            break;
                        }
                    }
                }
            }

            pedido.getProductosProgramados().removeIf(producto -> !producto.validarIncancelable_B());
        }
        
        return programacionesIncancelables;
    }

    /*
     * Procesa las programaciones del contexto según su tipo:
     * - Caso C (Planificado No Existente): Se elimina la programación y su producto asociado del estado
     * - Caso E (Planificado Existente): Se elimina la programación y se transiciona su producto a tipo A (No Planificado)
     * - Caso I (Incancelable): Se mantiene la programación sin modificaciones
     */
    private void procesarProgramacionesPrevias(ContextoSimulacion ctx) {
        List<Programacion> programacionesAMantener = new ArrayList<>();
        Map<UUID, Producto> productosReales = ctx.getEstado().getProductos();
        
        for (Programacion programacion : ctx.getEstado().getProgramaciones()) {
            Producto producto = programacion.getProducto();
            
            // Programación C se elimina la programación y su producto asociado
            if (producto.validarPlanificadoNoExistente_C()) {
                productosReales.remove(producto.getId());
            }
            // Programación E se elimina la programación y se transiciona el producto a tipo A
            else if (producto.validarPlanificadoExistente_D()) {
                producto.transPlanificadoExistente_D_NoPlanificado_A();
            }
            // Programación I se mantiene la programación
            else if (producto.validarIncancelable_B()) {
                programacionesAMantener.add(programacion);
            }
        }
        
        ctx.getEstado().getProgramaciones().clear();
        ctx.getEstado().getProgramaciones().addAll(programacionesAMantener);
    }

    /*
     * Procesa las programaciones de la salida del algoritmo según su tipo.
     * - Caso C (Planificado No Existente): Se agrega la programación y su producto al contexto
     * - Caso E (Planificado Existente): Se busca el producto en el contexto, se transiciona a tipo D, se actualiza la referencia del producto y se agrega la programación al contexto
     */
    private void procesarProgramacionesSalida(ContextoSimulacion ctx, SalidaProblemaPlanificacion salida) {
        Map<UUID, Producto> productosReales = ctx.getEstado().getProductos();

        for (Programacion programacion : salida.getProgramaciones()) {
            // Actualizar referencias de vuelos en la ruta a los del contexto
            actualizarVuelosDeRutaAlContexto(ctx, programacion);
            
            Producto productoPlanificacion = programacion.getProducto();
            UUID idProducto = productoPlanificacion.getId();
            Long idPedido = programacion.getPedido().getId();
            Pedido pedidoReal = ctx.getEstado().getPedidos().get(idPedido);

            if (productoPlanificacion.validarPlanificadoNoExistente_C()) {
                // Programación C se agrega el producto y la programación al contexto
                productosReales.put(idProducto, productoPlanificacion);
                ctx.getEstado().getProgramaciones().add(programacion);
                
//                if(!pedidoReal.registrarProductoProgramado(productoPlanificacion)){
//                    lanzarExcepcion("procesarProgramacionesSalida",
//                            "Fallo al registrar producto tipo C en pedido: " + idPedido);
//                }
            }else if (productoPlanificacion.validarPlanificadoExistente_D()) {
                // Programación E se busca el producto en el contexto, se transiciona a tipo D y se actualiza la referencia
                Producto productoReal = productosReales.get(idProducto);
                
                if (productoReal != null) {
                    productoReal.transNoPlanificado_A_PlanificadoExistente_Dv2();
                    programacion.setProducto(productoReal);
                    ctx.getEstado().getProgramaciones().add(programacion);
                    
//                    if(!pedidoReal.registrarProductoProgramado(productoReal)){
//                        lanzarExcepcion("procesarProgramacionesSalida",
//                                "Fallo al registrar producto tipo D en pedido: " + idPedido);
//                    }
                } else {
//                    lanzarExcepcion("procesarProgramacionesSalida",
//                        "No se encontró el producto tipo A en el contexto: " + idProducto);
                }
            }else if (productoPlanificacion.validarIncancelable_B()) {
                // programacion tipo I que creo el algoritmo
                Producto productoReal = productosReales.get(idProducto);

                if (productoReal != null) {
                    // Verificar si este producto ya pertenece a una programación incancelable previa
                    boolean perteneceAProgramacionPrevia = ctx.getEstado().getProgramaciones().stream()
                            .anyMatch(prog -> prog.getProducto().getId().equals(idProducto) 
                                    && prog.getProducto().validarIncancelable_B());
                    
                    if (perteneceAProgramacionPrevia) {
//Bitacora.escribir("El producto %s ya pertenece a una programación incancelable previa, omitiendo procesamiento", idProducto);
                        continue; // Saltar este producto, ya está en una programación incancelable previa
                    }
/*                  
Bitacora.escribir("procReal %s [memoria: %s]", productoReal, Integer.toHexString(System.identityHashCode(productoReal)));
Bitacora.escribir("procPlanif %s [memoria: %s]", productoPlanificacion, Integer.toHexString(System.identityHashCode(productoPlanificacion)));
*/
                    if(!productoReal.validarNoPlanificado_A()){


                        lanzarExcepcion("procesarProgramacionesSalida",
                                "El producto planificado no puede ser de tipo A si la programación es de tipo B");
                    }
                    productoReal.transNoPlanificado_A_PlanificadoExistente_Dv2();
                    productoReal.transPlanificadoExistente_D_Incancelable_Bv2();
                    programacion.setProducto(productoReal);
                    ctx.getEstado().getProgramaciones().add(programacion);

//                    if(!pedidoReal.registrarProductoProgramado(productoReal)){
//                        lanzarExcepcion("procesarProgramacionesSalida",
//                                "Fallo al registrar producto tipo D en pedido: " + idPedido);
//                    }

                    // Programamos su recojo inmediato en 2 horitas desde ahora (instanteAlgoritmo)
                    if(pedidoReal!=null) {
                        EventoSimulacion recojoPronto = new EventoEntregaPedidoTras2h(
                                pedidoReal.getId(),
                                pedidoReal.getAlmacenDestino().getId(),
                                productoReal,
                                productoReal.getId(),
                                instanteProgramado.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)),
                                webSocketService
                        );
                        ctx.programarEvento(recojoPronto);
                    }
/*
Bitacora.escribir("Programando recojo de productos OBVIOS en programaciones OBVIAS (vueltos incancelables");
Bitacora.escribir("Para la hora: "+ Bitacora.formatearInstante(
        instanteProgramado.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO))
) );
Bitacora.escribir("Para el pedido: "+ pedidoReal);
Bitacora.escribir("Con la progra: "+ programacion);
Bitacora.escribir("Con el prod (ya de la simu): "+ productoReal);
*/
                } else {
//                    lanzarExcepcion("procesarProgramacionesSalida",
//                            "No se encontró el producto tipo A en el contexto: " + idProducto);
                }
            }
        }
    }

    /*
     * Actualiza las referencias de los vuelos en la ruta de una programación
     * para que apunten a los vuelos reales del contexto en lugar de los de la copia
     * 
     * Maldision creo que Giano tenia razon xd
     */
    private void actualizarVuelosDeRutaAlContexto(ContextoSimulacion ctx, Programacion programacion) {
        Ruta ruta = programacion.getRuta();
        LinkedList<Vuelo> vuelosActualizados = new LinkedList<>();
        
        for (Vuelo vueloCopia : ruta.getVuelos()) {
            Vuelo vueloReal = ctx.getEstado().buscarVueloPorId(vueloCopia.getId());
            if (vueloReal != null) {
                vuelosActualizados.add(vueloReal);
            } else {
                lanzarExcepcion("actualizarVuelosDeRutaAlContexto", 
                    "No se encontró el vuelo ID=" + vueloCopia.getId() + " en el contexto");
            }
        }
        
        ruta.setVuelos(vuelosActualizados);
    }

















    public void procesar_v1(ContextoSimulacion ctx) throws Exception{
        System.out.println("\n📋 ========= APLICANDO RESULTADO DE PLANIFICACIÓN =========");
        System.out.println("⏰ Hora: " + instanteProgramado);
        System.out.println(
                "📦 Programaciones a aplicar: " + resultado.salida().getProgramaciones().size());
        System.out.println(
                "⚡ Tiempo que tomó el algoritmo: " + resultado.tiempoEjecucionMs() + " ms");
        System.out.println("📈 Fitness: " + resultado.fitness());
        System.out.println("===========================================================\n");

        ctx.log("📋 EventoAplicarResultadoPlanificacion: Aplicando resultado de planificación");
        ctx.log("📋 EventoAplicarResultadoPlanificacion: Tiempo que tomó el algoritmo: " + resultado.tiempoEjecucionMs() + " ms");
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
        if (salida.isColapsado() || salida.isHuboErrorEjecucion() ) {
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
                    "Colapso en planificación: no se pudo satisfacer todos los pedidos con los vuelos disponibles" +
                            " o hubo un error en ejecución. error si es que hay: " + salida.getError());
        }

        ctx.log("📋 EventoAplicarResultadoPlanificacion: SIN COLAPSO ✅");

        ctx.getEstado().getProgramaciones().clear();
        ctx.getEstado().getProgramaciones().addAll(salida.getProgramaciones());
        // ^^ ahora: Se trajo más incancelables de las que había en un inicio

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
     * Se convierte todos los productosReales a tipo A y
     * luego se compara con los productosPlanificacion si han cambiado de estado a tipo D.
     * La cantidad de productos tipo B YA NO debe coincidir entre reales y planificados.
     * Se añade los nuevos productos a la lista de productosProgramados de cada pedido.
     */
    private void agregarProductosEnEstadoContexto(ContextoSimulacion ctx,
            SalidaProblemaPlanificacion salida) throws Exception {
        Map<UUID, Producto> productosPlanificacion = salida.getProductos();
        Map<UUID, Producto> productosReales = ctx.getEstado().getProductos();

        List<Producto> productosBReales = new ArrayList<>();
        List<Producto> productosBPlanificacion = new ArrayList<>();

        productosBReales = productosReales.values().stream()
                .filter(Producto::validarIncancelable_B)
                .collect(Collectors.toList());
        productosBPlanificacion = productosPlanificacion.values().stream()
                .filter(Producto::validarIncancelable_B)
                .collect(Collectors.toList());

        // Vaciar la lista de productosProgramados de los pedidos
        ctx.getEstado().getPedidos().values().forEach(pedido -> {
            pedido.getProductosProgramados().clear();
        });

        // La cantidad de productos tipo B debe coincidir entre reales y planificados        
        if (productosBReales.size() == productosBPlanificacion.size()) {
//            ctx.getEstado().getProgramaciones().forEach(pg -> {
//                Producto productoReal = productosReales.get(pg.getProducto().getId());
//                pg.setProducto(pg.getProducto()); // :V
//            });
            Bitacora.escribir("########## NOTA: EL ALGORITMO NO CREÓ NUEVAS INCANCELABLES############");
        }else{
            Bitacora.escribir("########## NOTA: EL ALGORITMO CREÓ NUEVAS PROGRAS INCANCELABLES ############");
//            lanzarExcepcion("AplicarResultadoPlanificacion", String.format("La cantidad de productos de tipo B no coincide entre el estado y la planificación (Estado: %d, Planificación: %d)",productosBReales.size(), productosBPlanificacion.size()));
        }

        // Convierte todos los productos existentes REALES DE SIMU de tipo D a tipo A,
        // para luego verificar si alguna nueva programación los vuelve tipo D nuevamente
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
            if (!productosReales.containsKey(idProductoPlanificacion)) { // no está en nuestros reales, es tipo C lo programado
                // Producto nuevo, debería ser de tipo C
                if(productoPlanificacion.validarPlanificadoNoExistente_C()){
                    productosReales.put(idProductoPlanificacion, productoPlanificacion);

                    if(!pedido.registrarProductoProgramado(productoPlanificacion))  {
                        lanzarExcepcion("AplicarResultadoPlanificacion", "Fallo al registrar producto nuevo en pedido");
                    }
                }else{
                    Bitacora.escribir("Se intentó agregar un producto que no es de tipo C como nuevo producto. ");
                    lanzarExcepcion("AplicarResultadoPlanificacion", "Se intentó agregar un NUEVO producto que no es de tipo C como nuevo producto");
                }
            } else { 
                // Producto ya existente, actualmente de tipo A (pq recién lo transformamos)!
                // (los D se hicieron A, y los A se quedaron así, pero el algoritmo los pudo convertir en B)
                Producto productoReal = productosReales.get(idProductoPlanificacion);

                if(productoReal.validarNoPlanificado_A()){
                    // Producto existente, debería ser pasado a tipo D si el algoritmo lo decidió; o a tipo B!
                    if(productoPlanificacion.validarPlanificadoExistente_D()){
                        productoReal.transNoPlanificado_A_PlanificadoExistente_Dv2();
                        pg.setProducto(productoReal);
                        pedido.registrarProductoProgramado(productoReal); // xd
                    } else {
                        Bitacora.escribir("El algoritmo al parecer transformó internamente un A en un B, no un D...");
                        if(productoPlanificacion.validarIncancelable_B()){
                            Bitacora.escribir("Sí, es de tipo B!, transformando al producto en el estado de la simu...");
                            // Dos transformaciones seguidas para respetar el curso... ¿o crear una nueva?
                            productoReal.transNoPlanificado_A_PlanificadoExistente_Dv2();
                            productoReal.transPlanificadoExistente_D_Incancelable_Bv2();

                            pedido.registrarProductoProgramado(productoReal); // xd

                            // Programamos su recojo inmediato en 2 horitas desde ahora (instanteAlgoritmo)
                            EventoSimulacion recojoPronto = new EventoEntregaPedidoTras2h(
                                    pedido.getId(),
                                    pedido.getAlmacenDestino().getId(),
                                    productoReal,
                                    productoReal.getId(),
                                    instanteProgramado.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)),
                                    webSocketService
                            );
                            Bitacora.escribir("Programando recojo de productos OBVIOS en programaciones OBVIAS (vueltos incancelables");
                            Bitacora.escribir("Para la hora: "+ Bitacora.formatearInstante(
                                    instanteProgramado.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO))
                            ) );
                            Bitacora.escribir("Para el pedido: "+ pedido);
                            Bitacora.escribir("Con la progra: "+ pg);
                            Bitacora.escribir("Con el prod (ya de la simu): "+ productoReal);

                            ctx.programarEvento(recojoPronto);
                        }else{
                            Bitacora.escribir("No, no es de tipo B 💀");
                            lanzarExcepcion("AplicarResultadoPlanificacion", "Se intentó agregar un producto que no es de tipo C ni B como nuevo producto");
                        }
                        // vv Ya no, ya que si el algoritmo no lo devolvió como un D, lo hizo como un B.
//                        lanzarExcepcion("AplicarResultadoPlanificacion", "El producto planificado no es de tipo D");
                    }
                }
            }
        });
    }


}
