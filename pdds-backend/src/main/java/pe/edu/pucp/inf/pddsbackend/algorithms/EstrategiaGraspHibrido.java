package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
//import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Component
@Primary // Si en algún lugar no se especifica clase/estrategia concreta, esta se implementará por defecto.
public class EstrategiaGraspHibrido extends EstrategiaPlanificacion {

    //    long semilla = new Random().nextLong();
//    Random generadorAleatorio = new Random(semilla);
//    private LoggingReport loggingReport = new LoggingReport();
    private EstadoGlobal estadoGlobal;

    private static final double ALPHA_RUTAS = 0.8;
    private static final double ALPHA_PEDIDOS = 0.5; // por poner algo xd
    private static final int ITERACIONES_MAXIMAS_PRIMER_GRASP = 50000;
    private static final double UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA = 0.8;
    private static final double UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA = 0.2;

    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) {
        // Inicialización
        estadoGlobal = entrada.getEstadoGlobalCopia();
        setSemilla(entrada.getSemilla());
        // Obtener rutas a solo almacenes de destino y a partir de almacenes infinitos o no infinitos con al menos 1 producto.
        List<LinkedList<Long>> // Una clase para ruta que sea lo mismo que una lista de vuelos? No la necesité hasta ahora
                rutasPosibles = // recordar que no hay pedidos para almacenes infinitos hasta este punto (los filtramos antes).
                estadoGlobal.generarRutasParaPedidosPendientes(); // <- chamba de Axel
        estadoGlobal.crearIndiceIdsRutasPorAlmacenDestino(rutasPosibles); // a partir de aquí tenemos el tan deseado índice.
        // asignar puntajes a pedidos pendientes.
        List<Pedido> pedidosPendientes = estadoGlobal.obtenerPedidosPendientesDeEntregaYProgram();
        Map<Pedido, Double> puntajesPorPedido = asignarPuntajesPedidos(pedidosPendientes); // <- chamba de Axel

        // Ciclo principal
        int numIteraciones;
        try {
            numIteraciones = realizarCicloDePedidos(rutasPosibles, puntajesPorPedido);
        } catch (Exception ex) {
            SalidaProblemaPlanificacion solution = new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones());
            solution.setHuboErrorEjecucion(true);
            solution.setError(ex.getMessage());
            return solution;
        }
        Bitacora.escribir("Planificación finalizada. Iteraciones GRASP realizadas: " + numIteraciones +
                ". Programaciones creadas: " + estadoGlobal.getProgramaciones().size());
        SalidaProblemaPlanificacion solution =
                new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones());
        if (estadoGlobal.hayPedidosPendientesPorProgramar()) {
            Bitacora.escribir("NO SE LOGRÓ PLANIFICAR TODO, COLAPSO LOGÍSTICO!!!!!!!!!!!!");
            solution.setColapsado(true);
        }
        return solution;
    }

    private int realizarCicloDePedidos(
            List<LinkedList<Long>> rutasPosibles,
            Map<Pedido, Double> puntajesPorPedido
    ) {
        int numIteraciones = 0;
        while (estadoGlobal.hayPedidosPendientesPorProgramar() && numIteraciones < ITERACIONES_MAXIMAS_PRIMER_GRASP) {
            Bitacora.escribir("planificar: Iteración %d: quedan %d pedidos pendientes", numIteraciones, estadoGlobal.contarPedidosPendientes());

            List<Programacion> programacionesConstruidasGrasp =
                    elegirYProgramarParaPedido(rutasPosibles, puntajesPorPedido);

            if (programacionesConstruidasGrasp == null) {
                Bitacora.escribir("GRASP no pudo hacer una programación más, finalizando ciclo.");
                break;
            }
//            // Añadir el envío a la solución
//            estadoGlobal.anadirVariasProgramacionesSolucion(programacionesConstruidasGrasp);
            Bitacora.escribir("Programaciones solución añadidas: " + programacionesConstruidasGrasp);

            // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
            boolean removed = estadoGlobal.eliminarPedidoYaSatisfecho(programacionesConstruidasGrasp.get(0).getIdPedido());
            if (removed)
                Bitacora.escribir("Se eliminó el pedido " + programacionesConstruidasGrasp.get(0).getIdPedido() +
                        " por estar totalmente programado / atendido.");

            // Guardar reporte parcial si quieres (puedes ajustar la frecuencia) no m lo borres
//                if( iter % 100 == 0)
//                    loggingReport.writeReportFile("grasp-report-iter-" + iter+"-");

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
        List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido = filtrarRutasSegunPlazoPedido(pedidoElegido, rutasConDestinoCompartido);
        while (numProductosPorAtender > numProductosAtendidosPedido) { // Programar para todo el pedido.

            Programacion programacionHecha =
                    construccionGraspParaUnaProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);

            if (programacionHecha == null) return null;

            programaciones.add(programacionHecha);
            estadoGlobal.anadirProgramacionSolucion(programacionHecha); // mutar estado global!

            numProductosAtendidosPedido++;
        }
        Bitacora.escribir("programaciones: "+ programaciones);
        return programaciones;
    }

    private Programacion construccionGraspParaUnaProgramacion(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido
    ) {
        Producto productoAgarrado = null;
        LinkedList<Long> rutaElegida = null;
        boolean rclValido;
        do { // Medio rara esta lógica... Pero creo que es necesaria
            Map<LinkedList<Long>, Double> puntajesPorRuta
                    = asignarPuntajesRutas(rutasFiltradasSegunPlazoPedido, pedidoElegido); // <- chamba de Axel
            List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta);
            if (rclRutasCandidatas.isEmpty()) {
                Bitacora.escribir("construccionGRASPParaUnaRuta: RCL de rutas vacía");
                return null; // Lo más probable es que las rutas filtradas estén aberradas o nulas, no hay más que hacer.
            }
            rclValido = true;
            Bitacora.escribir("construccionGRASPParaUnaRuta: Rutas que entraron a la RCL:  \n" + rclRutasCandidatas);
            while (!rclRutasCandidatas.isEmpty()) { // Solo para asegurar ruta factible
                rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, false);
                boolean esRutaValida = estadoGlobal.rutaTieneCapacidadEnEstadoActual(rutaElegida, pedidoElegido); // capacidades, no plazos.
                if (!esRutaValida) {
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue; // el productoAgarrado no se define, queda en null aún.
                }
                productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
                // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
                if (productoAgarrado == null) { //throw new IllegalStateException("¡¿Cómo?!"); // xd
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue;
                }
                break;
            }
            if (productoAgarrado == null) rclValido = false; // quiere decir que en toda la RCL no consiguió nada
        } while (!rclValido && !rutasFiltradasSegunPlazoPedido.isEmpty());
        if (productoAgarrado == null) return null;
        if (!productoAgarrado.isExiste()) { // OJO: Alteramos estado!!! Se supone que entrará solo si es nuevo.
            estadoGlobal.anadirProducto(productoAgarrado);
        }
        return new Programacion(pedidoElegido.getId(), productoAgarrado.getUuid(), rutaElegida);
    }

    private Producto escogerProductoEnRuta(LinkedList<Long> ruta, Pedido pedido) {
        Almacen almacenOrigen = estadoGlobal.getAlmacenes().get(
                estadoGlobal.getVuelos().get(ruta.getFirst()).getIdAlmacenOrigen());
        Almacen almacenDestino = estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        if (almacenOrigen == null)
            throw new IllegalStateException("¿Cómo llegó un almacén nulo aquí?"); // no debería pasar...
        if (almacenOrigen.isEsInfinito()) { // Es un almacén no intermedio
            return new Producto(almacenOrigen.getId(), ruta);
        }
        // A partir de aquí, si es un almacén intermedio. Veremos sus prods en el futuro a ver cuál agarramos.
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
        List<LinkedList<Long>> rutas =
                rutasConDestinoCompartido.stream()
                        .filter(ruta -> estadoGlobal
                                .getVuelos().get(ruta.getLast())
                                .entregariaPedidoEnPlazoReal(pedido)

                        ).toList();
        return rutas;
    }


    /**
     * Evalúa todas las rutas candidatas y devuelve un map ruta -> score (mayor = mejor).
     */ // PUEDE MEJORARSE, O USAR LA FUNCIÓN FITNESS DE AXEL
    private Map<LinkedList<Long>, Double> asignarPuntajesRutas(
            List<LinkedList<Long>> rutas,
            Pedido pedidoElegido
    ) {
        // Pesos (ajustables)
        final double wArrival = 0.35;
        final double wLegs = 0.25;
        final double wCapacity = 0.25;
        final double wDemand = 0.15;

        Map<LinkedList<Long>, Double> rawArrival = new HashMap<>();
        Map<LinkedList<Long>, Integer> rawLegs = new HashMap<>();
        Map<LinkedList<Long>, Integer> rawCapacity = new HashMap<>();
        Map<LinkedList<Long>, Integer> rawDemand = new HashMap<>();

        // Precalcular demanda pendiente por almacen destino (sum of remaining quantities)
        Map<Long, Integer> demandaPorDestino = new HashMap<>();
        for (Pedido p : estadoGlobal.getPedidos().values()) {
            if (p == null) continue;
            if (p.getCantidadProductosPendientes() <= 0) continue;
            demandaPorDestino.merge(p.getIdAlmacenDestino(), p.getCantidadProductosPendientes(),
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
        for (LinkedList<Long> r : rutas) {
            if (r == null || r.isEmpty()) {
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
            int legs = r.size();
            rawLegs.put(r, legs);
            minLegs = Math.min(minLegs, legs);
            maxLegs = Math.max(maxLegs, legs);

            // arrival: uso el fin del último vuelo
            Vuelo ultimo = estadoGlobal.getVuelos().get(
                    r.getLast()
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
            for (Long idV : r) {
//                if (v == null) continue;
//                int max = v.getCapacidadMaximaProductos() == null ? 0 : v.getCapacidadMaximaProductos();
//                int occ = v.getCapacidadOcupadaProductos() == null ? 0 : v.getCapacidadOcupadaProductos();
//                int res = v.getCapacidadReservadaProductos() == null ? 0 : v.getCapacidadReservadaProductos();
//                int avail = max - occ - res;
                Vuelo vActual = estadoGlobal.getVuelos().get(idV);
                if (vActual == null) continue;
                int avail = vActual.getCapacidadSinOcupar();
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
        Map<LinkedList<Long>, Double> scores = new HashMap<>();
        for (LinkedList<Long> r : rutas) {
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
     * Garantiza que, para cada almacén destino no infinito (si existe alguna ruta para él),
     * al menos la mejor ruta (por score) quede incluida en la RCL resultante.
     *
     * @param scores mapa ruta -> score (mayor = mejor)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */ // DEUDA TÉCNICA, CREO QUE DA IGUAL LO DE AL MENOS UNA PARA CADA ALMACÉN
    private List<LinkedList<Long>> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
            Map<LinkedList<Long>, Double> scores) {

        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        // 0. obtener alpha (usar campo de clase o fallback)
        double alphaLocal = this.ALPHA_RUTAS; // asumir campo de clase
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

        // 2) Umbral clásico RCL (score mayor = mejor)
        double threshold = max - alphaLocal * (max - min);

        // 3) RCL inicial por umbral (LinkedHashSet para evitar duplicados y mantener determinismo)
        Set<LinkedList<Long>> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Encontrar la mejor ruta por destino (ignorando destinos infinitos)
        Map<Long, LinkedList<Long>> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();

        for (Map.Entry<LinkedList<Long>, Double> e : scores.entrySet()) {
            LinkedList<Long> ruta = e.getKey();
            Double score = e.getValue() == null || e.getValue().isNaN() ? Double.NEGATIVE_INFINITY : e.getValue();

            if (ruta == null || ruta.isEmpty()) continue;

            long ultimoVueloId = ruta.getLast();

            // obtener objeto vuelo
            Vuelo vueloUltimo = estadoGlobal.getVuelos().get(ultimoVueloId);
            if (vueloUltimo == null) {
//                if (loggingReport != null)
                Bitacora.escribir(
                        "construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
                continue;
            }

            // obtener id almacen destino desde el vuelo y then almacen
            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
            Almacen alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
            if (alm == null) {
//                if (loggingReport != null)
                Bitacora.escribir(
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
        for (Map.Entry<Long, LinkedList<Long>> be : bestByDestino.entrySet()) {
            LinkedList<Long> bestRuta = be.getValue();
            if (bestRuta != null) rclSet.add(bestRuta);
        }

        // 6) Ordenar por score descendente y devolver
        List<LinkedList<Long>> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(scores.getOrDefault(b, Double.NEGATIVE_INFINITY),
                scores.getOrDefault(a, Double.NEGATIVE_INFINITY)));
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

    //     * Evalúa mérito de pedidos candidatos para llenar un envío.
//     *
//     * @param pedidos lista de pedidos candidatos (pendientes) — solo los que tienen idAlmacenDestino == destino de la ruta
//     * @param envio   envío parcialmente construido (puede estar vacío al inicio)
//     * @param almacenes lista de almacenes (para estimar stock / orígenes infinitos)
//     * @param vuelos  lista de vuelos (no usada fuertemente aquí; opcional para extensiones)
//     * @return mapa pedido -> score (mayor = mejor)
//     */ // PODRÍA MEJORARSE CON LO DE AXEL,
    private Map<Pedido, Double> asignarPuntajesPedidos(
            List<Pedido> pedidosConDestino
    ) {

        Map<Pedido, Double> scores = new HashMap<>();
        if (pedidosConDestino == null || pedidosConDestino.isEmpty()) return scores;

        // Pesos (ajustables)
        final double wUrgency = 0.50;
        final double wSize = 0.20;
        final double wSupply = 0.30;

        Instant now = Instant.now();

        // Precompute remaining demand for each pedido
        Map<Pedido, Integer> remainingMap = new HashMap<>();
        int maxRemaining = 0;
        for (Pedido p : pedidosConDestino) {
            remainingMap.put(p, p.getCantidadProductosPendientes());
            maxRemaining = Math.max(maxRemaining, p.getCantidadProductosPendientes());
        }
        if (maxRemaining == 0) maxRemaining = 1; // evita división por cero

        // Precompute simple supply availability across almacenes (sum of available stocks)
        // Treat any infinite almacén as huge availability -> mark haveInfinite = true
        boolean haveInfinite = false;
        long totalAvailableAcrossAllOrigens = 0L;
        for (Almacen a : estadoGlobal.getAlmacenes().values()) {
            if (a == null) continue;
            if (a.isEsInfinito()) {
                haveInfinite = true;
                break;
            } else {
//                int ocupado = a.getCapacidadOcupada() == null ? 0 : a.getCapacidadOcupada();
//                int reserv = a.getCapacidadReservadaPorEnvios() == null ? 0 : a.getCapacidadReservadaPorEnvios();
//                int avail = Math.max(0, ocupado - reserv); disponible
                int disponible = a.getCapacidadSinOcupar();
                totalAvailableAcrossAllOrigens += disponible;
            }
        }

        // Raw component maps
        Map<Pedido, Double> rawUrgency = new HashMap<>();
        Map<Pedido, Double> rawSize = new HashMap<>();
        Map<Pedido, Double> rawSupply = new HashMap<>();

        double minUrg = Double.POSITIVE_INFINITY, maxUrg = Double.NEGATIVE_INFINITY;
        double minSize = Double.POSITIVE_INFINITY, maxSize = Double.NEGATIVE_INFINITY;
        double minSup = Double.POSITIVE_INFINITY, maxSup = Double.NEGATIVE_INFINITY;

        for (Pedido p : pedidosConDestino) {
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
        for (Pedido p : pedidosConDestino) {
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
    private List<Pedido> construirRCLDePedidos(Map<Pedido, Double> scores, double alpha) {
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

        List<Pedido> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Opcional: ordenar por score descendente (mejor primero)
        rcl.sort((a, b) -> Double
                .compare(scores.getOrDefault(b, 0.0), scores.getOrDefault(a, 0.0)));

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