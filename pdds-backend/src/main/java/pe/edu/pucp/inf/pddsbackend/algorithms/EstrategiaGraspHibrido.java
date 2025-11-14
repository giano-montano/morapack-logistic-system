package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.time.Duration;
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
    private Instant instanteActual;

    private static final double ALPHA_RUTAS = 0.8;
    private static final double ALPHA_PEDIDOS = 0.5; // por poner algo xd
    private static final int ITERACIONES_MAXIMAS_PRIMER_GRASP = 50000;
    private static final double UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA = 0.8;
    private static final double UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA = 0.2;


    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) throws Exception {
        // Inicialización
        estadoGlobal = entrada.getEstadoGlobalCopia();
        estadoGlobal.setLoggingReport(loggingReport);
        this.instanteActual = entrada.getInstanteActual();
        setSemilla(entrada.getSemilla()); // repoio
        // Obtener rutas a solo almacenes de destino y a partir de almacenes infinitos o no infinitos con al menos 1 producto.
        List<LinkedList<Long>> // Una clase para ruta que sea lo mismo que una lista de vuelos? No la necesité hasta ahora
                rutasPosibles = // recordar que no hay pedidos para almacenes infinitos hasta este punto (los filtramos antes).
                estadoGlobal.generarRutasParaPedidosPendientesBFS(instanteActual); //
//                estadoGlobal.generarRutasParaPedidosPendientesACO(instanteActual); // <- chamba de Axel
        estadoGlobal.crearIndiceIdsRutasPorAlmacenDestino(rutasPosibles); // a partir de aquí tenemos el tan deseado índice.

        loggingReport.appendReport("Comenzando estrategia GRASP Híbrido: "+ estadoGlobal);
        StringBuilder rutas= new StringBuilder();
        estadoGlobal.getRutasPorIdAlmacenDestino().forEach((aLong, linkedLists) ->
                rutas.append(aLong).append(" rutas: ").append(linkedLists).append("\n")
        );
//        loggingReport.appendReport( "Índice:\n " + rutas.toString());

        // asignar puntajes a pedidos pendientes.
        List<Pedido> pedidosPendientes = estadoGlobal.obtenerPedidosPendientesDeEntregaYProgram();
        Map<Pedido, Double> puntajesPorPedido = asignarPuntajesPedidos(pedidosPendientes, this.instanteActual); // <- chamba de Axel

        // Ciclo principal
        int numIteraciones;
        try {
            numIteraciones = realizarCicloDePedidos(rutasPosibles, puntajesPorPedido);
        } catch (Exception ex) {
            ex.printStackTrace();
            SalidaProblemaPlanificacion solution = new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones(), ex.getStackTrace().toString());
            loggingReport.appendReport(ex.toString());
            loggingReport.writeReportFile("Reporte-GRASP-error-" + estadoGlobal.getProgramaciones().size());
            return solution;
        }
        loggingReport.appendReport("Planificación finalizada. Iteraciones GRASP realizadas: " + numIteraciones +
                ". Programaciones creadas: " + estadoGlobal.getProgramaciones().size());
        // .............................................................................
        // ......................................................................
        // ................
        EstrategiaSecundariaACO estrategia = new EstrategiaSecundariaACO();
        estrategia.planificar(estadoGlobal);
        // ..

        SalidaProblemaPlanificacion solution =
                new SalidaProblemaPlanificacion(estadoGlobal.getProgramaciones(), estadoGlobal.getProductos());

        //


        if (estadoGlobal.hayPedidosPendientesPorProgramar()) {
            loggingReport.appendReport("NO SE LOGRÓ PLANIFICAR TODO, COLAPSO LOGÍSTICO!!!!!!!!!!!!");
            solution.setColapsado(true);
        }
        loggingReport.writeReportFile("Reporte-GRASP-" + estadoGlobal.getProgramaciones().size());
        return solution;
    }

    private int realizarCicloDePedidos(
            List<LinkedList<Long>> rutasPosibles,
            Map<Pedido, Double> puntajesPorPedido
    ) {
        int numIteraciones = 0;
        while (estadoGlobal.hayPedidosPendientesPorProgramar() && numIteraciones < ITERACIONES_MAXIMAS_PRIMER_GRASP) {
            loggingReport.appendReport("planificar: Iteración %d: quedan %d pedidos pendientes", numIteraciones, estadoGlobal.contarPedidosPendientes());

            List<Programacion> programacionesConstruidasGrasp =
                    elegirYProgramarParaPedido(rutasPosibles, puntajesPorPedido);

            if (programacionesConstruidasGrasp == null) {
                loggingReport.appendReport("GRASP no pudo hacer una programación más, finalizando ciclo.");
                break;
            }
//            // Añadir el envío a la solución
//            estadoGlobal.anadirVariasProgramacionesSolucion(programacionesConstruidasGrasp);
//            loggingReport.appendReport("Programaciones solución añadidas: " + programacionesConstruidasGrasp);

            // Limpieza de pedidos completamente satisfechos en la lista global (para acelerar próximas iteraciones)
            boolean removed = estadoGlobal.eliminarPedidoYaSatisfecho(programacionesConstruidasGrasp.get(0).getIdPedido());
            if (removed)
                loggingReport.appendReport("Se eliminó el pedido " + programacionesConstruidasGrasp.get(0).getIdPedido() +
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
//        loggingReport.appendReport("rutasConDestinoCompartido: "+rutasConDestinoCompartido);
        List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido =
                filtrarRutasSegunPlazoPedido(pedidoElegido, rutasConDestinoCompartido);
//        loggingReport.appendReport("rutasFiltradasSegunPlazoPedido: "+rutasFiltradasSegunPlazoPedido);
        while (numProductosPorAtender > numProductosAtendidosPedido) { // Programar para todo el pedido.

            Programacion programacionHecha =
                    construccionGraspParaUnaProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);

            if (programacionHecha == null) return null;

            programaciones.add(programacionHecha);
            estadoGlobal.anadirProgramacionSolucion(programacionHecha); // mutar estado global!

            numProductosAtendidosPedido++;
        }
//        loggingReport.appendReport("programaciones: "+ programaciones);
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
                    = asignarPuntajesRutas(rutasFiltradasSegunPlazoPedido, this.instanteActual, pedidoElegido); // <- chamba de Axel
//            loggingReport.appendReport("puntajesPorRuta (ya validadas según plazo y destino del pedido): \n");
//            loggingReport.appendMap(puntajesPorRuta);
            List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(puntajesPorRuta);
            if (rclRutasCandidatas.isEmpty()) {
                loggingReport.appendReport("construccionGraspParaUnaProgramacion: RCL de rutas vacía");
                return null; // Lo más probable es que las rutas filtradas estén aberradas o nulas, no hay más que hacer.
            }
            rclValido = true;
            loggingReport.appendReport("construccionGraspParaUnaProgramacion: Rutas que entraron a la RCL:  \n" + rclRutasCandidatas);
            while (!rclRutasCandidatas.isEmpty()) { // Solo para asegurar ruta factible
                rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, false);
                loggingReport.appendReport("rutaElegida: "+rutaElegida);
                boolean esRutaValida = estadoGlobal.rutaTieneCapacidadEnEstadoActual(rutaElegida, pedidoElegido, instanteActual); // capacidades, no plazos.
                loggingReport.appendReport("esRutaValida: "+esRutaValida);
                if (!esRutaValida) {
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue; // el productoAgarrado no se define, queda en null aún.
                }
                productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
                // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
                if (productoAgarrado == null) { //throw new IllegalStateException("¡¿Cómo?!"); // xd
                    loggingReport.appendReport("wtf, el producto agarrado fue nulo");
                    System.out.println("wtf, el producto agarrado fue nulo");
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un posible futuro puntaje.
                    continue;
                }
                break;
            }
            if (productoAgarrado == null) {
                loggingReport.appendReport("construccionGraspParaUnaProgramacion: Producto nulo, rcl invalido, nuevo rcl por generar");
                rclValido = false; // quiere decir que en toda la RCL no consiguió nada
            }
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
            return new Producto(almacenOrigen.getId(), ruta, instanteActual);
        }
        // A partir de aquí, sí es un almacén intermedio. Veremos sus prods en el futuro a ver cuál agarramos.
        List<Producto> productosDelOrigenEnPrimerVuelo = estadoGlobal.obtenerProductosAlmacenOrigenEnRuta(ruta);
        // División entre continentales e intercontinentales
        Map<Boolean, List<Producto>> listaPartidaProds = productosDelOrigenEnPrimerVuelo.stream()
                .collect(Collectors.partitioningBy(producto -> {
//                            if(producto==null) {
//                                System.out.println("wtf, producto nulo en escogerProductoEnRuta");
//                                loggingReport.appendReport("wtf, producto nulo en escogerProductoEnRuta");
//                                return false;
//                            }
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
        loggingReport.appendReport("Pedido: "+ pedido);
        boolean debug = pedido.getId() == 3589162L || pedido.getIdAlmacenDestino() == 25 || true;
        List<LinkedList<Long>> rutas =
                rutasConDestinoCompartido.stream()
                        .filter(ruta -> {
//                                    if(debug) loggingReport.appendReport("Ruta: " + ruta.toString());
//                                    if(debug) loggingReport.appendReport("Último vuelo: " + estadoGlobal
//                                            .getVuelos().get(ruta.getLast()));
                                    return estadoGlobal
                                            .getVuelos().get(ruta.getLast())
                                            .entregariaPedidoEnPlazoReal(pedido);
                                }

                        ).collect(Collectors.toList());
        return rutas; // menos eficiencia xdd pero pa que funque, porque el toList
        // de stream da listas inmutables
    }


    /*
     * Función que asigna puntajes a rutas según la siguiente formula:
     * score = alfa1 * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial
     * 
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a 0 significa que la ruta es mejor valorada.
     * Falta tunear los coeficientes alfa1 y alfa2
     * También podría evaluar qué tan cargados estén los vuelos (esto no esta ni implementado ni modelado en la ecuación).
     * También podría evaluar qué tanto espacio va a ocupar en almacenes escala (esto no esta ni implementado ni modelado en la ecuación).
     * 
     */
    private Map<LinkedList<Long>, Double> asignarPuntajesRutas(
            List<LinkedList<Long>> rutas,
            Instant instanteActual,
            Pedido pedido
    ) {
//        loggingReport.appendReport("me llegó para asignar puntaje: "+ rutas.size() + " rutas, instante act: "+instanteActual
//        +" pedido: "+pedido);
        Double score, alfa1, alfa2, aptitudTemporal, aptitudLogística, aptitudEspacial;
        Pair<Double, Double> aptitudes;
        List<Vuelo> vuelos;
        Map<LinkedList<Long>, Double> scores = new HashMap<>();

        alfa1 = 0.5;
        alfa2 = 0.7;

        for (LinkedList<Long> ruta : rutas) {
            vuelos = estadoGlobal.obtenerVariosVuelosPorIds(ruta);

            aptitudTemporal = this.calcularAptitudTemporal(vuelos, instanteActual, pedido);
            aptitudes = this.calcularAptitudLogisticaYEspacial(vuelos, estadoGlobal );
            aptitudLogística = aptitudes.getLeft();
            aptitudEspacial = aptitudes.getRight();

            score = alfa1 * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial;
//            loggingReport.appendReport("score obtenido en ruta: "+score + " ruta");
            scores.put(ruta, score);
        }
            
        return scores;
    }

    /*
     * Calcula segun la formula:
     * (instantePrimerVuelo - instanteActual) / (instanteMaximoParaEntregar - instanteUltimoVuelo) 
     * 
     * Las ruta asume que el primer vuelo todavía no sale
     * 
     * instantePrimerVuelo -> instante de salida del primer vuelo de la ruta
     * instanteUltimoVuelo -> instante de llegada del ultimo vuelo dela ruta
     * instanteActual -> instante en el que se solicito la planificación
     * instanteMaximoParaEntregar -> instante de entrega máximo
     * 
     */
    private Double calcularAptitudTemporal(List<Vuelo> ruta, Instant instanteActual, Pedido pedido) {
        Double tiempoPartida, tiempoSobrante;
        Instant instantePrimerVuelo, instanteMaximoParaEntregar, instanteUltimoVuelo;

        instantePrimerVuelo = ruta.get(0).getInicio();
        instanteUltimoVuelo = ruta.get(ruta.size() - 1).getFin();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
        tiempoPartida = Duration.between(instanteActual, instantePrimerVuelo).toMillis() / 1000.0;
        tiempoSobrante = Duration.between(instanteUltimoVuelo, instanteMaximoParaEntregar).toMillis() / 1000.0;

        return (tiempoPartida) / (tiempoSobrante);
    }

    /*
     * Calcula según la formula:
     * aptitudLogística = nVuelos / sum(sqrt(tiempoVuelo_i ^ 2 + tiempoEspera ^ 2_i))
     * aptitudEspacial = (capacidadOcupada ) / capacidadTotal
     * 
     * nVuelos -> cantidad de vuelos que posee la ruta
     * tiempoVuelo_i -> duración del vuelo i-ésimo de la ruta evaluada
     * tiempoEspera_i -> duración de la espera i-ésima antes de abordar el siguiente vuelo
     * capacidadOcupada -> capacidad ocupada del almacén capacidadTotal
     * capacidadTotal -> capacidad máxima del almacén
     * 
     */
    private Pair<Double, Double> calcularAptitudLogisticaYEspacial(List<Vuelo> ruta, EstadoGlobal estado) {
        Integer nVuelos;
        Double tiempoVuelo, tiempoEspera, espacioAlmacen,  aptitudLogística, aptitudEspacial;
        Instant instanteSalida, instanteLlegada;
        Vuelo vueloAnterior;
        Almacen almacenLlegada;

        nVuelos = 0;
        aptitudLogística = aptitudEspacial = tiempoEspera = 0D;

        for (Vuelo vuelo : ruta) 
        {
            instanteSalida = vuelo.getInicio();
            instanteLlegada = vuelo.getFin();
            almacenLlegada = estado.buscarAlmacen(vuelo.getIdAlmacenDestino());
            tiempoVuelo = Duration.between(instanteSalida, instanteLlegada).getSeconds() / 3600.0;
            espacioAlmacen = (double) almacenLlegada.getCapacidadOcupada() / almacenLlegada.getCapacidadMaxima();

            if(nVuelos > 0)
            {
                vueloAnterior = ruta.get(nVuelos - 1);
                instanteSalida = vuelo.getInicio();
                instanteLlegada = vueloAnterior.getFin();
                tiempoEspera = Duration.between(instanteLlegada, instanteSalida).getSeconds() / 3600.0;
            }

            aptitudLogística += Math.sqrt(Math.pow(tiempoVuelo, 2) + Math.pow(tiempoEspera, 2));
            aptitudEspacial += espacioAlmacen;
            nVuelos++;
        }
        
        aptitudLogística = nVuelos / aptitudLogística;
        aptitudEspacial = aptitudEspacial / nVuelos;

        return Pair.of(aptitudLogística, aptitudEspacial);
    }

    /**
     * Construye la RCL a partir del mapa ruta->score. Convención: score mayor = PEOR.
     * Garantiza que, para cada almacén destino no infinito (si existe alguna ruta para él),
     * al menos la mejor ruta (por score) quede incluida en la RCL resultante.
     *
     * @param scores mapa ruta -> score (MENOR = mejor)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */ // DEUDA TÉCNICA, CREO QUE DA IGUAL LO DE AL MENOS UNA PARA CADA ALMACÉN
    // RCL de rutas: ahora score menor = mejor
    private List<LinkedList<Long>> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
            Map<LinkedList<Long>, Double> scores) {

        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        // 0. obtener alpha (usar campo de clase o fallback)
        double alphaLocal = this.ALPHA_RUTAS;
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

        // 2) Umbral: ahora score menor = mejor
        double threshold = min + alphaLocal * (max - min);

        // 3) RCL inicial por umbral (LinkedHashSet para evitar duplicados y mantener determinismo)
        Set<LinkedList<Long>> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Encontrar la mejor ruta por destino (ignorando destinos infinitos)
        Map<Long, LinkedList<Long>> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();

        for (Map.Entry<LinkedList<Long>, Double> e : scores.entrySet()) {
            LinkedList<Long> ruta = e.getKey();
            Double score = e.getValue() == null || e.getValue().isNaN() ? Double.POSITIVE_INFINITY : e.getValue();

            if (ruta == null || ruta.isEmpty()) continue;

            long ultimoVueloId = ruta.getLast();

            // obtener objeto vuelo
            Vuelo vueloUltimo = estadoGlobal.getVuelos().get(ultimoVueloId);
            if (vueloUltimo == null) {
                loggingReport.appendReport(
                        "construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId + " -> se ignora ruta.");
                continue;
            }

            // obtener id almacen destino desde el vuelo y then almacen
            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
            Almacen alm = estadoGlobal.getAlmacenes().get(idAlmacenDestino);
            if (alm == null) {
                loggingReport.appendReport(
                        "construirRCL: vuelo id=" + ultimoVueloId + " apunta a almacenDestino id=" + idAlmacenDestino
                                + " que no existe en mesa -> se ignora ruta.");
                continue;
            }

            // ignorar destinos infinitos
            if (alm.isEsInfinito()) continue;

            // actualizar mejor por destino: ahora menor score = mejor
            Double bestScore = bestScoreByDestino.get(idAlmacenDestino);
            if (bestScore == null || score < bestScore) {
                bestScoreByDestino.put(idAlmacenDestino, score);
                bestByDestino.put(idAlmacenDestino, ruta);
            }
        }

        // 5) Asegurar que la mejor ruta por destino esté en la RCL
        for (Map.Entry<Long, LinkedList<Long>> be : bestByDestino.entrySet()) {
            LinkedList<Long> bestRuta = be.getValue();
            if (bestRuta != null) rclSet.add(bestRuta);
        }

        // 6) Ordenar por score ascendente (mejor primero) y devolver
        List<LinkedList<Long>> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)
        ));
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

    /*
     * Función que asigna puntajes a pedidos según la siguiente formula:
     * score = urgenciaTiempo + urgenciaTamaño
     * 
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a 0 significa que el pedido es más urgente. Para pedidos iguales de urgentes, el valor de score es de aproximadamente 6 y aumenta de forma logaritmica
     * 
     */
    private Map<Pedido, Double> asignarPuntajesPedidos(
            List<Pedido> pedidos, Instant instanteActual
    ) {
        Double score;
        Map<Pedido, Double> scores = new HashMap<>();
        
        try {
            for (Pedido pedido : pedidos)
            {
                score = this.calcularUrgenciaTiempo(pedido, instanteActual) + this.calcularUrgenciaTamano(pedido);
                scores.put(pedido, score);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return scores;
    }

    /*
     * Calcula segun la formula:
     * urgenciaTiempo = (instanteMaximoParaEntregar - instanteActual) / ( instanteMaximoParaEntregar - instanteRegistro)
     * 
     * instanteMaximoParaEntregar -> instante de entrega máximo
     * instanteActual -> instante en el que se solicito la planificacion
     * instanteRegistro -> instante de registro del pedido
     * 
     */
    private Double calcularUrgenciaTiempo(Pedido pedido, Instant instanteActual) {
        Double urgenciaTiempo, tiempoRestante, tiempoMaximoParaEntregar;
        Instant instanteRegistro, instanteMaximoParaEntregar;

        instanteRegistro = pedido.getInstanteRegistro();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
        tiempoRestante = Duration.between(instanteActual, instanteMaximoParaEntregar).toMillis() / 1000.0;
        tiempoMaximoParaEntregar = Duration.between(instanteRegistro, instanteMaximoParaEntregar).toMillis() / 1000.0;
        urgenciaTiempo = tiempoRestante/tiempoMaximoParaEntregar;

        return urgenciaTiempo;
    }
    
    /*
     * Calcula segun al formula:
     * ln((1 + productosTotales) / (1 + productosEntregados))
     * 
     * productosTotales -> cantidad de productos que compone el pedido
     * productosEntregados -> cantidad de productos entregados
     * 
     */
    private Double calcularUrgenciaTamano(Pedido pedido) {
        Integer productosTotales, productosEntregados;
        Double urgenciaTamano;

        productosEntregados = pedido.getCantidadProductosEntregados();
        productosTotales = pedido.getCantidadProductosPedidos();
        urgenciaTamano = (productosTotales + 1.0)/(productosEntregados + 1.0);
        urgenciaTamano = Math.log(urgenciaTamano);

        return urgenciaTamano;
    }



    //
//    /**
//     * Construye la RCL de pedidos a partir de un mapa pedido->score.
//     * Convención: score MENOR = mejor.
//     *
//     * alpha in [0,1]. alpha = 0 => solo el mejor; alpha = 1 => todos.
//     */
// RCL de pedidos: ahora score menor = mejor
    private List<Pedido> construirRCLDePedidos(Map<Pedido, Double> scores, double alpha) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values()) {
            if (v == null || v.isNaN()) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // defensiva
        if (Double.isInfinite(min) || Double.isInfinite(max)) return Collections.emptyList();

        // umbral: ahora score menor = mejor -> threshold parte de min hacia max
        double threshold = min + alpha * (max - min);

        List<Pedido> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN() && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // ordenar por score ascendente (mejor = menor primero)
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)
        ));

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