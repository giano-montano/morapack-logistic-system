package pe.edu.pucp.inf.pddsbackend.algorithms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary // SI en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class LoggedHeuristicAlgorithmStrategy implements PlanificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(LoggedHeuristicAlgorithmStrategy.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");

//    @Bean
@Override
public PlanificationSolutionOutput planificar(PlanificationProblemInput parametrosAlgoritmo) {
    StringBuilder report = new StringBuilder();
    try {
        appendReport(report, "=== INICIO planificar() ===");
        appendReport(report, "Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Leer input (defensivo: crear copias mutables)
        List<PedidoForAlgorithm> pedidos = parametrosAlgoritmo.pedidos() == null
                ? new ArrayList<>()
                : new ArrayList<>(parametrosAlgoritmo.pedidos());

        List<VueloForAlgorithm> vuelos = parametrosAlgoritmo.vuelos() == null
                ? new ArrayList<>()
                : new ArrayList<>(parametrosAlgoritmo.vuelos());

        List<AlmacenForAlgorithm> almacenes = parametrosAlgoritmo.almacenes() == null
                ? new ArrayList<>()
                : new ArrayList<>(parametrosAlgoritmo.almacenes());

        appendReport(report, "Input sizes - pedidos: " + safeSize(pedidos) + ", vuelos: " + safeSize(vuelos) + ", almacenes: " + safeSize(almacenes));

        // Resultado: lista de envíos
        List<EnvioSolution> enviosResult = new ArrayList<>();

        // Map para acceso rápido a almacenes y vuelos por id
        Map<Long, AlmacenForAlgorithm> almacenById = new HashMap<>();
        for (AlmacenForAlgorithm a : almacenes) {
            almacenById.put(a.getId(), a);
        }
        appendReport(report, "almacenById size: " + almacenById.size());

        Map<Long, VueloForAlgorithm> vueloById = new HashMap<>();
        for (VueloForAlgorithm v : vuelos) {
            vueloById.put(v.getId(), v);
        }
        appendReport(report, "vueloById size: " + vueloById.size());

        // Adyacencia: vuelos salientes por almacen origen (para búsqueda de rutas)
        Map<Long, List<VueloForAlgorithm>> outgoing = new HashMap<>();
        for (VueloForAlgorithm v : vuelos) {
            outgoing.computeIfAbsent(v.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(v);
        }
        appendReport(report, "Outgoing map built. keys: " + outgoing.keySet().size());

        // Ordenar pedidos por instanteMaximoParaEntregar (deadline ascendente)
        pedidos.sort(Comparator.comparing(PedidoForAlgorithm::getInstanteMaximoParaEntregar));
        appendReport(report, "Pedidos ordenados por deadline.");

        // Heurística principal: para cada pedido, intentar asignar desde orígenes
        int pedidoIndex = 0;
        for (PedidoForAlgorithm pedido : pedidos) {
            pedidoIndex++;
            appendReport(report, "---- Pedido #" + pedidoIndex + " id=" + pedido.getId() + " destinoAlmacen=" + pedido.getIdAlmacenDestino() + " ----");

            int deliveredSoFar = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
            int remaining = pedido.getCantidadProductosPedidos() - deliveredSoFar;
            appendReport(report, "Pedido cantidades -> pedidas: " + pedido.getCantidadProductosPedidos() + ", entregadasAntes: " + deliveredSoFar + ", remainingInicial: " + remaining);

            if (remaining <= 0) {
                appendReport(report, "Pedido ya satisfecho. saltando.");
                continue;
            }

            // Build candidate origin list: infinite almacenes first
            List<AlmacenForAlgorithm> originCandidates = new ArrayList<>();
            for (AlmacenForAlgorithm a : almacenes) {
                if (Boolean.TRUE.equals(a.getEsInfinito())) originCandidates.add(a);
            }
            appendReport(report, "Origenes candidatos (infinito) encontrados: " + originCandidates.size());

            // For each candidate origin, try to find a route (BFS) to destination and allocate greedily.
            for (AlmacenForAlgorithm origin : originCandidates) {
                appendReport(report, "Probando origen id=" + origin.getId() + " esInfinito=" + origin.getEsInfinito());
                if (remaining <= 0) {
                    appendReport(report, "Remaining es 0 -> romper origen loop.");
                    break;
                }

                // BFS queue: each entry is una ruta (lista de vuelos)
                Queue<List<VueloForAlgorithm>> q = new ArrayDeque<>();

                // initialize with outgoing flights from origin que tengan capacidad > 0 y estén EN_ESPERA
                List<VueloForAlgorithm> startFlights = outgoing.getOrDefault(origin.getId(), Collections.emptyList());
                appendReport(report, "Vuelos de salida desde origen: " + startFlights.size());
                for (VueloForAlgorithm vf : startFlights) {
                    int vfAvail = vf.getCapacidadMaximaProductos() - (vf.getCapacidadOcupadaProductos() == null ? 0 : vf.getCapacidadOcupadaProductos());
                    if (vf.getEstado() == EstadoVuelo.EN_ESPERA && vfAvail > 0) {
                        List<VueloForAlgorithm> path = new ArrayList<>();
                        path.add(vf);
                        q.add(path);
                        appendReport(report, "  -> enqueue (poner en cola) vuelo inicio id=" + vf.getId() + " avail=" + vfAvail + " inicio=" + vf.getInicio() + " fin=" + vf.getFin());
                    } else {
                        appendReport(report, "  -> omitir vuelo inicio id=" + vf.getId() + " estado=" + vf.getEstado() + " avail=" + vfAvail);
                    }
                }

                // BFS search limit to avoid loops
                int maxPathsToTry = 2000;
                int tried = 0;
                boolean allocatedInThisOrigin = false;

                while (!q.isEmpty() && remaining > 0 && tried < maxPathsToTry) {
                    tried++;
                    List<VueloForAlgorithm> path = q.poll();
                    if (path == null || path.isEmpty()) continue;
                    VueloForAlgorithm last = path.get(path.size() - 1);

                    appendReport(report, "BFS try #" + tried + " pathLen=" + path.size() + " lastVueloId=" + last.getId());

                    try {
                        // Check if reached destination warehouse
                        if (Objects.equals(last.getIdAlmacenDestino(), pedido.getIdAlmacenDestino())) {
                            appendReport(report, "  -> path alcanza destino. Calculando capacidad mínima entre vuelos.");
                            int minFlightAvail = Integer.MAX_VALUE;
                            for (VueloForAlgorithm step : path) {
                                int avail = step.getCapacidadMaximaProductos() - (step.getCapacidadOcupadaProductos() == null ? 0 : step.getCapacidadOcupadaProductos());
                                minFlightAvail = Math.min(minFlightAvail, avail);
                                appendReport(report, "     vueloId=" + step.getId() + " avail=" + avail);
                            }

                            // compute origin available stock (infinite -> large number)
                            int originStock = Integer.MAX_VALUE;
                            if (!Boolean.TRUE.equals(origin.getEsInfinito())) {
                                originStock = (origin.getCapacidadOcupada() == null ? 0 : origin.getCapacidadOcupada());
                            }
                            appendReport(report, "     originStock (if finite): " + (originStock == Integer.MAX_VALUE ? "INFINITO" : originStock));
                            int canAllocate = Math.min(remaining, Math.min(minFlightAvail, originStock));
                            appendReport(report, "     remaining=" + remaining + ", minFlightAvail=" + minFlightAvail + ", canAllocate=" + canAllocate);

                            if (canAllocate > 0) {
                                // create EnvioSolution for this allocation
                                EnvioSolution envio = new EnvioSolution();
                                List<Long> idsVuelos = path.stream().map(vf -> vf.getId()).collect(Collectors.toList());
                                envio.setIdsVuelosATomar(idsVuelos);

                                PedidoSolution ps = new PedidoSolution();
                                ps.setId(pedido.getId());
                                ps.setCantidadASerAtendidaDelPedido(canAllocate);
                                envio.setPedidosAAtenderTotalOParcialmente(Arrays.asList(ps));

                                enviosResult.add(envio);
                                appendReport(report, "     -> Allocation creada. envio.vuelos=" + idsVuelos + ", pedidoId=" + pedido.getId() + ", cantidad=" + canAllocate);

                                // Update flights' occupied capacities (add canAllocate)
                                for (VueloForAlgorithm step : path) {
                                    if (step.getCapacidadOcupadaProductos() == null) step.setCapacidadOcupadaProductos(0);
                                    int before = step.getCapacidadOcupadaProductos();
                                    step.setCapacidadOcupadaProductos(step.getCapacidadOcupadaProductos() + canAllocate);
                                    appendReport(report, "        vueloId=" + step.getId() + " ocupadoAntes=" + before + " ocupadoDespues=" + step.getCapacidadOcupadaProductos());
                                }

                                // Update origin stock if finite
                                if (!Boolean.TRUE.equals(origin.getEsInfinito())) {
                                    if (origin.getCapacidadOcupada() == null) origin.setCapacidadOcupada(0);
                                    int beforeOrigin = origin.getCapacidadOcupada();
                                    origin.setCapacidadOcupada(origin.getCapacidadOcupada() - canAllocate);
                                    if (origin.getCapacidadOcupada() < 0) origin.setCapacidadOcupada(0);
                                    appendReport(report, "        originId=" + origin.getId() + " stockAntes=" + beforeOrigin + " stockDespues=" + origin.getCapacidadOcupada());
                                }

                                // Update pedido delivered count
                                if (pedido.getCantidadProductosEntregados() == null) pedido.setCantidadProductosEntregados(0);
                                int beforeDelivered = pedido.getCantidadProductosEntregados();
                                pedido.setCantidadProductosEntregados(pedido.getCantidadProductosEntregados() + canAllocate);
                                appendReport(report, "        pedidoId=" + pedido.getId() + " entregadoAntes=" + beforeDelivered + " entregadoDespues=" + pedido.getCantidadProductosEntregados());

                                remaining -= canAllocate;
                                allocatedInThisOrigin = true;
                            } else {
                                appendReport(report, "     -> no hay capacidad para asignar en este path a pesar de alcanzar destino");
                            }
                        } // end reached destination

                        // expand path: find outgoing flights desde last.getIdAlmacenDestino() con compatibilidad temporal
                        List<VueloForAlgorithm> nextFlights = outgoing.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
                        for (VueloForAlgorithm nf : nextFlights) {
                            if (nf.getEstado() != EstadoVuelo.EN_ESPERA) continue;
                            // time chaining: nf.inicio >= last.fin (allow equal)
                            if (nf.getInicio() != null && last.getFin() != null && nf.getInicio().isBefore(last.getFin())) continue;
                            int nfAvail = nf.getCapacidadMaximaProductos() - (nf.getCapacidadOcupadaProductos() == null ? 0 : nf.getCapacidadOcupadaProductos());
                            if (nfAvail <= 0) continue;
                            // avoid cycles: do not revisit a flight already in the path
                            boolean already = false;
                            for (VueloForAlgorithm used : path) {
                                if (Objects.equals(used.getId(), nf.getId())) { already = true; break; }
                            }
                            if (already) continue;

                            List<VueloForAlgorithm> newPath = new ArrayList<>(path);
                            newPath.add(nf);
                            q.add(newPath);
                            appendReport(report, "    -> expand: enqueue vuelo id=" + nf.getId() + " from almacen=" + nf.getIdAlmacenOrigen() + " to=" + nf.getIdAlmacenDestino() + " avail=" + nfAvail);
                        }
                    } catch (Exception innerEx) {
                        // Log but continue BFS
                        appendReport(report, "EXCEPCION durante BFS try #" + tried + " : " + innerEx.getMessage());
                        StringWriter sw = new StringWriter();
                        innerEx.printStackTrace(new PrintWriter(sw));
                        appendReport(report, sw.toString());
                        log.error("Excepción interna en BFS", innerEx);
                    }
                } // end BFS loop

                appendReport(report, "BFS finalizado para origen " + origin.getId() + " tried=" + tried + " remaining=" + remaining + " allocatedInThisOrigin=" + allocatedInThisOrigin);

                // if no allocation happened en este origen, seguimos con el siguiente
                if (!allocatedInThisOrigin) {
                    appendReport(report, "No se asignó desde este origen. Continuando a siguiente origen...");
                }
            } // end originCandidates loop

            if (remaining > 0) {
                appendReport(report, "Pedido id=" + pedido.getId() + " quedó parcialmente/no atendido. remaining=" + remaining);
            } else {
                appendReport(report, "Pedido id=" + pedido.getId() + " atendido completamente.");
            }
        } // end pedidos loop

        // Build output
        PlanificationSolutionOutput out = new PlanificationSolutionOutput(enviosResult);

        // Escribir reporte a archivo justo antes de devolver
        writeReportFile(report);
        appendReport(report, "Reporte escrito a disco. Preparando return. enviosResult.size=" + enviosResult.size());
        appendReport(report, "=== FIN planificar() ===");

        // También imprimimos el resumen final a consola y logger
        log.info("Planificación finalizada. envios encontrados: {}", enviosResult.size());
        System.out.println("Planificación finalizada. envios encontrados: " + enviosResult.size());

        return out;
    } catch (Exception ex) {
        appendReport(report, "***** EXCEPCION EN planificar(): " + ex.getMessage());
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        appendReport(report, sw.toString());
        log.error("Excepción en planificar", ex);

        // Intentar escribir el reporte (contendrá el stacktrace)
        try {
            writeReportFile(report);
            appendReport(report, "Reporte (con excepción) escrito a disco.");
        } catch (Exception writeEx) {
            // si falló escribir el archivo, al menos mostramos por consola
            log.error("Fallo al escribir reporte tras excepción: {}", writeEx.getMessage(), writeEx);
            System.err.println("Fallo al escribir reporte tras excepción: " + writeEx.getMessage());
        }

        // Si tu política es propagar la excepción, la re-lanzamos; sino retornamos vacío o parcial según prefieras.
        throw new RuntimeException("Error en planificar(), ver reporte en ./reports/ para más detalles.", ex);
    }
}

    // Helpers
    private static void appendReport(StringBuilder sb, String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = "[" + ts + "] " + msg;
        sb.append(line).append(System.lineSeparator());
        // También logueamos inmediatamente con logger y consola
        log.info(line);
        System.out.println(line);
    }

    private static int safeSize(Collection<?> c) { return c == null ? 0 : c.size(); }

    private static void writeReportFile(StringBuilder report) throws Exception {
        String fileName = "planification-report-" + LocalDateTime.now().format(TS_FMT) + ".log";
        Path dir = Paths.get("reports");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve(fileName);
        // Crear/Escribir (no append, se crea un archivo nuevo por ejecución)
        Files.write(file, report.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        log.info("Report saved to {}", file.toAbsolutePath());
        System.out.println("Report saved to " + file.toAbsolutePath());
    }
}
