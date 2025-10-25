package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

/**
 * Utilidades específicas para el algoritmo Tabu Search
 */
public class TabuSearchUtils {

//     /**
//      * Valida si una ruta es factible considerando:
//      * - Capacidades de los vuelos
//      * - Ventanas de tiempo
//      * - Estado de los vuelos
//      */
//     public static boolean isRouteValid(List<VueloForAlgorithm> route, int quantity, Instant deadline) {
//         if (route == null || route.isEmpty()) return false;
        
//         Instant currentTime = null;
        
//         for (VueloForAlgorithm vuelo : route) {
//             // Verificar estado del vuelo
//             if (vuelo.getEstado() != EstadoVuelo.EN_ESPERA) {
//                 return false;
//             }
            
//             // Verificar capacidad disponible
//             int capacidadDisponible = vuelo.getCapacidadMaximaProductos() - 
//                     (vuelo.getCapacidadOcupadaProductos() != null ? vuelo.getCapacidadOcupadaProductos() : 0);
//             if (capacidadDisponible < quantity) {
//                 return false;
//             }
            
//             // Verificar ventana de tiempo
//             if (currentTime != null && vuelo.getInicio() != null && vuelo.getInicio().isBefore(currentTime)) {
//                 return false;
//             }
            
//             currentTime = vuelo.getFin();
//         }
        
//         // Verificar deadline
//         if (deadline != null && currentTime != null && currentTime.isAfter(deadline)) {
//             return false;
//         }
        
//         return true;
//     }

//     /**
//      * Calcula el costo total de una ruta (basado en número de vuelos y tiempo)
//      */
//     public static double calculateRouteCost(List<VueloForAlgorithm> route) {
//         if (route == null || route.isEmpty()) return Double.MAX_VALUE;
        
//         double cost = 0.0;
        
//         // Costo por número de vuelos (transferencias son costosas)
//         cost += route.size() * 10.0;
        
//         // Costo por tiempo total de viaje
//         if (route.get(0).getInicio() != null && route.get(route.size() - 1).getFin() != null) {
//             long travelTimeHours = (route.get(route.size() - 1).getFin().toEpochMilli() - 
//                                   route.get(0).getInicio().toEpochMilli()) / (1000 * 60 * 60);
//             cost += travelTimeHours * 0.5;
//         }
        
//         return cost;
//     }

//     /**
//      * Encuentra todos los almacenes que pueden servir como orígenes válidos
//      */
//     public static List<AlmacenForAlgorithm> findValidOrigins(List<AlmacenForAlgorithm> almacenes) {
//         return almacenes.stream()
//                 .filter(a -> Boolean.TRUE.equals(a.getEsInfinito()) || 
//                            (a.getCapacidadOcupada() != null && a.getCapacidadOcupada() > 0))
//                 .sorted((a1, a2) -> {
//                     // Priorizar almacenes infinitos
//                     if (Boolean.TRUE.equals(a1.getEsInfinito()) && !Boolean.TRUE.equals(a2.getEsInfinito())) {
//                         return -1;
//                     }
//                     if (!Boolean.TRUE.equals(a1.getEsInfinito()) && Boolean.TRUE.equals(a2.getEsInfinito())) {
//                         return 1;
//                     }
//                     return 0;
//                 })
//                 .collect(Collectors.toList());
//     }

//     /**
//      * Calcula la distancia heurística entre dos almacenes (basada en códigos de ciudad)
//      */
//     public static int calculateDistanceHeuristic(AlmacenForAlgorithm origen, AlmacenForAlgorithm destino) {
//         if (origen == null || destino == null) return Integer.MAX_VALUE;
//         if (Objects.equals(origen.getId(), destino.getId())) return 0;
        
//         // Heurística simple: diferencia en códigos de ciudad
//         String codigo1 = origen.getCodigoCiudadEn4Letras();
//         String codigo2 = destino.getCodigoCiudadEn4Letras();
        
//         if (codigo1 == null || codigo2 == null) return 1;
//         if (codigo1.equals(codigo2)) return 0;
        
//         return 1; // Todas las ciudades diferentes tienen distancia 1
//     }

//     /**
//      * Genera estadísticas de una solución para análisis
//      */
//     public static SolutionStats calculateStats(List<EnvioSolution> envios, 
//                                              List<PedidoForAlgorithm> pedidos) {
//         int totalPedidos = pedidos.size();
//         int pedidosAtendidos = 0;
//         int productosEntregados = 0;
//         int totalProductosPedidos = pedidos.stream()
//                 .mapToInt(p -> p.getCantidadProductosPedidos())
//                 .sum();
        
//         Set<Long> pedidosConEnvio = new HashSet<>();
//         int totalVuelos = 0;
//         int enviosConRetraso = 0;
        
//         for (EnvioSolution envio : envios) {
//             totalVuelos += envio.getIdsVuelosATomar().size();
//             productosEntregados += envio.getCantProductos();
            
//             for (PedidoSolution ps : envio.getPedidosAAtenderTotalOParcialmente()) {
//                 pedidosConEnvio.add(ps.getId());
                
//                 // Verificar retraso
//                 PedidoForAlgorithm pedido = pedidos.stream()
//                         .filter(p -> p.getId().equals(ps.getId()))
//                         .findFirst().orElse(null);
                
//                 if (pedido != null && envio.getFechaHoraDestino() != null &&
//                     envio.getFechaHoraDestino().isAfter(pedido.getInstanteMaximoParaEntregar())) {
//                     enviosConRetraso++;
//                 }
//             }
//         }
        
//         pedidosAtendidos = pedidosConEnvio.size();
        
//         double porcentajePedidosAtendidos = (double) pedidosAtendidos / totalPedidos * 100;
//         double porcentajeProductosEntregados = (double) productosEntregados / totalProductosPedidos * 100;
//         double promedioVuelosPorEnvio = envios.isEmpty() ? 0 : (double) totalVuelos / envios.size();
        
//         return new SolutionStats(
//                 totalPedidos,
//                 pedidosAtendidos,
//                 porcentajePedidosAtendidos,
//                 totalProductosPedidos,
//                 productosEntregados,
//                 porcentajeProductosEntregados,
//                 envios.size(),
//                 totalVuelos,
//                 promedioVuelosPorEnvio,
//                 enviosConRetraso
//         );
//     }

//     /**
//      * Verifica la factibilidad completa de una solución
//      */
//     public static List<String> validateSolution(List<EnvioSolution> envios, 
//                                                List<VueloForAlgorithm> vuelos,
//                                                List<PedidoForAlgorithm> pedidos) {
//         List<String> errores = new ArrayList<>();
        
//         Map<Long, VueloForAlgorithm> vueloMap = vuelos.stream()
//                 .collect(Collectors.toMap(VueloForAlgorithm::getId, v -> v));
        
//         Map<Long, Integer> capacidadUsada = new HashMap<>();
        
//         for (int i = 0; i < envios.size(); i++) {
//             EnvioSolution envio = envios.get(i);
            
//             // Verificar que los vuelos existen
//             for (Long vueloId : envio.getIdsVuelosATomar()) {
//                 if (!vueloMap.containsKey(vueloId)) {
//                     errores.add("Envío " + i + ": Vuelo " + vueloId + " no existe");
//                 }
//             }
            
//             // Verificar capacidades
//             for (Long vueloId : envio.getIdsVuelosATomar()) {
//                 int usada = capacidadUsada.getOrDefault(vueloId, 0) + envio.getCantProductos();
//                 capacidadUsada.put(vueloId, usada);
                
//                 VueloForAlgorithm vuelo = vueloMap.get(vueloId);
//                 if (vuelo != null && usada > vuelo.getCapacidadMaximaProductos()) {
//                     errores.add("Envío " + i + ": Vuelo " + vueloId + " excede capacidad");
//                 }
//             }
            
//             // Verificar secuencia temporal
//             List<VueloForAlgorithm> rutaVuelos = envio.getIdsVuelosATomar().stream()
//                     .map(vueloMap::get)
//                     .filter(Objects::nonNull)
//                     .collect(Collectors.toList());
            
//             for (int j = 1; j < rutaVuelos.size(); j++) {
//                 VueloForAlgorithm anterior = rutaVuelos.get(j - 1);
//                 VueloForAlgorithm actual = rutaVuelos.get(j);
                
//                 if (anterior.getFin() != null && actual.getInicio() != null &&
//                     actual.getInicio().isBefore(anterior.getFin())) {
//                     errores.add("Envío " + i + ": Secuencia temporal inválida entre vuelos " + 
//                               anterior.getId() + " y " + actual.getId());
//                 }
//             }
//         }
        
//         return errores;
//     }

//     /**
//      * Genera un reporte detallado de la solución
//      */
//     public static String generateSolutionReport(List<EnvioSolution> envios, 
//                                                List<PedidoForAlgorithm> pedidos,
//                                                List<VueloForAlgorithm> vuelos) {
//         StringBuilder report = new StringBuilder();
        
//         SolutionStats stats = calculateStats(envios, pedidos);
//         List<String> errores = validateSolution(envios, vuelos, pedidos);
        
//         report.append("=== REPORTE DE SOLUCIÓN TABU SEARCH ===\n\n");
        
//         report.append("ESTADÍSTICAS GENERALES:\n");
//         report.append(String.format("- Total de pedidos: %d\n", stats.totalPedidos));
//         report.append(String.format("- Pedidos atendidos: %d (%.1f%%)\n", 
//                 stats.pedidosAtendidos, stats.porcentajePedidosAtendidos));
//         report.append(String.format("- Productos entregados: %d/%d (%.1f%%)\n", 
//                 stats.productosEntregados, stats.totalProductosPedidos, stats.porcentajeProductosEntregados));
//         report.append(String.format("- Total de envíos: %d\n", stats.totalEnvios));
//         report.append(String.format("- Total de vuelos utilizados: %d\n", stats.totalVuelos));
//         report.append(String.format("- Promedio vuelos por envío: %.1f\n", stats.promedioVuelosPorEnvio));
//         report.append(String.format("- Envíos con retraso: %d\n", stats.enviosConRetraso));
        
//         if (!errores.isEmpty()) {
//             report.append("\nERRORES DE VALIDACIÓN:\n");
//             for (String error : errores) {
//                 report.append("- ").append(error).append("\n");
//             }
//         } else {
//             report.append("\n✓ Solución válida - sin errores detectados\n");
//         }
        
//         return report.toString();
//     }

//     /**
//      * Clase para encapsular estadísticas de una solución
//      */
//     public static class SolutionStats {
//         public final int totalPedidos;
//         public final int pedidosAtendidos;
//         public final double porcentajePedidosAtendidos;
//         public final int totalProductosPedidos;
//         public final int productosEntregados;
//         public final double porcentajeProductosEntregados;
//         public final int totalEnvios;
//         public final int totalVuelos;
//         public final double promedioVuelosPorEnvio;
//         public final int enviosConRetraso;

//         public SolutionStats(int totalPedidos, int pedidosAtendidos, double porcentajePedidosAtendidos,
//                            int totalProductosPedidos, int productosEntregados, double porcentajeProductosEntregados,
//                            int totalEnvios, int totalVuelos, double promedioVuelosPorEnvio, int enviosConRetraso) {
//             this.totalPedidos = totalPedidos;
//             this.pedidosAtendidos = pedidosAtendidos;
//             this.porcentajePedidosAtendidos = porcentajePedidosAtendidos;
//             this.totalProductosPedidos = totalProductosPedidos;
//             this.productosEntregados = productosEntregados;
//             this.porcentajeProductosEntregados = porcentajeProductosEntregados;
//             this.totalEnvios = totalEnvios;
//             this.totalVuelos = totalVuelos;
//             this.promedioVuelosPorEnvio = promedioVuelosPorEnvio;
//             this.enviosConRetraso = enviosConRetraso;
//         }
//     }

//     /**
//      * Configuración parametrizable para el algoritmo Tabu Search
//      */
//     public static class TabuSearchConfig {
//         public final int maxIterations;
//         public final int tabuListSize;
//         public final int maxNoImprovement;
//         public final int neighborhoodSize;
//         public final double fitnessWeightOrders;
//         public final double fitnessWeightDelay;
//         public final double fitnessWeightEfficiency;

//         public TabuSearchConfig(int maxIterations, int tabuListSize, int maxNoImprovement, 
//                                int neighborhoodSize, double fitnessWeightOrders, 
//                                double fitnessWeightDelay, double fitnessWeightEfficiency) {
//             this.maxIterations = maxIterations;
//             this.tabuListSize = tabuListSize;
//             this.maxNoImprovement = maxNoImprovement;
//             this.neighborhoodSize = neighborhoodSize;
//             this.fitnessWeightOrders = fitnessWeightOrders;
//             this.fitnessWeightDelay = fitnessWeightDelay;
//             this.fitnessWeightEfficiency = fitnessWeightEfficiency;
//         }

//         public static TabuSearchConfig getDefault() {
//             return new TabuSearchConfig(1000, 50, 100, 20, 100.0, -50.0, -1.0);
//         }

//         public static TabuSearchConfig getFast() {
//             return new TabuSearchConfig(100, 20, 50, 10, 100.0, -50.0, -1.0);
//         }

//         public static TabuSearchConfig getThorough() {
//             return new TabuSearchConfig(5000, 100, 500, 50, 100.0, -50.0, -1.0);
//         }
//     }
 }
