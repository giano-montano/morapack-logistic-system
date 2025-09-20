package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;

@Component
public class TabuSearchAlgorithmStrategy implements PlanificationStrategy {

    @Autowired
    private LoggedHeuristicAlgorithmStrategy heuristicAlgorithm;

    // Parámetros del algoritmo Tabu Search optimizados para ALMACORP
    private static final int MAX_ITERATIONS = 500;
    private static final int TABU_LIST_SIZE = 30;
    private static final int MAX_NO_IMPROVEMENT = 50;
    private static final int NEIGHBORHOOD_SIZE = 15;

    // Constantes del dominio ALMACORP
    private static final double COLLAPSE_PENALTY = -1000.0;
    private static final double DELAY_PENALTY = -100.0;
    private static final double DELIVERY_REWARD = 100.0;
    private static final double ROUTE_EFFICIENCY_FACTOR = -2.0;

    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) {

//        // Validar input
//        if (parametrosAlgoritmo.pedidos() == null || parametrosAlgoritmo.vuelos() == null ||
//            parametrosAlgoritmo.almacenes() == null) {
//            return SalidaProblemaPlanificacion.builder().envios(new ArrayList<>()).build();
//        }
//
//        // Leer input (defensivo: crear copias mutables)
//        List<PedidoParaAlgoritmo> pedidos = new ArrayList<>(parametrosAlgoritmo.pedidos());
//        List<VueloParaAlgoritmo> vuelos = new ArrayList<>(parametrosAlgoritmo.vuelos());
//        List<AlmacenParaAlgoritmo> almacenes = new ArrayList<>(parametrosAlgoritmo.almacenes());
//
//        // Filtrar solo pedidos pendientes
//        pedidos = pedidos.stream()
//                .filter(p -> getCantidadPendiente(p) > 0)
//                .collect(Collectors.toList());
//
//        if (pedidos.isEmpty()) {
//            return SalidaProblemaPlanificacion.builder().envios(new ArrayList<>()).build();
//        }
//
//        // Inicializar contexto del algoritmo
//        TabuSearchContext context = initializeContext(pedidos, vuelos, almacenes);
//
//        // Generar solución inicial usando heurística greedy
//        Solution currentSolution = generateInitialSolution(context);
//        if (currentSolution == null || currentSolution.getEnvios().isEmpty()) {
//            return SalidaProblemaPlanificacion.builder().envios(new ArrayList<>()).build();
//        }
//
//        Solution bestSolution = copySolution(currentSolution);
//
//        // Lista tabú para movimientos prohibidos
//        TabuList tabuList = new TabuList(TABU_LIST_SIZE);
//
//        // Contadores de control
//        int iterations = 0;
//        int noImprovementCount = 0;
//
//        // Algoritmo principal Tabu Search
//        while (iterations < MAX_ITERATIONS && noImprovementCount < MAX_NO_IMPROVEMENT) {
//
//            // Generar vecindario de soluciones candidatas
//            List<Solution> neighborhood = generateNeighborhood(currentSolution, context);
//
//            // Encontrar la mejor solución no tabú
//            Solution bestCandidate = null;
//            Move bestMove = null;
//            double bestCandidateFitness = Double.NEGATIVE_INFINITY;
//
//            for (Solution candidate : neighborhood) {
//                if (candidate == null) continue;
//
//                Move move = createMove(currentSolution, candidate);
//
//                // Verificar si el movimiento es tabú (con criterio de aspiración)
//                boolean isTabu = tabuList.isTabu(move);
//                boolean aspirationCriterion = candidate.getFitness() > bestSolution.getFitness();
//
//                if (!isTabu || aspirationCriterion) {
//                    if (candidate.getFitness() > bestCandidateFitness) {
//                        bestCandidate = candidate;
//                        bestMove = move;
//                        bestCandidateFitness = candidate.getFitness();
//                    }
//                }
//            }
//
//            // Actualizar solución actual
//            if (bestCandidate != null) {
//                currentSolution = bestCandidate;
//                if (bestMove != null) {
//                    tabuList.add(bestMove);
//                }
//
//                // Verificar si es la mejor solución global
//                if (currentSolution.getFitness() > bestSolution.getFitness()) {
//                    bestSolution = copySolution(currentSolution);
//                    noImprovementCount = 0;
//                } else {
//                    noImprovementCount++;
//                }
//            } else {
//                // No hay candidatos válidos, diversificar
//                currentSolution = diversifySolution(context, bestSolution);
//                noImprovementCount++;
//            }
//
//            iterations++;
//
//            // Intensificación: cada cierto número de iteraciones sin mejora
//            if (noImprovementCount % 20 == 0 && noImprovementCount > 0) {
//                currentSolution = intensifySolution(bestSolution, context);
//            }
//        }
//
//        // Convertir la mejor solución encontrada al formato de salida
//        return convertSolutionToOutput(bestSolution, context);
        return null;
    }

    /**
     * Inicializa el contexto del algoritmo con las estructuras de datos necesarias
     */
