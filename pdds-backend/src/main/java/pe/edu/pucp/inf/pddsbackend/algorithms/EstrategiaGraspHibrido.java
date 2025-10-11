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
        Map<Pedido, Double> puntajesPorPedido= asignarPuntajesPedidos(pedidosPendientes); // <- chamba de Axel

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
        if(estadoGlobal.hayPedidosPendientesPorProgramar()){
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
        while(estadoGlobal.hayPedidosPendientesPorProgramar() && numIteraciones < ITERACIONES_MAXIMAS_PRIMER_GRASP){
            Bitacora.escribir("planificar: Iteración %d: quedan %d pedidos pendientes", numIteraciones, estadoGlobal.contarPedidosPendientes());

            List<Programacion> programacionesConstruidasGrasp =
                    elegirYProgramarParaPedido(rutasPosibles, puntajesPorPedido);

            if (programacionesConstruidasGrasp == null) {
                Bitacora.escribir("GRASP no pudo hacer una programación más, finalizando ciclo.");
                break;
            }
            // Añadir el envío a la solución
            estadoGlobal.anadirVariasProgramacionesSolucion(programacionesConstruidasGrasp);
            Bitacora.escribir("Programaciones solución añadidas: " + programacionesConstruidasGrasp);

            // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
            boolean removed = estadoGlobal.eliminarPedidoYaSatisfecho(programacionesConstruidasGrasp.get(0).getIdPedido());
            if (removed)
                Bitacora.escribir("Se eliminó el pedido "+programacionesConstruidasGrasp.get(0).getIdPedido()+
                        " por estar totalmente programado / atendido.");

            // Guardar reporte parcial si quieres (puedes ajustar la frecuencia) no m lo borres
//                if( iter % 100 == 0)
//                    loggingReport.writeReportFile("grasp-report-iter-" + iter+"-");

            numIteraciones++;
        }
        return numIteraciones;
    }

    private List<Programacion> elegirYProgramarParaPedido(
            List<LinkedList<Long>>rutas,
            Map<Pedido, Double> puntajesPorPedido
    ){
        if(rutas.isEmpty()) return null;
        List<Pedido> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido, ALPHA_PEDIDOS);
        Pedido pedidoElegido =
                seleccionarPedidoDesdeRCL(rclPedidosCandidatos,puntajesPorPedido,generadorAleatorio,false);

        List<Programacion> programaciones = realizarCicloVariosProductosDePedido(pedidoElegido);

        if(programaciones == null || programaciones.isEmpty()) return null;
        return programaciones;
    }

    private List<Programacion> realizarCicloVariosProductosDePedido(Pedido pedidoElegido) {
        List<Programacion> programaciones = new LinkedList<>();
        int numProductosPorAtender = pedidoElegido.getCantidadProductosPendientes();
        int numProductosAtendidosPedido = 0;
        List<LinkedList<Long>> rutasConDestinoCompartido = obtenerRutasConMismoDestinoQuePedido(pedidoElegido);
        List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido = filtrarRutasSegunPlazoPedido(pedidoElegido,rutasConDestinoCompartido);
        while (numProductosPorAtender > numProductosAtendidosPedido) { // Programar para todo el pedido.

            Programacion programacionHecha =
                    construccionGraspParaUnaProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);

            if(programacionHecha == null) return null;
            programaciones.add( programacionHecha ) ;
            numProductosAtendidosPedido++;
        }
        return programaciones; // La persistencia al estado global la haremos afuera para mejor claridad.
    }

    private Programacion construccionGraspParaUnaProgramacion(
            List<LinkedList<Long>>rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido
    ){
        Map<LinkedList<Long>, Double>
                puntajesPorRuta = asignarPuntajesRutas(rutasFiltradasSegunPlazoPedido); // <- chamba de Axel
        List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta);
        if ( rclRutasCandidatas.isEmpty()) {
            Bitacora.escribir("construccionGRASPParaUnaRuta: RCL de rutas vacía");
            return null;
        }
        Bitacora.escribir("construccionGRASPParaUnaRuta: Rutas que entraron a la RCL:  \n" + rclRutasCandidatas /*PrettyPrinter.printList(rclRutasCandidatas)*/);
        Producto productoAgarrado = null; LinkedList<Long> rutaElegida = null;
        while(!rclRutasCandidatas.isEmpty()) { // Solo para asegurar ruta factible
            rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, false);
            boolean esRutaValida = estadoGlobal.rutaEsFactibleEnEstadoActual(rutaElegida);
            if(!esRutaValida){
                rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                continue;
            }
            productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
            // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
            if(productoAgarrado == null ) throw new IllegalStateException("¡¿Cómo?!"); // xd
            break;
        }
        assert productoAgarrado != null;
        return new Programacion(pedidoElegido.getId(), productoAgarrado.getUuid(), rutaElegida);
    }

    private Producto escogerProductoEnRuta(LinkedList<Long> ruta, Pedido pedido) {
        Almacen almacenOrigen = estadoGlobal.getAlmacenes().get(
                estadoGlobal.getVuelos().get(ruta.getFirst()).getIdAlmacenOrigen());
        Almacen almacenDestino = estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        if(almacenOrigen == null) throw new IllegalStateException("¿Cómo llegó un almacén nulo aquí?"); // no debería pasar...
        if(almacenOrigen.isEsInfinito()){ // Es un almacén no intermedio
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
        if( !productosIntercontinentales.isEmpty() && !productosContinentales.isEmpty()){
            Double aleatorio = generadorAleatorio.nextDouble(); // Sale de 0 a 1
            Double umbralIntercontinental = pedido.isIntercontinentalAhora()?
                    UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA : UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA;
            if(aleatorio<umbralIntercontinental){
                productoAAgarrar = productosIntercontinentales.get(0); // el primerito nomás, cualquiera...
                // ¿o deberíamos hacerlo de forma más inteligente? (ejm: sacar de un continente cercano) <- pto. de mejora
                return productoAAgarrar;
            }else{
                productoAAgarrar = productosContinentales.get(0);
                return productoAAgarrar;
            }
        }else{
            productoAAgarrar = !productosContinentales.isEmpty()?
                    productosContinentales.get(0):
                    productosIntercontinentales.get(0);
            return productoAAgarrar;
        }
    }


    private List<LinkedList<Long>> obtenerRutasConMismoDestinoQuePedido(Pedido pedido){
        Almacen almacen = estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        List<LinkedList<Long>> rutasConDestinoCompartido =
                estadoGlobal.getRutasPorIdAlmacenDestino().get(almacen.getId());
        return rutasConDestinoCompartido;
    }

    private List<LinkedList<Long>> filtrarRutasSegunPlazoPedido(Pedido pedido, List<LinkedList<Long>> rutasConDestinoCompartido){
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
    private Map<LinkedList<Long>, Double> asignarPuntajesRutas(List<LinkedList<Long>> rutas) {
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
            if (p == null ) continue;
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
                if(vActual == null) continue;
                int avail = vActual.getCapacidadSinOcupar();
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
        Map<LinkedList<Long>, Double> scores = new HashMap<>();
        for (LinkedList<Long> r : rutas) {
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
        if (Double.isInfinite(min) || Double.isInfinite(max) || Double.isNaN(min) || Double.isNaN(max)) return Collections.emptyList();

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
//                if (loggingReport != null) Bitacora.escribir("construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
//                continue;
//            }
//
//// 2) obtener id almacen destino desde el vuelo y luego el almacen
//            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
//            AlmacenParaAlgoritmo alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
//            if (alm == null) {
//                if (loggingReport != null) Bitacora.escribir("construirRCL: vuelo id=" + ultimoVueloId
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

//    private int decidirCantidadAAsignar(PedidoParaAlgoritmo pedido,
//                                        RutaProgramadaParaAlgoritmo rutaSol) {
//        Bitacora.escribir("Debo decidir cantidad a asignar para pedido : "+pedido + " en ruta: "+rutaSol);
//        if (pedido == null || rutaSol == null) return 0;
//        int remaining = pedido.getCantidadRestanteDeEntregaYProgram();
//        if (remaining <= 0) return 0;
//        Bitacora.escribir("decidirCantidadAAsignar: remaining pedido: "+remaining);
//        // capacidad mínima disponible en ruta (considerando reservas/ocupados)  Y CON ALMACENES TOMADOS EN CUENTA
//        int rutaCapacidadMin = estadoGlobal.obtenerCapacidadMaxParaTodosVuelosYAlmacenesEnRuta(rutaSol); // CAMBIADO!!! TOMANDO EN CUENTA TMB ALMACENES!!
//        int yaAsignadoEnEnvio =  rutaSol.getCantidadProductosEscogidosYaExistentes(); // herencia de cómo lo hacía antes xd
//        int disponibleRutaParaAsignar = Math.max(0, rutaCapacidadMin - yaAsignadoEnEnvio);
//        if (disponibleRutaParaAsignar <= 0) return 0;
//        Bitacora.escribir("decidirCantidadAAsignar: disponible en ruta para asignar: "+disponibleRutaParaAsignar);
//        // stock disponible en almacen origen (primer vuelo)
//        Vuelo primer = estadoGlobal.getVuelos().get(
//                rutaSol.getIdsVuelosEnOrden().getFirst());
//        if (primer == null) return 0;
//        Long idOrigen = primer.getIdAlmacenOrigen();
//
//        Almacen almacenOrigen = null;
//        if (estadoGlobal.getAlmacenes() != null) {
//            for (Almacen a : estadoGlobal.getAlmacenes().values()) {
//                if (a != null && Objects.equals(a.getId(), idOrigen)) {
//                    almacenOrigen = a;
//                    break;
//                }
//            }
//        }
//        int disponibleOrigen;
//        if (almacenOrigen == null) {
//            // conservador: si no conocemos el almacén consideramos que no hay stock
//            disponibleOrigen = 0;
//        } else if (almacenOrigen.isEsInfinito()) {
//            disponibleOrigen = Integer.MAX_VALUE / 4;
//        } else {
////            int ocupado =  almacenOrigen.getCapacidadOcupada();
//            AlmacenParaAlgoritmo almFuturo=
//                    estadoGlobal.obtenerAlmacenEnInstante(almacenOrigen, primer.getInicio());
//            int reservadoParaFuturo = almFuturo.getCapacidadSinOcupar();
//            disponibleOrigen = Math.max(0, /*ocupado-*/ reservadoParaFuturo/*- reserv*/); // CORREGIDO?!???!?!?!?!?!?!?!?
//        }
//        if (disponibleOrigen <= 0) {
//            // si origen sin stock, no se puede asignar
//            return 0;
//        }
//        Bitacora.escribir("decidirCantidadAAsignar: disponibleOrigen: "+disponibleOrigen);
//        // cantidad asignable = min(remaining, disponibleRutaParaAsignar, disponibleOrigen)
//        int asignable = (int) Math.min( (long) remaining, Math.min((long) disponibleRutaParaAsignar, (long) disponibleOrigen) );
//        return asignable; // Math.max(0, asignable);
//    }
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
//        Bitacora.escribir("Generando rutas candidatas");
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
//        Bitacora.escribir("Rutas candidatas finalizadas. Total: " + resultado.size());
//        Bitacora.escribir("Rutas candidatas: ");
//        for ( RutaADestino ruta : resultado) {
//            Bitacora.escribir("Rutas:");
//            for(VueloParaAlgoritmo vf : ruta.getVuelosOrdenados()) {
//                Bitacora.escribir( "   Vuelo:"+ vf);
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
            for (int i=0;i<rcl.size();i++) {
                acc += ws.get(i);
                if (pick <= acc) return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size()-1);
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
//            Bitacora.escribir("esFactible: pedido o ruta inválida.");
//            return false;
//        }
//
//        // 1) remaining del pedido
//        int totalPedidos = pedido.getCantidadProductosPedidos() == null ? 0 : pedido.getCantidadProductosPedidos();
//        int entregados = pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados();
//        int programados = pedido.getCantidadProductosProgramados() == null ? 0 : pedido.getCantidadProductosProgramados();
//        int remaining = Math.max(0, totalPedidos - entregados - programados);
//        if (remaining <= 0) {
//            Bitacora.escribir("esFactible: pedido id=" + pedido.getId() + " no tiene remanente.");
//            return false;
//        }
//
//        // 2) capacidad disponible en la ruta (min across legs) menos lo ya asignado al envio
//        int capacidadRutaDisponible = obtenerCapacidadMaxParaTodosVuelosEnRuta(ruta);
//        int yaAsignadoEnEnvio = envio == null || envio.getCantProductos() == null ? 0 : envio.getCantProductos();
//        int disponibleParaEsteEnvio = Math.max(0, capacidadRutaDisponible - yaAsignadoEnEnvio);
//        if (disponibleParaEsteEnvio <= 0) {
//            Bitacora.escribir("esFactible: ruta no tiene capacidad disponible (capRuta=" + capacidadRutaDisponible + ", yaAsignadoEnvio=" + yaAsignadoEnEnvio + ").");
//            return false;
//        }
//
//        // 3) stock en el almacén origen (primer vuelo)
//        VueloParaAlgoritmo primerVuelo = ruta.getVuelosOrdenados().getFirst();
//        if (primerVuelo == null) {
//            Bitacora.escribir("esFactible: primer vuelo nulo en ruta.");
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
//            Bitacora.escribir("esFactible: no se encontró info de almacen origen id=" + idAlmacenOrigen);
//            return false;
//        } else if (Boolean.TRUE.equals(almacenOrigen.getEsInfinito())) {
//            disponibleOrigen = Integer.MAX_VALUE / 4; // suficientemente grande
//        } else {
//            int ocupado = almacenOrigen.getCapacidadOcupada() == null ? 0 : almacenOrigen.getCapacidadOcupada();
//            int reserv = almacenOrigen.getCapacidadReservadaPorEnvios() == null ? 0 : almacenOrigen.getCapacidadReservadaPorEnvios();
//            disponibleOrigen = Math.max(0, ocupado - reserv);
//        }
//        if (disponibleOrigen <= 0) {
//            Bitacora.escribir("esFactible: origen id=" + idAlmacenOrigen + " no tiene stock disponible (o está vacío).");
//            return false;
//        }
//
//        // 4) Estados de vuelos en la ruta: todos deben ser EN_ESPERA o EN_CURSO
//        for (VueloParaAlgoritmo v : ruta.getVuelosOrdenados()) {
//            if (v == null || v.getEstado() == null) {
//                Bitacora.escribir("esFactible: vuelo nulo o sin estado en ruta.");
//                return false;
//            }
//            if (!(v.getEstado() == EstadoVuelo.EN_ESPERA || v.getEstado() == EstadoVuelo.EN_CURSO)) {
//                Bitacora.escribir("esFactible: vuelo id=" + v.getId() + " en estado no válido: " + v.getEstado());
//                return false;
//            }
//        }
//
//        // 5) Restricción temporal: llegada + 2h (pickup) <= instanteMaximoParaEntregar (si está definido)
//        VueloParaAlgoritmo ultimoVuelo = ruta.getVuelosOrdenados().getLast();
//        Instant llegada = ultimoVuelo.getFin();
//        if (llegada == null) {
//            Bitacora.escribir("esFactible: último vuelo no tiene hora de fin; asumimos no factible.");
//            return false;
//        }
//        Instant pickup = llegada.plusSeconds(2 * 60 * 60); // +2 horas
//        Instant deadline = pedido.getInstanteMaximoParaEntregar();
//        if (deadline != null) {
//            if (pickup.isAfter(deadline)) {
//                Bitacora.escribir("esFactible: pickup (" + pickup + ") posterior al deadline (" + deadline + ") para pedido id=" + pedido.getId());
//                return false;
//            }
//        } // si deadline null asumimos flexible
//
//        // 6) finalmente, comprobar que al menos 1 unidad pueda asignarse:
//        //    asignable = min(remaining, disponibleParaEsteEnvio, disponibleOrigen)
//        long asignable = Math.min(remaining, Math.min(disponibleParaEsteEnvio, disponibleOrigen));
//        if (asignable <= 0) {
//            Bitacora.escribir("esFactible: ninguna unidad asignable (remaining=" + remaining
//                    + ", disponibleRuta=" + disponibleParaEsteEnvio + ", disponibleOrigen=" + disponibleOrigen + ").");
//            return false;
//        }
//
//        // Si pasa todas las comprobaciones, se considera factible (al menos parcialmente)
//        Bitacora.escribir("esFactible: pedido id=" + pedido.getId() + " puede asignarse parcialmente. asignable=" + asignable);
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
//            Bitacora.escribir("anadirPedidoConCantidad: entrada inválida, no se hace nada.");
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
//        Bitacora.escribir(String.format("Se añadió pedido id=%d cantidad=%d al envío. Envío.cantProductos ahora=%d",
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
//        Bitacora.escribir("Actualizando estado temporal en memoria...");
//
//        // 1) Recalcular la capacidad mínima disponible en la ruta (considerando reservas ya aplicadas)
//        int capacidadDisponibleRuta = estadoGlobal.obtenerCapacidadMaxParaTodosVuelosEnRuta(rutaSol);
//        Bitacora.escribir("Capacidad disponible recalculada en la ruta: " + capacidadDisponibleRuta);
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
//                    Bitacora.escribir("Pedido id=" + p.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
//                    it.remove();
//                    removed++;
//                }
//            }
//            Bitacora.escribir("Pedidos removidos de pendientes: " + removed + ". Pendientes ahora: " + pedidosPendientes.size());
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
//            Bitacora.escribir("Pedidos pendientes reordenados por urgencia y size.");
//        }
//
//        // 4) (Opcional) - Recalcular otras métricas globales si las mantienes en memoria.
//        // Por ejemplo, podrías recalcular una medida de demanda total por destino, uso de vuelos, etc.
//        // (No hago nada extra aquí automáticamente, pero deja el lugar para agregar).
//
//        Bitacora.escribir("Estado temporal actualizado. capacidadDisponibleRuta=" + capacidadDisponibleRuta);
//        return capacidadDisponibleRuta;
//    }
//


    /**
     * Comprueba si hay al menos un pedido con remaining > 0.
     */



}
// que:
//                estadoGlobal.getIdsPedidosPorDestino()
//                        .getOrDefault(idAlmacenDestinoRutaSeleccionada, Collections.emptyList())
//        .stream()
//                        .map(id -> {
//Pedido p = estadoGlobal.getPedidos().get(id);
//                            return p;
//                        })
//                                .filter(Objects::nonNull) // eliminar ids sin pedido en el mapa
//                        .filter(p -> p.getCantidadProductosPendientes() > 0) // solo pendientes
//        .collect(Collectors.toList());