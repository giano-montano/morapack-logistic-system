package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;

@Component
public class HeuristicAlgorithmStrategy implements PlanificationStrategy {

//    @Bean
    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) {

//        List<PedidoParaAlgoritmo> pedidos = parametrosAlgoritmo.pedidos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.pedidos());
//        List<VueloParaAlgoritmo> vuelos = parametrosAlgoritmo.vuelos() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.vuelos());
//        List<AlmacenParaAlgoritmo> almacenes = parametrosAlgoritmo.almacenes() == null? new ArrayList<>(): new ArrayList<>(parametrosAlgoritmo.almacenes());
//
//
//        // Resultado: lista de envíos
//        List<EnvioSolution> enviosResult = new ArrayList<>();
//
//        // Map para acceso rápido a almacenes y vuelos por idVuelo
//        Map<Long, AlmacenParaAlgoritmo> almacenById = new HashMap<>();
//        for (AlmacenParaAlgoritmo a : almacenes) almacenById.put(a.getId(), a);
//
//        Map<Long, VueloParaAlgoritmo> vueloById = new HashMap<>();
//        for (VueloParaAlgoritmo v : vuelos) vueloById.put(v.getId(), v);
//
//        // Adyacencia: vuelos salientes por almacen origen (para búsqueda de rutas)
//        Map<Long, List<VueloParaAlgoritmo>> outgoing = new HashMap<>();
//        for (VueloParaAlgoritmo v : vuelos) {
//            outgoing.computeIfAbsent(v.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(v);
//        }
//
//        // Ordenar pedidos por instanteMaximoParaEntregar (deadline ascendente)
//        pedidos.sort(Comparator.comparing(p -> p.getInstanteMaximoParaEntregar()));
//
//        // Heurística principal: para cada pedido, intentar asignar desde orígenes
//        for (PedidoParaAlgoritmo pedido : pedidos) {
//            int remaining = pedido.getCantidadProductosPedidos() - (pedido.getCantidadProductosEntregados() == null ?
//                    0 : pedido.getCantidadProductosEntregados());
//            if (remaining <= 0) continue;
//
//            // Build candidate origin list: infinite almacenes first, then finite with stock > 0
//            List<AlmacenParaAlgoritmo> originCandidates = new ArrayList<>();
//            for (AlmacenParaAlgoritmo a : almacenes) {
//                if (Boolean.TRUE.equals(a.getEsInfinito())) originCandidates.add(a);
//            }
////            for (AlmacenParaAlgoritmo a : almacenes) {
////                if (!Boolean.TRUE.equals(a.getEsInfinito())) {
////                    // assume capacidadOcupada representa stock disponible en almacén finito
////                    if (a.getCapacidadOcupada() != null && a.getCapacidadOcupada() > 0) originCandidates.add(a);
////                }
////            } // no diagramé capacidad reservada, esto sería peligroso porque podría matar otros envíos programados.
//
//            // For each candidate origin, try to find a route (BFS) to destination and allocate greedily.
//            // O sea hará búsqueda en amplitud con una cola. Es el árbol de destinos.
//            for (AlmacenParaAlgoritmo origin : originCandidates) {
//                if (remaining <= 0) break;
//                // BFS queue: each entry is a path (list of vuelos)
//                Queue<List<VueloParaAlgoritmo>> q = new ArrayDeque<>();
//
//                // initialize with outgoing flights from origin that have capacity > 0 and are en espera
//                List<VueloParaAlgoritmo> startFlights = outgoing.getOrDefault(origin.getId(), Collections.emptyList());
//                for (VueloParaAlgoritmo vf : startFlights) {
//                    if (vf.getEstado() == EstadoVuelo.EN_ESPERA && (vf.getCapacidadMaximaProductos() - (vf.getCapacidadOcupadaProductos() == null ? 0 : vf.getCapacidadOcupadaProductos()) > 0)) {
//                        List<VueloParaAlgoritmo> path = new ArrayList<>();
//                        path.add(vf);
//                        q.add(path);
//                    }
//                }
//
//                // BFS search limit to avoid loops
//                int maxPathsToTry = 2000;
//                int tried = 0;
//                boolean allocatedInThisOrigin = false;
//
//                while (!q.isEmpty() && remaining > 0 && tried < maxPathsToTry) {
//                    tried++;
//                    List<VueloParaAlgoritmo> path = q.poll();
//                    if (path == null || path.isEmpty()) continue;
//                    VueloParaAlgoritmo last = path.get(path.size() - 1);
//
//                    // Check time chaining: we ensured on extension; now check if reached destination warehouse
//                    if (Objects.equals(last.getIdAlmacenDestino(), pedido.getIdAlmacenDestino() )) {
//                        // compute min available capacity across flights in path
//                        int minFlightAvail = Integer.MAX_VALUE;
//                        for (VueloParaAlgoritmo step : path) {
//                            int avail = step.getCapacidadMaximaProductos() - (step.getCapacidadOcupadaProductos() == null ? 0 : step.getCapacidadOcupadaProductos());
//                            minFlightAvail = Math.min(minFlightAvail, avail);
//                        }
//
//                        // compute origin available stock (infinite -> large number)
//                        int originStock = Integer.MAX_VALUE;
//                        if (!Boolean.TRUE.equals(origin.getEsInfinito())) {
//                            originStock = (origin.getCapacidadOcupada() == null ? 0 : origin.getCapacidadOcupada());
//                        }
//
//                        int canAllocate = Math.min(remaining, Math.min(minFlightAvail, originStock));
//                        if (canAllocate > 0) {
//                            // create EnvioSolution for this allocation
//                            EnvioSolution envio = new EnvioSolution();
//                            List<Long> idsVuelos = path.stream().map(vf -> vf.getId()).collect(Collectors.toList());
//                            envio.setIdsVuelosATomar(idsVuelos);
//
//                            PedidoSolution ps = new PedidoSolution();
//                            ps.setId(pedido.getId());
//                            ps.setCantidadASerAtendidaDelPedido(canAllocate);
//                            envio.setPedidosAAtenderTotalOParcialmente(Arrays.asList(ps));
//
//                            enviosResult.add(envio);
//
//                            // Update flights' occupied capacities
//                            for (VueloParaAlgoritmo step : path) {
//                                if (step.getCapacidadOcupadaProductos() == null) step.setCapacidadOcupadaProductos(  0 ) ;
//                                step.setCapacidadOcupadaProductos(step.getCapacidadOcupadaProductos() + 1);
////                                step.capacidadOcupadaProductos += canAllocate;
//                            }
//
//                            // Update origin stock if finite
//                            if (!Boolean.TRUE.equals(origin.getEsInfinito())) {
//                                if (origin.getCapacidadOcupada() == null) origin.setCapacidadOcupada(0) ;
//                                origin.setCapacidadOcupada(origin.getCapacidadOcupada() - 1);
////                                origin.capacidadOcupada -= canAllocate;
//                                if (origin.getCapacidadOcupada() < 0) origin.setCapacidadOcupada(0);
//                            }
//
//                            // Update pedido delivered count
//                            if (pedido.getCantidadProductosEntregados() == null) pedido.setCantidadProductosEntregados(0);
//                            pedido.setCantidadProductosEntregados(pedido.getCantidadProductosEntregados() + canAllocate);
////                            pedido.cantidadProductosEntregados += canAllocate;
//                            remaining -= canAllocate;
//                            allocatedInThisOrigin = true;
//
//                            // For this prototype we create one envio per found path/allocation.
//                            // Continue trying to fill remaining (might reuse same or other origins).
//                        }
//                        // else, even if path reached dest, cannot allocate -> continue searching other paths
//                    }
//
//                    // expand path: find outgoing flights from last.idAlmacenDestino with compatible time
//                    List<VueloParaAlgoritmo> nextFlights = outgoing.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
//                    for (VueloParaAlgoritmo nf : nextFlights) {
//                        if (nf.getEstado() != EstadoVuelo.EN_ESPERA) continue;
//                        // time chaining: nf.inicio should be >= last.fin (allow equal)
//                        if (nf.getInicio() != null && last.getFin() != null && nf.getInicio().isBefore(last.getFin())) continue;
//                        // capacity must be > 0
//                        int nfAvail = nf.getCapacidadMaximaProductos() - (nf.getCapacidadOcupadaProductos() == null ? 0 : nf.getCapacidadOcupadaProductos());
//                        if (nfAvail <= 0) continue;
//                        // avoid cycles: do not revisit a flight already in the path
//                        boolean already = false;
//                        for (VueloParaAlgoritmo used : path) {
//                            if (Objects.equals(used.getId(), nf.getId())) { already = true; break; }
//                        }
//                        if (already) continue;
//
//                        // append and queue new path
//                        List<VueloParaAlgoritmo> newPath = new ArrayList<>(path);
//                        newPath.add(nf);
//                        q.add(newPath);
//                    }
//                } // end BFS loop
//
//                // if allocated something from this origin, we may try it again (e.g., if remaining still >0)
//                // but to keep it simple, we continue to next origin if no more capacity in flights from this origin.
//                if (!allocatedInThisOrigin) {
//                    // no allocation possible from this origin -> try next origin
//                }
//            } // end originCandidates loop
//
//            // After trying all origins, if remaining > 0 we leave pedido partially/unfulfilled (provisional)
//        } // end pedidos loop
//
//        // Build output
//        SalidaProblemaPlanificacion out = new SalidaProblemaPlanificacion(enviosResult);
//        return out;
        return null;
    }
}
