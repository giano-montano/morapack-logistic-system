package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.ConstruccionProgramacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.PrettyPrinter;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Component
@Primary // Si en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class EstrategiaGraspHibrido extends EstrategiaPlanificacion {

    private EstadoGlobal estadoGlobal;
    private Instant instanteActual;

    private static final double ALPHA_RUTAS = 0.8;
    private static final double ALPHA_PEDIDOS = 0.5; // por poner algo xd
    private static final int ITERACIONES_MAXIMAS_PRIMER_GRASP = 50000;
    private static final double UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA = 0.8;
    private static final double UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA = 0.2;

    private EntradaProblemaPlanificacion entradaRecibida;

    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) throws Exception {

        lr.appendReport("DEBUG LR HASH at planificar start: " + System.identityHashCode(lr));
        lr.appendReport("DEBUG reporte length al inicio: " + (lr == null ? -1 : lr.safeSize(Collections.singleton(lr)))); // fallback
// Mejor: meter método en LoggingReport:
        lr.appendReport("DEBUG report length raw: " + (lr.getInternalLength())); // si añades getter abajo


        // Inicialización
        estadoGlobal = entrada.getEstadoGlobalCopia(); this.entradaRecibida = entrada;
        estadoGlobal.setLr(lr);
        this.instanteActual = entrada.getInstanteActual();
        setSemilla(entrada.getSemilla()); // repoio
        lr.appendReport("Vuelos posibles ids: "+ estadoGlobal.getVuelos().keySet());
        // Obtener rutas a solo almacenes de destino y a partir de almacenes infinitos o no infinitos con al menos 1 producto.
        List<LinkedList<Long>> // Una clase para ruta que sea lo mismo que una lista de vuelos? No la necesité hasta ahora
                rutasPosibles = // recordar que no hay pedidos para almacenes infinitos hasta este punto (los filtramos antes).
                estadoGlobal.generarRutasParaPedidosPendientesBFS(instanteActual); //
//                estadoGlobal.generarRutasParaPedidosPendientesACO(instanteActual); // <- chamba de Axel
        lr.appendReport("Las rutas posibles son: " + PrettyPrinter.printList(rutasPosibles));
        estadoGlobal.crearIndiceIdsRutasPorAlmacenDestino(rutasPosibles); // a partir de aquí tenemos el tan deseado índice.


        // --- integridad rutas vs vuelos (diagnóstico inmediato) ---
        Set<Long> vuelosKeysEstado = new HashSet<>(estadoGlobal.getVuelos().keySet());
        Set<Long> vuelosKeysEntrada = (entradaRecibida!=null && entradaRecibida.getEstadoGlobal()!=null)
                ? new HashSet<>(entradaRecibida.getEstadoGlobal().getVuelos().keySet())
                : Collections.emptySet();

        lr.appendReport("Comenzando estrategia GRASP Híbrido: "+ estadoGlobal);
        StringBuilder rutas= new StringBuilder();
        estadoGlobal.getRutasPorIdAlmacenDestino().forEach((aLong, linkedLists) ->
                rutas.append(aLong).append(" rutas: ").append(linkedLists).append("\n")
        );
//        lr.appendReport( "Índice:\n " + rutas.toString());

        // asignar puntajes a pedidos pendientes.
        List<Pedido> pedidosPendientes = estadoGlobal.obtenerPedidosPendientesDeEntregaYProgram();
        Map<Pedido, Double> puntajesPorPedido = asignarPuntajesPedidos(pedidosPendientes, this.instanteActual); // <- chamba de Axel

        // Ciclo principal
        int numIteraciones;
        try {
            numIteraciones = realizarCicloDePedidos(rutasPosibles, puntajesPorPedido);
        } catch (Exception ex) {
            ex.printStackTrace();
            SalidaProblemaPlanificacion solution = new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones(), ex.getStackTrace().toString());
            lr.appendReport(ex.toString());
            lr.writeReportFile("Reporte-GRASP-error-" + estadoGlobal.getProgramaciones().size()+"-");
            System.out.println("DEBUG after write file, buffer length: " + lr.getInternalLength());

            return solution;
        }
        lr.appendReport("Planificación finalizada. Iteraciones GRASP realizadas: " + numIteraciones +
                ". Programaciones creadas: " + estadoGlobal.getProgramaciones().size());

        SalidaProblemaPlanificacion solution =
                new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones(), estadoGlobal.getProductos());

        if (estadoGlobal.hayPedidosPendientesPorProgramar()) {
            lr.appendReport("NO SE LOGRÓ PLANIFICAR TODO, COLAPSO LOGÍSTICO!!!!!!!!!!!!");
            solution.setColapsado(true);
        }
        lr.writeReportFile("Reporte-GRASP-" + estadoGlobal.getProgramaciones().size()+"-");
        System.out.println("DEBUG after write file, buffer length: " + lr.getInternalLength());

        return solution;
    }

    private int realizarCicloDePedidos(
            List<LinkedList<Long>> rutasPosibles,
            Map<Pedido, Double> puntajesPorPedido
    ) {
        int numIteraciones = 0;
        while (estadoGlobal.hayPedidosPendientesPorProgramar() && numIteraciones < ITERACIONES_MAXIMAS_PRIMER_GRASP) {
            lr.appendReport("planificar: Iteración %d: quedan %d pedidos pendientes", numIteraciones, estadoGlobal.contarPedidosPendientes());

            List<Programacion> programacionesConstruidasGrasp =
                    elegirYProgramarParaPedido(rutasPosibles, puntajesPorPedido);

            if (programacionesConstruidasGrasp == null) {
                lr.appendReport("GRASP no pudo hacer una programación más, finalizando ciclo.");
                break;
            }
//            // Añadir el envío a la solución
//            estadoGlobal.anadirVariasProgramacionesSolucion(programacionesConstruidasGrasp);
//            lr.appendReport("Programaciones solución añadidas: " + programacionesConstruidasGrasp);

            // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
            boolean removed = estadoGlobal.eliminarPedidoYaSatisfecho(programacionesConstruidasGrasp.get(0).getIdPedido());
            if (removed)
                lr.appendReport("Se eliminó el pedido " + programacionesConstruidasGrasp.get(0).getIdPedido() +
                        " por estar totalmente programado / atendido.");

            // Guardar reporte parcial si quieres (puedes ajustar la frecuencia) no m lo borres
//                if( iter % 100 == 0)
//                    lr.writeReportFile("grasp-report-iter-" + iter+"-");

            numIteraciones++;
        }
        return numIteraciones;
    }

    private List<Programacion> elegirYProgramarParaPedido(
            List<LinkedList<Long>> rutas,
            Map<Pedido, Double> puntajesPorPedido
    ) {
        if (rutas.isEmpty()) return null;
        List<Pedido> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido, ALPHA_PEDIDOS);
        Pedido pedidoElegido =
                seleccionarPedidoDesdeRCL(rclPedidosCandidatos, puntajesPorPedido, generadorAleatorio, false);

        List<Programacion> programaciones = realizarCicloVariosProductosDePedido(pedidoElegido);

        if (programaciones == null || programaciones.isEmpty()) return null;

        // Ahora actualizar puntajes si lo necesita, recordar que es mutable.
        // Para que la RCL se vuelva a armar considerando el siguiente.
        puntajesPorPedido.remove(pedidoElegido);

        return programaciones;
    }

    /* Se asegura de darle una programación a cada producto que necesite el pedido; de otra forma, retorna nulo.*/
    private List<Programacion> realizarCicloVariosProductosDePedido(Pedido pedidoElegido) {
        List<Programacion> programaciones = new LinkedList<>();
        int numProductosPorAtender = pedidoElegido.getCantidadProductosPendientes();
        int numProductosAtendidosPedido = 0;
        List<LinkedList<Long>> rutasConDestinoCompartido = obtenerRutasConMismoDestinoQuePedido(pedidoElegido);
//        lr.appendReport("rutasConDestinoCompartido: "+rutasConDestinoCompartido);
        if(rutasConDestinoCompartido==null|| rutasConDestinoCompartido.isEmpty()) { //<- vaya caso más raro
            lr.appendReport("Rutas con destino compartido dio null o empty, pedido: " + pedidoElegido);
            return null;
        }
        List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido =
                filtrarRutasSegunPlazoPedido(pedidoElegido, rutasConDestinoCompartido);
//        lr.appendRepxort("rutasFiltradasSegunPlazoPedido: "+rutasFiltradasSegunPlazoPedido);
        while (pedidoElegido.getCantidadProductosPendientes() > 0/*numProductosPorAtender > numProductosAtendidosPedido*/) { // Programar para todo el pedido.
            int remaining = pedidoElegido.getCantidadProductosPendientes();
            List<Programacion> creadas = construirVariasPrograsYPersistir2
                    (rutasFiltradasSegunPlazoPedido, pedidoElegido, remaining);
            if (creadas == null || creadas.isEmpty()) {
                lr.appendReport("No se pudo atender más productos del pedido "+ pedidoElegido.getId());
                return null;
            }
            programaciones.addAll(creadas);
            numProductosAtendidosPedido += creadas.size();
        }
//        lr.appendReport("programaciones: "+ programaciones);
        return programaciones;
    }

    private ConstruccionProgramacion obtenerRutaYProgramacion(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido){
        Producto productoAgarrado = null;
        LinkedList<Long> rutaElegida = null; int capacidadRuta =0;
        boolean rclValido;
        do { // Medio rara esta lógica... Pero creo que es necesaria
            Map<LinkedList<Long>, Double> puntajesPorRuta
                    = asignarPuntajesRutas(rutasFiltradasSegunPlazoPedido, this.instanteActual, pedidoElegido);
//            lr.appendReport("puntajesPorRuta (ya validadas según plazo y destino del pedido): \n");
//            lr.appendMap(puntajesPorRuta);
            List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta);
            if (rclRutasCandidatas.isEmpty()) {
                lr.appendReport("construccionGraspParaUnaProgramacion: RCL de rutas vacía");
                return null; // Lo más probable es que las rutas filtradas estén aberradas o nulas, no hay más que hacer.
            }
            rclValido = true;
            lr.appendReport("construccionGraspParaUnaProgramacion: Rutas que entraron a la RCL:  \n" + rclRutasCandidatas);
            while (!rclRutasCandidatas.isEmpty()) { // Solo para asegurar ruta factible
                rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, false);
                lr.appendReport("rutaElegida: "+rutaElegida);
                capacidadRuta = estadoGlobal.obtenerCapacidadRutaEnEstadoActual(rutaElegida, pedidoElegido, instanteActual); // capacidades, no plazos.
                lr.appendReport("esRutaValida: "+(capacidadRuta>0));
                if (capacidadRuta <= 0) {
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue; // el productoAgarrado no se define, queda en null aún.
                }
                productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
                // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
                if (productoAgarrado == null) { //throw new IllegalStateException("¡¿Cómo?!"); // xd
                    lr.appendReport("wtf, el producto agarrado fue nulo");
                    System.out.println("wtf, el producto agarrado fue nulo");
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue;
                }
                break;
            }
            if (productoAgarrado == null) {
                lr.appendReport("construccionGraspParaUnaProgramacion: Producto nulo, rcl invalido, nuevo rcl por generar");
                rclValido = false; // quiere decir qu.3e en toda la RCL no consiguió nada
            }
        } while (!rclValido && !rutasFiltradasSegunPlazoPedido.isEmpty());
        if (productoAgarrado == null) return null;
