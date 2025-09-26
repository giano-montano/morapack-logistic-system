package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Component
@Primary // Si en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class GraspAndGeneticAlgorithmStrategy extends PlanificationStrategy {

    long seed = new Random().nextLong();
//    private LoggingReport loggingReport = new LoggingReport();
    private EstadoGlobalMutableProblemaPlanificacion estadoGlobal;

    private static final double alpha = 0.2; // número máximo de tramos por ruta (incluye primer vuelo)
    private static final int MAX_INTERATIONS_FIRST_GRASP = 5000;

    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion input) throws Exception {
        estadoGlobal = EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(input); // aquí recién se inyecta, aunque podría ser desde antes ehhh!
        estadoGlobal.setLoggingReport(loggingReport);
        seed = input.getSeed()!=null?input.getSeed():seed;
//        System.out.println("Seed: " + seed);
        loggingReport.appendReport("Inicio de planificacion con varios GRASP. pedidos="
                + estadoGlobal.getPedidos().size() + ", vuelos=" + estadoGlobal.getVuelos().size() + ", almacenes=" + estadoGlobal.getAlmacenes().size()+
                "\n seed: " + seed);
        // límite de iteraciones para evitar ciclos infinitos (ajustar según el dominio)
        int iter = 0;
        try {
            while(estadoGlobal.hayPedidosPendientesPorProgramar() && iter < MAX_INTERATIONS_FIRST_GRASP){
                loggingReport.appendReport(String.format("Iteración %d: quedan %d pedidos pendientes", iter, estadoGlobal.contarPedidosPendientes()));
                loggingReport.appendReport("Necesito planificar una ruta para pedido...");

                RutaProgramadaParaAlgoritmo rutaConstruidaGrasp = construccionGRASPParaUnaRuta();
                // GA AQUI?????????????
                // ya actualiza el input en memoria!
                if (rutaConstruidaGrasp == null) {
                    loggingReport.appendReport("GRASP no pudo construir más rutas (null) — terminando planificación. SIGUE INTENTANDO!!!!!!!!!!!!!");
                    iter++;
                    continue;
                }
                // en un futuro podría añadir el GA aquí
                // Añadir el envío a la solución
                estadoGlobal.anadirRutaSolucion(rutaConstruidaGrasp);
                loggingReport.appendReport("Ruta construida añadido a la solución: " + rutaConstruidaGrasp);

                // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
                boolean removed = estadoGlobal.eliminarPedidoYaSatisfecho(rutaConstruidaGrasp.getIdPedidoAsociado());
                if (removed)
                    loggingReport.appendReport("Se eliminó el pedido "+rutaConstruidaGrasp.getIdPedidoAsociado()+
                            " por estar totalmente programado / atendido.");

                // Guardar reporte parcial si quieres (puedes ajustar la frecuencia)
//                if( iter % 100 == 0)
//                    loggingReport.writeReportFile("grasp-report-iter-" + iter+"-");

                iter++;
            }
            //  COMO METO GA AQUIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
            /*
            * YA NO VERLO COMO PROBLEMA DE RUTEO
            * GENETICO SE CENTRA EN PROBLEMA DE MOCHILA O ASIGNACION
            * INDIVIDUO: UNA SOLUCIÓN (PLANIFICACIÓN) = UNA LISTA DE RUTAS PROGRAMADAS {lista de vuelos ordenada;
            *   Pedido; CantidadParcialOTotalDelPedidoAtender}
            * POBLACIÓN: CONJUNTO DE INDIVIDUOS
            * Genético necesita partir con población
            * Iteración de grasp nos dio un individuo.
            * Un individuo cromosoma solución desde la perspectiva del genético:
            * [{Ruta, Pedido1, cantPretendida}, { Ruta , Pedido2, cantPretendida},{ Ruta, Pedido1, cantPretendida}]
            * crossover: intercambiar cantidades, VALIDAS y reparas con cierto porcentaje (?)
            * mutación:
            * spliteo: ...
            * ...
            * ...
            * ...
            * */
            loggingReport.appendReport("Planificación finalizada. Iteraciones realizadas: " + iter +
                    ". Rutas creadas: " + estadoGlobal.getRutasSolucionQueGeneraAlgoritmo().size());

//            System.out.println("solu: "+ estadoGlobal.getRutasSolucionQueGeneraAlgoritmo());
            SalidaProblemaPlanificacion solution =
                    new SalidaProblemaPlanificacion(estadoGlobal.getRutasSolucionQueGeneraAlgoritmo());
            if(estadoGlobal.hayPedidosPendientesPorProgramar()){
                loggingReport.appendReport("NO SE LOGRÓ PLANIFICAR TODO, COLAPSO LOGÍSTICO!!!!!!!!!!!!");
                solution.setColapsado(true);
            }
            if(iter>1)
                loggingReport.writeReportFile("grasp-report-final");
            return solution;
        } catch (Exception ex) {
            loggingReport.appendReport("Excepción en planificar(): " + ex.getMessage());
            loggingReport.writeReportFile("grasp-report-error");
            SalidaProblemaPlanificacion solution =
                    new SalidaProblemaPlanificacion(estadoGlobal.getRutasSolucionQueGeneraAlgoritmo());
            solution.setHuboErrorEjecucion(true);
            solution.setError(ex.getMessage());
            return solution;
//            throw ex;
        }
    }
//
    private RutaProgramadaParaAlgoritmo construccionGRASPParaUnaRuta(){
        try {
            // Primero generamos rutas para todos los destinos posibles
            List<RutaProgramadaParaAlgoritmo>
                    rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios = // recordar que no hay pedidos para almacenes infinitos
                    estadoGlobal.generarRutasParaPedidosPendientes() // top-K orígenes, BFS limitado, maxEscalas generarTodasRutasPosiblesATodosDestinos
                    ; // Lo que sí podría hacer es un RCL que tenga solo las mejores rutas para CADA almacén posible.
            loggingReport.appendReport("Rutas para pedidos pendientes: "+rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios.size());
            if(rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios.isEmpty()){ return null; }
            Map<RutaProgramadaParaAlgoritmo, Double> puntajesPorRuta = evaluarMeritoRutas(rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios);
//            loggingReport.appendReport("Puntajes por ruta:\n " + PrettyPrinter.printMap(puntajesPorRuta));
//            loggingReport.appendReport("Vuelos de cada ruta rcl:\n " + estadoGlobal.getVuelos().values().stream().filter(v->
//                      v.getId()) );
            List<RutaProgramadaParaAlgoritmo> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta /*, alpha*/);
            if ( rclRutasCandidatas.isEmpty()) {
                loggingReport.appendReport("RCL de rutas vacía -> null");
                return null;
            }
            loggingReport.appendReport("Rutas que entraron a la RCL:  \n" + rclRutasCandidatas.size() /*PrettyPrinter.printList(rclRutasCandidatas)*/);
//            RutaADestino rutaSeleccionada = seleccionarRutaDesdeRCL(rclRutasCandidatas,puntajesPorRuta,new Random(),false);
            // Recorremos la RCL en orden (puedes barajar si quieres diversidad)
            Random rng = new Random(seed);
            List<RutaProgramadaParaAlgoritmo> rutasAProbar = new ArrayList<>(rclRutasCandidatas);
            // opcional: shuffle para mayor aleatoriedad en ejecuciones repetidas
            Collections.shuffle(rutasAProbar, rng);
            // Podríamos encontrar algún método que soporte el weighted; y también que vaya eliminando la ruta del rcl o el idDestinoFinal como tal...
            // DEUDA TÉCNICA !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            // DEBERÍA AGARRAR CON CIERTO COMPONENTE RANDONÓMICO DE LA RCL, SI NO FUNCA UNO, AGARRAR OTRO CON COMP RANDOMINCO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            for (RutaProgramadaParaAlgoritmo rutaSeleccionada : rutasAProbar) {
                if (rutaSeleccionada == null || rutaSeleccionada.getIdsVuelosEnOrden() == null
                        || rutaSeleccionada.getIdsVuelosEnOrden().isEmpty()) {
                    continue;
                }
                loggingReport.appendReport("Vuelos de la ruta Seleccionada:\n " + PrettyPrinter.printList(rutaSeleccionada.getIdsVuelosEnOrden() ) );

                Long idAlmacenDestinoRutaSeleccionada =
                        estadoGlobal.getVueloFromId( rutaSeleccionada.getIdsVuelosEnOrden().getLast()).getIdAlmacenDestino();
//                loggingReport.appendReport("id de almacén final de la ruta: "+idAlmacenDestinoRutaSeleccionada);

                List<PedidoParaAlgoritmo> NpedidosPendientesConDestino =
                        estadoGlobal.getIdsPedidosPorDestino()
                                .getOrDefault(idAlmacenDestinoRutaSeleccionada, Collections.emptyList())
                                .stream()
                                .map(id -> {
                                    PedidoParaAlgoritmo p = estadoGlobal.getPedidos().get(id);
                                    if (p == null) {
                                        if (loggingReport != null) loggingReport.appendReport("ID pedido referenciado pero no existe, no está pendiente: idPedido=" + id);
                                    }
                                    return p;
                                })
                                .filter(Objects::nonNull) // eliminar ids sin pedido en el mapa
                                .filter(p -> p.getCantidadRestanteDeEntregaYProgram() > 0) // solo pendientes
                                .collect(Collectors.toList());
                List<Long> ids = estadoGlobal.getIdsPedidosPorDestino()
                        .getOrDefault(idAlmacenDestinoRutaSeleccionada, Collections.emptyList());
//                loggingReport.appendReport("DEBUG: ids indexados para destino " + idAlmacenDestinoRutaSeleccionada + " => " + ids);
//                loggingReport.appendReport("DEBUG: NpedidosPendientesConDestino" + NpedidosPendientesConDestino + " => destino: " + idAlmacenDestinoRutaSeleccionada);
//                        obtenerPedidosPendientesConDestino(idAlmacenDestinoRutaSeleccionada, pedidos);
                if (NpedidosPendientesConDestino.isEmpty() || NpedidosPendientesConDestino.stream().allMatch(Objects::isNull)) {
                    loggingReport.appendReport("No hay pedidos pendientes para destino " + idAlmacenDestinoRutaSeleccionada + " -> null");
                    continue; // probar la siguiente ruta de la RCL, en vez de returnear null de fresa
                }
                loggingReport.appendReport("Pedidos pendientes con el destino final: "+ NpedidosPendientesConDestino);
                Integer capacidadMaxParaVuelosRuta= estadoGlobal.obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSeleccionada);
                loggingReport.appendReport("Mínima capacidad encontrada en los vuelos de la ruta que llega al destino final: "+capacidadMaxParaVuelosRuta);

                Map<PedidoParaAlgoritmo, Double> puntajesPorPedido= evaluarMeritoPedidos(NpedidosPendientesConDestino/*, envioSolucion, almacenes,vuelos*/); // usa info de pedidos, lo que ya llenamos del envío y estado global