//    private TabuSearchContext initializeContext(List<PedidoParaAlgoritmo> pedidos,
//                                               List<VueloParaAlgoritmo> vuelos,
//                                               List<AlmacenParaAlgoritmo> almacenes) {
//
//        // Mapas para acceso rápido
//        Map<Long, AlmacenParaAlgoritmo> almacenById = almacenes.stream()
//                .collect(Collectors.toMap(AlmacenParaAlgoritmo::getId, a -> a));
//
//        Map<Long, VueloParaAlgoritmo> vueloById = vuelos.stream()
//                .collect(Collectors.toMap(VueloParaAlgoritmo::getId, v -> v));
//
//        // Vuelos salientes por almacén origen
//        Map<Long, List<VueloParaAlgoritmo>> vuelosPorOrigen = new HashMap<>();
//        for (VueloParaAlgoritmo vuelo : vuelos) {
//            if (vuelo.getIdAlmacenOrigen() != null) {
//                vuelosPorOrigen.computeIfAbsent(vuelo.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(vuelo);
//            }
//        }
//
//        // Filtrar solo vuelos disponibles y con capacidad
//        List<VueloParaAlgoritmo> vuelosDisponibles = vuelos.stream()
//                .filter(v -> v.getEstado() == EstadoVuelo.EN_ESPERA)
//                .filter(v -> getCapacidadDisponible(v) > 0)
//                .filter(v -> v.getInicio() != null && v.getFin() != null)
//                .collect(Collectors.toList());
//
//        // Mapas de conexiones para búsqueda de rutas
//        Map<Long, Set<Long>> conexionesDirectas = new HashMap<>();
//        for (VueloParaAlgoritmo vuelo : vuelosDisponibles) {
//            conexionesDirectas.computeIfAbsent(vuelo.getIdAlmacenOrigen(), k -> new HashSet<>())
//                    .add(vuelo.getIdAlmacenDestino());
//        }
//
//        return new TabuSearchContext(pedidos, vuelosDisponibles, almacenes,
//                                   almacenById, vueloById, vuelosPorOrigen, conexionesDirectas);
//    }
//
//    /**
//     * Genera una solución inicial usando heurística greedy con múltiples criterios
//     */
//    private Solution generateInitialSolution(TabuSearchContext context) {
//        // Usar el algoritmo heurístico para generar una solución inicial de calidad
//        try {
//            System.out.println("🧠 Generando solución inicial usando algoritmo heurístico...");
//            SalidaProblemaPlanificacion heuristicResult = generateHeuristicInitialSolution(context);
//            if (heuristicResult != null && heuristicResult.getEnvios() != null && !heuristicResult.getEnvios().isEmpty()) {
//                // Calcular fitness de la solución heurística
//                Map<Long, Integer> almacenesOcupados = calculateAlmacenOccupancy(heuristicResult.getEnvios(), context);
//                double fitness = calculateFitness(heuristicResult.getEnvios(), context, almacenesOcupados);
//                System.out.println("✅ Solución heurística inicial generada: " + heuristicResult.getEnvios().size() +
//                                 " envíos, fitness: " + String.format("%.2f", fitness));
//                return new Solution(heuristicResult.getEnvios(), fitness);
//            }
//        } catch (Exception e) {
//            // Si falla la heurística, usar el método original
//            System.err.println("❌ Error usando solución heurística inicial, usando método greedy: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        // Fallback: usar el método greedy original
//        System.out.println("🔄 Generando solución inicial con método greedy...");
//        return generateGreedyInitialSolution(context);
//    }
//
//    /**
//     * Genera una solución inicial usando el algoritmo heurístico
//     */
//    private SalidaProblemaPlanificacion generateHeuristicInitialSolution(TabuSearchContext context) {
//        // Crear el input para la heurística con copias simples de los datos
//        // Para evitar modificaciones mutuales, usamos las listas directamente
//        // ya que son inmutables durante la búsqueda heurística
//        EntradaProblemaPlanificacion heuristicInput = EntradaProblemaPlanificacion.builder()
//                .vuelos(context.getVuelosDisponibles())
//                .almacenes(context.getAlmacenes())
//                .pedidos(context.getPedidos())
//                .build();
//
//        SalidaProblemaPlanificacion out = heuristicAlgorithm.planificar(heuristicInput);
//        // Normalizar la solución heurística para que Tabu pueda calcular fitness correctamente
//        if (out != null && out.getEnvios() != null) {
//            normalizeHeuristicEnvios(out.getEnvios(), context);
//        }
//        return out;
//    }
//
//    /**
//     * Completa campos faltantes de los envíos generados por la heurística para que Tabu pueda usarlos:
//     * - cantProductos: suma de cantidades de PedidoSolution si está null
//     * - idAlmacenDestino y fechaHoraDestino: a partir del último vuelo de la ruta o del pedido
//     */
//    private void normalizeHeuristicEnvios(List<EnvioSolution> envios, TabuSearchContext context) {
//        if (envios == null) return;
//        Map<Long, PedidoParaAlgoritmo> pedidoById = new HashMap<>();
//        for (PedidoParaAlgoritmo p : context.getPedidos()) pedidoById.put(p.getId(), p);
//
//        for (EnvioSolution envio : envios) {
//            if (envio == null) continue;
//
//            // Asegurar listas no nulas
//            if (envio.getPedidosAAtenderTotalOParcialmente() == null) {
//                envio.setPedidosAAtenderTotalOParcialmente(new ArrayList<>());
//            }
//            if (envio.getIdsVuelosATomar() == null) {
//                envio.setIdsVuelosATomar(new ArrayList<>());
//            }
//
//            // cantProductos: si está null, usar la suma de pedidos
//            if (envio.getCantProductos() == null || envio.getCantProductos() == 0) {
//                int suma = envio.getPedidosAAtenderTotalOParcialmente().stream()
//                        .map(ps -> ps.getCantidadASerAtendidaDelPedido() == null ? 0 : ps.getCantidadASerAtendidaDelPedido())
//                        .mapToInt(Integer::intValue)
//                        .sum();
//                envio.setCantProductos(suma);
//            }
//
//            // Destino y fecha: intentar con el último vuelo de la ruta
//            if (envio.getIdAlmacenDestino() == null || envio.getFechaHoraDestino() == null) {
//                if (!envio.getIdsVuelosATomar().isEmpty()) {
//                    Long lastVueloId = envio.getIdsVuelosATomar().get(envio.getIdsVuelosATomar().size() - 1);
//                    VueloParaAlgoritmo lastVuelo = context.getVueloById().get(lastVueloId);
//                    if (lastVuelo != null) {
//                        if (envio.getIdAlmacenDestino() == null) envio.setIdAlmacenDestino(lastVuelo.getIdAlmacenDestino());
//                        if (envio.getFechaHoraDestino() == null) envio.setFechaHoraDestino(lastVuelo.getFin());
//                    }
//                }
//
//                // Si aún faltan datos, intentar deducir destino del primer pedido
//                if (envio.getIdAlmacenDestino() == null && !envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) {
//                    PedidoSolution ps = envio.getPedidosAAtenderTotalOParcialmente().get(0);
//                    PedidoParaAlgoritmo p = pedidoById.get(ps.getId());
//                    if (p != null) envio.setIdAlmacenDestino(p.getIdAlmacenDestino());
//                }
//            }
//        }
//    }
//
//    /**
//     * Calcula la ocupación de almacenes basada en los envíos
//     */
//    private Map<Long, Integer> calculateAlmacenOccupancy(List<EnvioSolution> envios, TabuSearchContext context) {
//        Map<Long, Integer> ocupacion = new HashMap<>();
//
//        // Inicializar con ocupación actual
//        for (AlmacenParaAlgoritmo almacen : context.getAlmacenes()) {
//            ocupacion.put(almacen.getId(),
//                almacen.getCapacidadOcupada() != null ? almacen.getCapacidadOcupada() : 0);
//        }
//
//        // Agregar ocupación por envíos
//        for (EnvioSolution envio : envios) {
//            for (PedidoSolution pedidoSol : envio.getPedidosAAtenderTotalOParcialmente()) {
//                // Para simplicidad, asumimos que se consume del primer almacén de la ruta
//                if (!envio.getIdsVuelosATomar().isEmpty()) {
//                    Long vueloId = envio.getIdsVuelosATomar().get(0);
//                    VueloParaAlgoritmo vuelo = context.getVueloById().get(vueloId);
//                    if (vuelo != null && !context.getAlmacenById().get(vuelo.getIdAlmacenOrigen()).getEsInfinito()) {
//                        Long almacenId = vuelo.getIdAlmacenOrigen();
//                        ocupacion.merge(almacenId, pedidoSol.getCantidadASerAtendidaDelPedido(), Integer::sum);
//                    }
//                }
//            }
//        }
//
//        return ocupacion;
//    }
//
//    /**
//     * Método greedy original (renombrado)
//     */
//    private Solution generateGreedyInitialSolution(TabuSearchContext context) {
//        List<EnvioSolution> envios = new ArrayList<>();
//        Map<Long, Integer> capacidadesOcupadas = new HashMap<>();
//        Map<Long, Integer> almacenesOcupados = new HashMap<>();
//
//        // Inicializar capacidades ocupadas
//        for (VueloParaAlgoritmo vuelo : context.getVuelosDisponibles()) {
//            capacidadesOcupadas.put(vuelo.getId(),
//                vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0);
//        }
//
//        // Inicializar ocupación de almacenes
//        for (AlmacenParaAlgoritmo almacen : context.getAlmacenes()) {
//            almacenesOcupados.put(almacen.getId(),
//                almacen.getCapacidadOcupada() != null ? almacen.getCapacidadOcupada() : 0);
//        }
//
//        // Ordenar pedidos por urgencia y cantidad
//        List<PedidoParaAlgoritmo> pedidosOrdenados = context.getPedidos().stream()
//                .sorted((p1, p2) -> {
//                    // Primero por deadline (más urgente primero)
//                    int deadlineComparison = p1.getInstanteMaximoParaEntregar()
//                            .compareTo(p2.getInstanteMaximoParaEntregar());
//                    if (deadlineComparison != 0) return deadlineComparison;
//
//                    // Luego por cantidad (menos cantidad primero para completar más pedidos)
//                    return Integer.compare(getCantidadPendiente(p1), getCantidadPendiente(p2));
//                })
//                .collect(Collectors.toList());
//
//        // Procesar cada pedido
//        for (PedidoParaAlgoritmo pedido : pedidosOrdenados) {
//            int cantidadPendiente = getCantidadPendiente(pedido);
//            if (cantidadPendiente <= 0) continue;
//
//            // Intentar dividir el pedido en múltiples envíos si es necesario
//            cantidadPendiente = procesarPedidoConDivision(pedido, cantidadPendiente,
//                    context, envios, capacidadesOcupadas, almacenesOcupados);
//        }
//
//        double fitness = calculateFitness(envios, context, almacenesOcupados);
//        return new Solution(envios, fitness);
//    }
//
//    /**
//     * Procesa un pedido permitiendo división en múltiples envíos
//     */
//    private int procesarPedidoConDivision(PedidoParaAlgoritmo pedido, int cantidadPendiente,
//                                          TabuSearchContext context, List<EnvioSolution> envios,
//                                          Map<Long, Integer> capacidadesOcupadas,
//                                          Map<Long, Integer> almacenesOcupados) {
//
//        // Buscar almacenes origen válidos (con inventario suficiente o infinito)
//        List<AlmacenParaAlgoritmo> origenesValidos = findValidOrigins(context.getAlmacenes(), cantidadPendiente);
//
//        // Ordenar orígenes por distancia/eficiencia al destino
//        origenesValidos = origenesValidos.stream()
//                .sorted((a1, a2) -> {
//                    // Preferir almacenes con conexión directa al destino
//                    boolean a1Direct = context.getConexionesDirectas().getOrDefault(a1.getId(), new HashSet<>())
//                            .contains(pedido.getIdAlmacenDestino());
//                    boolean a2Direct = context.getConexionesDirectas().getOrDefault(a2.getId(), new HashSet<>())
//                            .contains(pedido.getIdAlmacenDestino());
//
//                    if (a1Direct && !a2Direct) return -1;
//                    if (!a1Direct && a2Direct) return 1;
//
//                    // Si ambos son iguales, preferir almacenes infinitos
//                    return Boolean.compare(a2.getEsInfinito() == null ? false : a2.getEsInfinito(),
//                                         a1.getEsInfinito() == null ? false : a1.getEsInfinito());
//                })
//                .collect(Collectors.toList());
//
//        // Intentar crear envíos desde cada origen válido
//        for (AlmacenParaAlgoritmo origen : origenesValidos) {
//            if (cantidadPendiente <= 0) break;
//
//            // Buscar la mejor ruta desde este origen al destino
//            List<VueloParaAlgoritmo> mejorRuta = findBestRoute(origen.getId(),
//                    pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(),
//                    capacidadesOcupadas);
//
//            if (mejorRuta != null && !mejorRuta.isEmpty()) {
//                // Calcular la máxima cantidad que se puede enviar por esta ruta
//                int capacidadRuta = getMinCapacidadDisponibleRuta(mejorRuta, capacidadesOcupadas);
//                int cantidadDisponibleOrigen = getCapacidadDisponibleAlmacen(origen, almacenesOcupados);
//
//                int cantidadAsignar = Math.min(cantidadPendiente,
//                        Math.min(capacidadRuta, cantidadDisponibleOrigen));
//
//                if (cantidadAsignar > 0) {
//                    // Verificar que el envío no cause colapso en destino
//                    if (!causaColapso(pedido.getIdAlmacenDestino(), cantidadAsignar,
//                                    mejorRuta, context, almacenesOcupados)) {
//
//                        // Crear y agregar el envío
//                        EnvioSolution envio = createEnvio(mejorRuta, pedido, cantidadAsignar);
//                        envios.add(envio);
//
//                        // Actualizar capacidades ocupadas
//                        updateCapacidadesOcupadas(mejorRuta, cantidadAsignar, capacidadesOcupadas);
//                        updateAlmacenOcupado(origen.getId(), cantidadAsignar, almacenesOcupados);
//
//                        cantidadPendiente -= cantidadAsignar;
//                    }
//                }
//            }
//        }
//
//        return cantidadPendiente;
//    }
//
//    /**
//     * Genera el vecindario de soluciones para la solución actual
//     */
//    private List<Solution> generateNeighborhood(Solution currentSolution, TabuSearchContext context) {
//        List<Solution> neighborhood = new ArrayList<>();
//        Random random = new Random();
//
//        // Generar diferentes tipos de movimientos
//        for (int i = 0; i < NEIGHBORHOOD_SIZE; i++) {
//            Solution neighbor = null;
//            int moveType = random.nextInt(7); // 7 tipos de movimientos
//
//            switch (moveType) {
//                case 0:
//                    neighbor = swapRoutesMovement(currentSolution, context);
//                    break;
//                case 1:
//                    neighbor = reassignOrderMovement(currentSolution, context);
//                    break;
//                case 2:
//                    neighbor = insertRouteMovement(currentSolution, context);
//                    break;
//                case 3:
//                    neighbor = removeRouteMovement(currentSolution, context);
//                    break;
//                case 4:
//                    neighbor = optimizeRouteMovement(currentSolution, context);
//                    break;
//                case 5:
//                    neighbor = adjustQuantityMovement(currentSolution, context);
//                    break;
//                case 6:
//                    neighbor = replaceLegMovement(currentSolution, context);
//                    break;
//            }
//
//            if (neighbor != null && neighbor.getFitness() != Double.NEGATIVE_INFINITY) {
//                neighborhood.add(neighbor);
//            }
//        }
//
//        return neighborhood;
//    }
//
//    /**
//     * Movimiento 1: Intercambiar rutas entre dos envíos
//     */
//    private Solution swapRoutesMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().size() < 2) return null;
//
//        Solution newSolution = copySolution(solution);
//        Random random = new Random();
//
//        // Seleccionar dos envíos al azar
//        int idx1 = random.nextInt(newSolution.getEnvios().size());
//        int idx2 = random.nextInt(newSolution.getEnvios().size());
//        while (idx1 == idx2 && newSolution.getEnvios().size() > 1) {
//            idx2 = random.nextInt(newSolution.getEnvios().size());
//        }
//
//        EnvioSolution envio1 = newSolution.getEnvios().get(idx1);
//        EnvioSolution envio2 = newSolution.getEnvios().get(idx2);
//
//        // Intercambiar rutas manteniendo destinos compatibles
//        if (envio1.getIdAlmacenDestino().equals(envio2.getIdAlmacenDestino())) {
//            List<Long> rutaTemp = new ArrayList<>(envio1.getIdsVuelosATomar());
//            envio1.setIdsVuelosATomar(new ArrayList<>(envio2.getIdsVuelosATomar()));
//            envio2.setIdsVuelosATomar(rutaTemp);
//
//            // Recalcular fitness
//            Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//            newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//
//            return newSolution;
//        }
//
//        return null;
//    }
//
//    /**
//     * Movimiento 2: Reasignar un pedido a una ruta diferente
//     */
//    private Solution reassignOrderMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().isEmpty()) return null;
//
//        Solution newSolution = copySolution(solution);
//        Random random = new Random();
//
//        // Seleccionar un envío al azar
//        int envioIdx = random.nextInt(newSolution.getEnvios().size());
//        EnvioSolution envio = newSolution.getEnvios().get(envioIdx);
//
//        if (envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) return null;
//
//        // Seleccionar un pedido del envío
//        PedidoSolution pedidoSolution = envio.getPedidosAAtenderTotalOParcialmente().get(0);
//        PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                .filter(p -> p.getId().equals(pedidoSolution.getId()))
//                .findFirst().orElse(null);
//
//        if (pedido == null) return null;
//
//        // Buscar una nueva ruta para este pedido
//        List<AlmacenParaAlgoritmo> origenes = findValidOrigins(context.getAlmacenes(), pedidoSolution.getCantidadASerAtendidaDelPedido());
//        for (AlmacenParaAlgoritmo origen : origenes) {
//            List<VueloParaAlgoritmo> nuevaRuta = findBestRoute(origen.getId(),
//                    pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(), new HashMap<>());
//
//            if (nuevaRuta != null && !nuevaRuta.isEmpty()) {
//                // Actualizar el envío con la nueva ruta
//                envio.setIdsVuelosATomar(nuevaRuta.stream()
//                        .map(VueloParaAlgoritmo::getId)
//                        .collect(Collectors.toList()));
//
//                // Actualizar fecha de llegada
//                if (!nuevaRuta.isEmpty()) {
//                    envio.setFechaHoraDestino(nuevaRuta.get(nuevaRuta.size() - 1).getFin());
//                }
//                break;
//            }
//        }
//
//        // Recalcular fitness
//        Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//        newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//
//        return newSolution;
//    }
//
//    /**
//     * Movimiento 3: Insertar una nueva ruta para pedidos no atendidos
//     */
//    private Solution insertRouteMovement(Solution solution, TabuSearchContext context) {
//        Solution newSolution = copySolution(solution);
//
//        // Encontrar pedidos no completamente atendidos
//        List<PedidoParaAlgoritmo> pedidosPendientes = findUnfulfilledOrders(newSolution, context);
//
//        if (pedidosPendientes.isEmpty()) return null;
//
//        Random random = new Random();
//        PedidoParaAlgoritmo pedido = pedidosPendientes.get(random.nextInt(pedidosPendientes.size()));
//
//        // Buscar una ruta válida para este pedido
//        List<AlmacenParaAlgoritmo> origenes = findValidOrigins(context.getAlmacenes(), 1);
//        for (AlmacenParaAlgoritmo origen : origenes) {
//            List<VueloParaAlgoritmo> ruta = findBestRoute(origen.getId(),
//                    pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(), new HashMap<>());
//
//            if (ruta != null && !ruta.isEmpty()) {
//                int cantidadPendiente = getCantidadPendienteEnSolucion(pedido, newSolution);
//                int capacidadRuta = getMinCapacidadDisponibleRuta(ruta, new HashMap<>());
//                int cantidadAsignar = Math.min(cantidadPendiente, capacidadRuta);
//
//                if (cantidadAsignar > 0) {
//                    EnvioSolution nuevoEnvio = createEnvio(ruta, pedido, cantidadAsignar);
//                    newSolution.getEnvios().add(nuevoEnvio);
//                    break;
//                }
//            }
//        }
//
//        // Recalcular fitness
//        Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//        newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//
//        return newSolution;
//    }
//
//    /**
//     * Movimiento 4: Eliminar una ruta de bajo rendimiento
//     */
//    private Solution removeRouteMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().isEmpty()) return null;
//
//        Solution newSolution = copySolution(solution);
//
//        // Encontrar el envío con peor rendimiento (fitness individual más bajo)
//        double peorFitness = Double.POSITIVE_INFINITY;
//        int peorIndex = -1;
//
//        for (int i = 0; i < newSolution.getEnvios().size(); i++) {
//            EnvioSolution envio = newSolution.getEnvios().get(i);
//            double fitnessEnvio = calcularFitnessEnvio(envio, context);
//
//            if (fitnessEnvio < peorFitness) {
//                peorFitness = fitnessEnvio;
//                peorIndex = i;
//            }
//        }
//
//        if (peorIndex >= 0) {
//            newSolution.getEnvios().remove(peorIndex);
//
//            // Recalcular fitness
//            Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//            newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//        }
//
//        return newSolution;
//    }
//
//    /**
//     * Movimiento 5: Optimizar una ruta existente buscando alternativas
//     */
//    private Solution optimizeRouteMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().isEmpty()) return null;
//
//        Solution newSolution = copySolution(solution);
//        Random random = new Random();
//
//        // Seleccionar un envío al azar
//        int envioIdx = random.nextInt(newSolution.getEnvios().size());
//        EnvioSolution envio = newSolution.getEnvios().get(envioIdx);
//
//        if (envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) return null;
//
//        // Obtener el pedido asociado
//        PedidoSolution pedidoSolution = envio.getPedidosAAtenderTotalOParcialmente().get(0);
//        PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                .filter(p -> p.getId().equals(pedidoSolution.getId()))
//                .findFirst().orElse(null);
//
//        if (pedido == null || envio.getIdsVuelosATomar().isEmpty()) return null;
//
//        // Obtener el origen actual (primer vuelo)
//        Long origenActualId = context.getVuelosDisponibles().stream()
//                .filter(v -> v.getId().equals(envio.getIdsVuelosATomar().get(0)))
//                .map(VueloParaAlgoritmo::getIdAlmacenOrigen)
//                .findFirst().orElse(null);
//
//        if (origenActualId == null) return null;
//
//        // Buscar rutas alternativas desde el mismo origen
//        List<VueloParaAlgoritmo> mejorRutaAlternativa = findBestRoute(origenActualId,
//                pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(), new HashMap<>());
//
//        if (mejorRutaAlternativa != null && !mejorRutaAlternativa.isEmpty() &&
//            !rutasIguales(mejorRutaAlternativa, envio.getIdsVuelosATomar(), context)) {
//
//            // Aplicar la nueva ruta
//            envio.setIdsVuelosATomar(mejorRutaAlternativa.stream()
//                    .map(VueloParaAlgoritmo::getId)
//                    .collect(Collectors.toList()));
//
//            // Actualizar fecha de llegada
//            envio.setFechaHoraDestino(mejorRutaAlternativa.get(mejorRutaAlternativa.size() - 1).getFin());
//
//            // Recalcular fitness
//            Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//            newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//        }
//
//        return newSolution;
//    }
//
//    /**
//     * Movimiento 6: Ajustar la cantidad de un envío existente (pequeño +/- delta)
//     * - Reduce o incrementa cantProductos respetando capacidad de ruta y stock origen
//     */
//    private Solution adjustQuantityMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().isEmpty()) return null;
//        Solution newSolution = copySolution(solution);
//        Random random = new Random();
//
//        int envioIdx = random.nextInt(newSolution.getEnvios().size());
//        EnvioSolution envio = newSolution.getEnvios().get(envioIdx);
//        if (envio.getIdsVuelosATomar().isEmpty() || envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) return null;
//
//        // Calcular capacidad mínima disponible en la ruta (con capacidades actuales aproximadas 0 para simplicidad)
//        List<VueloParaAlgoritmo> ruta = envio.getIdsVuelosATomar().stream()
//                .map(id -> context.getVueloById().get(id))
//                .filter(Objects::nonNull)
//                .collect(Collectors.toList());
//        if (ruta.isEmpty()) return null;
//
//        int actual = Math.max(0, envio.getCantProductos() == null ? 0 : envio.getCantProductos());
//        int minCapRuta = getMinCapacidadRuta(ruta);
//
//        // Delta pequeño (1 a 3 unidades)
//        int delta = 1 + random.nextInt(3);
//        boolean incrementar = random.nextBoolean();
//
//        int nuevo = actual;
//        if (incrementar) {
//            nuevo = Math.min(actual + delta, minCapRuta);
//        } else {
//            nuevo = Math.max(0, actual - delta);
//        }
//
//        if (nuevo == actual) return null;
//
//        // Actualizar cantidades en el envío y en el primer pedido del envío
//        envio.setCantProductos(nuevo);
//        int diff = nuevo - actual;
//        PedidoSolution ps = envio.getPedidosAAtenderTotalOParcialmente().get(0);
//
//        // Obtener el pedido original para validar la cantidad total
//        PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                .filter(p -> p.getId().equals(ps.getId()))
//                .findFirst()
//                .orElse(null);
//        if (pedido == null) return null;
//
//        int cantidadTotalPedido = pedido.getCantidadProductosPedidos();
//        int nuevaCantidadAtendida = Math.min(cantidadTotalPedido, ps.getCantidadASerAtendidaDelPedido() + diff);
//        ps.setCantidadASerAtendidaDelPedido(nuevaCantidadAtendida);
//
//        // Recalcular fitness
//        Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//        newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//        return newSolution;
//    }
//
//    /**
//     * Movimiento 7: Reemplazar un tramo (leg) de la ruta por una alternativa compatible
//     */
//    private Solution replaceLegMovement(Solution solution, TabuSearchContext context) {
//        if (solution.getEnvios().isEmpty()) return null;
//        Solution newSolution = copySolution(solution);
//        Random random = new Random();
//
//        int envioIdx = random.nextInt(newSolution.getEnvios().size());
//        EnvioSolution envio = newSolution.getEnvios().get(envioIdx);
//        if (envio.getIdsVuelosATomar().size() < 1 || envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) return null;
//
//        // Elegir un leg a reemplazar
//        int legIdx = random.nextInt(envio.getIdsVuelosATomar().size());
//        Long legVueloId = envio.getIdsVuelosATomar().get(legIdx);
//        VueloParaAlgoritmo legVuelo = context.getVueloById().get(legVueloId);
//        if (legVuelo == null) return null;
//
//        // Buscar vuelos alternos del mismo origen que lleguen al mismo destino del leg o permitan recomponer el resto
//        List<VueloParaAlgoritmo> candidatos = context.getVuelosPorOrigen().getOrDefault(legVuelo.getIdAlmacenOrigen(), Collections.emptyList())
//                .stream()
//                .filter(v -> !Objects.equals(v.getId(), legVuelo.getId()))
//                .filter(v -> v.getInicio() != null && v.getFin() != null)
//                .filter(v -> v.getEstado() == EstadoVuelo.EN_ESPERA)
//                .collect(Collectors.toList());
//
//        // Necesitamos que el nuevo tramo conecte con el resto de la ruta
//        for (VueloParaAlgoritmo candidato : candidatos) {
//            // Verificar compatibilidad temporal con tramo anterior y siguiente
//            boolean okAnterior = true;
//            boolean okSiguiente = true;
//            if (legIdx > 0) {
//                Long prevId = envio.getIdsVuelosATomar().get(legIdx - 1);
//                VueloParaAlgoritmo prev = context.getVueloById().get(prevId);
//                okAnterior = prev != null && esConexionValida(prev, candidato);
//            }
//            if (legIdx < envio.getIdsVuelosATomar().size() - 1) {
//                Long nextId = envio.getIdsVuelosATomar().get(legIdx + 1);
//                VueloParaAlgoritmo next = context.getVueloById().get(nextId);
//                okSiguiente = next != null && esConexionValida(candidato, next);
//            }
//            if (!okAnterior || !okSiguiente) continue;
//
//            // Aplicar reemplazo
//            List<Long> nuevaRutaIds = new ArrayList<>(envio.getIdsVuelosATomar());
//            nuevaRutaIds.set(legIdx, candidato.getId());
//            envio.setIdsVuelosATomar(nuevaRutaIds);
//
//            // Actualizar llegada
//            Long lastId = nuevaRutaIds.get(nuevaRutaIds.size() - 1);
//            VueloParaAlgoritmo last = context.getVueloById().get(lastId);
//            if (last != null) envio.setFechaHoraDestino(last.getFin());
//
//            // Recalcular fitness
//            Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(newSolution, context);
//            newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context, almacenesOcupados));
//            return newSolution;
//        }
//
//        return null;
//    }
//
//    // ===== MÉTODOS AUXILIARES =====
//
//    /**
//     * Crea un movimiento representando la transición entre dos soluciones
//     */
//    private Move createMove(Solution from, Solution to) {
//        // Determinar qué tipo de movimiento se realizó
//        String moveType = "unknown";
//        if (from.getEnvios().size() != to.getEnvios().size()) {
//            moveType = from.getEnvios().size() < to.getEnvios().size() ? "insert" : "remove";
//        } else {
//            moveType = "modify";
//        }
//        return new Move(moveType, System.currentTimeMillis());
//    }
//
//    /**
//     * Diversifica la solución cuando se llega a un óptimo local
//     */
//    private Solution diversifySolution(TabuSearchContext context, Solution bestSolution) {
//        // Crear una nueva solución con diferentes orígenes y rutas
//        List<EnvioSolution> envios = new ArrayList<>();
//        Random random = new Random();
//
//        // Seleccionar algunos pedidos aleatorios
//        List<PedidoParaAlgoritmo> pedidosAleatorios = new ArrayList<>(context.getPedidos());
//        Collections.shuffle(pedidosAleatorios);
//
//        int numPedidosAProcesar = Math.min(5, pedidosAleatorios.size());
//
//        for (int i = 0; i < numPedidosAProcesar; i++) {
//            PedidoParaAlgoritmo pedido = pedidosAleatorios.get(i);
//            int cantidadPendiente = getCantidadPendiente(pedido);
//
//            if (cantidadPendiente > 0) {
//                List<AlmacenParaAlgoritmo> origenes = findValidOrigins(context.getAlmacenes(), cantidadPendiente);
//                if (!origenes.isEmpty()) {
//                    AlmacenParaAlgoritmo origen = origenes.get(random.nextInt(origenes.size()));
//
//                    List<VueloParaAlgoritmo> ruta = findBestRoute(origen.getId(),
//                            pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(), new HashMap<>());
//
//                    if (ruta != null && !ruta.isEmpty()) {
//                        int capacidadRuta = getMinCapacidadDisponibleRuta(ruta, new HashMap<>());
//                        int cantidadAsignar = Math.min(cantidadPendiente, capacidadRuta);
//
//                        if (cantidadAsignar > 0) {
//                            EnvioSolution envio = createEnvio(ruta, pedido, cantidadAsignar);
//                            envios.add(envio);
//                        }
//                    }
//                }
//            }
//        }
//
//        Map<Long, Integer> almacenesOcupados = new HashMap<>();
//        double fitness = calculateFitness(envios, context, almacenesOcupados);
//        return new Solution(envios, fitness);
//    }
//
//    /**
//     * Intensifica la búsqueda mejorando la mejor solución conocida
//     */
//    private Solution intensifySolution(Solution bestSolution, TabuSearchContext context) {
//        Solution intensified = copySolution(bestSolution);
//
//        // Intentar mejorar cada envío de la mejor solución
//        for (EnvioSolution envio : intensified.getEnvios()) {
//            if (!envio.getPedidosAAtenderTotalOParcialmente().isEmpty()) {
//                PedidoSolution pedidoSol = envio.getPedidosAAtenderTotalOParcialmente().get(0);
//                PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                        .filter(p -> p.getId().equals(pedidoSol.getId()))
//                        .findFirst().orElse(null);
//
//                if (pedido != null && !envio.getIdsVuelosATomar().isEmpty()) {
//                    // Buscar origen actual
//                    VueloParaAlgoritmo primerVuelo = context.getVuelosDisponibles().stream()
//                            .filter(v -> v.getId().equals(envio.getIdsVuelosATomar().get(0)))
//                            .findFirst().orElse(null);
//
//                    if (primerVuelo != null) {
//                        // Buscar ruta alternativa más eficiente
//                        List<VueloParaAlgoritmo> mejorRuta = findBestRoute(primerVuelo.getIdAlmacenOrigen(),
//                                pedido.getIdAlmacenDestino(), context, pedido.getInstanteMaximoParaEntregar(), new HashMap<>());
//
//                        if (mejorRuta != null && !mejorRuta.isEmpty() &&
//                            mejorRuta.size() < envio.getIdsVuelosATomar().size()) {
//
//                            envio.setIdsVuelosATomar(mejorRuta.stream()
//                                    .map(VueloParaAlgoritmo::getId)
//                                    .collect(Collectors.toList()));
//
//                            envio.setFechaHoraDestino(mejorRuta.get(mejorRuta.size() - 1).getFin());
//                        }
//                    }
//                }
//            }
//        }
//
//        // Recalcular fitness
//        Map<Long, Integer> almacenesOcupados = calcularOcupacionAlmacenes(intensified, context);
//        intensified.setFitness(calculateFitness(intensified.getEnvios(), context, almacenesOcupados));
//
//        return intensified;
//    }
//
//    private int getCapacidadDisponible(VueloParaAlgoritmo vuelo) {
//        int ocupada = vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0;
//        return Math.max(0, vuelo.getCapacidadMaximaProductos() - ocupada);
//    }
//
//    private int getCantidadPendiente(PedidoParaAlgoritmo pedido) {
//        int entregados = pedido.getCantidadProductosEntregados() != null ? pedido.getCantidadProductosEntregados() : 0;
//        return Math.max(0, pedido.getCantidadProductosPedidos() - entregados);
//    }
//
//    private List<AlmacenParaAlgoritmo> findValidOrigins(List<AlmacenParaAlgoritmo> almacenes, int cantidadRequerida) {
//        // Por ahora solo consideramos Lima (LIMA) como origen válido
//        // Posteriormente se expandirá a Brussels y Baku
//        Set<String> origenesPermitidos = Set.of("LIMA");
//
//        return almacenes.stream()
//                .filter(a -> {
//                    // Primero verificar si el almacén está en la lista de orígenes permitidos
//                    if (!origenesPermitidos.contains(a.getCodigoCiudadEn4Letras())) {
//                        return false;
//                    }
//
//                    // Almacenes infinitos siempre son válidos
//                    if (Boolean.TRUE.equals(a.getEsInfinito())) return true;
//
//                    // Para almacenes normales, verificar capacidad disponible
//                    int disponible = a.getCapacidadTotal() - a.getCapacidadOcupada();
//                    return disponible >= cantidadRequerida;
//                })
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * Busca la mejor ruta entre dos almacenes considerando múltiples criterios
//     */
//    private List<VueloParaAlgoritmo> findBestRoute(Long origenId, Long destinoId,
//                                                   TabuSearchContext context, Instant deadline,
//                                                   Map<Long, Integer> capacidadesOcupadas) {
//
//        if (Objects.equals(origenId, destinoId)) return new ArrayList<>();
//
//        // Usar A* o Dijkstra modificado para encontrar la mejor ruta
//        PriorityQueue<RutaCandidata> queue = new PriorityQueue<>(
//                Comparator.comparingDouble(r -> r.costo + r.heuristica));
//        Set<Long> visited = new HashSet<>();
//
//        // Inicializar con vuelos desde el origen
//        List<VueloParaAlgoritmo> vuelosDesdeOrigen = context.getVuelosPorOrigen()
//                .getOrDefault(origenId, new ArrayList<>());
//
//        for (VueloParaAlgoritmo vuelo : vuelosDesdeOrigen) {
//            if (esVueloValido(vuelo, deadline, capacidadesOcupadas)) {
//                List<VueloParaAlgoritmo> rutaInicial = Arrays.asList(vuelo);
//                double costo = calcularCostoRuta(rutaInicial);
//                double heuristica = calcularHeuristica(vuelo.getIdAlmacenDestino(), destinoId, context);
//
//                queue.add(new RutaCandidata(rutaInicial, costo, heuristica));
//            }
//        }
//
//        while (!queue.isEmpty()) {
//            RutaCandidata current = queue.poll();
//            VueloParaAlgoritmo lastFlight = current.ruta.get(current.ruta.size() - 1);
//
//            if (Objects.equals(lastFlight.getIdAlmacenDestino(), destinoId)) {
//                return current.ruta; // Encontramos la mejor ruta
//            }
//
//            if (visited.contains(lastFlight.getIdAlmacenDestino())) continue;
//            visited.add(lastFlight.getIdAlmacenDestino());
//
//            // Expandir el camino
//            List<VueloParaAlgoritmo> nextFlights = context.getVuelosPorOrigen()
//                    .getOrDefault(lastFlight.getIdAlmacenDestino(), new ArrayList<>());
//
//            for (VueloParaAlgoritmo nextFlight : nextFlights) {
//                if (esVueloValido(nextFlight, deadline, capacidadesOcupadas) &&
//                    esConexionValida(lastFlight, nextFlight)) {
//
//                    List<VueloParaAlgoritmo> nuevaRuta = new ArrayList<>(current.ruta);
//                    nuevaRuta.add(nextFlight);
//
//                    // Evitar rutas muy largas
//                    if (nuevaRuta.size() <= 4) {
//                        double nuevoCosto = calcularCostoRuta(nuevaRuta);
//                        double nuevaHeuristica = calcularHeuristica(nextFlight.getIdAlmacenDestino(), destinoId, context);
//
//                        queue.add(new RutaCandidata(nuevaRuta, nuevoCosto, nuevaHeuristica));
//                    }
//                }
//            }
//        }
//
//        return null; // No se encontró ruta
//    }
//
//    // ===== MÉTODOS AUXILIARES ADICIONALES =====
//
//    private boolean esVueloValido(VueloParaAlgoritmo vuelo, Instant deadline, Map<Long, Integer> capacidadesOcupadas) {
//        if (vuelo.getEstado() != EstadoVuelo.EN_ESPERA) return false;
//        if (deadline != null && vuelo.getFin() != null && vuelo.getFin().isAfter(deadline)) return false;
//
//        int ocupada = capacidadesOcupadas.getOrDefault(vuelo.getId(),
//                vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0);
//        return vuelo.getCapacidadMaximaProductos() > ocupada;
//    }
//
//    private boolean esConexionValida(VueloParaAlgoritmo vueloAnterior, VueloParaAlgoritmo vueloSiguiente) {
//        // Verificar que el destino del vuelo anterior sea el origen del siguiente
//        if (!Objects.equals(vueloAnterior.getIdAlmacenDestino(), vueloSiguiente.getIdAlmacenOrigen())) {
//            return false;
//        }
//
//        // Verificar que haya tiempo suficiente para la conexión (mínimo 1 hora)
//        if (vueloAnterior.getFin() != null && vueloSiguiente.getInicio() != null) {
//            return !vueloSiguiente.getInicio().isBefore(vueloAnterior.getFin().plusSeconds(3600));
//        }
//
//        return true;
//    }
//
//    private double calcularCostoRuta(List<VueloParaAlgoritmo> ruta) {
//        double costo = 0.0;
//
//        // Penalizar rutas más largas
//        costo += ruta.size() * 10.0;
//
//        // Penalizar rutas con poca capacidad
//        int minCapacidad = ruta.stream()
//                .mapToInt(this::getCapacidadDisponible)
//                .min().orElse(0);
//        costo += (100.0 - minCapacidad) * 0.1;
//
//        // Penalizar tiempo total de viaje
//        if (!ruta.isEmpty() && ruta.get(0).getInicio() != null &&
//            ruta.get(ruta.size() - 1).getFin() != null) {
//            long horasViaje = ruta.get(0).getInicio().until(ruta.get(ruta.size() - 1).getFin(),
//                    java.time.temporal.ChronoUnit.HOURS);
//            costo += horasViaje * 0.5;
//        }
//
//        return costo;
//    }
//
//    private double calcularHeuristica(Long ubicacionActual, Long destino, TabuSearchContext context) {
//        // Heurística simple: si hay conexión directa, costo menor
//        Set<Long> conexiones = context.getConexionesDirectas().getOrDefault(ubicacionActual, new HashSet<>());
//        if (conexiones.contains(destino)) {
//            return 5.0; // Costo bajo para conexión directa
//        }
//
//        // Para ubicaciones sin conexión directa, estimar basado en conexiones disponibles
//        int numConexiones = conexiones.size();
//        return 20.0 + (5.0 / Math.max(1, numConexiones)); // Más conexiones = menor costo heurístico
//    }
//
//    private int getMinCapacidadDisponibleRuta(List<VueloParaAlgoritmo> ruta, Map<Long, Integer> capacidadesOcupadas) {
//        return ruta.stream()
//                .mapToInt(vuelo -> {
//                    int ocupada = capacidadesOcupadas.getOrDefault(vuelo.getId(),
//                            vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0);
//                    return Math.max(0, vuelo.getCapacidadMaximaProductos() - ocupada);
//                })
//                .min()
//                .orElse(0);
//    }
//
//    private int getCapacidadDisponibleAlmacen(AlmacenParaAlgoritmo almacen, Map<Long, Integer> almacenesOcupados) {
//        if (Boolean.TRUE.equals(almacen.getEsInfinito())) {
//            return Integer.MAX_VALUE;
//        }
//
//        int ocupado = almacenesOcupados.getOrDefault(almacen.getId(),
//                almacen.getCapacidadOcupada() != null ? almacen.getCapacidadOcupada() : 0);
//
//        return Math.max(0, ocupado); // Retorna la cantidad disponible en el almacén
//    }
//
//    private boolean causaColapso(Long almacenDestinoId, int cantidadEnvio,
//                                 List<VueloParaAlgoritmo> ruta, TabuSearchContext context,
//                                 Map<Long, Integer> almacenesOcupados) {
//
//        AlmacenParaAlgoritmo almacenDestino = context.getAlmacenes().stream()
//                .filter(a -> a.getId().equals(almacenDestinoId))
//                .findFirst().orElse(null);
//
//        if (almacenDestino == null || Boolean.TRUE.equals(almacenDestino.getEsInfinito())) {
//            return false; // Almacenes infinitos no colapsan
//        }
//
//        int ocupacionActual = almacenesOcupados.getOrDefault(almacenDestinoId,
//                almacenDestino.getCapacidadOcupada() != null ? almacenDestino.getCapacidadOcupada() : 0);
//        int capacidadTotal = almacenDestino.getCapacidadTotal() != null ? almacenDestino.getCapacidadTotal() : 0;
//
//        return (ocupacionActual + cantidadEnvio) > capacidadTotal;
//    }
//
//    private void updateCapacidadesOcupadas(List<VueloParaAlgoritmo> ruta, int cantidad,
//                                           Map<Long, Integer> capacidadesOcupadas) {
//        for (VueloParaAlgoritmo vuelo : ruta) {
//            int ocupadaActual = capacidadesOcupadas.getOrDefault(vuelo.getId(),
//                    vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0);
//            capacidadesOcupadas.put(vuelo.getId(), ocupadaActual + cantidad);
//        }
//    }
//
//    private void updateAlmacenOcupado(Long almacenId, int cantidad, Map<Long, Integer> almacenesOcupados) {
//        int ocupadoActual = almacenesOcupados.getOrDefault(almacenId, 0);
//        almacenesOcupados.put(almacenId, Math.max(0, ocupadoActual - cantidad));
//    }
//
//    private Map<Long, Integer> calcularOcupacionAlmacenes(Solution solution, TabuSearchContext context) {
//        Map<Long, Integer> ocupacion = new HashMap<>();
//
//        // Inicializar con ocupación actual
//        for (AlmacenParaAlgoritmo almacen : context.getAlmacenes()) {
//            ocupacion.put(almacen.getId(),
//                    almacen.getCapacidadOcupada() != null ? almacen.getCapacidadOcupada() : 0);
//        }
//
//        // Agregar ocupación por envíos pendientes
//        for (EnvioSolution envio : solution.getEnvios()) {
//            Long destinoId = envio.getIdAlmacenDestino();
//            int cantidad = envio.getCantProductos();
//
//            int ocupacionActual = ocupacion.getOrDefault(destinoId, 0);
//            ocupacion.put(destinoId, ocupacionActual + cantidad);
//        }
//
//        return ocupacion;
//    }
//
//    private double calcularFitnessEnvio(EnvioSolution envio, TabuSearchContext context) {
//        double fitness = 0.0;
//
//        // Recompensa por productos entregados
//        fitness += envio.getCantProductos() * DELIVERY_REWARD;
//
//        // Penalización por número de vuelos (preferir rutas directas)
//        fitness += envio.getIdsVuelosATomar().size() * ROUTE_EFFICIENCY_FACTOR;
//
//        // Penalización por retrasos
//        for (PedidoSolution pedidoSol : envio.getPedidosAAtenderTotalOParcialmente()) {
//            PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                    .filter(p -> p.getId().equals(pedidoSol.getId()))
//                    .findFirst().orElse(null);
//
//            if (pedido != null && envio.getFechaHoraDestino() != null) {
//                if (envio.getFechaHoraDestino().isAfter(pedido.getInstanteMaximoParaEntregar())) {
//                    long retrasoHoras = envio.getFechaHoraDestino().toEpochMilli() -
//                            pedido.getInstanteMaximoParaEntregar().toEpochMilli();
//                    retrasoHoras = retrasoHoras / (1000 * 60 * 60); // Convertir a horas
//                    fitness += retrasoHoras * DELAY_PENALTY;
//                }
//            }
//        }
//
//        return fitness;
//    }
//
//    private boolean rutasIguales(List<VueloParaAlgoritmo> ruta1, List<Long> idsRuta2, TabuSearchContext context) {
//        if (ruta1.size() != idsRuta2.size()) return false;
//
//        for (int i = 0; i < ruta1.size(); i++) {
//            if (!ruta1.get(i).getId().equals(idsRuta2.get(i))) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    private int getMinCapacidadRuta(List<VueloParaAlgoritmo> ruta) {
//        return ruta.stream()
//                .mapToInt(this::getCapacidadDisponible)
//                .min()
//                .orElse(0);
//    }
//
//    private EnvioSolution createEnvio(List<VueloParaAlgoritmo> ruta, PedidoParaAlgoritmo pedido, int cantidad) {
//        EnvioSolution envio = new EnvioSolution();
//
//        envio.setIdsVuelosATomar(ruta.stream()
//                .map(VueloParaAlgoritmo::getId)
//                .collect(Collectors.toList()));
//
//        envio.setIdAlmacenDestino(pedido.getIdAlmacenDestino());
//        envio.setCantProductos(cantidad);
//
//        if (!ruta.isEmpty()) {
//            envio.setFechaHoraDestino(ruta.get(ruta.size() - 1).getFin());
//        }
//
//        PedidoSolution pedidoSolution = new PedidoSolution();
//        pedidoSolution.setId(pedido.getId());
//        pedidoSolution.setCantidadASerAtendidaDelPedido(cantidad);
//
//        envio.setPedidosAAtenderTotalOParcialmente(Arrays.asList(pedidoSolution));
//
//        return envio;
//    }
//
//    /**
//     * Función de fitness mejorada que considera el contexto específico de ALMACORP
//     */
//    private double calculateFitness(List<EnvioSolution> envios, TabuSearchContext context,
//                                   Map<Long, Integer> almacenesOcupados) {
//        double fitness = 0.0;
//
//        // Calcular métricas principales
//        int totalProductosEntregados = 0;
//        int totalRetrasos = 0;
//        int totalVuelos = 0;
//        int colapsos = 0;
//
//        for (EnvioSolution envio : envios) {
//            totalProductosEntregados += envio.getCantProductos();
//            totalVuelos += envio.getIdsVuelosATomar().size();
//
//            // Verificar retrasos
//            for (PedidoSolution pedidoSol : envio.getPedidosAAtenderTotalOParcialmente()) {
//                PedidoParaAlgoritmo pedido = context.getPedidos().stream()
//                        .filter(p -> p.getId().equals(pedidoSol.getId()))
//                        .findFirst().orElse(null);
//
//                if (pedido != null && envio.getFechaHoraDestino() != null) {
//                    if (envio.getFechaHoraDestino().isAfter(pedido.getInstanteMaximoParaEntregar())) {
//                        totalRetrasos++;
//                    }
//                }
//            }
//
//            // Verificar colapsos potenciales
//            Long destinoId = envio.getIdAlmacenDestino();
//            AlmacenParaAlgoritmo almacenDestino = context.getAlmacenes().stream()
//                    .filter(a -> a.getId().equals(destinoId))
//                    .findFirst().orElse(null);
//
//            if (almacenDestino != null && !Boolean.TRUE.equals(almacenDestino.getEsInfinito())) {
//                int ocupacionTotal = almacenesOcupados.getOrDefault(destinoId, 0);
//                int capacidadTotal = almacenDestino.getCapacidadTotal() != null ?
//                        almacenDestino.getCapacidadTotal() : 0;
//
//                if (ocupacionTotal > capacidadTotal) {
//                    colapsos++;
//                }
//            }
//        }
//
//        // Aplicar pesos y penalizaciones
//        fitness += totalProductosEntregados * DELIVERY_REWARD;
//        fitness += totalRetrasos * DELAY_PENALTY;
//        fitness += totalVuelos * ROUTE_EFFICIENCY_FACTOR;
//        fitness += colapsos * COLLAPSE_PENALTY;
//
//        // Bonificación por eficiencia: más pedidos completados
//        Set<Long> pedidosAtendidos = new HashSet<>();
//        for (EnvioSolution envio : envios) {
//            for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//                pedidosAtendidos.add(ps.getId());
//            }
//        }
//        fitness += pedidosAtendidos.size() * 50.0; // Bonificación por diversidad de pedidos
//
//        return fitness;
//    }
//
//    private Solution copySolution(Solution original) {
//        if (original == null) return null;
//
//        List<EnvioSolution> nuevosEnvios = new ArrayList<>();
//
//        for (EnvioSolution envio : original.getEnvios()) {
//            EnvioSolution nuevoEnvio = new EnvioSolution();
//            nuevoEnvio.setCantProductos(envio.getCantProductos());
//            nuevoEnvio.setIdAlmacenDestino(envio.getIdAlmacenDestino());
//            nuevoEnvio.setFechaHoraDestino(envio.getFechaHoraDestino());
//            nuevoEnvio.setIdsVuelosATomar(new ArrayList<>(envio.getIdsVuelosATomar()));
//
//            List<PedidoSolution> nuevosPedidos = new ArrayList<>();
//            for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//                PedidoSolution nuevoPS = new PedidoSolution();
//                nuevoPS.setId(ps.getId());
//                nuevoPS.setCantidadASerAtendidaDelPedido(ps.getCantidadASerAtendidaDelPedido());
//                nuevosPedidos.add(nuevoPS);
//            }
//            nuevoEnvio.setPedidosAAtenderTotalOParcialmente(nuevosPedidos);
//
//            nuevosEnvios.add(nuevoEnvio);
//        }
//
//        return new Solution(nuevosEnvios, original.getFitness());
//    }
//
//    private List<PedidoParaAlgoritmo> findUnfulfilledOrders(Solution solution, TabuSearchContext context) {
//        Map<Long, Integer> cantidadesAtendidas = new HashMap<>();
//
//        // Recopilar cantidades atendidas por pedido
//        for (EnvioSolution envio : solution.getEnvios()) {
//            for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//                cantidadesAtendidas.put(ps.getId(),
//                    cantidadesAtendidas.getOrDefault(ps.getId(), 0) + ps.getCantidadASerAtendidaDelPedido());
//            }
//        }
//
//        // Encontrar pedidos no completamente atendidos
//        return context.getPedidos().stream()
//                .filter(p -> {
//                    int cantidadPendiente = getCantidadPendiente(p);
//                    int atendido = cantidadesAtendidas.getOrDefault(p.getId(), 0);
//                    return cantidadPendiente > atendido;
//                })
//                .collect(Collectors.toList());
//    }
//
//    private int getCantidadPendienteEnSolucion(PedidoParaAlgoritmo pedido, Solution solution) {
//        int atendido = 0;
//        for (EnvioSolution envio : solution.getEnvios()) {
//            for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//                if (ps.getId().equals(pedido.getId())) {
//                    atendido += ps.getCantidadASerAtendidaDelPedido();
//                }
//            }
//        }
//        return Math.max(0, getCantidadPendiente(pedido) - atendido);
//    }
//
//    private SalidaProblemaPlanificacion convertSolutionToOutput(Solution solution, TabuSearchContext context) {
//        if (solution == null || solution.getEnvios() == null) {
//            return SalidaProblemaPlanificacion.builder()
//                    .envios(new ArrayList<>())
//                    .build();
//        }
//
//        return SalidaProblemaPlanificacion.builder()
//                .envios(solution.getEnvios())
//                .build();
//    }
//
//    // ===== CLASES AUXILIARES =====
//
//    /**
//     * Contexto que contiene toda la información necesaria para el algoritmo
//     */
//    private static class TabuSearchContext {
//        private final List<PedidoParaAlgoritmo> pedidos;
//        private final List<VueloParaAlgoritmo> vuelosDisponibles;
//        private final List<AlmacenParaAlgoritmo> almacenes;
//        private final Map<Long, AlmacenParaAlgoritmo> almacenById;
//        private final Map<Long, VueloParaAlgoritmo> vueloById;
//        private final Map<Long, List<VueloParaAlgoritmo>> vuelosPorOrigen;
//        private final Map<Long, Set<Long>> conexionesDirectas;
//
//        public TabuSearchContext(List<PedidoParaAlgoritmo> pedidos, List<VueloParaAlgoritmo> vuelosDisponibles,
//                                 List<AlmacenParaAlgoritmo> almacenes, Map<Long, AlmacenParaAlgoritmo> almacenById,
//                                 Map<Long, VueloParaAlgoritmo> vueloById, Map<Long, List<VueloParaAlgoritmo>> vuelosPorOrigen,
//                                 Map<Long, Set<Long>> conexionesDirectas) {
//            this.pedidos = pedidos;
//            this.vuelosDisponibles = vuelosDisponibles;
//            this.almacenes = almacenes;
//            this.almacenById = almacenById;
//            this.vueloById = vueloById;
//            this.vuelosPorOrigen = vuelosPorOrigen;
//            this.conexionesDirectas = conexionesDirectas;
//        }
//
//        // Getters
//        public List<PedidoParaAlgoritmo> getPedidos() { return pedidos; }
//        public List<VueloParaAlgoritmo> getVuelosDisponibles() { return vuelosDisponibles; }
//        public List<AlmacenParaAlgoritmo> getAlmacenes() { return almacenes; }
//        public Map<Long, AlmacenParaAlgoritmo> getAlmacenById() { return almacenById; }
//        public Map<Long, VueloParaAlgoritmo> getVueloById() { return vueloById; }
//        public Map<Long, List<VueloParaAlgoritmo>> getVuelosPorOrigen() { return vuelosPorOrigen; }
//        public Map<Long, Set<Long>> getConexionesDirectas() { return conexionesDirectas; }
//    }
//
//    /**
//     * Representa una solución completa del problema
//     */
//    private static class Solution {
//        private List<EnvioSolution> envios;
//        private double fitness;
//
//        public Solution(List<EnvioSolution> envios, double fitness) {
//            this.envios = envios;
//            this.fitness = fitness;
//        }
//
//        public List<EnvioSolution> getEnvios() { return envios; }
//        public void setEnvios(List<EnvioSolution> envios) { this.envios = envios; }
//        public double getFitness() { return fitness; }
//        public void setFitness(double fitness) { this.fitness = fitness; }
//    }
//
//    /**
//     * Representa un movimiento en el espacio de soluciones
//     */
//    private static class Move {
//        private final String type;
//        private final long timestamp;
//
//        public Move(String type, long timestamp) {
//            this.type = type;
//            this.timestamp = timestamp;
//        }
//
//        public String getType() { return type; }
//        public long getTimestamp() { return timestamp; }
//
//        @Override
//        public boolean equals(Object obj) {
//            if (this == obj) return true;
//            if (obj == null || getClass() != obj.getClass()) return false;
//            Move move = (Move) obj;
//            return Objects.equals(type, move.type);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(type);
//        }
//    }
//
//    /**
//     * Lista tabú para mantener movimientos prohibidos
//     */
//    private static class TabuList {
//        private final Queue<Move> tabuMoves;
//        private final int maxSize;
//
//        public TabuList(int maxSize) {
//            this.maxSize = maxSize;
//            this.tabuMoves = new LinkedList<>();
//        }
//
//        public void add(Move move) {
//            if (move == null) return;
//
//            if (tabuMoves.size() >= maxSize) {
//                tabuMoves.poll();
//            }
//            tabuMoves.offer(move);
//        }
//
//        public boolean isTabu(Move move) {
//            return move != null && tabuMoves.contains(move);
//        }
//    }
//
//    /**
//     * Clase auxiliar para el algoritmo de búsqueda de rutas A*
//     */
//    private static class RutaCandidata {
//        public final List<VueloParaAlgoritmo> ruta;
//        public final double costo;
//        public final double heuristica;
//
//        public RutaCandidata(List<VueloParaAlgoritmo> ruta, double costo, double heuristica) {
//            this.ruta = ruta;
//            this.costo = costo;
//            this.heuristica = heuristica;
//        }
//    }
}