//        if (!productoAgarrado.isExiste()) { // OJO: Alteramos estado!!! Se supone que entrará solo si es nuevo.
//            estadoGlobal.anadirProducto(productoAgarrado);
//        } <- en otro lado mutamos el estado, afuerita
        return new ConstruccionProgramacion(
                rutaElegida, productoAgarrado, capacidadRuta-1 // ya que uno se va a usar ahora
        );
    }

    /**
     * Construye hasta 'maximoPorCrear' programaciones para el pedido dado, reusando rutas cuando convenga.
     * Devuelve la lista de Programacion creadas (puede ser vacía si no se pudo crear ninguna).
     */
    private  List<Programacion>  construirVariasPrograsYPersistir(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido,
            int maximoPorCrear
    ){
        if (rutasFiltradasSegunPlazoPedido == null || rutasFiltradasSegunPlazoPedido.isEmpty() || maximoPorCrear <= 0)
            return Collections.emptyList();

        long idPedido = pedidoElegido.getId();
        LinkedList<Long> rutaAReutilizar = null;
        int capacidadDeLaRutaAReutilizar = 0;
        List<Programacion> prograsAPersistir = new ArrayList<>();

        int created = 0;

        do{
            Producto productoAgarrado = null;
            Programacion prograARealizar = null;
            if(capacidadDeLaRutaAReutilizar <= 0){
                ConstruccionProgramacion cp =
                        obtenerRutaYProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);
                if (cp == null) {
                    lr.appendReport("wtf, no se pudo obtener ruta y programación");
                    rutaAReutilizar = null; capacidadDeLaRutaAReutilizar=0;
                    continue;
                }
                rutaAReutilizar = cp.ruta();
                capacidadDeLaRutaAReutilizar = cp.capacidadRutaParaMasProds();
                productoAgarrado = cp.productoEscogido();
            }else{
                productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);

                if (productoAgarrado == null) {
                    lr.appendReport("wtf, el producto agarrado fue nulo");
                    rutaAReutilizar = null; capacidadDeLaRutaAReutilizar=0;
                    continue;
                }
                capacidadDeLaRutaAReutilizar--; // desgastamos la capacidad
            }

            // Antes de reservar definitivamente, revalidar capacidad real de la ruta
            lr.appendReport("rutaAReutilizar antes de obtener capacidad: " + rutaAReutilizar);
            int capAhora = estadoGlobal.obtenerCapacidadRutaEnEstadoActual(rutaAReutilizar, pedidoElegido, instanteActual);
            if (capAhora <= 0) {
                lr.appendReport("construirVariasPrograsYPersistir: ruta perdió capacidad antes de reservar: " + rutaAReutilizar);
                // invalidar ruta actual y forzar buscar una nueva
                rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                rutaAReutilizar = null;
                capacidadDeLaRutaAReutilizar = 0;
                // opcional: remover esta ruta de rutasFiltradasSegunPlazoPedido para no reintentar

                continue;
            }

            prograARealizar =
                    new Programacion(idPedido, productoAgarrado.getUuid(), rutaAReutilizar);
            prograsAPersistir.add(prograARealizar);
            if (!productoAgarrado.isExiste()) { // OJO: Alteramos estado!!! Se supone que entrará solo si es nuevo.
                estadoGlobal.anadirProducto(productoAgarrado);
            }
            estadoGlobal.anadirProgramacionSolucion(prograARealizar); // mutar estado global!

        }while(rutaAReutilizar != null);

        return prograsAPersistir;
    }

    /**
     * Construye hasta 'maxToCreate' programaciones para el pedido dado, reusando rutas cuando convenga.
     * Devuelve la lista de Programacion creadas (puede ser vacía si no se pudo crear ninguna).
     */
    private List<Programacion> construirVariasPrograsYPersistir2(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido,
            int maxToCreate
    ) {
        if (rutasFiltradasSegunPlazoPedido == null || rutasFiltradasSegunPlazoPedido.isEmpty() || maxToCreate <= 0)
            return Collections.emptyList();

        long idPedido = pedidoElegido.getId();
        LinkedList<Long> rutaAReutilizar = null;
        int capacidadDeLaRutaAReutilizar = 0;
        List<Programacion> prograsAPersistir = new ArrayList<>(Math.min(16, maxToCreate));

        int created = 0;
        // Mantener un conjunto local de rutas actualmente consideradas para evitar repetir rutas ya descartadas
        Set<LinkedList<Long>> rutasDescartadas = new HashSet<>();

        while (created < maxToCreate) {
            Producto productoAgarrado = null;
            // Si no hay capacidad en la ruta reutilizable, conseguir nueva ruta
            if (capacidadDeLaRutaAReutilizar <= 0 || rutaAReutilizar == null) {
                // obtener nueva ruta válida (obtenerRutaYProgramacion ya remueve rutas inválidas de la lista interna)
                ConstruccionProgramacion cp = obtenerRutaYProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);
                if (cp == null) {
                    // No hay más rutas válidas
                    break;
                }
                rutaAReutilizar = cp.ruta();
                capacidadDeLaRutaAReutilizar = cp.capacidadRutaParaMasProds();
                productoAgarrado = cp.productoEscogido();
                // si la ruta recién obtenida fue descartada por concurso anterior, evitar volver a usarla
                if (rutasDescartadas.contains(rutaAReutilizar)) {
                    rutaAReutilizar = null;
                    capacidadDeLaRutaAReutilizar = 0;
                    continue;
                }
            } else {
                // Reusar ruta existente: intentar escoger otro producto
                productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
                if (productoAgarrado == null) {
                    // la ruta no tiene producto ya (o hubo cambio) -> descartarla y buscar otra
                    rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                    rutasDescartadas.add(rutaAReutilizar);
                    rutaAReutilizar = null;
                    capacidadDeLaRutaAReutilizar = 0;
                    continue;
                }
            }

            // revalidación FINAL antes de reservar (evita inconsistencias)
//            int capAhora = estadoGlobal.obtenerCapacidadRutaEnEstadoActual(rutaAReutilizar, pedidoElegido, instanteActual);
//            if (capAhora <= 0) {
//                lr.appendReport("construirVariasPrograsYPersistir: ruta perdió capacidad antes de reservar: " + rutaAReutilizar);
//                // eliminar la ruta del pool y buscar otra
//                rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
//                rutasDescartadas.add(rutaAReutilizar);
//                rutaAReutilizar = null;
//                capacidadDeLaRutaAReutilizar = 0;
//                continue;
//            }

            // Decidir cuántas programaciones usar de esta ruta: al menos 1, hasta min(capacidadDeLaRutaAReutilizar, capAhora, remaining)
            int allowedFromRoute = Math.min(capacidadDeLaRutaAReutilizar, Math.min(capacidadDeLaRutaAReutilizar, maxToCreate - created));
            // generamos programaciones **una por una** (porque cada adición muta estadoGlobal y afecta siguiente disponibilidad)
            for (int i = 0; i < allowedFromRoute && created < maxToCreate; i++) {
                Programacion prograARealizar = new Programacion(idPedido, productoAgarrado.getUuid(), rutaAReutilizar);
                prograsAPersistir.add(prograARealizar);
                // si producto es "nuevo", registrarlo
                if (!productoAgarrado.isExiste()) {
                    estadoGlobal.anadirProducto(productoAgarrado);
                }
                // reservar en el estado (esto decrementa capacidades en vuelos etc.)
                estadoGlobal.anadirProgramacionSolucion(prograARealizar);
                created++;
                capacidadDeLaRutaAReutilizar--; // hemos consumido una unidad de la capacidad estimada

                // si aún queremos más y la ruta mantiene productos disponibles, preseleccionar otro producto para siguiente iteración:
                if (i < allowedFromRoute - 1) {
                    productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
                    if (productoAgarrado == null) {
                        // la ruta ya no tiene más productos
                        rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                        rutasDescartadas.add(rutaAReutilizar);
                        rutaAReutilizar = null;
                        capacidadDeLaRutaAReutilizar = 0;
                        break;
                    }
                }
            } // end for allowedFromRoute

            // si capacidadDeLaRutaAReutilizar llegó a 0, forzamos buscar otra en próximo while
            if (capacidadDeLaRutaAReutilizar <= 0) {
                rutaAReutilizar = null;
            }
        } // end while created < maxToCreate

        return prograsAPersistir;
    }



    private Producto escogerProductoEnRuta(LinkedList<Long> ruta, Pedido pedido) {
//        if(ruta.getFirst() == 1340L || ruta.getLast() == 1340L)
//            System.out.println("ruta a escogerle prod: "+ ruta);
        Almacen almacenOrigen = estadoGlobal.getAlmacenes().get(
                estadoGlobal.getVuelos().get(ruta.getFirst()).getIdAlmacenOrigen());
        Almacen almacenDestino = estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        if (almacenOrigen == null)
            throw new IllegalStateException("¿Cómo llegó un almacén nulo aquí?"); // no debería pasar...
        if (almacenOrigen.isEsInfinito()) { // Es un almacén no intermedio
            return new Producto(almacenOrigen.getId(), ruta, instanteActual);
        }
        // A partir de aquí, sí es un almacén intermedio. Veremos sus prods en el futuro a ver cuál agarramos.
        List<Producto> productosDelOrigenEnPrimerVuelo = estadoGlobal.obtenerProductosAlmacenOrigenEnRuta(ruta);
        // División entre continentales e intercontinentales
        Map<Boolean, List<Producto>> listaPartidaProds = productosDelOrigenEnPrimerVuelo.stream()
                .collect(Collectors.partitioningBy(producto -> {
                            Continente continenteOrigen =
                                    estadoGlobal.getAlmacenes().get(producto.getIdAlmacenInfinitoOrigen()).getContinente();
                            return continenteOrigen.equals(almacenDestino.getContinente()); // true si continental
                        }
                ));
        List<Producto> productosContinentales = listaPartidaProds.get(true);
        List<Producto> productosIntercontinentales = listaPartidaProds.get(false);

        // Si **ambas** listas están vacías (pese a la lista inicial no vacía), defensiva:
        if ((productosContinentales == null || productosContinentales.isEmpty())
                && (productosIntercontinentales == null || productosIntercontinentales.isEmpty())) {
            lr.appendReport("escogerProductoEnRuta: después de particionar, NO quedan productos útiles en ruta: " + ruta);
            return null;
        }

        Producto productoAAgarrar;
        if (!productosIntercontinentales.isEmpty() && !productosContinentales.isEmpty()) {
            Double aleatorio = generadorAleatorio.nextDouble(); // Sale de 0 a 1
            Double umbralIntercontinental = pedido.isIntercontinentalAhora() ? // asegurarse de que esto se mantenga act.
                    UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA : UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA;
            if (aleatorio < umbralIntercontinental) {
                productoAAgarrar = productosIntercontinentales.get(0); // el primerito nomás, cualquiera...
                // ¿o deberíamos hacerlo de forma más inteligente? (ejm: sacar de un continente cercano) <- pto. de mejora
                return productoAAgarrar;
            } else {
                productoAAgarrar = productosContinentales.get(0);
                return productoAAgarrar;
            }
        } else {
            productoAAgarrar = !productosContinentales.isEmpty() ?
                    productosContinentales.get(0) :
                    productosIntercontinentales.get(0);
            return productoAAgarrar;
        }
    }


    private List<LinkedList<Long>> obtenerRutasConMismoDestinoQuePedido(Pedido pedido) {
        Almacen almacen = estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        List<LinkedList<Long>> rutasConDestinoCompartido =
                estadoGlobal.getRutasPorIdAlmacenDestino().get(almacen.getId());
        return rutasConDestinoCompartido;
    }

    private List<LinkedList<Long>> filtrarRutasSegunPlazoPedido(Pedido pedido, List<LinkedList<Long>> rutasConDestinoCompartido) {
        lr.appendReport("Pedido: "+ pedido);

        List<LinkedList<Long>> rutas =
                rutasConDestinoCompartido.stream()
                        .filter(ruta -> {
//                                    if(debug) lr.appendReport("Ruta: " + ruta.toString());
//                                    if(debug) lr.appendReport("Último vuelo: " + estadoGlobal
//                                            .getVuelos().get(ruta.getLast()));
                                    return estadoGlobal
                                            .getVuelos().get(ruta.getLast())
                                            .entregariaPedidoEnPlazoReal(pedido);
                                }

                        ).collect(Collectors.toList());
        return rutas; // menos eficiencia xdd pero pa que funque, porque el toList
        // de stream da listas inmutables
    }


    /*
     * Función que asigna puntajes a rutas según la siguiente formula:
     * score = alfa1 * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial
     * 
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a 0 significa que la ruta es mejor valorada.
     * Falta tunear los coeficientes alfa1 y alfa2
     * También podría evaluar qué tan cargados estén los vuelos (esto no esta ni implementado ni modelado en la ecuación).
     * También podría evaluar qué tanto espacio va a ocupar en almacenes escala (esto no esta ni implementado ni modelado en la ecuación).
     * 
     */
    private Map<LinkedList<Long>, Double> asignarPuntajesRutas(
            List<LinkedList<Long>> rutas,
            Instant instanteActual,
            Pedido pedido
    ) {
//        lr.appendReport("me llegó para asignar puntaje: "+ rutas.size() + " rutas, instante act: "+instanteActual
//        +" pedido: "+pedido);
        Double score, alfa1, alfa2, aptitudTemporal, aptitudLogística, aptitudEspacial;
        Pair<Double, Double> aptitudes;
        List<Vuelo> vuelos;
        Map<LinkedList<Long>, Double> scores = new HashMap<>();

        alfa1 = 0.5;
        alfa2 = 0.7;

        for (LinkedList<Long> ruta : rutas) {
            vuelos = estadoGlobal.obtenerVariosVuelosPorIds(ruta, entradaRecibida);
            lr.appendReport("vuelos de ruta a asignar puntaje: "+ruta);
            aptitudTemporal = this.calcularAptitudTemporal(vuelos, instanteActual, pedido);
            aptitudes = this.calcularAptitudLogisticaYEspacial(vuelos, estadoGlobal );
            aptitudLogística = aptitudes.getLeft();
            aptitudEspacial = aptitudes.getRight();

            score = alfa1 * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial;
//            lr.appendReport("score obtenido en ruta: "+score + " ruta");
            scores.put(ruta, score);
        }
            
        return scores;
    }

    /*
     * Calcula segun la formula:
     * (instantePrimerVuelo - instanteActual) / (instanteMaximoParaEntregar - instanteUltimoVuelo) 
     * 
     * Las ruta asume que el primer vuelo todavía no sale
     * 
     * instantePrimerVuelo -> instante de salida del primer vuelo de la ruta
     * instanteUltimoVuelo -> instante de llegada del ultimo vuelo dela ruta
     * instanteActual -> instante en el que se solicito la planificación
     * instanteMaximoParaEntregar -> instante de entrega máximo
     * 
     */
    private Double calcularAptitudTemporal(List<Vuelo> ruta, Instant instanteActual, Pedido pedido) {
        Double tiempoPartida, tiempoSobrante;
        Instant instantePrimerVuelo, instanteMaximoParaEntregar, instanteUltimoVuelo;

        instantePrimerVuelo = ruta.get(0).getInicio();
        instanteUltimoVuelo = ruta.get(ruta.size() - 1).getFin();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
        tiempoPartida = Duration.between(instanteActual, instantePrimerVuelo).toMillis() / 1000.0;
        tiempoSobrante = Duration.between(instanteUltimoVuelo, instanteMaximoParaEntregar).toMillis() / 1000.0;

        return (tiempoPartida) / (tiempoSobrante);
    }

    /*
     * Calcula según la formula:
     * aptitudLogística = nVuelos / sum(sqrt(tiempoVuelo_i ^ 2 + tiempoEspera ^ 2_i))
     * aptitudEspacial = (capacidadOcupada ) / capacidadTotal
     * 
     * nVuelos -> cantidad de vuelos que posee la ruta
     * tiempoVuelo_i -> duración del vuelo i-ésimo de la ruta evaluada
     * tiempoEspera_i -> duración de la espera i-ésima antes de abordar el siguiente vuelo
     * capacidadOcupada -> capacidad ocupada del almacén capacidadTotal
     * capacidadTotal -> capacidad máxima del almacén
     * 
     */
    private Pair<Double, Double> calcularAptitudLogisticaYEspacial(List<Vuelo> ruta, EstadoGlobal estado) {
        Integer nVuelos;
        Double tiempoVuelo, tiempoEspera, espacioAlmacen,  aptitudLogística, aptitudEspacial;
        Instant instanteSalida, instanteLlegada;
        Vuelo vueloAnterior;
        Almacen almacenLlegada;

        nVuelos = 0;
        aptitudLogística = aptitudEspacial = tiempoEspera = 0D;

        for (Vuelo vuelo : ruta) 
        {
            instanteSalida = vuelo.getInicio();
            instanteLlegada = vuelo.getFin();
            almacenLlegada = estado.buscarAlmacen(vuelo.getIdAlmacenDestino());
            tiempoVuelo = Duration.between(instanteSalida, instanteLlegada).getSeconds() / 3600.0;
            espacioAlmacen = (double) almacenLlegada.getCapacidadOcupada() / almacenLlegada.getCapacidadMaxima();

            if(nVuelos > 0)
            {
                vueloAnterior = ruta.get(nVuelos - 1);
                instanteSalida = vuelo.getInicio();
                instanteLlegada = vueloAnterior.getFin();
                tiempoEspera = Duration.between(instanteLlegada, instanteSalida).getSeconds() / 3600.0;
            }

            aptitudLogística += Math.sqrt(Math.pow(tiempoVuelo, 2) + Math.pow(tiempoEspera, 2));
            aptitudEspacial += espacioAlmacen;
            nVuelos++;
        }
        
        aptitudLogística = nVuelos / aptitudLogística;
        aptitudEspacial = aptitudEspacial / nVuelos;

        return Pair.of(aptitudLogística, aptitudEspacial);
    }

    /**
     * Construye la RCL a partir del mapa ruta->score. Convención: score mayor = PEOR.
     * Garantiza que, para cada almacén destino no infinito (si existe alguna ruta para él),
     * al menos la mejor ruta (por score) quede incluida en la RCL resultante.
     *
     * @param scores mapa ruta -> score (MENOR = mejor)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */ // DEUDA TÉCNICA, CREO QUE DA IGUAL LO DE AL MENOS UNA PARA CADA ALMACÉN
    // RCL de rutas: ahora score menor = mejor
    private List<LinkedList<Long>> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
            Map<LinkedList<Long>, Double> scores) {

        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        // 0. obtener alpha (usar campo de clase o fallback)
        double alphaLocal = this.ALPHA_RUTAS;
        if (Double.isNaN(alphaLocal) || alphaLocal < 0.0 || alphaLocal > 1.0) alphaLocal = 0.1;

        // 1) calc min/max scores
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null || v.isNaN()) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Double.isInfinite(min) || Double.isInfinite(max) || Double.isNaN(min) || Double.isNaN(max))
            return Collections.emptyList();

        // 2) Umbral: ahora score menor = mejor
        double threshold = min + alphaLocal * (max - min);

        // 3) RCL inicial por umbral (LinkedHashSet para evitar duplicados y mantener determinismo)
        Set<LinkedList<Long>> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Encontrar la mejor ruta por destino (ignorando destinos infinitos)
        Map<Long, LinkedList<Long>> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();

        for (Map.Entry<LinkedList<Long>, Double> e : scores.entrySet()) {
            LinkedList<Long> ruta = e.getKey();
            Double score = e.getValue() == null || e.getValue().isNaN() ? Double.POSITIVE_INFINITY : e.getValue();

            if (ruta == null || ruta.isEmpty()) continue;

            long ultimoVueloId = ruta.getLast();

            // obtener objeto vuelo
            Vuelo vueloUltimo = estadoGlobal.getVuelos().get(ultimoVueloId);
            if (vueloUltimo == null) {
                lr.appendReport(
                        "construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
                continue;
            }

            // obtener id almacen destino desde el vuelo y then almacen
            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
            Almacen alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
            if (alm == null) {
                lr.appendReport(
                        "construirRCL: vuelo id=" + ultimoVueloId + " apunta a almacenDestino id=" + idAlmacenDestino
                                + " que no existe en mesa -> se ignora ruta.");
                continue;
            }

            // ignorar destinos infinitos
            if (alm.isEsInfinito()) continue;

            // actualizar mejor por destino: ahora menor score = mejor
            Double bestScore = bestScoreByDestino.get(idAlmacenDestino);
            if (bestScore == null || score < bestScore) {
                bestScoreByDestino.put(idAlmacenDestino, score);
                bestByDestino.put(idAlmacenDestino, ruta);
            }
        }

        // 5) Asegurar que la mejor ruta por destino esté en la RCL
        for (Map.Entry<Long, LinkedList<Long>> be : bestByDestino.entrySet()) {
            LinkedList<Long> bestRuta = be.getValue();
            if (bestRuta != null) rclSet.add(bestRuta);
        }

        // 6) Ordenar por score ascendente (mejor primero) y devolver
        List<LinkedList<Long>> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)
        ));
        return rcl;
    }



    //
