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
public class BorradorStrategy implements PlanificationStrategy {

    private LoggingReport loggingReport = new LoggingReport();

    @Override
    public PlanificationSolutionOutput planificar(PlanificationProblemInput input) throws Exception {

        List<PedidoForAlgorithm> pedidos = input.pedidos() == null ? new ArrayList<>() : new ArrayList<>(input.pedidos());
        List<VueloForAlgorithm> vuelos = input.vuelos() == null ? new ArrayList<>() : new ArrayList<>(input.vuelos());
        List<AlmacenForAlgorithm> almacenes = input.almacenes() == null ? new ArrayList<>() : new ArrayList<>(input.almacenes());

        PlanificationSolutionOutput solution = new PlanificationSolutionOutput();
        solution.setEnvios(new ArrayList<>());
        // límite de iteraciones para evitar ciclos infinitos (ajustar según el dominio)
        final int MAX_ITERATIONS = Math.max(5000, pedidos.size() * 10);
        int iter = 0;
        try {
            while (hayPedidosPendientes(pedidos) && iter < MAX_ITERATIONS) {
                EnvioSolution envioConstruidoPorGrasp = graspConstructionForOneEnvio(pedidos, vuelos, almacenes);
                // ya actualiza el input en memoria!
                if (envioConstruidoPorGrasp == null) {
                    iter++;
                    continue;
//                    break;
                }
                // en un futuro podría añadir el GA aquí
                // Añadir el envío a la solución
                solution.getEnvios().add(envioConstruidoPorGrasp);
                // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
                int removed = eliminarPedidosCompletamenteSatisfechos(pedidos);
                iter++;
            }
            return solution;
        } catch (Exception ex) {
            throw ex;
        }
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
    //integrar una política de re-try con diferentes alpha/semillas para salir de situaciones difíciles.????????????????????
    private static final double alpha = 0.1; // número máximo de tramos por ruta (incluye primer vuelo)

    private EnvioSolution graspConstructionForOneEnvio(List<PedidoForAlgorithm> pedidos, List<VueloForAlgorithm> vuelos, List<AlmacenForAlgorithm> almacenes) {
        try {
            // Primero generamos rutas para todos los destinos posibles
            List<RutaADestino>
                    rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios = // recordar que no hay pedidos para almacenes infinitos
                    generarRutasCandidatas(vuelos, almacenes) // top-K orígenes, BFS limitado, maxEscalas
                    ; // Lo que sí podría hacer es un RCL que tenga solo las mejores rutas para CADA almacén posible.
            if (rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios.isEmpty()) {
                return null;
            }
            Map<RutaADestino, Double> puntajesPorRuta = evaluarMeritoRutas(rutasParaDestinosNoInfinitosDesdeAlmacenesInfinitosONoVacios, pedidos);
            List<RutaADestino> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta, alpha, almacenes);
            if (rclRutasCandidatas == null || rclRutasCandidatas.isEmpty()) {
                return null;
            }
            // Recorremos la RCL en orden (puedes barajar si quieres diversidad)
            Random rng = new Random();
            List<RutaADestino> rutasAProbar = new ArrayList<>(rclRutasCandidatas);
            // opcional: shuffle para mayor aleatoriedad en ejecuciones repetidas
            Collections.shuffle(rutasAProbar, rng);
            // Podríamos encontrar algún método que soporte el weighted; y también que vaya eliminando la ruta del rcl o el idDestinoFinal como tal...
            for (RutaADestino rutaSeleccionada : rutasAProbar) {
                if (rutaSeleccionada == null || rutaSeleccionada.getVuelosOrdenados() == null || rutaSeleccionada.getVuelosOrdenados().isEmpty()) {
                    continue;
                }

                Long idAlmacenDestinoRutaSeleccionada = rutaSeleccionada.getVuelosOrdenados().getLast().getIdAlmacenDestino();

                List<PedidoForAlgorithm> NpedidosPendientesConDestino = obtenerPedidosPendientesConDestino(idAlmacenDestinoRutaSeleccionada, pedidos);
                if (NpedidosPendientesConDestino == null || NpedidosPendientesConDestino.isEmpty()) {
                    continue; // probar la siguiente ruta de la RCL, en vez de returnear null de fresa
                }
                Integer capacidadMaxParaVuelosRuta = obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSeleccionada);

                // Siguente fase: Construir contenido de 1 envío utilizando esta buena ruta.
                EnvioSolution envioSolucion = new EnvioSolution();
                // esto de aquí abajo qué es? vvvvvvvvvvvvvvvvvvvv
                envioSolucion.setCantProductos(0);
                envioSolucion.setPedidosAAtenderTotalOParcialmente(new ArrayList<>());
                List<Long> idsVuelos = rutaSeleccionada.getVuelosOrdenados().stream().map(VueloForAlgorithm::getId).collect(Collectors.toList());
                envioSolucion.setIdsVuelosATomar(idsVuelos);
                envioSolucion.setIdAlmacenDestino(idAlmacenDestinoRutaSeleccionada);
                envioSolucion.setFechaHoraDestino(rutaSeleccionada.getVuelosOrdenados().getLast().getFin());
                // Aggg ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                int i = 0;
                while (capacidadMaxParaVuelosRuta > 0 && !NpedidosPendientesConDestino.isEmpty()) {
                    Map<PedidoForAlgorithm, Double> puntajesPorPedido = evaluarMeritoPedidos(NpedidosPendientesConDestino, envioSolucion, almacenes, vuelos); // usa info de pedidos, lo que ya llenamos del envío y estado global
                    List<PedidoForAlgorithm> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido, alpha);
                    if (rclPedidosCandidatos.isEmpty()) {
                        break;
                    }
                    PedidoForAlgorithm pedidoElegido = seleccionarPedidoDesdeRCL(rclPedidosCandidatos, puntajesPorPedido, rng, false);
                    if (pedidoElegido == null) {
                        break;
                    }
                    esFactibleAnadirPedidoAEnvio(pedidoElegido, envioSolucion, rutaSeleccionada, almacenes, vuelos);
                    int cantidad = decidirCantidadAAsignar(pedidoElegido, envioSolucion, rutaSeleccionada, almacenes, vuelos);
                    envioSolucion = anadirPedidoConCantidad(envioSolucion, pedidoElegido, cantidad, rutaSeleccionada, almacenes, vuelos);
                    // actualizar la capacidadMaxParaVuelosRuta de forma aproximada restando la cantidad asignada
                    capacidadMaxParaVuelosRuta = actualizarEstadoTemporalEnMemoria(envioSolucion, pedidoElegido, rutaSeleccionada, almacenes, vuelos, NpedidosPendientesConDestino);
                    //Si implementas prioridad para agrupar pedidos, considera, tras añadir, reordenar NpedidosPendientesConDestino para intentar consolidaciones.

                    NpedidosPendientesConDestino = removerPedidosSatisfechosOIrrelevantes(NpedidosPendientesConDestino, pedidoElegido, rutaSeleccionada, almacenes, vuelos);

                    i++;
                    // safety: evitar loops muy largos en una sola ruta
                    if (i > 1000) {
                        break;
                    }
                }
                // Si construimos al menos 1 producto, devolvemos este envio (quedarán las reservas aplicadas en memoria)
                if (envioSolucion.getCantProductos() != null && envioSolucion.getCantProductos() > 0) {
                    return envioSolucion;
                }
            } // end for rutas de la RCL
            // ninguna ruta produjo un envío válido
            return null; // aquí recién rompemos la iteración de graspcitos, porque produjo basura (?)
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    /*
    Notas finales / seguridad
Esta versión no persiste nada: todas las reservas son mutaciones en memoria (vuelos.capacidadReservadaProductos, almacen.capacidadReservadaPorEnvios,
pedido.cantidadProductosProgramados) realizadas por anadirPedidoConCantidad. Persiste después, al confirmar el envío (como diseñamos antes).
removerPedidosSatisfechosOIrrelevantes usa esFactibleAnadirPedidoAEnvio de forma conservadora para eliminar pedidos que hoy no son servibles por la ruta.
Si prefieres mantenerlos para intentar más tarde con otra ruta, cambia la función para sólo eliminar los satisfechos.
Si deseas que graspConstructionForOneEnvio intente variar ruta si la ruta seleccionada no logra llenar nada, añade lógica para quitar esa ruta de la RCL y
elegir otra. Actualmente selecciona una sola ruta y rellena lo que pueda con ella.
¿Quieres que ahora:
A) haga que graspConstructionForOneEnvio itere rutas de la RCL hasta llenar una con éxito (en lugar de solo tomar la primera), o

     */
    private Integer obtenerCapacidadMaxParaTodosVuelosEnRuta(RutaADestino rutaSeleccionada) {
        if (rutaSeleccionada == null || rutaSeleccionada.getVuelosOrdenados() == null || rutaSeleccionada.getVuelosOrdenados().isEmpty())
            return 0;
        return rutaSeleccionada.getVuelosOrdenados().stream().
                mapToInt(
                        (v) -> {
                            return v.getCapacidadMaximaProductos() - v.getCapacidadOcupadaProductos() - v.getCapacidadReservadaProductos(); // CON PURA FE A LOS NO NULL POINTERS 🙏🙏🙏🙏🙏🙏
                        }
                ).min().orElse(0);
    }