//                loggingReport.appendReport("Puntajes por pedido: \n" + PrettyPrinter.printMap(puntajesPorPedido));
                List<PedidoParaAlgoritmo> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido,alpha);
                if (rclPedidosCandidatos.isEmpty()) {
                    loggingReport.appendReport("RCL de pedidos vacía para esta ruta -> interrumpiendo llenado de esta ruta");
                    break;
                }
                loggingReport.appendReport("Pedidos que entraron a la RCL: \n" + PrettyPrinter.printList(rclPedidosCandidatos));
                PedidoParaAlgoritmo pedidoElegido = seleccionarPedidoDesdeRCL(rclPedidosCandidatos,puntajesPorPedido,rng,false);
                if (pedidoElegido == null) {
                    loggingReport.appendReport("No se seleccionó pedido  -> interrumpiendo llenado de esta ruta");
                    break;
                }
                loggingReport.appendReport("Pedido seleccionado:  \n" + pedidoElegido);
//                int cantidadMaximaPosibleParaELPedidoEnLaRuta = calcularCantidadPosibleALlevarEnRuta(RutaProgramadaParaAlgoritmo rutaProspecto);

                if (estadoGlobal.esFactibleLlevarPedidoEnRuta(pedidoElegido.getId(), rutaSeleccionada) ){
                    int cantidad = decidirCantidadAAsignar(pedidoElegido, rutaSeleccionada);
                    rutaSeleccionada.setIdPedidoAsociado(pedidoElegido.getId());
                    rutaSeleccionada.setCantidadTotalOParcial(cantidad);
//                    estadoGlobal.anadirRutaSolucion(rutaSeleccionada);
//                    envioSolucion = anadirPedidoConCantidad(envioSolucion, pedidoElegido, cantidad, rutaSeleccionada, almacenes, vuelos);
                    // actualizar la capacidadMaxParaVuelosRuta de forma aproximada restando la cantidad asignada
//                    capacidadMaxParaVuelosRuta = actualizarEstadoTemporalEnMemoria(envioSolucion, pedidoElegido, rutaSeleccionada, almacenes, vuelos, NpedidosPendientesConDestino);
// confío en que ya está persistindo en memoria el estado tras anadirRutaSolucion
                    loggingReport.appendReport("cantidad de prods a llevar: " + cantidad);
                    // Estas operaciones son mutaciones en memoria (reservas temporales). Asegúrate de no persistir hasta que decidas confirmar el envío completo (persistir se hace después).
                    loggingReport.appendReport("La solución va luciendo así: \n" + rutaSeleccionada + " con capacidad máxima: " + capacidadMaxParaVuelosRuta);

                    //Si implementas prioridad para agrupar pedidos, considera, tras añadir, reordenar NpedidosPendientesConDestino para intentar consolidaciones.
                }else{
                    loggingReport.appendReport("Pedido id=" + pedidoElegido.getId() + " no factible ");
                }