//    /**
//     * Selecciona aleatoriamente un pedido desde la RCL.
//     * @param rcl lista no vacía (puede ser vacía -> retorna null)
//     * @param scores mapa pedido->score (opcional si weighted=false)
//     * @param rng Random instance (si null, se crea una nueva)
//     * @param weighted si true selecciona ponderado por score; si false selección uniforme
//     * @return pedido seleccionado o null si rcl vacío
//     */
    private Pedido seleccionarPedidoDesdeRCL(List<Pedido> rcl,
                                             Map<Pedido, Double> scores,
                                             Random rng,
                                             boolean weighted) {
        if (rcl == null || rcl.isEmpty()) return null;
        if (rng == null) rng = generadorAleatorio;

        if (!weighted) {
            return rcl.get(rng.nextInt(rcl.size()));
        } else {
            // selección ponderada por score (aseguramos pesos positivos)
            double sum = 0.0;
            List<Double> weights = new ArrayList<>(rcl.size());
            for (Pedido p : rcl) {
                double s = scores == null ? 1.0 : scores.getOrDefault(p, 1.0);
                double w = Math.max(1e-6, s); // evita pesos 0
                weights.add(w);
                sum += w;
            }
            double pick = rng.nextDouble() * sum;
            double acc = 0.0;
            for (int i = 0; i < rcl.size(); i++) {
                acc += weights.get(i);
                if (pick <= acc) return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size() - 1);
        }
    }

    /*
     * Función que asigna puntajes a pedidos según la siguiente formula:
     * score = urgenciaTiempo + urgenciaTamaño
     * 
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a 0 significa que el pedido es más urgente. Para pedidos iguales de urgentes, el valor de score es de aproximadamente 6 y aumenta de forma logaritmica
     * 
     */
    private Map<Pedido, Double> asignarPuntajesPedidos(
            List<Pedido> pedidos, Instant instanteActual
    ) {
        Double score;
        Map<Pedido, Double> scores = new HashMap<>();
        
        try {
            for (Pedido pedido : pedidos)
            {
                score = this.calcularUrgenciaTiempo(pedido, instanteActual) + this.calcularUrgenciaTamano(pedido);
                scores.put(pedido, score);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return scores;
    }

    /*
     * Calcula segun la formula:
     * urgenciaTiempo = (instanteMaximoParaEntregar - instanteActual) / ( instanteMaximoParaEntregar - instanteRegistro)
     * 
     * instanteMaximoParaEntregar -> instante de entrega máximo
     * instanteActual -> instante en el que se solicito la planificacion
     * instanteRegistro -> instante de registro del pedido
     * 
     */
    private Double calcularUrgenciaTiempo(Pedido pedido, Instant instanteActual) {
        Double urgenciaTiempo, tiempoRestante, tiempoMaximoParaEntregar;
        Instant instanteRegistro, instanteMaximoParaEntregar;

        instanteRegistro = pedido.getInstanteRegistro();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
        tiempoRestante = Duration.between(instanteActual, instanteMaximoParaEntregar).toMillis() / 1000.0;
        tiempoMaximoParaEntregar = Duration.between(instanteRegistro, instanteMaximoParaEntregar).toMillis() / 1000.0;
        urgenciaTiempo = tiempoRestante/tiempoMaximoParaEntregar;

        return urgenciaTiempo;
    }
    
    /*
     * Calcula segun al formula:
     * ln((1 + productosTotales) / (1 + productosEntregados))
     * 
     * productosTotales -> cantidad de productos que compone el pedido
     * productosEntregados -> cantidad de productos entregados
     * 
     */
    private Double calcularUrgenciaTamano(Pedido pedido) {
        Integer productosTotales, productosEntregados;
        Double urgenciaTamano;

        productosEntregados = pedido.getCantidadProductosEntregados();
        productosTotales = pedido.getCantidadProductosPedidos();
        urgenciaTamano = (productosTotales + 1.0)/(productosEntregados + 1.0);
        urgenciaTamano = Math.log(urgenciaTamano);

        return urgenciaTamano;
    }



    //
//    /**
//     * Construye la RCL de pedidos a partir de un mapa pedido->score.
//     * Convención: score MENOR = mejor.
//     *
//     * alpha in [0,1]. alpha = 0 => solo el mejor; alpha = 1 => todos.
//     */
// RCL de pedidos: ahora score menor = mejor
    private List<Pedido> construirRCLDePedidos(Map<Pedido, Double> scores, double alpha) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null || v.isNaN()) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // defensiva
        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();

        // umbral: ahora score menor = mejor -> threshold parte de min hacia max
        double threshold = min + alpha * (max - min);

        List<Pedido> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // ordenar por score ascendente (mejor = menor primero)
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)
        ));

        return rcl;
    }



    //
