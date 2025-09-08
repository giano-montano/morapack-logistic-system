package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.grasp.RutaADestino;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Component
@Primary // Si en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class GraspAndGeneticAlgorithmStrategy implements PlanificationStrategy {

    private LoggingReport loggingReport = new LoggingReport();
    /*
    GRASP_Construct(input, alpha, stop_condition):
  N = inicializarCandidatos(input)         // conjunto de elementos candidatos
  S = vacio                                 // solución construida (fenotipo: envíos)
  while not stop_condition(S, N):
    // Evaluar méritos
    for x in N:
      score[x] = evaluarMeritoGreedy(x, S, input)

    beta = min_x score[x]
    tau  = max_x score[x]
    RCL = { x in N | beta <= score[x] <= beta + alpha * (tau - beta) }

    a = seleccionarAleatorio(RCL)
    if esFactibleAñadir(S, a, input):
      S = añadirElemento(S, a, input)
    // en cualquier caso eliminar x de N si se decide no volver a considerarlo
    N = removerDeCandidatos(N, a)

    // (opcional) actualizar datos derivados: capacidad de vuelos, stock, deadlines
  return S
    * */
//    @Bean
    @Override
    public PlanificationSolutionOutput planificar(PlanificationProblemInput input) throws Exception {

        List<PedidoForAlgorithm> pedidos = input.pedidos() == null? new ArrayList<>(): new ArrayList<>(input.pedidos());
        List<VueloForAlgorithm> vuelos = input.vuelos() == null? new ArrayList<>(): new ArrayList<>(input.vuelos());
        List<AlmacenForAlgorithm> almacenes = input.almacenes() == null? new ArrayList<>(): new ArrayList<>(input.almacenes());
        PlanificationSolutionOutput solution = new PlanificationSolutionOutput();
        solution.setEnvios(new ArrayList<>());
        while(necesitoPlanificarMasEnviosDebidoAPedidos()){
            loggingReport.appendReport("Necesito planificar un envío...");
            EnvioSolution envioConstruidoPorGrasp = graspConstructionForOneEnvio(pedidos, vuelos, almacenes);
// actualizar data del input (pedidos, vuelos, almacenes) para que se tome en cuenta el envío ya planificado
// y no haya redundancia

            //en un futuro le pondría aquí el GA
            solution.getEnvios().add(envioConstruidoPorGrasp);
            loggingReport.writeReportFile("grasp-report");
            return null;
        }

        return solution;
    }

    private boolean necesitoPlanificarMasEnviosDebidoAPedidos(){

        return true;
    }

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
    private static final double alpha = 0.1; // número máximo de tramos por ruta (incluye primer vuelo)
    private EnvioSolution graspConstructionForOneEnvio(List<PedidoForAlgorithm> pedidos, List<VueloForAlgorithm> vuelos, List<AlmacenForAlgorithm> almacenes){
        // Primero generamos rutas para todos los destinos posibles
        List<RutaADestino>
                rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios = // recordar que no hay pedidos para almacenes infinitos
                generarRutasCandidatas(vuelos,almacenes) // top-K orígenes, BFS limitado, maxEscalas
                ;
        if(rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios.isEmpty()){ return null; }
        Map<RutaADestino, Double> puntajesPorRuta = evaluarMeritoRutas(rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios,pedidos);
        loggingReport.appendReport("Puntajes por ruta:\n " + PrettyPrinter.printMap(puntajesPorRuta));
        List<RutaADestino> rclRutasCandidatas = construirRCLDeRutas(puntajesPorRuta, alpha);
        loggingReport.appendReport("Rutas que entraron a la RCL:  \n" + PrettyPrinter.printList(rclRutasCandidatas));
        RutaADestino rutaSeleccionada = seleccionarRutaDesdeRCL(rclRutasCandidatas,puntajesPorRuta,new Random(),false);
        loggingReport.appendReport("Vuelos de la ruta Seleccionada:\n " + PrettyPrinter.printList(rutaSeleccionada.getVuelosOrdenados() ) );

        Long idAlmacenDestinoRutaSeleccionada = rutaSeleccionada.getVuelosOrdenados().getLast().getIdAlmacenDestino();
        loggingReport.appendReport("id de almacén final de la ruta: "+idAlmacenDestinoRutaSeleccionada);
        List<PedidoForAlgorithm> NpedidosPendientesConDestino = obtenerPedidosPendientesConDestino(idAlmacenDestinoRutaSeleccionada, pedidos);
        loggingReport.appendReport("Pedidos pendientes con el destino final: "+ PrettyPrinter.printList(NpedidosPendientesConDestino));
        Integer capacidadMaxParaVuelosRuta= obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSeleccionada);
        loggingReport.appendReport("Mínima capacidad encontrada en los vuelos de la ruta que llega al destino final: "+capacidadMaxParaVuelosRuta);

        // Siguente fase: Construir contenido de 1 envío utilizando esta buena ruta.
        EnvioSolution envioSolucion = new EnvioSolution();
        int i=0;
        while( capacidadMaxParaVuelosRuta>0 && !NpedidosPendientesConDestino.isEmpty()){
            Map<PedidoForAlgorithm, Double> puntajesPorPedido= evaluarMeritoPedidos(NpedidosPendientesConDestino, envioSolucion, almacenes,vuelos); // usa info de pedidos, lo que ya llenamos del envío y estado global
            loggingReport.appendReport("Puntajes por pedido, iteración "+i+": \n" + PrettyPrinter.printMap(puntajesPorPedido));
            List<PedidoForAlgorithm> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido,alpha);
            loggingReport.appendReport("Pedidos que entraron a la RCL: \n" + PrettyPrinter.printList(rclPedidosCandidatos));
            PedidoForAlgorithm pedidoElegido = seleccionarPedidoDesdeRCL(rclPedidosCandidatos,puntajesPorPedido,new Random(),false);
            loggingReport.appendReport("Pedido seleccionado:  \n" + pedidoElegido);
            if (esFactibleAnadirPedidoAEnvio(pedidoElegido, envioSolucion, rutaSeleccionada, almacenes,vuelos) ){
                AlmacenForAlgorithm almOrigen =  almacenes.stream().filter(a-> rutaSeleccionada.getVuelosOrdenados().getFirst().getIdAlmacenOrigen().equals(a.getId()) ).findFirst().get();
                int cantidad = Math.min(pedidoElegido.getCantidadProductosPedidos()-pedidoElegido.getCantidadProductosEntregados()-pedidoElegido.getCantidadProductosProgramados(),
                        Math.min(obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSeleccionada) - (envioSolucion.getCantProductos()!=null?envioSolucion.getCantProductos() : 0),
                                almOrigen.getCapacidadTotal()-almOrigen.getCapacidadOcupada()-almOrigen.getCapacidadReservadaPorEnvios()
                                  ));
                loggingReport.appendReport("cantidad qué? " + cantidad);

            }
            //            cantidad = decidirCantidadAAsignar(pedido, S, rutaSeleccionada, estadoGlobal) // max posible o heurística
            //...

            i++;
            return null;
        }

        return null;
    }
    private Integer  obtenerCapacidadMaxParaTodosVuelosEnRuta(RutaADestino rutaSeleccionada){
        if (rutaSeleccionada == null || rutaSeleccionada.getVuelosOrdenados() == null || rutaSeleccionada.getVuelosOrdenados().isEmpty()) return 0;
        return rutaSeleccionada.getVuelosOrdenados().stream().
                mapToInt(
                        (v)->{
                            return v.getCapacidadMaximaProductos()-v.getCapacidadOcupadaProductos()-v.getCapacidadReservadaProductos(); // CON PURA FE A LOS NO NULL POINTERS 🙏🙏🙏🙏🙏🙏
                        }
                ).min().orElse(0);
    }

    private List<PedidoForAlgorithm> obtenerPedidosPendientesConDestino(Long idAlmacenDestino, List<PedidoForAlgorithm> pedidos){
        return pedidos.stream().filter(
                p ->
                        Objects.equals(p.getIdAlmacenDestino(), idAlmacenDestino)
                        && (
                                p.getCantidadProductosProgramados()<p.getCantidadProductosPedidos()
                            && p.getCantidadProductosEntregados()<p.getCantidadProductosPedidos()
                        )
                ).collect(Collectors.toList());
    }

    // estas variables podrían servir a otros lados???
    private static final int MAX_LEGS = 10; // número máximo de tramos por ruta (incluye primer vuelo)
    private static final int MAX_RUTAS_POR_DESTINO = 20;
    private static final int MAX_RUTAS_POR_ORIGEN = 10;
    /**
     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o no vacíos"
     * hacia destinos que NO son infinitos.
     *
     * Filtra vuelos que no tengan capacidad disponible y asegura encadenamiento temporal
     * (siguiente.inicio >= anterior.fin).
     */
    List<RutaADestino> generarRutasCandidatas(List<VueloForAlgorithm> vuelos, List<AlmacenForAlgorithm> almacenes){
        loggingReport.appendReport("Generando rutas candidatas");

        // Map de vuelos salientes por almacen origen (idAlmacenOrigen -> lista vuelos)
        Map<Long, List<VueloForAlgorithm>> outgoing = new HashMap<>();
        for (VueloForAlgorithm v : vuelos) {
            outgoing.computeIfAbsent(v.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(v);
        }

        // Identificar destinos: almacenes que NO son infinitos
        Set<Long> destinos = almacenes.stream()
                .filter(a -> Boolean.FALSE.equals(a.getEsInfinito()))
                .map(AlmacenForAlgorithm::getId)
                .collect(Collectors.toSet());

        // Orígenes candidatos: infinitos o con stock disponible (> reserved)
        List<AlmacenForAlgorithm> origenes = almacenes.stream()
                .filter(a -> Boolean.TRUE.equals(a.getEsInfinito())
                        || ((a.getCapacidadOcupada() != null ? a.getCapacidadOcupada() : 0)
                        - (a.getCapacidadReservadaPorEnvios() != null ? a.getCapacidadReservadaPorEnvios() : 0)) > 0)
                .collect(Collectors.toList());

        List<RutaADestino> resultado = new ArrayList<>();

        // Para evitar rutas duplicadas, guardamos un hash de secuencia de vuelos
        Set<String> rutasVistas = new HashSet<>();

        for (Long destId : destinos) {
            int rutasEncontradasParaDestino = 0;

            for (AlmacenForAlgorithm origen : origenes) {
                if (rutasEncontradasParaDestino >= MAX_RUTAS_POR_DESTINO) break;

                // BFS sobre caminos de vuelos desde origen.id hasta destId
                Queue<List<VueloForAlgorithm>> q = new ArrayDeque<>();

                // Inicializar con vuelos salientes del origen que tengan capacidad disponible y estado válido
                List<VueloForAlgorithm> iniciales = outgoing.getOrDefault(origen.getId(), Collections.emptyList());
                for (VueloForAlgorithm v : iniciales) {
                    if (!vueloTieneCapacidadDisponible(v)) continue;
                    if (!vueloEstadoValido(v)) continue;
                    List<VueloForAlgorithm> path = new ArrayList<>();
                    path.add(v);
                    q.add(path);
                }

                int rutasPorOrigen = 0;
                while (!q.isEmpty() && rutasPorOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasEncontradasParaDestino < MAX_RUTAS_POR_DESTINO) {
                    List<VueloForAlgorithm> path = q.poll();
                    if (path == null) continue;

                    // Chequeamos si el último vuelo llega al destino buscado
                    VueloForAlgorithm last = path.get(path.size() - 1);
                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
                        // validar ruta: todas las legs tienen capacidad y encadenan tiempos (ya garantizado al expandir)
                        String signature = path.stream().map(vf -> String.valueOf(vf.getId())).collect(Collectors.joining("-"));
                        if (!rutasVistas.contains(signature)) {
                            resultado.add(new RutaADestino(path)); // Un List a un LinkedList=?
                            rutasVistas.add(signature);
                            rutasPorOrigen++;
                            rutasEncontradasParaDestino++;
                        }
                        // no expandimos más esta ruta (ya llegó)
                        continue;
                    }

                    // Si no llegó al destino, expandir si no excede MAX_LEGS
                    if (path.size() >= MAX_LEGS) continue;

                    // Expandir: vuelos salientes del almacen destino del último tramo
                    List<VueloForAlgorithm> siguientes = outgoing.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
                    for (VueloForAlgorithm next : siguientes) {
                        if (!vueloTieneCapacidadDisponible(next)) continue;
                        if (!vueloEstadoValido(next)) continue;

                        // Chequeo de encadenamiento temporal: next.inicio >= last.fin (permitimos igual)
                        if (next.getInicio() != null && last.getFin() != null && next.getInicio().isBefore(last.getFin())) {
                            continue;
                        }

                        // Evitar ciclos por almacen o por vuelo repetido en path
                        boolean ciclo = false;
                        for (VueloForAlgorithm used : path) {
                            if (Objects.equals(used.getId(), next.getId())) { ciclo = true; break; }
                            if (Objects.equals(used.getIdAlmacenOrigen(), next.getIdAlmacenDestino())
                                    && Objects.equals(used.getIdAlmacenDestino(), next.getIdAlmacenOrigen())) {
                                // conservador: evitar volver al mismo par invertido
                                ciclo = true; break;
                            }
                        }
                        if (ciclo) continue;

                        // Nuevo path candidato
                        List<VueloForAlgorithm> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        q.add(newPath);
                    }
                } // end BFS for this origin
            } // end origins loop
        } // end destinations loop

        loggingReport.appendReport("Rutas candidatas finalizadas. Total: " + resultado.size());
        loggingReport.appendReport("Rutas candidatas: ");
        for ( RutaADestino ruta : resultado) {
            loggingReport.appendReport("Rutas:");
            for(VueloForAlgorithm vf : ruta.getVuelosOrdenados()) {
                loggingReport.appendReport( "   Vuelo:"+ vf);
            }
        }
        return resultado;
    }
    // Helpers
    private boolean vueloTieneCapacidadDisponible(VueloForAlgorithm v) {
        if (v == null) return false;
        Integer max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
        Integer ocup = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
        Integer reserv = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
        return (max - ocup - reserv) > 0;
    }

    private boolean vueloEstadoValido(VueloForAlgorithm v) {
        if (v == null) return false;
        // Permitimos EN_ESPERA y EN_CURSO en prototipo; excluimos CANCELADO / FINALIZADO
        if (v.getEstado() == null) return false;
        return v.getEstado() == EstadoVuelo.EN_ESPERA || v.getEstado() == EstadoVuelo.EN_CURSO;
        // aún me pregunto por qué puse en curso.
    }

    /**
     * Evalúa todas las rutas candidatas y devuelve un map ruta -> score (mayor = mejor).
     */
    private Map<RutaADestino, Double> evaluarMeritoRutas(List<RutaADestino> rutas,
                                                         List<PedidoForAlgorithm> pedidos) {
        // Pesos (ajustables)
        final double wArrival = 0.35;
        final double wLegs = 0.25;
        final double wCapacity = 0.25;
        final double wDemand = 0.15;

        Map<RutaADestino, Double> rawArrival = new HashMap<>();
        Map<RutaADestino, Integer> rawLegs = new HashMap<>();
        Map<RutaADestino, Integer> rawCapacity = new HashMap<>();
        Map<RutaADestino, Integer> rawDemand = new HashMap<>();

        // Precalcular demanda pendiente por almacen destino (sum of remaining quantities)
        Map<Long, Integer> demandaPorDestino = new HashMap<>();
        for (PedidoForAlgorithm p : pedidos) {
            if (p == null || p.getIdAlmacenDestino() == null) continue;
            int restante = (p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos())
                    - (p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados())
                    - (p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados());
            if (restante <= 0) continue;
            demandaPorDestino.merge(p.getIdAlmacenDestino(), restante, Integer::sum);
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
        for (RutaADestino r : rutas) {
            if (r == null || r.getVuelosOrdenados() == null || r.getVuelosOrdenados().isEmpty()) {
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
            int legs = r.getVuelosOrdenados().size();
            rawLegs.put(r, legs);
            minLegs = Math.min(minLegs, legs);
            maxLegs = Math.max(maxLegs, legs);

            // arrival: uso el fin del último vuelo
            VueloForAlgorithm ultimo = r.getVuelosOrdenados().get(r.getVuelosOrdenados().size() - 1);
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
            for (VueloForAlgorithm v : r.getVuelosOrdenados()) {
                if (v == null) continue;
                int max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
                int occ = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
                int res = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
                int avail = max - occ - res;
                if (avail < minAvailable) minAvailable = avail;
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
        Map<RutaADestino, Double> scores = new HashMap<>();
        for (RutaADestino r : rutas) {
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
     * alpha in [0,1]. alpha=0 => only best, alpha=1 => all.
     */
    private List<RutaADestino> construirRCLDeRutas(Map<RutaADestino, Double> scores, double alpha) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // defensivo
        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();

        // Para convención "mayor = mejor", definimos umbral:
        // threshold = max - alpha*(max - min)  => alpha=0 => threshold=max (solo el mejor), alpha=1 => threshold=min (todos)
        double threshold = max - alpha * (max - min);

        List<RutaADestino> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Orden opcional: por score descendente
        rcl.sort((a,b) -> Double.compare(scores.get(b), scores.get(a)));

        return rcl;
    }

    /**
     * Selecciona una ruta desde la RCL.
     * @param rcl lista de rutas candidatas (no vacía)
     * @param scores mapa ruta->score (debe contener las rutas)
     * @param rng Random
     * @param weighted si true se selecciona ponderado por score; si false seleccion uniforme
     */
    private RutaADestino seleccionarRutaDesdeRCL(List<RutaADestino> rcl, Map<RutaADestino, Double> scores, Random rng, boolean weighted) {
        if (rcl == null || rcl.isEmpty()) return null;
        if (rng == null) rng = new Random();

        if (!weighted) {
            return rcl.get(rng.nextInt(rcl.size()));
        } else {
            // ponderado por score (score may be 0..1)
            double sum = 0.0;
            List<Double> ws = new ArrayList<>(rcl.size());
            for (RutaADestino r : rcl) {
                double s = scores.getOrDefault(r, 0.0);
                // evitar 0 estrictos -> small epsilon
                double w = Math.max(1e-6, s);
                ws.add(w);
                sum += w;
            }
            double pick = rng.nextDouble() * sum;
            double acc = 0.0;
            for (int i=0;i<rcl.size();i++) {
                acc += ws.get(i);
                if (pick <= acc) return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size()-1);
        }
    }

    /**
     * Evalúa mérito de pedidos candidatos para llenar un envío.
     *
     * @param pedidos lista de pedidos candidatos (pendientes) — solo los que tienen idAlmacenDestino == destino de la ruta
     * @param envio   envío parcialmente construido (puede estar vacío al inicio)
     * @param almacenes lista de almacenes (para estimar stock / orígenes infinitos)
     * @param vuelos  lista de vuelos (no usada fuertemente aquí; opcional para extensiones)
     * @return mapa pedido -> score (mayor = mejor)
     */
    private Map<PedidoForAlgorithm, Double> evaluarMeritoPedidos(
            List<PedidoForAlgorithm> pedidos,
            EnvioSolution envio,
            List<AlmacenForAlgorithm> almacenes,
            List<VueloForAlgorithm> vuelos) {

        Map<PedidoForAlgorithm, Double> scores = new HashMap<>();
        if (pedidos == null || pedidos.isEmpty()) return scores;

        // Pesos (ajustables)
        final double wUrgency = 0.50;
        final double wSize = 0.20;
        final double wSupply = 0.30;

        Instant now = Instant.now();

        // Precompute remaining demand for each pedido
        Map<PedidoForAlgorithm, Integer> remainingMap = new HashMap<>();
        int maxRemaining = 0;
        for (PedidoForAlgorithm p : pedidos) {
            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = Math.max(0, total - entregados - programados);
            remainingMap.put(p, remaining);
            maxRemaining = Math.max(maxRemaining, remaining);
        }
        if (maxRemaining == 0) maxRemaining = 1; // evita división por cero

        // Precompute simple supply availability across almacenes (sum of available stocks)
        // Treat any infinite almacén as huge availability -> mark haveInfinite = true
        boolean haveInfinite = false;
        long totalAvailableAcrossAllOrigens = 0L;
        for (AlmacenForAlgorithm a : almacenes) {
            if (a == null) continue;
            if (Boolean.TRUE.equals(a.getEsInfinito())) {
                haveInfinite = true;
                break;
            } else {
                int ocupado = a.getCapacidadOcupada() == null ? 0 : a.getCapacidadOcupada();
                int reserv = a.getCapacidadReservadaPorEnvios() == null ? 0 : a.getCapacidadReservadaPorEnvios();
                int avail = Math.max(0, ocupado - reserv);
                totalAvailableAcrossAllOrigens += avail;
            }
        }

        // Raw component maps
        Map<PedidoForAlgorithm, Double> rawUrgency = new HashMap<>();
        Map<PedidoForAlgorithm, Double> rawSize = new HashMap<>();
        Map<PedidoForAlgorithm, Double> rawSupply = new HashMap<>();

        double minUrg = Double.POSITIVE_INFINITY, maxUrg = Double.NEGATIVE_INFINITY;
        double minSize = Double.POSITIVE_INFINITY, maxSize = Double.NEGATIVE_INFINITY;
        double minSup = Double.POSITIVE_INFINITY, maxSup = Double.NEGATIVE_INFINITY;

        for (PedidoForAlgorithm p : pedidos) {
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
        for (PedidoForAlgorithm p : pedidos) {
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

    /**
     * Construye la RCL de pedidos a partir de un mapa pedido->score.
     * Convención: score mayor = mejor.
     *
     * alpha in [0,1]. alpha = 0 => solo el mejor; alpha = 1 => todos.
     */
    private List<PedidoForAlgorithm> construirRCLDePedidos(Map<PedidoForAlgorithm, Double> scores, double alpha) {
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

        List<PedidoForAlgorithm> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Opcional: ordenar por score descendente (mejor primero)
        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

        return rcl;
    }

    /**
     * Selecciona aleatoriamente un pedido desde la RCL.
     * @param rcl lista no vacía (puede ser vacía -> retorna null)
     * @param scores mapa pedido->score (opcional si weighted=false)
     * @param rng Random instance (si null, se crea una nueva)
     * @param weighted si true selecciona ponderado por score; si false selección uniforme
     * @return pedido seleccionado o null si rcl vacío
     */
    private PedidoForAlgorithm seleccionarPedidoDesdeRCL(List<PedidoForAlgorithm> rcl,
                                                         Map<PedidoForAlgorithm, Double> scores,
                                                         Random rng,
                                                         boolean weighted) {
        if (rcl == null || rcl.isEmpty()) return null;
        if (rng == null) rng = new Random();

        if (!weighted) {
            return rcl.get(rng.nextInt(rcl.size()));
        } else {
            // selección ponderada por score (aseguramos pesos positivos)
            double sum = 0.0;
            List<Double> weights = new ArrayList<>(rcl.size());
            for (PedidoForAlgorithm p : rcl) {
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
    private boolean esFactibleAnadirPedidoAEnvio(PedidoForAlgorithm pedido,
                                                 EnvioSolution envio,
                                                 RutaADestino ruta,
                                                 List<AlmacenForAlgorithm> almacenes,
                                                 List<VueloForAlgorithm> vuelos) {
        if (pedido == null || ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) {
            loggingReport.appendReport("esFactible: pedido o ruta inválida.");
            return false;
        }

        // 1) remaining del pedido
        int totalPedidos = pedido.getCantidadProductosPedidos() == null ? 0 : pedido.getCantidadProductosPedidos();
        int entregados = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
        int programados = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
        int remaining = Math.max(0, totalPedidos - entregados - programados);
        if (remaining <= 0) {
            loggingReport.appendReport("esFactible: pedido id=" + pedido.getId() + " no tiene remanente.");
            return false;
        }

        // 2) capacidad disponible en la ruta (min across legs) menos lo ya asignado al envio
        int capacidadRutaDisponible = obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
        int yaAsignadoEnEnvio = envio == null || envio.getCantProductos() == null ? 0 : envio.getCantProductos();
        int disponibleParaEsteEnvio = Math.max(0, capacidadRutaDisponible - yaAsignadoEnEnvio);
        if (disponibleParaEsteEnvio <= 0) {
            loggingReport.appendReport("esFactible: ruta no tiene capacidad disponible (capRuta=" + capacidadRutaDisponible + ", yaAsignadoEnvio=" + yaAsignadoEnEnvio + ").");
            return false;
        }

        // 3) stock en el almacén origen (primer vuelo)
        VueloForAlgorithm primerVuelo = ruta.getVuelosOrdenados().getFirst();
        if (primerVuelo == null) {
            loggingReport.appendReport("esFactible: primer vuelo nulo en ruta.");
            return false;
        }
        Long idAlmacenOrigen = primerVuelo.getIdAlmacenOrigen();
        AlmacenForAlgorithm almacenOrigen = null;
        if (almacenes != null) {
            for (AlmacenForAlgorithm a : almacenes) {
                if (a != null && Objects.equals(a.getId(), idAlmacenOrigen)) {
                    almacenOrigen = a;
                    break;
                }
            }
        }
        int disponibleOrigen;
        if (almacenOrigen == null) {
            // Si no encontramos info del almacén en la lista, conservador: asumir no disponible
            loggingReport.appendReport("esFactible: no se encontró info de almacen origen id=" + idAlmacenOrigen);
            return false;
        } else if (Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
            disponibleOrigen = Integer.MAX_VALUE / 4; // suficientemente grande
        } else {
            int ocupado = almacenOrigen.getCapacidadOcupada() == null ? 0 : almacenOrigen.getCapacidadOcupada();
            int reserv = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
            disponibleOrigen = Math.max(0, ocupado - reserv);
        }
        if (disponibleOrigen <= 0) {
            loggingReport.appendReport("esFactible: origen id=" + idAlmacenOrigen + " no tiene stock disponible (o está vacío).");
            return false;
        }

        // 4) Estados de vuelos en la ruta: todos deben ser EN_ESPERA o EN_CURSO
        for (VueloForAlgorithm v : ruta.getVuelosOrdenados()) {
            if (v == null || v.getEstado() == null) {
                loggingReport.appendReport("esFactible: vuelo nulo o sin estado en ruta.");
                return false;
            }
            if (!(v.getEstado() == EstadoVuelo.EN_ESPERA || v.getEstado() == EstadoVuelo.EN_CURSO)) {
                loggingReport.appendReport("esFactible: vuelo id=" + v.getId() + " en estado no válido: " + v.getEstado());
                return false;
            }
        }

        // 5) Restricción temporal: llegada + 2h (pickup) <= instanteMaximoParaEntregar (si está definido)
        VueloForAlgorithm ultimoVuelo = ruta.getVuelosOrdenados().getLast();
        Instant llegada = ultimoVuelo.getFin();
        if (llegada == null) {
            loggingReport.appendReport("esFactible: último vuelo no tiene hora de fin; asumimos no factible.");
            return false;
        }
        Instant pickup = llegada.plusSeconds(2 * 60 * 60); // +2 horas
        Instant deadline = pedido.getInstanteMaximoParaEntregar();
        if (deadline != null) {
            if (pickup.isAfter(deadline)) {
                loggingReport.appendReport("esFactible: pickup (" + pickup + ") posterior al deadline (" + deadline + ") para pedido id=" + pedido.getId());
                return false;
            }
        } // si deadline null asumimos flexible

        // 6) finalmente, comprobar que al menos 1 unidad pueda asignarse:
        //    asignable = min(remaining, disponibleParaEsteEnvio, disponibleOrigen)
        long asignable = Math.min(remaining, Math.min(disponibleParaEsteEnvio, disponibleOrigen));
        if (asignable <= 0) {
            loggingReport.appendReport("esFactible: ninguna unidad asignable (remaining=" + remaining
                    + ", disponibleRuta=" + disponibleParaEsteEnvio + ", disponibleOrigen=" + disponibleOrigen + ").");
            return false;
        }

        // Si pasa todas las comprobaciones, se considera factible (al menos parcialmente)
        loggingReport.appendReport("esFactible: pedido id=" + pedido.getId() + " puede asignarse parcialmente. asignable=" + asignable);
        return true;
    }

}


/*antigua implementación general grasp:
double alpha = 0.5;
        Duration timeLimit = Duration.ofMinutes(1);

        Instant start = Instant.now();
        Set<Candidate> N = inicializarCandidatos(input);
        Solution S = new Solution(); // fenotipo vacío: lista de EnvioSolution y datas auxiliares

        while (!stopCondition(S, N, start, timeLimit)) {
            Map<Candidate, Double> scores = new HashMap<>();
            for (Candidate x : N) {
                scores.put(x, evaluarMeritoGreedy(x, S, input));
            }

            List<Candidate> rcl = construirRCL(scores, alpha);
            if (rcl.isEmpty()) break;

            Candidate a = seleccionarAleatorio(rcl);
            if (esFactibleAñadir(S, a, input)) {
                S = añadirElemento(S, a, input); // aquí se construye el envío y se actualizan recursos
            }
            N = removerDeCandidatos(N, a, S, input);
            actualizarEstadoDerivado(S, input);
        }

        return convertirSolutionAPlanificationSolutionOutput(S);

 */

/*

private int obtenerCapacidadMaxParaTodosVuelosEnRuta(RutaADestino ruta) {
    if (ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) return 0;
    int minAvail = Integer.MAX_VALUE;
    for (VueloForAlgorithm v : ruta.getVuelosOrdenados()) {
        if (v == null) return 0;
        int max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
        int occ = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
        int res = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
        int avail = Math.max(0, max - occ - res);
        minAvail = Math.min(minAvail, avail);
    }
    if (minAvail == Integer.MAX_VALUE) return 0;
    return Math.max(0, minAvail);
}
* */