//                    NpedidosPendientesConDestino = estadoGlobal.removerPedidosSatisfechosOIrrelevantesParaRuta(rutaSeleccionada);
//                    i++;
//                    // safety: evitar loops muy largos en una sola ruta
//                    if (i > 1000) {
//                        loggingReport.appendReport("Iteración excedida en fill-loop (ruta) -> rompiendo");
//                        break;
//                    }
                //             Si construimos al menos 1 producto, devolvemos este envio (quedarán las reservas aplicadas en memoria)
                if (rutaSeleccionada.getCantidadTotalOParcial() > 0) {
                    loggingReport.appendReport("Ruta seleccionada lleva " + rutaSeleccionada.getCantidadTotalOParcial() + " productos. Retornando envío.");
                    return rutaSeleccionada;
                } else {
                    loggingReport.appendReport("La ruta no produjo asignaciones útiles -> probando siguiente ruta de la RCL.");
                    // continuar con la siguiente ruta en rutasAProbar
                }
            }
         // }  end for rutas de la RCL
            // ninguna ruta produjo un envío válido
            loggingReport.appendReport("Ninguna ruta en la RCL produjo un envío válido -> retornando null");
            return null; // aquí recién rompemos la iteración de graspcitos, porque produjo basura (?)
        } catch (Exception ex) {
            loggingReport.appendReport("Error en graspConstructionForOneEnvio: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }
    /**
     * Evalúa todas las rutas candidatas y devuelve un map ruta -> score (mayor = mejor).
     */ // PUEDE MEJORARSE, O USAR LA FUNCIÓN FITNESS DE AXEL
    private Map<RutaProgramadaParaAlgoritmo, Double> evaluarMeritoRutas(List<RutaProgramadaParaAlgoritmo> rutas) {
        // Pesos (ajustables)
        final double wArrival = 0.35;
        final double wLegs = 0.25;
        final double wCapacity = 0.25;
        final double wDemand = 0.15;

        Map<RutaProgramadaParaAlgoritmo, Double> rawArrival = new HashMap<>();
        Map<RutaProgramadaParaAlgoritmo, Integer> rawLegs = new HashMap<>();
        Map<RutaProgramadaParaAlgoritmo, Integer> rawCapacity = new HashMap<>();
        Map<RutaProgramadaParaAlgoritmo, Integer> rawDemand = new HashMap<>();

        // Precalcular demanda pendiente por almacen destino (sum of remaining quantities)
        Map<Long, Integer> demandaPorDestino = new HashMap<>();
        for (PedidoParaAlgoritmo p : estadoGlobal.getPedidos().values()) {
            if (p == null ) continue;
            if (p.getCantidadRestanteDeEntregaYProgram() <= 0) continue;
            demandaPorDestino.merge(p.getIdAlmacenDestino(), p.getCantidadRestanteDeEntregaYProgram(),
                    Integer::sum);
        }

        long minArrivalEpoch = Long.MAX_VALUE;
        long maxArrivalEpoch = Long.MIN_VALUE;
        int minLegs = Integer.MAX_VALUE;
        int maxLegs = Integer.MIN_VALUE;
        int minCap = Integer.MAX_VALUE;
        int maxCap = Integer.MIN_VALUE;
        int minDemand = Integer.MAX_VALUE;
        int maxDemand = Integer.MIN_VALUE;

        // Recolectar raw metrics
        for (RutaProgramadaParaAlgoritmo r : rutas) {
            if (r == null || r.getIdsVuelosEnOrden() == null || r.getIdsVuelosEnOrden().isEmpty()) {
                // asignar valores por defecto bajos
                rawLegs.put(r, 0);
                rawCapacity.put(r, 0);
                rawDemand.put(r, 0);
                rawArrival.put(r, (double) Instant.MAX.getEpochSecond());
                // actualizar mins/maxs de forma defensiva
                minLegs = Math.min(minLegs, 0);
                maxLegs = Math.max(maxLegs, 0);
                minCap = Math.min(minCap, 0);
                maxCap = Math.max(maxCap, 0);
                minDemand = Math.min(minDemand, 0);
                maxDemand = Math.max(maxDemand, 0);
                continue;
            }

            // legs
            int legs = r.getIdsVuelosEnOrden().size();
            rawLegs.put(r, legs);
            minLegs = Math.min(minLegs, legs);
            maxLegs = Math.max(maxLegs, legs);

            // arrival: uso el fin del último vuelo
            VueloParaAlgoritmo ultimo = estadoGlobal.getVuelos().get(
                    r.getIdsVuelosEnOrden().getLast()
            );
//            VueloParaAlgoritmo ultimo = r.getVuelosOrdenados().get(r.getVuelosOrdenados().size() - 1);
            long arrivalEpoch = Long.MAX_VALUE;
            if (ultimo != null && ultimo.getFin() != null) {
                arrivalEpoch = ultimo.getFin().getEpochSecond();
            }
            rawArrival.put(r, (double) arrivalEpoch);
            if (arrivalEpoch != Long.MAX_VALUE) {
                minArrivalEpoch = Math.min(minArrivalEpoch, arrivalEpoch);
                maxArrivalEpoch = Math.max(maxArrivalEpoch, arrivalEpoch);
            }

            // capacity: mínimo disponible (capacidadMaxima - ocupada - reservada) entre legs
            int minAvailable = Integer.MAX_VALUE;
            for (Long idV : r.getIdsVuelosEnOrden()) {
//                if (v == null) continue;
//                int max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
//                int occ = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
//                int res = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
//                int avail = max - occ - res;
                VueloParaAlgoritmo vActual = estadoGlobal.getVuelos().get(idV);
                if(vActual == null) continue;
                int avail = vActual.obtenerCapacidadSinOcupar();
                if (avail< minAvailable) minAvailable = avail;
            }
            if (minAvailable == Integer.MAX_VALUE) minAvailable = 0;
            rawCapacity.put(r, minAvailable);
            minCap = Math.min(minCap, minAvailable);
            maxCap = Math.max(maxCap, minAvailable);

            // demand: pendiente en el almacen destino del ultimo vuelo
            Long destId = ultimo == null ? null : ultimo.getIdAlmacenDestino();
            int demand = destId == null ? 0 : demandaPorDestino.getOrDefault(destId, 0);
            rawDemand.put(r, demand);
            minDemand = Math.min(minDemand, demand);
            maxDemand = Math.max(maxDemand, demand);
        }

        // Si no hubo arrivals válidos, fijar min/max para evitar división por cero
        if (minArrivalEpoch == Long.MAX_VALUE) {
            minArrivalEpoch = 0;
            maxArrivalEpoch = 0;
        }

        // Normalizar y combinar
        Map<RutaProgramadaParaAlgoritmo, Double> scores = new HashMap<>();
        for (RutaProgramadaParaAlgoritmo r : rutas) {
            // legsScore: menos legs -> mejor
            double legsScore;
            int legs = rawLegs.getOrDefault(r, 0);
            if (maxLegs == minLegs) legsScore = 1.0;
            else legsScore = 1.0 - ((double)(legs - minLegs) / (double)(maxLegs - minLegs)); // 1 = fewest legs, 0 = most legs

            // arrivalScore: earlier -> better
            double arrivalScore;
            double arrivalE = rawArrival.getOrDefault(r, (double)Long.MAX_VALUE);
            if (maxArrivalEpoch == minArrivalEpoch) arrivalScore = 1.0;
            else {
                // map arrivalEpoch in [minArrival,maxArrival] to [1..0] (earlier=1)
                arrivalScore = 1.0 - ((arrivalE - minArrivalEpoch) / (double)(Math.max(1, maxArrivalEpoch - minArrivalEpoch)));
            }

            // capacityScore: higher available -> better
            double capScore;
            int cap = rawCapacity.getOrDefault(r, 0);
            if (maxCap == minCap) capScore = 1.0;
            else capScore = (double)(cap - minCap) / (double)(Math.max(1, maxCap - minCap));

            // demandScore: higher demand -> better
            double demandScore;
            int dem = rawDemand.getOrDefault(r, 0);
            if (maxDemand == minDemand) demandScore = 1.0;
            else demandScore = (double)(dem - minDemand) / (double)(Math.max(1, maxDemand - minDemand));

            // Weighted sum
            double score = wArrival * arrivalScore + wLegs * legsScore + wCapacity * capScore + wDemand * demandScore;
            scores.put(r, score);
        }

        return scores;
    }
    /**
     * Construye la RCL a partir del mapa ruta->score. Convención: score mayor = mejor.
     * Garantiza que, para cada almacén destino no infinito (si existe alguna ruta para él),
     * al menos la mejor ruta (por score) quede incluida en la RCL resultante.
     *
     * @param scores mapa ruta -> score (mayor = mejor)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */ // DEUDA TÉCNICA
    private List<RutaProgramadaParaAlgoritmo> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
            Map<RutaProgramadaParaAlgoritmo, Double> scores) {

        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        // 0. obtener alpha (usar campo de clase o fallback)
        double alphaLocal = this.alpha; // asumir campo de clase
        if (Double.isNaN(alphaLocal) || alphaLocal < 0.0 || alphaLocal > 1.0) alphaLocal = 0.1;

        // 1) calc min/max scores
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null || v.isNaN()) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Double.isInfinite(min) || Double.isInfinite(max) || Double.isNaN(min) || Double.isNaN(max)) return Collections.emptyList();

        // 2) Umbral clásico RCL (score mayor = mejor)
        double threshold = max - alphaLocal * (max - min);

        // 3) RCL inicial por umbral (LinkedHashSet para evitar duplicados y mantener determinismo)
        Set<RutaProgramadaParaAlgoritmo> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Encontrar la mejor ruta por destino (ignorando destinos infinitos)
        Map<Long, RutaProgramadaParaAlgoritmo> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();

        for (Map.Entry<RutaProgramadaParaAlgoritmo, Double> e : scores.entrySet()) {
            RutaProgramadaParaAlgoritmo ruta = e.getKey();
            Double score = e.getValue() == null || e.getValue().isNaN() ? Double.NEGATIVE_INFINITY : e.getValue();

            if (ruta == null || ruta.getIdsVuelosEnOrden() == null || ruta.getIdsVuelosEnOrden().isEmpty()) continue;

            long ultimoVueloId = ruta.getIdsVuelosEnOrden().getLast();

            // obtener objeto vuelo
            VueloParaAlgoritmo vueloUltimo = estadoGlobal.getVuelos().get(ultimoVueloId);
            if (vueloUltimo == null) {
                if (loggingReport != null) loggingReport.appendReport(
                        "construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
                continue;
            }

            // obtener id almacen destino desde el vuelo y then almacen
            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
            AlmacenParaAlgoritmo alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
            if (alm == null) {
                if (loggingReport != null) loggingReport.appendReport(
                        "construirRCL: vuelo id=" + ultimoVueloId + " apunta a almacenDestino id=" + idAlmacenDestino
                                + " que no existe en mesa -> se ignora ruta.");
                continue;
            }

            // ignorar destinos infinitos
            if (alm.isEsInfinito()) continue;

            // actualizar mejor por destino
            Double bestScore = bestScoreByDestino.get(idAlmacenDestino);
            if (bestScore == null || score > bestScore) {
                bestScoreByDestino.put(idAlmacenDestino, score);
                bestByDestino.put(idAlmacenDestino, ruta);
            }
        }

        // 5) Asegurar que la mejor ruta por destino esté en la RCL
        for (Map.Entry<Long, RutaProgramadaParaAlgoritmo> be : bestByDestino.entrySet()) {
            RutaProgramadaParaAlgoritmo bestRuta = be.getValue();
            if (bestRuta != null) rclSet.add(bestRuta);
        }

        // 6) Ordenar por score descendente y devolver
        List<RutaProgramadaParaAlgoritmo> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, Double.NEGATIVE_INFINITY),
                scores.getOrDefault(a, Double.NEGATIVE_INFINITY)));
        return rcl;
    }