//    /**
//     * Selecciona una ruta desde la RCL.
//     * @param rcl lista de rutas candidatas (no vacía)
//     * @param scores mapa ruta->score (debe contener las rutas)
//     * @param rng Random
//     * @param weighted si true se selecciona ponderado por score; si false seleccion uniforme
//     */
    private LinkedList<Long> seleccionarRutaDesdeRCL(
            List<LinkedList<Long>> rcl,
            Map<LinkedList<Long>, Double> scores,
//            Random rng,
            boolean weighted) {
        if (rcl == null || rcl.isEmpty()) return null;
        Random rng = generadorAleatorio;

        if (!weighted) {
            return rcl.get(rng.nextInt(rcl.size()));
        } else {
            // ponderado por score (score may be 0..1)
            double sum = 0.0;
            List<Double> ws = new ArrayList<>(rcl.size());
            for (LinkedList<Long> r : rcl) {
                double s = scores.getOrDefault(r, 0.0);
                // evitar 0 estrictos -> small epsilon
                double w = Math.max(1e-6, s);
                ws.add(w);
                sum += w;
            }
            double pick = rng.nextDouble() * sum;
            double acc = 0.0;
            for (int i = 0; i < rcl.size(); i++) {
                acc += ws.get(i);
                if (pick <= acc) return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size() - 1);
        }
    }
}