    private List<PedidoForAlgorithm> obtenerPedidosPendientesConDestino(Long idAlmacenDestino, List<PedidoForAlgorithm> pedidos) {
        if (idAlmacenDestino == null || pedidos == null) return Collections.emptyList();

        return pedidos.stream().filter( // REZANDO PARA NO TENER NULL POINTERS
                p ->
                        Objects.equals(p.getIdAlmacenDestino(), idAlmacenDestino)
                                && (
                                p.getCantidadProductosProgramados() < p.getCantidadProductosPedidos()
                                        && p.getCantidadProductosEntregados() < p.getCantidadProductosPedidos()
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
     * <p>
     * Filtra vuelos que no tengan capacidad disponible y asegura encadenamiento temporal
     * (siguiente.inicio >= anterior.fin).
     */
    List<RutaADestino> generarRutasCandidatas(List<VueloForAlgorithm> vuelos, List<AlmacenForAlgorithm> almacenes) {
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
                            if (Objects.equals(used.getId(), next.getId())) {
                                ciclo = true;
                                break;
                            }
                            if (Objects.equals(used.getIdAlmacenOrigen(), next.getIdAlmacenDestino())
                                    && Objects.equals(used.getIdAlmacenDestino(), next.getIdAlmacenOrigen())) {
                                // conservador: evitar volver al mismo par invertido
                                ciclo = true;
                                break;
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
        for (RutaADestino ruta : resultado) {
            loggingReport.appendReport("Rutas:");
            for (VueloForAlgorithm vf : ruta.getVuelosOrdenados()) {
                loggingReport.appendReport("   Vuelo:" + vf);
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
            else
                legsScore = 1.0 - ((double) (legs - minLegs) / (double) (maxLegs - minLegs)); // 1 = fewest legs, 0 = most legs

            // arrivalScore: earlier -> better
            double arrivalScore;
            double arrivalE = rawArrival.getOrDefault(r, (double) Long.MAX_VALUE);
            if (maxArrivalEpoch == minArrivalEpoch) arrivalScore = 1.0;
            else {
                // map arrivalEpoch in [minArrival,maxArrival] to [1..0] (earlier=1)
                arrivalScore = 1.0 - ((arrivalE - minArrivalEpoch) / (double) (Math.max(1, maxArrivalEpoch - minArrivalEpoch)));
            }

            // capacityScore: higher available -> better
            double capScore;
            int cap = rawCapacity.getOrDefault(r, 0);
            if (maxCap == minCap) capScore = 1.0;
            else capScore = (double) (cap - minCap) / (double) (Math.max(1, maxCap - minCap));

            // demandScore: higher demand -> better
            double demandScore;
            int dem = rawDemand.getOrDefault(r, 0);
            if (maxDemand == minDemand) demandScore = 1.0;
            else demandScore = (double) (dem - minDemand) / (double) (Math.max(1, maxDemand - minDemand));

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
        rcl.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        return rcl;
    }

    /**
     * Versión extendida: construye la RCL y garantiza que haya al menos una ruta
     * candidata por cada almacén destino NO infinito (siempre que haya rutas para ese destino).
     *
     * @param scores    mapa ruta -> score (mayor = mejor)
     * @param alpha     parámetro RCL
     * @param almacenes lista de almacenes para identificar destinos infinitos (puede ser null)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */
    private List<RutaADestino> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(Map<RutaADestino, Double> scores,
                                                                               double alpha,
                                                                               List<AlmacenForAlgorithm> almacenes) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        // calc min/max scores
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();

        // Umbral clásico RCL (score mayor = mejor)
        double threshold = max - alpha * (max - min);

        // 1) RCL inicial por umbral
        Set<RutaADestino> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new)); // mantener orden de inserción

        // 2) Mapear la mejor ruta por destino (según score)
        Map<Long, RutaADestino> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();
        for (Map.Entry<RutaADestino, Double> e : scores.entrySet()) {
            RutaADestino ruta = e.getKey();
            Double score = e.getValue() == null ? Double.NEGATIVE_INFINITY : e.getValue();
            if (ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) continue;
            Long destinoId = ruta.getVuelosOrdenados().getLast().getIdAlmacenDestino();
            if (destinoId == null) continue;

            // comprobar si el destino es infinito (si recibimos lista de almacenes)
            if (almacenes != null) {
                Optional<AlmacenForAlgorithm> aOpt = almacenes.stream()
                        .filter(a -> a != null && Objects.equals(a.getId(), destinoId))
                        .findFirst();
                if (aOpt.isPresent() && Boolean.TRUE.equals(aOpt.get().getEsInfinito())) {
                    // ignorar destinos infinitos
                    continue;
                }
            }

            Double bestScore = bestScoreByDestino.get(destinoId);
            if (bestScore == null || score > bestScore) {
                bestScoreByDestino.put(destinoId, score);
                bestByDestino.put(destinoId, ruta);
            }
        }

        // 3) Asegurar que la mejor ruta por destino esté en la RCL
        for (Map.Entry<Long, RutaADestino> be : bestByDestino.entrySet()) {
            RutaADestino bestRuta = be.getValue();
            if (bestRuta == null) continue;
            if (!rclSet.contains(bestRuta)) {
                rclSet.add(bestRuta);
            }
        }

        // 4) Ordenar por score descendente y devolver
        List<RutaADestino> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));
        return rcl;
    }

    /**
     * Selecciona una ruta desde la RCL.
     *
     * @param rcl      lista de rutas candidatas (no vacía)
     * @param scores   mapa ruta->score (debe contener las rutas)
     * @param rng      Random
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
            for (int i = 0; i < rcl.size(); i++) {
                acc += ws.get(i);
                if (pick <= acc) return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size() - 1);
        }
    }

    /**
     * Evalúa mérito de pedidos candidatos para llenar un envío.
     *
     * @param pedidos   lista de pedidos candidatos (pendientes) — solo los que tienen idAlmacenDestino == destino de la ruta
     * @param envio     envío parcialmente construido (puede estar vacío al inicio)
     * @param almacenes lista de almacenes (para estimar stock / orígenes infinitos)
     * @param vuelos    lista de vuelos (no usada fuertemente aquí; opcional para extensiones)
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
     * <p>
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
     *
     * @param rcl      lista no vacía (puede ser vacía -> retorna null)
     * @param scores   mapa pedido->score (opcional si weighted=false)
     * @param rng      Random instance (si null, se crea una nueva)
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
     * <p>
     * Requisitos verificados (conservador):
     * - el pedido tiene cantidad restante > 0
     * - la ruta tiene capacidad mínima disponible entre todos sus vuelos (considerando reservas/ocupados)
     * descontando lo ya agregado al envio en construcción
     * - el almacén origen (primer vuelo) tiene stock disponible (a menos que sea infinito)
     * - los vuelos en la ruta están en estados válidos (EN_ESPERA / EN_CURSO)
     * - la llegada estimada + 2 horas (pickup) cumple con el instanteMaximoParaEntregar del pedido (si está definido) REVISAR BN !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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

    /**
     * Decide cuántas unidades asignar del pedido al envío en construcción.
     * Regla: máxima cantidad posible limitada por:
     * - remaining del pedido,
     * - capacidad mínima disponible en la ruta (considerando ya asignado en el envío),
     * - stock disponible en el almacen origen (a menos que sea infinito).
     *
     * @return cantidad asignable (>0) o 0 si no hay nada asignable.
     */
    private int decidirCantidadAAsignar(PedidoForAlgorithm pedido,
                                        EnvioSolution envio,
                                        RutaADestino ruta,
                                        List<AlmacenForAlgorithm> almacenes,
                                        List<VueloForAlgorithm> vuelos) {
        if (pedido == null || ruta == null) return 0;

        // remaining del pedido
        int total = pedido.getCantidadProductosPedidos() == null ? 0 : pedido.getCantidadProductosPedidos();
        int entregados = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
        int programados = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
        int remaining = Math.max(0, total - entregados - programados);
        if (remaining <= 0) return 0;

        // capacidad mínima disponible en ruta (considerando reservas/ocupados)
        int rutaCapacidadMin = obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
        int yaAsignadoEnEnvio = envio == null || envio.getCantProductos() == null ? 0 : envio.getCantProductos();
        int disponibleRutaParaAsignar = Math.max(0, rutaCapacidadMin - yaAsignadoEnEnvio);
        if (disponibleRutaParaAsignar <= 0) return 0;

        // stock disponible en almacen origen (primer vuelo)
        VueloForAlgorithm primer = ruta.getVuelosOrdenados().getFirst();
        if (primer == null) return 0;
        Long idOrigen = primer.getIdAlmacenOrigen();

        AlmacenForAlgorithm almacenOrigen = null;
        if (almacenes != null) {
            for (AlmacenForAlgorithm a : almacenes) {
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
        } else if (Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
            disponibleOrigen = Integer.MAX_VALUE / 4;
        } else {
            int ocupado = almacenOrigen.getCapacidadOcupada() == null ? 0 : almacenOrigen.getCapacidadOcupada();
            int reserv = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
            disponibleOrigen = Math.max(0, ocupado - reserv);
        }
        if (disponibleOrigen <= 0) {
            // si origen sin stock, no se puede asignar
            return 0;
        }

        // cantidad asignable = min(remaining, disponibleRutaParaAsignar, disponibleOrigen)
        int asignable = (int) Math.min((long) remaining, Math.min((long) disponibleRutaParaAsignar, (long) disponibleOrigen));
        return Math.max(0, asignable);
    }

    /**
     * Añade al EnvioSolution una asignación del pedido por la cantidad indicada.
     * Actualiza:
     * - envio.cantProductos (sumando qty)
     * - envio.pedidosAAtenderTotalOParcialmente (agrega o suma si ya existe)
     * - reserva temporal en cada vuelo de la ruta (capacidadReservadaProductos += qty)
     * - reserva temporal en almacen origen (capacidadReservadaPorEnvios += qty) si no es infinito
     * - pedido.cantidadProductosProgramados += qty
     * <p>
     * Devuelve el envio actualizado (mismo objeto modificado).
     */
    private EnvioSolution anadirPedidoConCantidad(EnvioSolution envio,
                                                  PedidoForAlgorithm pedido,
                                                  int cantidad,
                                                  RutaADestino ruta,
                                                  List<AlmacenForAlgorithm> almacenes,
                                                  List<VueloForAlgorithm> vuelos) {
        if (envio == null) envio = new EnvioSolution();
        if (pedido == null || cantidad <= 0 || ruta == null || ruta.getVuelosOrdenados() == null || ruta.getVuelosOrdenados().isEmpty()) {
            loggingReport.appendReport("anadirPedidoConCantidad: entrada inválida, no se hace nada.");
            return envio;
        }

        // 1) Actualizar envio.cantProductos
        int prevCant = envio.getCantProductos() == null ? 0 : envio.getCantProductos();
        envio.setCantProductos(prevCant + cantidad);

        // 2) Asegurar lista de pedidos en envio y agregar o acumular
        if (envio.getPedidosAAtenderTotalOParcialmente() == null) {
            envio.setPedidosAAtenderTotalOParcialmente(new ArrayList<>());
        }
        boolean merged = false;
        for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
            if (Objects.equals(ps.getId(), pedido.getId())) {
                // sumar cantidades si ya existía
                int prev = ps.getCantidadASerAtendidaDelPedido() == null ? 0 : ps.getCantidadASerAtendidaDelPedido();
                ps.setCantidadASerAtendidaDelPedido(prev + cantidad);
                merged = true;
                break;
            }
        }
        if (!merged) {
            PedidoSolution nuevo = new PedidoSolution();
            nuevo.setId(pedido.getId());
            nuevo.setCantidadASerAtendidaDelPedido(cantidad);
            envio.getPedidosAAtenderTotalOParcialmente().add(nuevo);
        }

        // 3) Actualizar idAlmacenDestino y fechaHoraDestino (tomar del último vuelo de la ruta)
        VueloForAlgorithm ultimo = ruta.getVuelosOrdenados().getLast();
        if (ultimo != null) {
            envio.setIdAlmacenDestino(ultimo.getIdAlmacenDestino());
            envio.setFechaHoraDestino(ultimo.getFin());
        }

        // 4) Reservar en cada vuelo de la ruta incrementando capacidadReservadaProductos
        for (VueloForAlgorithm v : ruta.getVuelosOrdenados()) {
            if (v == null) continue;
            Integer prevRes = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
            v.setCapacidadReservadaProductos(prevRes + cantidad);
        }

        // 5) Reservar en almacen origen si no infinito (incrementar capacidadReservadaPorEnvios)
        VueloForAlgorithm primer = ruta.getVuelosOrdenados().getFirst();
        if (primer != null) {
            Long idOrigen = primer.getIdAlmacenOrigen();
            AlmacenForAlgorithm almacenOrigen = null;
            if (almacenes != null) {
                for (AlmacenForAlgorithm a : almacenes) {
                    if (a != null && Objects.equals(a.getId(), idOrigen)) {
                        almacenOrigen = a;
                        break;
                    }
                }
            }
            if (almacenOrigen != null && !Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
                int prev = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
                almacenOrigen.setCapacidadReservadaPorEnvios(prev + cantidad);
            }
        }

        // 6) Actualizar pedido.cantidadProductosProgramados
        int prevProg = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
        pedido.setCantidadProductosProgramados(prevProg + cantidad);

        loggingReport.appendReport(String.format("Se añadió pedido id=%d cantidad=%d al envío. Envío.cantProductos ahora=%d",
                pedido.getId(), cantidad, envio.getCantProductos()));

        return envio;
    }

    /**
     * Actualiza el estado temporal en memoria tras asignar un pedido a un envío.
     *
     * @param envio             Envío en construcción (ya actualizado por anadirPedidoConCantidad)
     * @param pedidoAsignado    Pedido que se acaba de asignar (puede ser null si la llamada es genérica)
     * @param ruta              Ruta seleccionada (sus vuelos ya tienen capacidadReservada actualizada)
     * @param almacenes         lista de almacenes (mutada por anadirPedidoConCantidad si aplica)
     * @param vuelos            lista de vuelos (mutada por anadirPedidoConCantidad si aplica)
     * @param pedidosPendientes lista mutable de pedidos pendientes para el destino; se modifica in-place (se eliminan satisfechos)
     * @return la nueva capacidad mínima disponible en la ruta (>=0)
     */
    private int actualizarEstadoTemporalEnMemoria(EnvioSolution envio,
                                                  PedidoForAlgorithm pedidoAsignado,
                                                  RutaADestino ruta,
                                                  List<AlmacenForAlgorithm> almacenes,
                                                  List<VueloForAlgorithm> vuelos,
                                                  List<PedidoForAlgorithm> pedidosPendientes) {
        loggingReport.appendReport("Actualizando estado temporal en memoria...");

        // 1) Recalcular la capacidad mínima disponible en la ruta (considerando reservas ya aplicadas)
        int capacidadDisponibleRuta = obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
        loggingReport.appendReport("Capacidad disponible recalculada en la ruta: " + capacidadDisponibleRuta);

        // 2) Remover pedidos completamente satisfechos de la lista de pendientes (mutamos pedidosPendientes in-place)
        if (pedidosPendientes != null && !pedidosPendientes.isEmpty()) {
            Iterator<PedidoForAlgorithm> it = pedidosPendientes.iterator();
            int removed = 0;
            while (it.hasNext()) {
                PedidoForAlgorithm p = it.next();
                if (p == null) {
                    it.remove();
                    removed++;
                    continue;
                }
                int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
                int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
                int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
                int remaining = Math.max(0, total - entregados - programados);
                if (remaining <= 0) {
                    loggingReport.appendReport("Pedido id=" + p.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
                    it.remove();
                    removed++;
                }
            }
            loggingReport.appendReport("Pedidos removidos de pendientes: " + removed + ". Pendientes ahora: " + pedidosPendientes.size());
        }

        // 3) Reordenar pedidosPendientes para favorecer urgencia y consolidación:
        //    - por instanteMaximoParaEntregar asc (más urgente primero)
        //    - luego por remaining asc (pedidos pequeños primero para que sea más fácil consolidar)
        if (pedidosPendientes != null && pedidosPendientes.size() > 1) {
            pedidosPendientes.sort((a, b) -> {
                // compare by deadline
                Instant da = a == null ? null : a.getInstanteMaximoParaEntregar();
                Instant db = b == null ? null : b.getInstanteMaximoParaEntregar();
                if (da != null && db != null) {
                    int cmp = da.compareTo(db);
                    if (cmp != 0) return cmp;
                } else if (da != null) {
                    return -1;
                } else if (db != null) {
                    return 1;
                }
                // tie-breaker: remaining qty ascending
                int ra = Math.max(0, (a.getCantidadProductosPedidos() == null ? 0 : a.getCantidadProductosPedidos())
                        - (a.getCantidadProductosEntregados() == null ? 0 : a.getCantidadProductosEntregados())
                        - (a.getCantidadProductosProgramados() == null ? 0 : a.getCantidadProductosProgramados()));
                int rb = Math.max(0, (b.getCantidadProductosPedidos() == null ? 0 : b.getCantidadProductosPedidos())
                        - (b.getCantidadProductosEntregados() == null ? 0 : b.getCantidadProductosEntregados())
                        - (b.getCantidadProductosProgramados() == null ? 0 : b.getCantidadProductosProgramados()));
                return Integer.compare(ra, rb);
            });
            loggingReport.appendReport("Pedidos pendientes reordenados por urgencia y size.");
        }

        // 4) (Opcional) - Recalcular otras métricas globales si las mantienes en memoria.
        // Por ejemplo, podrías recalcular una medida de demanda total por destino, uso de vuelos, etc.
        // (No hago nada extra aquí automáticamente, pero deja el lugar para agregar).

        loggingReport.appendReport("Estado temporal actualizado. capacidadDisponibleRuta=" + capacidadDisponibleRuta);
        return capacidadDisponibleRuta;
    }

    private List<PedidoForAlgorithm> removerPedidosSatisfechosOIrrelevantes(
            List<PedidoForAlgorithm> pedidosLocal,
            PedidoForAlgorithm ultimoPedidoAsignado,
            RutaADestino ruta,
            List<AlmacenForAlgorithm> almacenes,
            List<VueloForAlgorithm> vuelos) {

        if (pedidosLocal == null || pedidosLocal.isEmpty()) return Collections.emptyList();

        List<PedidoForAlgorithm> nuevaLista = new ArrayList<>();
        for (PedidoForAlgorithm p : pedidosLocal) {
            if (p == null) continue;

            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = Math.max(0, total - entregados - programados);

            // eliminar si ya satisfecho
            if (remaining <= 0) {
                loggingReport.appendReport("remover: pedido id=" + p.getId() + " removido (satisfecho).");
                continue;
            }

            // probar factibilidad local con la ruta actual (si falla, lo consideramos irrelevante para esta ruta)
            boolean factible = esFactibleAnadirPedidoAEnvio(p, /*envio*/ new EnvioSolution(), ruta, almacenes, vuelos);
            if (!factible) {
                loggingReport.appendReport("remover: pedido id=" + p.getId() + " no factible para la ruta actual -> se remueve del pool local");
                continue;
            }

            // si pasó las pruebas, lo mantenemos en la lista local
            nuevaLista.add(p);
        }

        return nuevaLista;
    }

    /**
     * Comprueba si hay al menos un pedido con remaining > 0.
     */
    private boolean hayPedidosPendientes(List<PedidoForAlgorithm> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) return false;
        for (PedidoForAlgorithm p : pedidos) {
            if (p == null) continue;
            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = Math.max(0, total - entregados - programados);
            if (remaining > 0) return true;
        }
        return false;
    }

    /**
     * Cuenta pedidos pendientes (útil para logs).
     */
    private int countPedidosPendientes(List<PedidoForAlgorithm> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) return 0;
        int c = 0;
        for (PedidoForAlgorithm p : pedidos) {
            if (p == null) continue;
            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = Math.max(0, total - entregados - programados);
            if (remaining > 0) c++;
        }
        return c;
    }

    /**
     * Elimina de la lista 'pedidos' los pedidos que estén completamente satisfechos (remaining == 0).
     * Retorna el número de pedidos removidos.
     */
    private int eliminarPedidosCompletamenteSatisfechos(List<PedidoForAlgorithm> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) return 0;
        int removed = 0;
        Iterator<PedidoForAlgorithm> it = pedidos.iterator();
        while (it.hasNext()) {
            PedidoForAlgorithm p = it.next();
            if (p == null) {
                it.remove();
                removed++;
                continue;
            }
            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = Math.max(0, total - entregados - programados);
            if (remaining <= 0) {
                it.remove();
                removed++;
            }
        }
        return removed;
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