// antiguo:
//    private List<RutaProgramadaParaAlgoritmo> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
//            Map<RutaProgramadaParaAlgoritmo, Double> scores
////                                                  , double alpha,
//            /*List<AlmacenParaAlgoritmo> almacenes*/) {
//        if (scores == null || scores.isEmpty()) return Collections.emptyList();
//
//        // calc min/max scores
//        double min = Double.POSITIVE_INFINITY;
//        double max = Double.NEGATIVE_INFINITY;
//        for (Double v : scores.values()) {
//            if (v == null) continue;
//            min = Math.min(min, v);
//            max = Math.max(max, v);
//        }
//        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();
//
//        // Umbral clásico RCL (score mayor = mejor)
//        double threshold = max - alpha * (max - min);
//
//        // 1) RCL inicial por umbral
//        Set<RutaProgramadaParaAlgoritmo> rclSet = scores.entrySet().stream()
//                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toCollection(LinkedHashSet::new)); // mantener orden de inserción
//
//        // 2) Mapear la mejor ruta por destino (según score)
//        Map<Long, RutaProgramadaParaAlgoritmo> bestByDestino = new HashMap<>();
//        Map<Long, Double> bestScoreByDestino = new HashMap<>();
//        for (Map.Entry<RutaProgramadaParaAlgoritmo, Double> e : scores.entrySet()) {
//            RutaProgramadaParaAlgoritmo ruta = e.getKey();
//            Double score = e.getValue() == null ? Double.NEGATIVE_INFINITY : e.getValue();
//            if (ruta == null || ruta.getIdsVuelosEnOrden() == null || ruta.getIdsVuelosEnOrden().isEmpty()) continue;
//            long ultimoVueloId = ruta.getIdsVuelosEnOrden().getLast();
//
//// 1) obtener objeto vuelo
//            VueloParaAlgoritmo vueloUltimo = estadoGlobal.getVuelos().get(ultimoVueloId);
//            if (vueloUltimo == null) {
//                // ruta contiene un id de vuelo inválido; lo informamos y saltamos
//                if (loggingReport != null) loggingReport.appendReport("construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
//                continue;
//            }
//
//// 2) obtener id almacen destino desde el vuelo y luego el almacen
//            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
//            AlmacenParaAlgoritmo alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
//            if (alm == null) {
//                if (loggingReport != null) loggingReport.appendReport("construirRCL: vuelo id=" + ultimoVueloId
//                        + " apunta a almacenDestino id=" + idAlmacenDestino + " que no existe en mesa -> se ignora ruta.");
//                continue;
//            }
//
//            Long destinoId = alm.getId();
//
//            // comprobar si el destino es infinito (si recibimos lista de almacenes)
//            if (estadoGlobal.getAlmacenes() != null) {
//                Optional<AlmacenParaAlgoritmo> aOpt = estadoGlobal.getAlmacenes().values().stream()
//                        .filter(a -> a != null && Objects.equals(a.getId(), destinoId))
//                        .findFirst();
//                if (aOpt.isPresent() && aOpt.get().isEsInfinito()) {
//                    // ignorar destinos infinitos
//                    continue;
//                }
//            }
//
//            Double bestScore = bestScoreByDestino.get(destinoId);
//            if (bestScore == null || score > bestScore) {
//                bestScoreByDestino.put(destinoId, score);
//                bestByDestino.put(destinoId, ruta);
//            }
//        }
//
//        // 3) Asegurar que la mejor ruta por destino esté en la RCL
//        for (Map.Entry<Long, RutaProgramadaParaAlgoritmo> be : bestByDestino.entrySet()) {
//            RutaProgramadaParaAlgoritmo bestRuta = be.getValue();
//            if (bestRuta == null) continue;
//            if (!rclSet.contains(bestRuta)) { // !!!!!!!!!!!!!!!???!!!!!!!!!!!!!1
//                rclSet.add(bestRuta);
//            }
//        }
//
//        // 4) Ordenar por score descendente y devolver
//        List<RutaProgramadaParaAlgoritmo> rcl = new ArrayList<>(rclSet);
//        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));
//        return rcl;
//    }

    private int decidirCantidadAAsignar(PedidoParaAlgoritmo pedido,
                                        RutaProgramadaParaAlgoritmo rutaSol) {
        if (pedido == null || rutaSol == null) return 0;
        int remaining = pedido.getCantidadRestanteDeEntregaYProgram();
        if (remaining <= 0) return 0;

        // capacidad mínima disponible en ruta (considerando reservas/ocupados)
        int rutaCapacidadMin = estadoGlobal.obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSol);
        int yaAsignadoEnEnvio =  rutaSol.getCantidadTotalOParcial();
        int disponibleRutaParaAsignar = Math.max(0, rutaCapacidadMin - yaAsignadoEnEnvio);
        if (disponibleRutaParaAsignar <= 0) return 0;

        // stock disponible en almacen origen (primer vuelo)
        VueloParaAlgoritmo primer = estadoGlobal.getVuelos().get(
                rutaSol.getIdsVuelosEnOrden().getFirst());
        if (primer == null) return 0;
        Long idOrigen = primer.getIdAlmacenOrigen();

        AlmacenParaAlgoritmo almacenOrigen = null;
        if (estadoGlobal.getAlmacenes() != null) {
            for (AlmacenParaAlgoritmo a : estadoGlobal.getAlmacenes().values()) {
                if (a != null && Objects.equals(a.getId(), idOrigen)) {
                    almacenOrigen = a;
                    break;
                }
            }
        }
        int disponibleOrigen;
        if (almacenOrigen == null) {
            // conservador: si no conocemos el almacén consideramos que no hay stock
            disponibleOrigen = 0;
        } else if (almacenOrigen.isEsInfinito()) {
            disponibleOrigen = Integer.MAX_VALUE / 4;
        } else {
            int ocupado =  almacenOrigen.getCapacidadOcupada();
//            int reserv = almacenOrigen.getCapacidadReservadaPorEnvios();
            disponibleOrigen = Math.max(0, ocupado /*- reserv*/);
        }
        if (disponibleOrigen <= 0) {
            // si origen sin stock, no se puede asignar
            return 0;
        }

        // cantidad asignable = min(remaining, disponibleRutaParaAsignar, disponibleOrigen)
        int asignable = (int) Math.min( (long) remaining, Math.min((long) disponibleRutaParaAsignar, (long) disponibleOrigen) );
        return asignable; // Math.max(0, asignable);
    }
    // remaining del pedido

    //