/*
* private Programacion construccionGraspParaUnaProgramacion(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido
    ) {
        Producto productoAgarrado = null;
        LinkedList<Long> rutaElegida = null;
        boolean rclValido;
        do { // Medio rara esta lógica... Pero creo que es necesaria
            Map<LinkedList<Long>, Double> puntajesPorRuta
                    = asignarPuntajesRutas(rutasFiltradasSegunPlazoPedido, this.instanteActual, pedidoElegido); // <- chamba de Axel
//            lr.appendReport("puntajesPorRuta (ya validadas según plazo y destino del pedido): \n");
//            lr.appendMap(puntajesPorRuta);
            List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta);
            if (rclRutasCandidatas.isEmpty()) {
                lr.appendReport("construccionGraspParaUnaProgramacion: RCL de rutas vacía");
                return null; // Lo más probable es que las rutas filtradas estén aberradas o nulas, no hay más que hacer.
            }
            rclValido = true;
            lr.appendReport("construccionGraspParaUnaProgramacion: Rutas que entraron a la RCL:  \n" + rclRutasCandidatas);
            while (!rclRutasCandidatas.isEmpty()) { // Solo para asegurar ruta factible
                rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, false);
                lr.appendReport("rutaElegida: "+rutaElegida);
                boolean esRutaValida = estadoGlobal.obtenerCapacidadRutaEnEstadoActual(rutaElegida, pedidoElegido, instanteActual); // capacidades, no plazos.
                lr.appendReport("esRutaValida: "+esRutaValida);
                if (!esRutaValida) {
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue; // el productoAgarrado no se define, queda en null aún.
                }
                productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
                // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
                if (productoAgarrado == null) { //throw new IllegalStateException("¡¿Cómo?!"); // xd
                    lr.appendReport("wtf, el producto agarrado fue nulo");
                    System.out.println("wtf, el producto agarrado fue nulo");
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue;
                }
                break;
            }
            if (productoAgarrado == null) {
                lr.appendReport("construccionGraspParaUnaProgramacion: Producto nulo, rcl invalido, nuevo rcl por generar");
                rclValido = false; // quiere decir que en toda la RCL no consiguió nada
            }
        } while (!rclValido && !rutasFiltradasSegunPlazoPedido.isEmpty());
        if (productoAgarrado == null) return null;
        if (!productoAgarrado.isExiste()) { // OJO: Alteramos estado!!! Se supone que entrará solo si es nuevo.
            estadoGlobal.anadirProducto(productoAgarrado);
        }
        return new Programacion(pedidoElegido.getId(), productoAgarrado.getUuid(), rutaElegida);
    }
*
* */


/*
        int totalRutas = rutasPosibles.size();
        int rutasConIdsFaltantes = 0;
        for (LinkedList<Long> ruta : rutasPosibles) {
            for (Long id : ruta) {
                if (!vuelosKeysEstado.contains(id)) {
                    rutasConIdsFaltantes++;
                    lr.appendReport("INCONSISTENCIA: ruta contiene id de vuelo ausente en estadoGlobal.vuelos: " + id + " ruta=" + ruta);
                    // comparar con entrada
                    if (!vuelosKeysEntrada.contains(id)) {
                        lr.appendReport("   -> El id tampoco está en entrada.estadoGlobal.vuelos");
                    } else {
                        lr.appendReport("   -> El id SÍ está en entrada.estadoGlobal.vuelos (desalineamiento snapshot)");
                    }
                }
            }
        }
        lr.appendReport(String.format("Check rutas: total=%d, rutasConIdsFaltantes=%d, vuelosEstado=%d, vuelosEntrada=%d",
                totalRutas, rutasConIdsFaltantes, vuelosKeysEstado.size(), vuelosKeysEntrada.size()));
        */