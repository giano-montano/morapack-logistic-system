package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.springframework.stereotype.Component;
// (import removed duplicate)
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobalMutableProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.PedidoParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaProgramadaParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.AlmacenParaAlgoritmo;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;

import java.util.*;

@Component
public class TabuSearchAlgorithmStrategy implements PlanificationStrategy {

    // Heuristic strategy kept out for now; Tabu builds its own initial solution.

    // Parámetros del algoritmo Tabu Search optimizados para ALMACORP
    private static final int MAX_ITERATIONS = 500;
    private static final int TABU_LIST_SIZE = 30;
    private static final int MAX_NO_IMPROVEMENT = 50;
    private static final int NEIGHBORHOOD_SIZE = 15;

    // Constantes del dominio ALMACORP
    private static final double DELIVERY_REWARD = 100.0;
    private static final double ROUTE_EFFICIENCY_FACTOR = -2.0;

    private LoggingReport loggingReport = new LoggingReport();
    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) {
    // 1) Clonar entrada: una copia para trabajar y una base limpia para reconstruir al final
    EntradaProblemaPlanificacion baseline = deepCopyEntrada(parametrosAlgoritmo);
    EntradaProblemaPlanificacion working = deepCopyEntrada(parametrosAlgoritmo);

    // Construir la mesa de trabajo (estado global mutable) sobre la copia de trabajo
    EstadoGlobalMutableProblemaPlanificacion mesaTrabajo = EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(working);
        mesaTrabajo.setLoggingReport(loggingReport);
        // 2) Generar rutas candidatas por BFS (ya implementado en mesaTrabajo)
        //    Nota: estas rutas NO tienen pedido ni cantidad aún.
        List<RutaProgramadaParaAlgoritmo> rutasCandidatas = mesaTrabajo.generarTodasRutasPosiblesATodosDestinos();
        // 3) Construir una solución inicial heurística (BFS greedy modularizada)
        construirSolucionInicialHeuristica(mesaTrabajo, rutasCandidatas);

        // 4) Evaluar solución inicial y preparar estructuras Tabu
        double currentScore = evaluarMesa(mesaTrabajo);
        double bestScore = currentScore;
        List<RutaProgramadaParaAlgoritmo> bestRutas = copiarRutas(mesaTrabajo.getRutasSolucionQueGeneraAlgoritmo());

        Deque<String> tabuQueue = new ArrayDeque<>();
        Set<String> tabuSet = new HashSet<>();
        int noImprove = 0;

        // 5) Bucle principal de Tabu Search
        for (int iter = 0; iter < MAX_ITERATIONS && noImprove < MAX_NO_IMPROVEMENT; iter++) {
            // Generar vecindario de movimientos candidatos
            List<MoveCandidate> vecinos = generarVecindario(mesaTrabajo, rutasCandidatas, NEIGHBORHOOD_SIZE);
            if (vecinos.isEmpty()) break; // no hay más movimientos posibles

            MoveCandidate mejorMovimiento = null;
            double mejorScore = Double.NEGATIVE_INFINITY;

            for (MoveCandidate mv : vecinos) {
                String moveKey = mv.tabuKey();
                boolean esTabu = tabuSet.contains(moveKey);

                if (!aplicarMovimiento(mesaTrabajo, mv)) {
                    continue; // no se pudo aplicar (no factible, etc.)
                }
                double score = evaluarMesa(mesaTrabajo);
                deshacerMovimiento(mesaTrabajo, mv); // revertir para comparar limpiamente

                boolean aspiracion = score > bestScore; // criterio de aspiración simple
                if (esTabu && !aspiracion) {
                    continue; // descartar movimientos tabu que no superen lo mejor
                }

                if (score > mejorScore) {
                    mejorScore = score;
                    mejorMovimiento = mv;
                }
            }

            if (mejorMovimiento == null) {
                break; // no hubo movimiento aplicable aceptable
            }

            // Aplicar el mejor movimiento seleccionado
            if (!aplicarMovimiento(mesaTrabajo, mejorMovimiento)) {
                break; // inconsistencia inesperada
            }

            currentScore = evaluarMesa(mesaTrabajo);

            // Actualizar lista Tabu
            String key = mejorMovimiento.tabuKey();
            if (!key.isEmpty()) {
                tabuQueue.addLast(key);
                tabuSet.add(key);
                while (tabuQueue.size() > TABU_LIST_SIZE) {
                    String old = tabuQueue.removeFirst();
                    tabuSet.remove(old);
                }
            }

            // Actualizar mejor global si corresponde
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestRutas = copiarRutas(mesaTrabajo.getRutasSolucionQueGeneraAlgoritmo());
                noImprove = 0;
            } else {
                noImprove++;
            }
        }

    // 6) Reconstruir una mesa con la mejor solución encontrada usando la BASE limpia (no mutada)
    EstadoGlobalMutableProblemaPlanificacion mesaFinal = EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(baseline);
        mesaFinal.setLoggingReport(loggingReport);
        for (RutaProgramadaParaAlgoritmo r : bestRutas) {
            mesaFinal.anadirRutaSolucion(r);
        }

        // 7) Persistir reporte Tabu
        try {
            loggingReport.writeReportFile("tabu-report-final");
        } catch (Exception e) {
            // si falla la escritura, lo dejamos registrado en el propio reporte/console
            loggingReport.appendReport("Error al escribir tabu-report-final: " + e.getMessage());
        }

        // 8) Construir salida con las rutas de la mejor solución
        return SalidaProblemaPlanificacion.builder()
                .rutasProgramadasParaSatisfacerTodoPedido(mesaFinal.getRutasSolucionQueGeneraAlgoritmo())
                .huboErrorEjecucion(false)
                .colapsado(false)
                .build();
    }


    // Encuentra la primera ruta (en el orden en que fueron generadas por el BFS) que:
    // - termine en el almacén destino del pedido
    // - sea factible según el estado actual de la mesa
    private RutaProgramadaParaAlgoritmo encontrarPrimeraRutaBFSFactibleParaPedido(
            PedidoParaAlgoritmo pedido,
            List<RutaProgramadaParaAlgoritmo> rutasCandidatas,
            EstadoGlobalMutableProblemaPlanificacion mesaTrabajo
    ) {
        if (pedido == null || rutasCandidatas == null || rutasCandidatas.isEmpty()) return null;
        Long destPedido = pedido.getIdAlmacenDestino();
        for (RutaProgramadaParaAlgoritmo ruta : rutasCandidatas) {
            if (ruta == null || ruta.getIdsVuelosEnOrden() == null || ruta.getIdsVuelosEnOrden().isEmpty()) continue;
            // último vuelo de la ruta
            List<Long> ids = ruta.getIdsVuelosEnOrden();
            Long idUltimoVuelo = ids.get(ids.size() - 1);
            VueloParaAlgoritmo ultimo = mesaTrabajo.getVueloFromId(idUltimoVuelo);
            if (ultimo == null) continue;
            if (!Objects.equals(ultimo.getIdAlmacenDestino(), destPedido)) continue;

            // factibilidad conservadora en el estado actual
            if (mesaTrabajo.esFactibleLlevarPedidoEnRuta(pedido.getId(), ruta)) {
                return ruta;
            }
        }
        return null;
    }

    // Cálculo conservador de la cantidad asignable en una ruta actualmente factible.
    private int decidirCantidadAsignable(PedidoParaAlgoritmo pedido,
                                         RutaProgramadaParaAlgoritmo ruta,
                                         EstadoGlobalMutableProblemaPlanificacion mesaTrabajo) {
        if (pedido == null || ruta == null) return 0;
        int remaining = pedido.getCantidadRestanteDeEntregaYProgram();
        if (remaining <= 0) return 0;

        // capacidad mínima entre los vuelos de la ruta (considerando ocupados actuales)
        int capacidadMinRuta = mesaTrabajo.obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
        if (capacidadMinRuta <= 0) return 0;

        // stock disponible en el almacén de origen del primer vuelo
    List<Long> ids = ruta.getIdsVuelosEnOrden();
    Long idPrimerVuelo = ids.get(0);
        VueloParaAlgoritmo primer = mesaTrabajo.getVueloFromId(idPrimerVuelo);
        if (primer == null) return 0;
        long idAlmacenOrigen = primer.getIdAlmacenOrigen();
        int disponibleOrigen;
        {
            // buscar el almacén origen en la mesa
            // Nota: almacenes es un HashMap<Long, AlmacenParaAlgoritmo>
            var alm = mesaTrabajo.getAlmacenes().get(idAlmacenOrigen);
            if (alm == null) {
                disponibleOrigen = 0;
            } else if (alm.isEsInfinito()) {
                disponibleOrigen = Integer.MAX_VALUE / 4; // suficiente para no limitar
            } else {
                // usamos la capacidad ocupada como "stock" aproximado disponible de salida
                disponibleOrigen = Math.max(0, alm.getCapacidadOcupada());
            }
        }

        int asignable = Math.min(remaining, Math.min(capacidadMinRuta, disponibleOrigen));
        return Math.max(0, asignable);
    }
    
    // Construye solución inicial heurística (BFS greedy) asignando rutas factibles hasta agotar pedidos
    private void construirSolucionInicialHeuristica(EstadoGlobalMutableProblemaPlanificacion mesaTrabajo,
                                                    List<RutaProgramadaParaAlgoritmo> rutasCandidatas) {
        // Ordenar pedidos por deadline (si existe) para dar prioridad básica
        List<PedidoParaAlgoritmo> pedidos = new ArrayList<>(mesaTrabajo.getPedidos().values());
        pedidos.sort(Comparator.comparing(PedidoParaAlgoritmo::getInstanteMaximoParaEntregar, Comparator.nullsLast(Comparator.naturalOrder())));

        for (PedidoParaAlgoritmo pedido : pedidos) {
            if (pedido == null) continue;
            if (pedido.getCantidadRestanteDeEntregaYProgram() <= 0) continue;

            int intentos = 0;
            int maxIntentos = Math.max(1, rutasCandidatas.size());
            while (pedido.getCantidadRestanteDeEntregaYProgram() > 0 && intentos < maxIntentos) {
                intentos++;
                RutaProgramadaParaAlgoritmo ruta = encontrarPrimeraRutaBFSFactibleParaPedido(pedido, rutasCandidatas, mesaTrabajo);
                if (ruta == null) break;
                int cantidad = decidirCantidadAsignable(pedido, ruta, mesaTrabajo);
                if (cantidad <= 0) break;
                RutaProgramadaParaAlgoritmo rutaAsignada = new RutaProgramadaParaAlgoritmo(
                        new LinkedList<>(ruta.getIdsVuelosEnOrden()),
                        pedido.getId(),
                        cantidad
                );
                // clamp defensivo: ajustar a la mínima capacidad actual por si cambió entre decisiones
                int capMinActual = mesaTrabajo.obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaAsignada);
                if (capMinActual <= 0) break;
                if (rutaAsignada.getCantidadTotalOParcial() > capMinActual) {
                    rutaAsignada.setCantidadTotalOParcial(capMinActual);
                }
                mesaTrabajo.anadirRutaSolucion(rutaAsignada);
                //if (mesaTrabajo.eliminarPedidoYaSatisfecho(pedido.getId())) break;
            }
        }
    }

    // Evaluación de la solución actual en la mesa: recompensa por entregas y penalización por ineficiencia de rutas
    private double evaluarMesa(EstadoGlobalMutableProblemaPlanificacion mesaTrabajo) {
        double delivered = 0.0;
        double legs = 0.0;
        List<RutaProgramadaParaAlgoritmo> rutas = mesaTrabajo.getRutasSolucionQueGeneraAlgoritmo();
        if (rutas != null) {
            for (RutaProgramadaParaAlgoritmo r : rutas) {
                if (r == null) continue;
                delivered += Math.max(0, r.getCantidadTotalOParcial());
                List<Long> ids = r.getIdsVuelosEnOrden();
                legs += (ids == null) ? 0 : Math.max(0, ids.size());
            }
        }
        return DELIVERY_REWARD * delivered + ROUTE_EFFICIENCY_FACTOR * legs;
    }

    private List<RutaProgramadaParaAlgoritmo> copiarRutas(List<RutaProgramadaParaAlgoritmo> rutas) {
        if (rutas == null) return new ArrayList<>();
        List<RutaProgramadaParaAlgoritmo> copia = new ArrayList<>(rutas.size());
        for (RutaProgramadaParaAlgoritmo r : rutas) {
            if (r == null) continue;
            copia.add(new RutaProgramadaParaAlgoritmo(new LinkedList<>(r.getIdsVuelosEnOrden()), r.getIdPedidoAsociado(), r.getCantidadTotalOParcial()));
        }
        return copia;
    }

    private String firmaRuta(RutaProgramadaParaAlgoritmo ruta) {
        if (ruta == null || ruta.getIdsVuelosEnOrden() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Long id : ruta.getIdsVuelosEnOrden()) {
            if (sb.length() > 0) sb.append('-');
            sb.append(id);
        }
        return sb.toString();
    }

    private EntradaProblemaPlanificacion deepCopyEntrada(EntradaProblemaPlanificacion src) {
        if (src == null) return EntradaProblemaPlanificacion.builder()
                .almacenes(new HashMap<>())
                .vuelos(new HashMap<>())
                .pedidos(new HashMap<>())
                .parametrosOpcionalesPersonalizados(new ArrayList<>())
                .build();

        HashMap<Long, AlmacenParaAlgoritmo> almacenes = new HashMap<>();
        if (src.getAlmacenes() != null) {
            for (var e : src.getAlmacenes().entrySet()) {
                AlmacenParaAlgoritmo a = e.getValue();
                almacenes.put(e.getKey(), a == null ? null : a.clone());
            }
        }

        HashMap<Long, VueloParaAlgoritmo> vuelos = new HashMap<>();
        if (src.getVuelos() != null) {
            for (var e : src.getVuelos().entrySet()) {
                VueloParaAlgoritmo v = e.getValue();
                if (v == null) { vuelos.put(e.getKey(), null); continue; }
                VueloParaAlgoritmo copy = new VueloParaAlgoritmo(
                        v.getId(), v.getInicio(), v.getFin(),
                        v.getIdAlmacenOrigen(), v.getIdAlmacenDestino(),
                        v.getCapacidadMaximaProductos(), v.getCapacidadOcupadaProductos(),
                        null, null
                );
                vuelos.put(e.getKey(), copy);
            }
        }

        HashMap<Long, PedidoParaAlgoritmo> pedidos = new HashMap<>();
        if (src.getPedidos() != null) {
            for (var e : src.getPedidos().entrySet()) {
                PedidoParaAlgoritmo p = e.getValue();
                if (p == null) { pedidos.put(e.getKey(), null); continue; }
                PedidoParaAlgoritmo copy = new PedidoParaAlgoritmo(
                        p.getId(), p.getIdAlmacenDestino(), p.getCantidadProductosPedidos(),
                        p.getCantidadProductosEntregados(), p.getInstanteRegistro(), p.getInstanteMaximoParaEntregar(),
                        null, p.getEstado()
                );
                pedidos.put(e.getKey(), copy);
            }
        }

        return EntradaProblemaPlanificacion.builder()
                .almacenes(almacenes)
                .vuelos(vuelos)
                .pedidos(pedidos)
                .parametrosOpcionalesPersonalizados(src.getParametrosOpcionalesPersonalizados() == null ? new ArrayList<>() : new ArrayList<>(src.getParametrosOpcionalesPersonalizados()))
                .build();
    }

    // Movimiento: añadir una ruta nueva o reemplazar una existente por otra
    private static class MoveCandidate {
        final RutaProgramadaParaAlgoritmo rutaAEliminar; // puede ser null si es una adición
        final RutaProgramadaParaAlgoritmo rutaAAñadir;   // ruta completa con pedido y cantidad
        MoveCandidate(RutaProgramadaParaAlgoritmo remove, RutaProgramadaParaAlgoritmo add) {
            this.rutaAEliminar = remove;
            this.rutaAAñadir = add;
        }
        String tabuKey() {
            // usar pedidoId|firmaDeRutaAñadida si existe; si no, pedidoId|firmaEliminada
            if (rutaAAñadir != null) {
                // usar firma compacta de IDs de vuelos
                return rutaAAñadir.getIdPedidoAsociado() + "|" + buildSignature(rutaAAñadir);
            }
            if (rutaAEliminar != null) {
                return rutaAEliminar.getIdPedidoAsociado() + "|" + buildSignature(rutaAEliminar);
            }
            return "";
        }

        private String buildSignature(RutaProgramadaParaAlgoritmo r) {
            if (r == null) return "";
            List<Long> ids = r.getIdsVuelosEnOrden();
            if (ids == null || ids.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Long id : ids) {
                if (sb.length() > 0) sb.append('-');
                sb.append(id);
            }
            return sb.toString();
        }
    }

    private boolean aplicarMovimiento(EstadoGlobalMutableProblemaPlanificacion mesa,
                                      MoveCandidate mv) {
        try {
            if (mv.rutaAEliminar != null) {
                mesa.eliminarRutaSolucion(mv.rutaAEliminar);
            }
            if (mv.rutaAAñadir != null) {
                // Validación suave: factibilidad ya fue calculada al crear el movimiento
                // Clamp defensivo por si la capacidad cambió entre la evaluación y la aplicación
                int capMinActual = mesa.obtenerCapacidadMaxParaTodosVuelosEnRuta(mv.rutaAAñadir);
                if (capMinActual <= 0) throw new IllegalStateException("capacidad actual mínima 0");
                if (mv.rutaAAñadir.getCantidadTotalOParcial() > capMinActual) {
                    mv.rutaAAñadir.setCantidadTotalOParcial(capMinActual);
                }
                mesa.anadirRutaSolucion(mv.rutaAAñadir);
            }
            return true;
        } catch (Exception e) {
            // rollback best-effort
            try {
                if (mv.rutaAAñadir != null) {
                    mesa.eliminarRutaSolucion(mv.rutaAAñadir);
                }
            } catch (Exception ignore) {}
            try {
                if (mv.rutaAEliminar != null) {
                    mesa.anadirRutaSolucion(mv.rutaAEliminar);
                }
            } catch (Exception ignore) {}
            return false;
        }
    }

    private void deshacerMovimiento(EstadoGlobalMutableProblemaPlanificacion mesa,
                                    MoveCandidate mv) {
        try {
            if (mv.rutaAAñadir != null) {
                mesa.eliminarRutaSolucion(mv.rutaAAñadir);
            }
        } catch (Exception ignore) {}
        try {
            if (mv.rutaAEliminar != null) {
                mesa.anadirRutaSolucion(mv.rutaAEliminar);
            }
        } catch (Exception ignore) {}
    }

    private List<MoveCandidate> generarVecindario(EstadoGlobalMutableProblemaPlanificacion mesa,
                                                  List<RutaProgramadaParaAlgoritmo> rutasCandidatas,
                                                  int maxVecinos) {
        List<MoveCandidate> vecinos = new ArrayList<>();

        // 1) Intentar reemplazos de rutas existentes por alternativas (mejores/otras)
        List<RutaProgramadaParaAlgoritmo> actuales = new ArrayList<>(mesa.getRutasSolucionQueGeneraAlgoritmo());
        for (RutaProgramadaParaAlgoritmo rExistente : actuales) {
            if (vecinos.size() >= maxVecinos) break;
            long pedidoId = rExistente.getIdPedidoAsociado();
            // buscar una ruta candidata distinta que llegue al mismo destino del pedido
            PedidoParaAlgoritmo pedido = mesa.getPedidos().get(pedidoId);
            if (pedido == null) continue; // pudo ser removido del mapa
            Long destPedido = pedido.getIdAlmacenDestino();
            String firmaExistente = firmaRuta(rExistente);

            // Remover temporalmente para evaluar alternativas
            try {
                mesa.eliminarRutaSolucion(rExistente);
            } catch (Exception e) {
                continue;
            }

            for (RutaProgramadaParaAlgoritmo cand : rutasCandidatas) {
                if (vecinos.size() >= maxVecinos) break;
                if (cand == null || cand.getIdsVuelosEnOrden() == null || cand.getIdsVuelosEnOrden().isEmpty()) continue;
                // último vuelo llega al destino del pedido
                List<Long> ids = cand.getIdsVuelosEnOrden();
                VueloParaAlgoritmo ult = mesa.getVueloFromId(ids.get(ids.size() - 1));
                if (ult == null || !Objects.equals(ult.getIdAlmacenDestino(), destPedido)) continue;
                // evitar misma firma
                String firmaCand = firmaRuta(cand);
                if (firmaCand.equals(firmaExistente)) continue;
                // factible y cantidad asignable en estado actual (sin la ruta existente)
                if (!mesa.esFactibleLlevarPedidoEnRuta(pedidoId, cand)) continue;
                int cantidad = decidirCantidadAsignable(pedido, cand, mesa);
                if (cantidad <= 0) continue;

                RutaProgramadaParaAlgoritmo nueva = new RutaProgramadaParaAlgoritmo(new LinkedList<>(cand.getIdsVuelosEnOrden()), pedidoId, cantidad);
                vecinos.add(new MoveCandidate(rExistente, nueva));
            }

            // restaurar la ruta existente tras generar candidatos
            try {
                mesa.anadirRutaSolucion(rExistente);
            } catch (Exception ignore) {}

            if (vecinos.size() >= maxVecinos) break;
        }

        // 2) Intentar adiciones para pedidos con remanente
        // if (vecinos.size() < maxVecinos) {
        //     for (PedidoParaAlgoritmo p : new ArrayList<>(mesa.getPedidos().values())) {
        //         if (vecinos.size() >= maxVecinos) break;
        //         if (p == null) continue;
        //         if (p.getCantidadRestanteDeEntregaYProgram() <= 0) continue;
        //         RutaProgramadaParaAlgoritmo cand = encontrarPrimeraRutaBFSFactibleParaPedido(p, rutasCandidatas, mesa);
        //         if (cand == null) continue;
        //         int cantidad = decidirCantidadAsignable(p, cand, mesa);
        //         if (cantidad <= 0) continue;
        //         RutaProgramadaParaAlgoritmo nueva = new RutaProgramadaParaAlgoritmo(new LinkedList<>(cand.getIdsVuelosEnOrden()), p.getId(), cantidad);
        //         vecinos.add(new MoveCandidate(null, nueva));
        //     }
        // }

        return vecinos;
    }
        
}