//    /**
//     * Selecciona aleatoriamente un pedido desde la RCL.
//     * @param rcl lista no vacía (puede ser vacía -> retorna null)
//     * @param scores mapa pedido->score (opcional si weighted=false)
//     * @param rng Random instance (si null, se crea una nueva)
//     * @param weighted si true selecciona ponderado por score; si false selección uniforme
//     * @return pedido seleccionado o null si rcl vacío
//     */
    private PedidoParaAlgoritmo seleccionarPedidoDesdeRCL(List<PedidoParaAlgoritmo> rcl,
                                                          Map<PedidoParaAlgoritmo, Double> scores,
                                                          Random rng,
                                                          boolean weighted) {
        if (rcl == null || rcl.isEmpty()) return null;
        if (rng == null) rng = new Random(seed);

        if (!weighted) {
            return rcl.get(rng.nextInt(rcl.size()));
        } else {
            // selección ponderada por score (aseguramos pesos positivos)
            double sum = 0.0;
            List<Double> weights = new ArrayList<>(rcl.size());
            for (PedidoParaAlgoritmo p : rcl) {
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
//     * Evalúa mérito de pedidos candidatos para llenar un envío.
//     *
//     * @param pedidos lista de pedidos candidatos (pendientes) — solo los que tienen idAlmacenDestino == destino de la ruta
//     * @param envio   envío parcialmente construido (puede estar vacío al inicio)
//     * @param almacenes lista de almacenes (para estimar stock / orígenes infinitos)
//     * @param vuelos  lista de vuelos (no usada fuertemente aquí; opcional para extensiones)
//     * @return mapa pedido -> score (mayor = mejor)
//     */ // PODRÍA MEJORARSE CON LO DE AXEL,
    private Map<PedidoParaAlgoritmo, Double> evaluarMeritoPedidos(
            List<PedidoParaAlgoritmo> pedidosConDestino
    ) {

        Map<PedidoParaAlgoritmo, Double> scores = new HashMap<>();
        if (pedidosConDestino == null || pedidosConDestino.isEmpty()) return scores;

        // Pesos (ajustables)
        final double wUrgency = 0.50;
        final double wSize = 0.20;
        final double wSupply = 0.30;

        Instant now = Instant.now();

        // Precompute remaining demand for each pedido
        Map<PedidoParaAlgoritmo, Integer> remainingMap = new HashMap<>();
        int maxRemaining = 0;
        for (PedidoParaAlgoritmo p : pedidosConDestino) {
            remainingMap.put(p, p.getCantidadRestanteDeEntregaYProgram());
            maxRemaining = Math.max(maxRemaining, p.getCantidadRestanteDeEntregaYProgram());
        }
        if (maxRemaining == 0) maxRemaining = 1; // evita división por cero

        // Precompute simple supply availability across almacenes (sum of available stocks)
        // Treat any infinite almacén as huge availability -> mark haveInfinite = true
        boolean haveInfinite = false;
        long totalAvailableAcrossAllOrigens = 0L;
        for (AlmacenParaAlgoritmo a : estadoGlobal.getAlmacenes().values()) {
            if (a == null) continue;
            if (a.isEsInfinito()) {
                haveInfinite = true;
                break;
            } else {
//                int ocupado = a.getCapacidadOcupada() == null ? 0 : a.getCapacidadOcupada();
//                int reserv = a.getCapacidadReservadaPorEnvios() == null ? 0 : a.getCapacidadReservadaPorEnvios();
//                int avail = Math.max(0, ocupado - reserv); disponible
                int disponible = a.obtenerCapacidadSinOcupar();
                totalAvailableAcrossAllOrigens += disponible;
            }
        }

        // Raw component maps
        Map<PedidoParaAlgoritmo, Double> rawUrgency = new HashMap<>();
        Map<PedidoParaAlgoritmo, Double> rawSize = new HashMap<>();
        Map<PedidoParaAlgoritmo, Double> rawSupply = new HashMap<>();

        double minUrg = Double.POSITIVE_INFINITY, maxUrg = Double.NEGATIVE_INFINITY;
        double minSize = Double.POSITIVE_INFINITY, maxSize = Double.NEGATIVE_INFINITY;
        double minSup = Double.POSITIVE_INFINITY, maxSup = Double.NEGATIVE_INFINITY;

        for (PedidoParaAlgoritmo p : pedidosConDestino) {
            int remaining = remainingMap.getOrDefault(p, 0);

            // --- URGENCY (higher is better) ---
            double hoursToDeadline;
            if (p.getInstanteMaximoParaEntregar() == null) {
                hoursToDeadline = Double.POSITIVE_INFINITY;
            } else {
                long seconds = java.time.Duration.between(now, p.getInstanteMaximoParaEntregar()).getSeconds();
                // si ya pasó, lo consideramos muy urgente -> hours = 0
                hoursToDeadline = Math.max(0.0, seconds / 3600.0);
            }
            // rawUrgency: 1/(hours+1) -> more urgent (smaller hours) -> closer to 1
            double urg = 1.0 / (hoursToDeadline + 1.0);
            rawUrgency.put(p, urg);
            minUrg = Math.min(minUrg, urg);
            maxUrg = Math.max(maxUrg, urg);

            // --- SIZE (favor small remaining pedidos): higher is better ---
            // rawSize = 1/(remaining+1)  -> smaller remaining -> higher
            double sizeScore = 1.0 / (remaining + 1.0);
            rawSize.put(p, sizeScore);
            minSize = Math.min(minSize, sizeScore);
            maxSize = Math.max(maxSize, sizeScore);

            // --- SUPPLY (higher is better) ---
            double sup;
            if (haveInfinite) {
                sup = 1.0;
            } else {
                // if remaining == 0 then supply = 1 (but those should have been filtered out earlier)
                if (remaining <= 0) {
                    sup = 1.0;
                } else {
                    double avail = (double) totalAvailableAcrossAllOrigens;
                    sup = Math.min(1.0, avail / (double) remaining);
                }
            }
            rawSupply.put(p, sup);
            minSup = Math.min(minSup, sup);
            maxSup = Math.max(maxSup, sup);
        }

        // Normalizar cada componente en [0,1]
        for (PedidoParaAlgoritmo p : pedidosConDestino) {
            double urg = rawUrgency.getOrDefault(p, 0.0);
            double size = rawSize.getOrDefault(p, 0.0);
            double sup = rawSupply.getOrDefault(p, 0.0);

            double normUrg;
            if (Double.compare(maxUrg, minUrg) == 0) normUrg = 1.0;
            else normUrg = (urg - minUrg) / (maxUrg - minUrg);

            double normSize;
            if (Double.compare(maxSize, minSize) == 0) normSize = 1.0;
            else normSize = (size - minSize) / (maxSize - minSize);

            double normSup;
            if (Double.compare(maxSup, minSup) == 0) normSup = 1.0;
            else normSup = (sup - minSup) / (maxSup - minSup);

            // Weighted sum
            double score = wUrgency * normUrg + wSize * normSize + wSupply * normSup;
            scores.put(p, score);
        }

        return scores;
    }
    //
//    /**
//     * Construye la RCL de pedidos a partir de un mapa pedido->score.
//     * Convención: score mayor = mejor.
//     *
//     * alpha in [0,1]. alpha = 0 => solo el mejor; alpha = 1 => todos.
//     */
    private List<PedidoParaAlgoritmo> construirRCLDePedidos(Map<PedidoParaAlgoritmo, Double> scores, double alpha) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // defensiva
        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();

        // umbral: si score mayor = mejor, threshold = max - alpha*(max-min)
        double threshold = max - alpha * (max - min);

        List<PedidoParaAlgoritmo> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Opcional: ordenar por score descendente (mejor primero)
        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

        return rcl;
    }

    //    /**
//     * Construye la RCL a partir del mapa ruta->score. Convención: score mayor = mejor.
//     * alpha in [0,1]. alpha=0 => only best, alpha=1 => all.
//     */
//    private List<RutaADestino> construirRCLDeRutas(Map<RutaADestino, Double> scores, double alpha) {
//        if (scores == null || scores.isEmpty()) return Collections.emptyList();
//        double min = Double.POSITIVE_INFINITY;
//        double max = Double.NEGATIVE_INFINITY;
//        for (Double v : scores.values()) {
//            if (v == null) continue;
//            min = Math.min(min, v);
//            max = Math.max(max, v);
//        }
//        // defensivo
//        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();
//
//        // Para convención "mayor = mejor", definimos umbral:
//        // threshold = max - alpha*(max - min)  => alpha=0 => threshold=max (solo el mejor), alpha=1 => threshold=min (todos)
//        double threshold = max - alpha * (max - min);
//
//        List<RutaADestino> rcl = scores.entrySet().stream()
//                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList());
//
//        // Orden opcional: por score descendente
//        rcl.sort((a,b) -> Double.compare(scores.get(b), scores.get(a)));
//
//        return rcl;
//    }
//    private Integer  obtenerCapacidadMaxParaTodosVuelosEnRuta(RutaADestino rutaSeleccionada){
//        if (rutaSeleccionada == null || rutaSeleccionada.getVuelosOrdenados() == null || rutaSeleccionada.getVuelosOrdenados().isEmpty()) return 0;
//        return rutaSeleccionada.getVuelosOrdenados().stream().
//                mapToInt(
//                        (v)->{
//                            return v.getCapacidadMaximaProductos()-v.getCapacidadOcupadaProductos()-v.getCapacidadReservadaProductos(); // CON PURA FE A LOS NO NULL POINTERS 🙏🙏🙏🙏🙏🙏
//                        }
//                ).min().orElse(0);
//    }
//
//    private List<PedidoParaAlgoritmo> obtenerPedidosPendientesConDestino(Long idAlmacenDestino, List<PedidoParaAlgoritmo> pedidos){
//        if (idAlmacenDestino == null || pedidos == null) return Collections.emptyList();
//
//        return pedidos.stream().filter( // REZANDO PARA NO TENER NULL POINTERS
//                p ->
//                        Objects.equals(p.getIdAlmacenDestino(), idAlmacenDestino)
//                        && (
//                                p.getCantidadProductosProgramados()<p.getCantidadProductosPedidos()
//                            && p.getCantidadProductosEntregados()<p.getCantidadProductosPedidos()
//                        )
//                ).collect(Collectors.toList());
//    }
//
//    // estas variables podrían servir a otros lados???
//    private static final int MAX_LEGS = 10; // número máximo de tramos por ruta (incluye primer vuelo)
//    private static final int MAX_RUTAS_POR_DESTINO = 20;
//    private static final int MAX_RUTAS_POR_ORIGEN = 10;
//    /**
//     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o no vacíos"
//     * hacia destinos que NO son infinitos.
//     *
//     * Filtra vuelos que no tengan capacidad disponible y asegura encadenamiento temporal
//     * (siguiente.inicio >= anterior.fin).
//     */
//    List<RutaADestino> generarRutasCandidatas(List<VueloParaAlgoritmo> vuelos, List<AlmacenParaAlgoritmo> almacenes){
//        loggingReport.appendReport("Generando rutas candidatas");
//
//        // Map de vuelos salientes por almacen origen (idAlmacenOrigen -> lista vuelos)
//        Map<Long, List<VueloParaAlgoritmo>> outgoing = new HashMap<>();
//        for (VueloParaAlgoritmo v : vuelos) {
//            outgoing.computeIfAbsent(v.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(v);
//        }
//
//        // Identificar destinos: almacenes que NO son infinitos
//        Set<Long> destinos = almacenes.stream()
//                .filter(a -> Boolean.FALSE.equals(a.getEsInfinito()))
//                .map(AlmacenParaAlgoritmo::getId)
//                .collect(Collectors.toSet());
//
//        // Orígenes candidatos: infinitos o con stock disponible (> reserved)
//        List<AlmacenParaAlgoritmo> origenes = almacenes.stream()
//                .filter(a -> Boolean.TRUE.equals(a.getEsInfinito())
//                        || ((a.getCapacidadOcupada() != null ? a.getCapacidadOcupada() : 0)
//                        - (a.getCapacidadReservadaPorEnvios() != null ? a.getCapacidadReservadaPorEnvios() : 0)) > 0)
//                .collect(Collectors.toList());
//
//        List<RutaADestino> resultado = new ArrayList<>();
//
//        // Para evitar rutas duplicadas, guardamos un hash de secuencia de vuelos
//        Set<String> rutasVistas = new HashSet<>();
//
//        for (Long destId : destinos) {
//            int rutasEncontradasParaDestino = 0;
//
//            for (AlmacenParaAlgoritmo origen : origenes) {
//                if (rutasEncontradasParaDestino >= MAX_RUTAS_POR_DESTINO) break;
//
//                // BFS sobre caminos de vuelos desde origen.id hasta destId
//                Queue<List<VueloParaAlgoritmo>> q = new ArrayDeque<>();
//
//                // Inicializar con vuelos salientes del origen que tengan capacidad disponible y estado válido
//                List<VueloParaAlgoritmo> iniciales = outgoing.getOrDefault(origen.getId(), Collections.emptyList());
//                for (VueloParaAlgoritmo v : iniciales) {
//                    if (!vueloTieneCapacidadDisponible(v)) continue;
//                    if (!vueloEstadoValido(v)) continue;
//                    List<VueloParaAlgoritmo> path = new ArrayList<>();
//                    path.add(v);
//                    q.add(path);
//                }
//
//                int rutasPorOrigen = 0;
//                while (!q.isEmpty() && rutasPorOrigen < MAX_RUTAS_POR_ORIGEN
//                        && rutasEncontradasParaDestino < MAX_RUTAS_POR_DESTINO) {
//                    List<VueloParaAlgoritmo> path = q.poll();
//                    if (path == null) continue;
//
//                    // Chequeamos si el último vuelo llega al destino buscado
//                    VueloParaAlgoritmo last = path.get(path.size() - 1);
//                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
//                        // validar ruta: todas las legs tienen capacidad y encadenan tiempos (ya garantizado al expandir)
//                        String signature = path.stream().map(vf -> String.valueOf(vf.getId())).collect(Collectors.joining("-"));
//                        if (!rutasVistas.contains(signature)) {
//                            resultado.add(new RutaADestino(path)); // Un List a un LinkedList=?
//                            rutasVistas.add(signature);
//                            rutasPorOrigen++;
//                            rutasEncontradasParaDestino++;
//                        }
//                        // no expandimos más esta ruta (ya llegó)
//                        continue;
//                    }
//
//                    // Si no llegó al destino, expandir si no excede MAX_LEGS
//                    if (path.size() >= MAX_LEGS) continue;
//
//                    // Expandir: vuelos salientes del almacen destino del último tramo
//                    List<VueloParaAlgoritmo> siguientes = outgoing.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
//                    for (VueloParaAlgoritmo next : siguientes) {
//                        if (!vueloTieneCapacidadDisponible(next)) continue;
//                        if (!vueloEstadoValido(next)) continue;
//
//                        // Chequeo de encadenamiento temporal: next.inicio >= last.fin (permitimos igual)
//                        if (next.getInicio() != null && last.getFin() != null && next.getInicio().isBefore(last.getFin())) {
//                            continue;
//                        }
//
//                        // Evitar ciclos por almacen o por vuelo repetido en path
//                        boolean ciclo = false;
//                        for (VueloParaAlgoritmo used : path) {
//                            if (Objects.equals(used.getId(), next.getId())) { ciclo = true; break; }
//                            if (Objects.equals(used.getIdAlmacenOrigen(), next.getIdAlmacenDestino())
//                                    && Objects.equals(used.getIdAlmacenDestino(), next.getIdAlmacenOrigen())) {
//                                // conservador: evitar volver al mismo par invertido
//                                ciclo = true; break;
//                            }
//                        }
//                        if (ciclo) continue;
//
//                        // Nuevo path candidato
//                        List<VueloParaAlgoritmo> newPath = new ArrayList<>(path);
//                        newPath.add(next);
//                        q.add(newPath);
//                    }
//                } // end BFS for this origin
//            } // end origins loop
//        } // end destinations loop
//
//        loggingReport.appendReport("Rutas candidatas finalizadas. Total: " + resultado.size());
//        loggingReport.appendReport("Rutas candidatas: ");
//        for ( RutaADestino ruta : resultado) {
//            loggingReport.appendReport("Rutas:");
//            for(VueloParaAlgoritmo vf : ruta.getVuelosOrdenados()) {
//                loggingReport.appendReport( "   Vuelo:"+ vf);
//            }
//        }
//        return resultado;
//    }
//    // Helpers
//    private boolean vueloTieneCapacidadDisponible(VueloParaAlgoritmo v) {
//        if (v == null) return false;
//        Integer max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
//        Integer ocup = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
//        Integer reserv = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
//        return (max - ocup - reserv) > 0;
//    }
//
//    private boolean vueloEstadoValido(VueloParaAlgoritmo v) {
//        if (v == null) return false;
//        // Permitimos EN_ESPERA y EN_CURSO en prototipo; excluimos CANCELADO / FINALIZADO
//        if (v.getEstado() == null) return false;
//        return v.getEstado() == EstadoVuelo.EN_ESPERA || v.getEstado() == EstadoVuelo.EN_CURSO;
//        // aún me pregunto por qué puse en curso.
//    }
//
//
//    /**
//     * Selecciona una ruta desde la RCL.
//     * @param rcl lista de rutas candidatas (no vacía)
//     * @param scores mapa ruta->score (debe contener las rutas)
//     * @param rng Random
//     * @param weighted si true se selecciona ponderado por score; si false seleccion uniforme
//     */
//    private RutaADestino seleccionarRutaDesdeRCL(List<RutaADestino> rcl, Map<RutaADestino, Double> scores, Random rng, boolean weighted) {
//        if (rcl == null || rcl.isEmpty()) return null;
//        if (rng == null) rng = new Random();
//
//        if (!weighted) {
//            return rcl.get(rng.nextInt(rcl.size()));
//        } else {
//            // ponderado por score (score may be 0..1)
//            double sum = 0.0;
//            List<Double> ws = new ArrayList<>(rcl.size());
//            for (RutaADestino r : rcl) {
//                double s = scores.getOrDefault(r, 0.0);
//                // evitar 0 estrictos -> small epsilon
//                double w = Math.max(1e-6, s);
//                ws.add(w);
//                sum += w;
//            }
//            double pick = rng.nextDouble() * sum;
//            double acc = 0.0;
//            for (int i=0;i<rcl.size();i++) {
//                acc += ws.get(i);
//                if (pick <= acc) return rcl.get(i);
//            }
//            // fallback
//            return rcl.get(rcl.size()-1);
//        }
//    }
    /*
        // Fase A: generar pool de rutas candidatas (limitadas)
    rutas = generarRutasCandidatas(estadoGlobal, params)  // top-K orígenes, BFS limitado, maxEscalas

    if rutas.isEmpty(): return null

    // evaluar mérito de cada ruta (capacidad disponible, coste, tiempo llegada, cobertura demanda)
    scoresRuta = mapRutaAMerit(rutas, estadoGlobal)
    rutaSeleccionada = seleccionarRCL(scoresRuta, alphaRuta) // RCL + pick aleatorio

    // Fase B: construir contenido del envío para rutaSeleccionada
    S = envío vacío con rutaSeleccionada
    N = pedidosPendientesConDestino(rutaSeleccionada.destino, estadoGlobal)
    while (capacidadRutaDisponible(S, rutaSeleccionada) > 0 && !N.isEmpty()):
        scoresPedido = evaluarMeritoPedidos(N, S, estadoGlobal)   // urgencia, tamaño, encaja
        RCL_ped = construirRCL(scoresPedido, alphaCarga)
        pedido = seleccionarAleatorio(RCL_ped)
        if (esFactibleAñadirPedidoAShipment(pedido, S, rutaSeleccionada, estadoGlobal)):
            cantidad = decidirCantidadAAsignar(pedido, S, rutaSeleccionada, estadoGlobal) // max posible o heurística
            S = añadirPedidoConCantidad(S, pedido, cantidad)
            actualizarEstadoTemporalEnMemoria(S, pedido, rutaSeleccionada) // reduce capacidad disponible en ruta/origen
        N = removerPedidosSatisfechosOIrrelevantes(N, pedido, estadoGlobal)
    end while

    if S.cantProductos == 0: return null
    return S
     */
    //integrar una política de re-try con diferentes alpha/semillas para salir de situaciones difíciles.????????????????????

    /**
     * Comprueba si es factible añadir (parte de) un pedido al envío actual sobre la ruta dada.
     *
     * Requisitos verificados (conservador):
     *  - el pedido tiene cantidad restante > 0
     *  - la ruta tiene capacidad mínima disponible entre todos sus vuelos (considerando reservas/ocupados)
     *    descontando lo ya agregado al envio en construcción
     *  - el almacén origen (primer vuelo) tiene stock disponible (a menos que sea infinito)
     *  - los vuelos en la ruta están en estados válidos (EN_ESPERA / EN_CURSO)
     *  - la llegada estimada + 2 horas (pickup) cumple con el instanteMaximoParaEntregar del pedido (si está definido) REVISAR BN !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     *
     * @return true si al menos 1 unidad podría ser asignada bajo los recursos actuales
     */
//    private boolean esFactibleAnadirPedidoAEnvio(PedidoParaAlgoritmo pedido,
//                                                 EnvioSolution envio,
//                                                 RutaADestino ruta,
//                                                 List<AlmacenParaAlgoritmo> almacenes,
//                                                 List<VueloParaAlgoritmo> vuelos) {
//        if (pedido == null || ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) {
//            loggingReport.appendReport("esFactible: pedido o ruta inválida.");
//            return false;
//        }
//
//        // 1) remaining del pedido
//        int totalPedidos = pedido.getCantidadProductosPedidos() == null ? 0 : pedido.getCantidadProductosPedidos();
//        int entregados = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
//        int programados = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
//        int remaining = Math.max(0, totalPedidos - entregados - programados);
//        if (remaining <= 0) {
//            loggingReport.appendReport("esFactible: pedido id=" + pedido.getId() + " no tiene remanente.");
//            return false;
//        }
//
//        // 2) capacidad disponible en la ruta (min across legs) menos lo ya asignado al envio
//        int capacidadRutaDisponible = obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
//        int yaAsignadoEnEnvio = envio == null || envio.getCantProductos() == null ? 0 : envio.getCantProductos();
//        int disponibleParaEsteEnvio = Math.max(0, capacidadRutaDisponible - yaAsignadoEnEnvio);
//        if (disponibleParaEsteEnvio <= 0) {
//            loggingReport.appendReport("esFactible: ruta no tiene capacidad disponible (capRuta=" + capacidadRutaDisponible + ", yaAsignadoEnvio=" + yaAsignadoEnEnvio + ").");
//            return false;
//        }
//
//        // 3) stock en el almacén origen (primer vuelo)
//        VueloParaAlgoritmo primerVuelo = ruta.getVuelosOrdenados().getFirst();
//        if (primerVuelo == null) {
//            loggingReport.appendReport("esFactible: primer vuelo nulo en ruta.");
//            return false;
//        }
//        Long idAlmacenOrigen = primerVuelo.getIdAlmacenOrigen();
//        AlmacenParaAlgoritmo almacenOrigen = null;
//        if (almacenes != null) {
//            for (AlmacenParaAlgoritmo a : almacenes) {
//                if (a != null && Objects.equals(a.getId(), idAlmacenOrigen)) {
//                    almacenOrigen = a;
//                    break;
//                }
//            }
//        }
//        int disponibleOrigen;
//        if (almacenOrigen == null) {
//            // Si no encontramos info del almacén en la lista, conservador: asumir no disponible
//            loggingReport.appendReport("esFactible: no se encontró info de almacen origen id=" + idAlmacenOrigen);
//            return false;
//        } else if (Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
//            disponibleOrigen = Integer.MAX_VALUE / 4; // suficientemente grande
//        } else {
//            int ocupado = almacenOrigen.getCapacidadOcupada() == null ? 0 : almacenOrigen.getCapacidadOcupada();
//            int reserv = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
//            disponibleOrigen = Math.max(0, ocupado - reserv);
//        }
//        if (disponibleOrigen <= 0) {
//            loggingReport.appendReport("esFactible: origen id=" + idAlmacenOrigen + " no tiene stock disponible (o está vacío).");
//            return false;
//        }
//
//        // 4) Estados de vuelos en la ruta: todos deben ser EN_ESPERA o EN_CURSO
//        for (VueloParaAlgoritmo v : ruta.getVuelosOrdenados()) {
//            if (v == null || v.getEstado() == null) {
//                loggingReport.appendReport("esFactible: vuelo nulo o sin estado en ruta.");
//                return false;
//            }
//            if (!(v.getEstado() == EstadoVuelo.EN_ESPERA || v.getEstado() == EstadoVuelo.EN_CURSO)) {
//                loggingReport.appendReport("esFactible: vuelo id=" + v.getId() + " en estado no válido: " + v.getEstado());
//                return false;
//            }
//        }
//
//        // 5) Restricción temporal: llegada + 2h (pickup) <= instanteMaximoParaEntregar (si está definido)
//        VueloParaAlgoritmo ultimoVuelo = ruta.getVuelosOrdenados().getLast();
//        Instant llegada = ultimoVuelo.getFin();
//        if (llegada == null) {
//            loggingReport.appendReport("esFactible: último vuelo no tiene hora de fin; asumimos no factible.");
//            return false;
//        }
//        Instant pickup = llegada.plusSeconds(2 * 60 * 60); // +2 horas
//        Instant deadline = pedido.getInstanteMaximoParaEntregar();
//        if (deadline != null) {
//            if (pickup.isAfter(deadline)) {
//                loggingReport.appendReport("esFactible: pickup (" + pickup + ") posterior al deadline (" + deadline + ") para pedido id=" + pedido.getId());
//                return false;
//            }
//        } // si deadline null asumimos flexible
//
//        // 6) finalmente, comprobar que al menos 1 unidad pueda asignarse:
//        //    asignable = min(remaining, disponibleParaEsteEnvio, disponibleOrigen)
//        long asignable = Math.min(remaining, Math.min(disponibleParaEsteEnvio, disponibleOrigen));
//        if (asignable <= 0) {
//            loggingReport.appendReport("esFactible: ninguna unidad asignable (remaining=" + remaining
//                    + ", disponibleRuta=" + disponibleParaEsteEnvio + ", disponibleOrigen=" + disponibleOrigen + ").");
//            return false;
//        }
//
//        // Si pasa todas las comprobaciones, se considera factible (al menos parcialmente)
//        loggingReport.appendReport("esFactible: pedido id=" + pedido.getId() + " puede asignarse parcialmente. asignable=" + asignable);
//        return true;
//    }
//
//    /**
//     * Decide cuántas unidades asignar del pedido al envío en construcción.
//     * Regla: máxima cantidad posible limitada por:
//     *   - remaining del pedido,
//     *   - capacidad mínima disponible en la ruta (considerando ya asignado en el envío),
//     *   - stock disponible en el almacen origen (a menos que sea infinito).
//     *
//     * @return cantidad asignable (>0) o 0 si no hay nada asignable.
//     */
//        int total = pedido.getCantidadProductosPedidos() == null ? 0 : pedido.getCantidadProductosPedidos();
//        int entregados = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
//        int programados = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
//        int remaining = Math.max(0, total - entregados - programados);

//
//    /**
//     * Añade al EnvioSolution una asignación del pedido por la cantidad indicada.
//     * Actualiza:
//     *  - envio.cantProductos (sumando qty)
//     *  - envio.pedidosAAtenderTotalOParcialmente (agrega o suma si ya existe)
//     *  - reserva temporal en cada vuelo de la ruta (capacidadReservadaProductos += qty)
//     *  - reserva temporal en almacen origen (capacidadReservadaPorEnvios += qty) si no es infinito
//     *  - pedido.cantidadProductosProgramados += qty
//     *
//     * Devuelve el envio actualizado (mismo objeto modificado).
//     */
//    private EnvioSolution anadirPedidoConCantidad(EnvioSolution envio,
//                                                  PedidoParaAlgoritmo pedido,
//                                                  int cantidad,
//                                                  RutaADestino ruta,
//                                                  List<AlmacenParaAlgoritmo> almacenes,
//                                                  List<VueloParaAlgoritmo> vuelos) {
//        if (envio == null) envio = new EnvioSolution();
//        if (pedido == null || cantidad <= 0 || ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) {
//            loggingReport.appendReport("anadirPedidoConCantidad: entrada inválida, no se hace nada.");
//            return envio;
//        }
//
//        // 1) Actualizar envio.cantProductos
//        int prevCant = envio.getCantProductos() == null ? 0 : envio.getCantProductos();
//        envio.setCantProductos(prevCant + cantidad);
//
//        // 2) Asegurar lista de pedidos en envio y agregar o acumular
//        if (envio.getPedidosAAtenderTotalOParcialmente() == null) {
//            envio.setPedidosAAtenderTotalOParcialmente(new ArrayList<>());
//        }
//        boolean merged = false;
//        for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//            if (Objects.equals(ps.getId(), pedido.getId())) {
//                // sumar cantidades si ya existía
//                int prev = ps.getCantidadASerAtendidaDelPedido() == null ? 0 : ps.getCantidadASerAtendidaDelPedido();
//                ps.setCantidadASerAtendidaDelPedido(prev + cantidad);
//                merged = true;
//                break;
//            }
//        }
//        if (!merged) {
//            PedidoSolution nuevo = new PedidoSolution();
//            nuevo.setId(pedido.getId());
//            nuevo.setCantidadASerAtendidaDelPedido(cantidad);
//            envio.getPedidosAAtenderTotalOParcialmente().add(nuevo);
//        }
//
//        // 3) Actualizar idAlmacenDestino y fechaHoraDestino (tomar del último vuelo de la ruta)
//        VueloParaAlgoritmo ultimo = ruta.getVuelosOrdenados().getLast();
//        if (ultimo != null) {
//            envio.setIdAlmacenDestino(ultimo.getIdAlmacenDestino());
//            envio.setFechaHoraDestino(ultimo.getFin());
//        }
//
//        // 4) Reservar en cada vuelo de la ruta incrementando capacidadReservadaProductos
//        for (VueloParaAlgoritmo v : ruta.getVuelosOrdenados()) {
//            if (v == null) continue;
//            Integer prevRes = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
//            v.setCapacidadReservadaProductos(prevRes + cantidad);
//        }
//
//        // 5) Reservar en almacen origen si no infinito (incrementar capacidadReservadaPorEnvios)
//        VueloParaAlgoritmo primer = ruta.getVuelosOrdenados().getFirst();
//        if (primer != null) {
//            Long idOrigen = primer.getIdAlmacenOrigen();
//            AlmacenParaAlgoritmo almacenOrigen = null;
//            if (almacenes != null) {
//                for (AlmacenParaAlgoritmo a : almacenes) {
//                    if (a != null && Objects.equals(a.getId(), idOrigen)) {
//                        almacenOrigen = a;
//                        break;
//                    }
//                }
//            }
//            if (almacenOrigen != null && !Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
//                int prev = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
//                almacenOrigen.setCapacidadReservadaPorEnvios(prev + cantidad);
//            }
//        }
//
//        // 6) Actualizar pedido.cantidadProductosProgramados
//        int prevProg = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
//        pedido.setCantidadProductosProgramados(prevProg + cantidad);
//
//        loggingReport.appendReport(String.format("Se añadió pedido id=%d cantidad=%d al envío. Envío.cantProductos ahora=%d",
//                pedido.getId(), cantidad, envio.getCantProductos()));
//
//        return envio;
//    }
//
//    /**
//     * Actualiza el estado temporal en memoria tras asignar un pedido a un envío.
//     *
//     * @param envio                Envío en construcción (ya actualizado por anadirPedidoConCantidad)
//     * @param pedidoAsignado       Pedido que se acaba de asignar (puede ser null si la llamada es genérica)
//     * @param ruta                 Ruta seleccionada (sus vuelos ya tienen capacidadReservada actualizada)
//     * @param almacenes            lista de almacenes (mutada por anadirPedidoConCantidad si aplica)
//     * @param vuelos               lista de vuelos (mutada por anadirPedidoConCantidad si aplica)
//     * @param pedidosPendientes    lista mutable de pedidos pendientes para el destino; se modifica in-place (se eliminan satisfechos)
//     * @return la nueva capacidad mínima disponible en la ruta (>=0)
//     */
//    private int actualizarEstadoTemporalEnMemoria(RutaProgramadaParaAlgoritmo rutaSol,
////                                                  PedidoParaAlgoritmo pedidoAsignado,
////                                                  RutaADestino ruta,
////                                                  List<AlmacenParaAlgoritmo> almacenes,
////                                                  List<VueloParaAlgoritmo> vuelos,
//                                                  List<PedidoParaAlgoritmo> pedidosPendientes) {
//        loggingReport.appendReport("Actualizando estado temporal en memoria...");
//
//        // 1) Recalcular la capacidad mínima disponible en la ruta (considerando reservas ya aplicadas)
//        int capacidadDisponibleRuta = estadoGlobal.obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSol);
//        loggingReport.appendReport("Capacidad disponible recalculada en la ruta: " + capacidadDisponibleRuta);
//
//        // 2) Remover pedidos completamente satisfechos de la lista de pendientes (mutamos pedidosPendientes in-place)
//        if (pedidosPendientes != null && !pedidosPendientes.isEmpty()) {
//            Iterator<PedidoParaAlgoritmo> it = pedidosPendientes.iterator();
//            int removed = 0;
//            while (it.hasNext()) {
//                PedidoParaAlgoritmo p = it.next();
//                if (p == null) {
//                    it.remove();
//                    removed++;
//                    continue;
//                }
//                int remaining = p.getCantidadRestanteDeEntregaYProgram();
//                if (remaining <= 0) {
//                    loggingReport.appendReport("Pedido id=" + p.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
//                    it.remove();
//                    removed++;
//                }
//            }
//            loggingReport.appendReport("Pedidos removidos de pendientes: " + removed + ". Pendientes ahora: " + pedidosPendientes.size());
//        }
//
//        // 3) Reordenar pedidosPendientes para favorecer urgencia y consolidación:
//        //    - por instanteMaximoParaEntregar asc (más urgente primero)
//        //    - luego por remaining asc (pedidos pequeños primero para que sea más fácil consolidar)
//        if (pedidosPendientes != null && pedidosPendientes.size() > 1) {
//            pedidosPendientes.sort((a, b) -> {
//                // compare by deadline
//                Instant da = a == null ? null : a.getInstanteMaximoParaEntregar();
//                Instant db = b == null ? null : b.getInstanteMaximoParaEntregar();
//                if (da != null && db != null) {
//                    int cmp = da.compareTo(db);
//                    if (cmp != 0) return cmp;
//                } else if (da != null) {
//                    return -1;
//                } else if (db != null) {
//                    return 1;
//                }
//                // tie-breaker: remaining qty ascending
//                int ra = Math.max(0, (a.getCantidadProductosPedidos() == null ? 0 : a.getCantidadProductosPedidos())
//                        - (a.getCantidadProductosEntregados() == null ? 0 : a.getCantidadProductosEntregados())
//                        - (a.getCantidadProductosProgramados() == null ? 0 : a.getCantidadProductosProgramados()));
//                int rb = Math.max(0, (b.getCantidadProductosPedidos() == null ? 0 : b.getCantidadProductosPedidos())
//                        - (b.getCantidadProductosEntregados() == null ? 0 : b.getCantidadProductosEntregados())
//                        - (b.getCantidadProductosProgramados() == null ? 0 : b.getCantidadProductosProgramados()));
//                return Integer.compare(ra, rb);
//            });
//            loggingReport.appendReport("Pedidos pendientes reordenados por urgencia y size.");
//        }
//
//        // 4) (Opcional) - Recalcular otras métricas globales si las mantienes en memoria.
//        // Por ejemplo, podrías recalcular una medida de demanda total por destino, uso de vuelos, etc.
//        // (No hago nada extra aquí automáticamente, pero deja el lugar para agregar).
//
//        loggingReport.appendReport("Estado temporal actualizado. capacidadDisponibleRuta=" + capacidadDisponibleRuta);
//        return capacidadDisponibleRuta;
//    }
//


    /**
     * Comprueba si hay al menos un pedido con remaining > 0.
     */



}
