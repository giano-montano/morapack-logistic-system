package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Getter
public class EstadoGlobal implements Serializable {
    @NotNull
    private HashMap<Long, Almacen> almacenes;
    @NotNull
    private HashMap<Long, Vuelo> vuelos;
    @NotNull
    private HashMap<Long, Pedido> pedidos;
    @NotNull
    private HashMap<UUID, Producto> productos; // Main añadirá

    @NotNull
    @Setter
    private List<Programacion> programaciones; // EMPIEZA VACÍO !!!!!!!!!!!!!!!!!!!!!!!

    @Setter
    LoggingReport loggingReport = new LoggingReport(); // mientras usamos la bitácora

    // índices
    HashMap<Long, List<Long>> idsVuelosPorOrigen;
    HashMap<Long, List<Long>> idsVuelosPorDestino;
    HashMap<Long, List<Long>> idsPedidosPorDestino;
    HashMap<Long, List<LinkedList<Long>> > rutasPorIdAlmacenDestino = new HashMap<>(); //??

    Set<Long> idsAlmacenesNoInfinitos = new HashSet<>();
    Set<Long> idsAlmacenesInfinitosOConStock = new HashSet<>();
//    Set<Almacen> almacenesInfinitosOConStock = new HashSet<>();

    private final int HORAS_PARA_RECOGER_PEDIDO = 2;
    private final long SEGUNDOS_PARA_RECOGER_PEDIDO = HORAS_PARA_RECOGER_PEDIDO * 3600L;
    private static final int MAX_LEGS = 15; // número máximo de tramos por ruta (incluye primer vuelo)
    private static final int MAX_RUTAS_POR_DESTINO = 200;
    private static final int MAX_RUTAS_POR_ORIGEN = 195;

//    public static EstadoGlobal desdeEntradaPlanificacion(EntradaProblemaPlanificacion entradaPlanificacion) {
//        return new EstadoGlobal(
//                entradaPlanificacion.almacenes,entradaPlanificacion.vuelos,entradaPlanificacion.pedidos);
//    }

    public EstadoGlobal(Map<Long, Almacen> almacenes,
                        Map<Long, Vuelo> vuelos,
                        Map<Long, Pedido> pedidos,
                        List<Programacion> programaciones,
                        Map<UUID, Producto> productos
                        ) {
        this.almacenes = almacenes != null?new HashMap<>(almacenes):new HashMap<>();
        this.vuelos = vuelos != null?new HashMap<>(vuelos):new HashMap<>();
        this.pedidos = pedidos != null?new HashMap<>(pedidos):new HashMap<>();
        this.programaciones = programaciones!=null?new LinkedList<>(programaciones): new LinkedList<>();
        this.productos = productos != null ? new HashMap<>(productos):new HashMap<>(); // ⚠️ IMPORTANTE: inicializar el HashMap de productos

       // A partir de acá, inicializar índices necesarios:
        this.inicializarIndices();
    }

    public EstadoGlobal (EstadoGlobal estadoGlobal) { // clonación
        HashMap<Long,Almacen> copiaAlmacenes = new HashMap<>();
        HashMap<Long,Almacen> originalAlmacenes = estadoGlobal.getAlmacenes();
        for (Map.Entry<Long, Almacen> entry : originalAlmacenes.entrySet()) {
            Long newKey = entry.getKey();
            Almacen newValue = new Almacen (entry.getValue());
            copiaAlmacenes.put(newKey, newValue);
        }

        HashMap<Long,Vuelo> copiaVuelos = new HashMap<>();
        HashMap<Long,Vuelo> originalVuelos = estadoGlobal.getVuelos();
        for (Map.Entry<Long, Vuelo> entry : originalVuelos.entrySet()) {
            Long newKey = entry.getKey();
            Vuelo newValue = new Vuelo (entry.getValue());
            copiaVuelos.put(newKey, newValue);
        }

        HashMap<Long,Pedido> copiaPedidos = new HashMap<>();
        HashMap<Long,Pedido> originalPedidos = estadoGlobal.getPedidos();
        for (Map.Entry<Long, Pedido> entry : originalPedidos.entrySet()) {
            Long newKey = entry.getKey();
            Pedido newValue = new Pedido (entry.getValue());
            copiaPedidos.put(newKey, newValue);
        }

        List<Programacion> copiaProgramaciones = estadoGlobal.getProgramaciones().stream()
                .map(Programacion::new)
                .collect(Collectors.toCollection(LinkedList::new)); // <- Antes lo creaba como inmutable, mal mal,
        // debe ser mutable y concreto con la LinkedList, el stream toList es inmutable

        HashMap<UUID,Producto> copiaProds = new HashMap<>();
        HashMap<UUID,Producto> originalProds = estadoGlobal.getProductos();
        for (Map.Entry<UUID, Producto> entry : originalProds.entrySet()) {
            UUID newKey = entry.getKey();
            Producto newValue = new Producto (entry.getValue());
            copiaProds.put(newKey, newValue);
        }

        almacenes= copiaAlmacenes;
        vuelos= copiaVuelos;
        pedidos= copiaPedidos;
        programaciones= copiaProgramaciones;
        productos = copiaProds; //XDDDDDnew HashMap<>(); // ⚠️ IMPORTANTE: inicializar productos también en constructor de copia
        loggingReport = estadoGlobal.getLoggingReport();
        inicializarIndices();
    }

    private void inicializarIndices(){
        // Con sus propios campos ya asumiendo que están inicializados (alm,vu,ped,prod,prog):
        //...
    }

    public boolean hayPedidosPendientesPorProgramar() {
        if (pedidos == null || pedidos.isEmpty()) return false;
        return pedidos.values().stream().anyMatch(pedido -> pedido.getEstado().equals(EstadoPedido.PENDIENTE));
    }

    public int contarPedidosPendientes() {
        if (pedidos == null || pedidos.isEmpty()) return 0;
        int c = 0;
        for (Pedido p : pedidos.values()) {
            if (p == null) continue;
//            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
//            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
//            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = p.getCantidadProductosPendientes();
            if (remaining > 0) c++;
        }
        return c;
    }
    
    public boolean rutaTieneCapacidadEnEstadoActual(LinkedList<Long> rutaPlanificacion, Pedido pedido, Instant instanteActual) {
        if (!validarCapacidadAvionesEnRuta(rutaPlanificacion, pedido)){
            return false;
        }
        if(!validarCapacidadAlmacenesEnLlegadasOSalidas(rutaPlanificacion)){
            return false;
        }
        if(!validarCapacidadAlmacenesEntremedioLlegadasOSalidas(rutaPlanificacion, instanteActual)){
            return false;
        }
        return true;

    }

    private boolean validarCapacidadAlmacenesEntremedioLlegadasOSalidas(LinkedList<Long> rutaPlanificacion, Instant instanteActual) {
        int asignable = 1;
        int maxDiferenciaColapso = 0;
        List<Vuelo> vuelosRuta = rutaPlanificacion
                .stream()
                .map(id -> vuelos.get(id))
                .toList();
        for (Vuelo vuelo : vuelosRuta) {
            Map.Entry<Almacen,Integer> almacenPosiblementeColapsado;
            Producto señuelo = new Producto(0L, new LinkedList<>(), instanteActual);
            if (vuelo != vuelosRuta.get(vuelosRuta.size() - 1)) {
                Vuelo next = vuelosRuta.get( vuelosRuta.indexOf(vuelo)+1);
                Almacen almDestinoOriginal = almacenes.get( vuelo.getIdAlmacenDestino() );
                Almacen almDestino = almDestinoOriginal!=null?new Almacen(almDestinoOriginal):null;

                if(almDestino!=null) almDestino.agregarProducto(señuelo); // sólo sobre el CLON
                almacenPosiblementeColapsado=
                        simularAlmacenHastaInstanteIlegalmente(
                                almDestino!=null?almDestino:almDestinoOriginal, vuelo.getFin()
                                        .plus(Duration.between( //duration implements TemporalAmount
                                                next.getInicio(), vuelo.getFin()
                                        ))); //lo que esperará, debería ser 1h
            }else{
                Almacen almFinalOriginal = almacenes.get( vuelo.getIdAlmacenDestino() );
                Almacen almFinal = almFinalOriginal!=null?new Almacen(almFinalOriginal):null;
                if(almFinal!=null) almFinal.agregarProductoIlegalmente(señuelo); // sólo sobre el CLON
                almacenPosiblementeColapsado=
                        simularAlmacenHastaInstanteIlegalmente(
                                almFinal!=null?almFinal:almFinalOriginal, vuelo.getFin()
                                        .plus(2, ChronoUnit.HOURS));
            }
            loggingReport.appendReport(
                    "Simulación del almacén destino hasta siguiente inicio: "+almacenPosiblementeColapsado
            );
            int diferenciaQueHizoColapso =almacenPosiblementeColapsado.getValue(); /*almacenPosiblementeColapsado.getKey().getCapacidadOcupada()
                    -almacenPosiblementeColapsado.getKey().getCapacidadMaxima();*/
            if (diferenciaQueHizoColapso>0) {//colapsado
                loggingReport.appendReport("El almacén colapsaría con una diferencia de: "
                        + diferenciaQueHizoColapso);
//                    asignable -= diferenciaQueHizoColapso;
                maxDiferenciaColapso=Math.max(maxDiferenciaColapso,diferenciaQueHizoColapso);
            }
        }
        if(maxDiferenciaColapso>0) {
            loggingReport.appendReport("maxDiferenciaColapso " + maxDiferenciaColapso, "no se puede llevar " +
                    "debido al entremedio");
            asignable = asignable - maxDiferenciaColapso; // CORRREGIDOA
            return false;
        }
            return true;
    }

    private boolean validarCapacidadAlmacenesEnLlegadasOSalidas(LinkedList<Long> rutaPlanificacion) {
        List<Vuelo> vuelosAsociados = rutaPlanificacion
                .stream()
                .map(id -> vuelos.get(id))
                .toList();
        Vuelo prev = null;
        // opcional: cache para evitar recalcular mismo almacen+instante muchas veces
        Map<String, Almacen> cacheSimulAlmacenes = new HashMap<>();
        for (Vuelo vuelo : vuelosAsociados) {
            // conectividad entre tramos: prev.dest == current.origin
            if (prev != null) {
                if (prev.getIdAlmacenDestino() != vuelo.getIdAlmacenOrigen()) {
                    return false; // ruta desconectada
                }
                // orden temporal: inicio actual >= fin prev
                if (vuelo.getInicio().isBefore(prev.getFin())) {
                    return false; // solapamiento temporal inválido
                }
            }

//            // capacidad del vuelo (usar cálculo actualizado)
//            if (vuelo.getCapacidadDisponibleParaReserva() < 1) {
//                return false; // vuelo sin espacio suficiente
//            }

            // 3.d capacidad en almacén origen al inicio del vuelo
            Almacen almOrigen = almacenes.get(vuelo.getIdAlmacenOrigen());
            String keyOrigen = almOrigen.getId() + "|" + vuelo.getInicio().toString();
            Almacen simulOrigen = cacheSimulAlmacenes.computeIfAbsent(keyOrigen,
                    k -> getAlmacenEnInstante(almOrigen, vuelo.getInicio()));
            // ---------- REPARACIÓN AQUÍ ----------
            /*
             * Para el almacén origen necesitamos CHEQUEAR INVENTARIO (hay productos disponibles para sacar),
             * no el espacio libre. Usar capacidadOcupada o idsProductosExistentes.size()
             */
            int productosDisponiblesEnOrigen = simulOrigen.getIdsProductosExistentes() != null
                    ? simulOrigen.getIdsProductosExistentes().size()
                    : simulOrigen.getCapacidadOcupada(); // fallback por seguridad

            if (productosDisponiblesEnOrigen < 1 && !almOrigen.isEsInfinito()) { // <- puede estar mejor codificado
                return false; // origen no puede suministrar (no hay productos)
            }

            // 3.e capacidad en almacén destino al fin del vuelo
            Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
            String keyDestino = almDestino.getId() + "|" + vuelo.getFin().toString();
            Almacen simulDestino = cacheSimulAlmacenes.computeIfAbsent(keyDestino,
                    k -> getAlmacenEnInstante(almDestino, vuelo.getFin()));
            if (simulDestino.getCapacidadSinOcupar() < 1) {
                return false; // destino no tiene espacio al llegar
            }
            prev = vuelo;
        }
            // todas las comprobaciones pasaron
            return true;
    }

    private boolean validarCapacidadAvionesEnRuta(LinkedList<Long> rutaPlanificacion, Pedido pedido) {
        List<Long> idsVuelos = rutaPlanificacion.stream().toList();
        LinkedList<Vuelo> vuelosRuta = new LinkedList<>(idsVuelos.stream()
                .map(vId -> this.vuelos.get(vId))
                .filter(Objects::nonNull)
                .toList());
        if (vuelosRuta.size() != idsVuelos.size()) return false; // hay un vuelo corrupto?

        Vuelo ultimoVuelo = vuelosRuta.get(vuelosRuta.size() - 1);
        if (!Objects.equals(ultimoVuelo.getIdAlmacenDestino(), pedido.getIdAlmacenDestino())) return false; //no tiene
        // que ver con capacidad, pero igual porsia

        boolean unVueloNoTieneEspacioParaUno= vuelosRuta.stream().anyMatch(
                vuelo -> vuelo.getCapacidadDisponibleParaReserva()<=0);
        return !unVueloNoTieneEspacioParaUno;
    }

    /**
     * Añade a la mesa (estado global) una programación ya validada y actualiza todos los estados respectivos.
     * IMPORTANTE: se asume que la programación fue validada previamente contra el estado actual
     * (capacidad de vuelos/almacenes, conectividad temporal, etc.). Si hay una inconsistencia
     * (por ejemplo, falta de capacidad en un vuelo) lanzamos IllegalStateException para detectar
     * condiciones de carrera o errores lógicos.
     * MÁS IMPORTANTE: no usar esta función en cualquier contexto fuera del algoritmo, ya que el algoritmo
     * usa como artificios el mutar estados como la capacidad ocupada de vuelos = reservados
     */
    public void anadirProgramacionSolucion(Programacion programacion) {
        if (programacion == null) return;

        // Protección simple: si ya existe la misma instancia no la volvemos a añadir
        if (this.programaciones.contains(programacion)) return; // ya añadida, nada que hacer

        final int cantidad = 1;
        final long idPedido = programacion.getIdPedido();
        final Producto productoElegido = productos.get(programacion.getUuidProducto());
        // 1) Añadir la ruta al conjunto de rutas actuales (esto permite que las simulaciones vean la nueva ruta)
        loggingReport.appendReport("Programación añadida al estado global "+programacion);
        this.programaciones.add(programacion);
        // 2) Actualizar el pedido: incrementar cantidadProgramada
        Pedido pedido = this.pedidos.get(idPedido);
        if (pedido != null) {
            Almacen origen = almacenes.get(productoElegido.getIdAlmacenInfinitoOrigen());
            pedido.agregarProductoProgramado(productoElegido,origen.getContinente());
            //^^^esto actualiza el estado interno del pedido en el hashmap, incluyendo prods y plazo si es intercontinental o no
// Cosa rara: Cuando esta programación quede obsoleta por una nueva program, el pedido no volverá a ser continental xD!
            int restante = pedido.getCantidadProductosPendientes();
            if (restante <= 0)
                Bitacora.escribir("PedidoEntidad id=" + pedido.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
            if(almacenes.get(pedido.getIdAlmacenDestino()).getContinente().equals( origen.getContinente() ) ){
                // algo pendiente... o no?
            }
        } else {
            // si no existe el pedido algo anda mal en la lógica previa — lo dejamos claro lanzando excepción
            throw new IllegalStateException("PedidoEntidad inexistente al añadir ruta: idPedido=" + idPedido);
        }

        // 3) Ocupar capacidad en cada vuelo de la ruta (opera de forma sincronizada en Vuelo)
        //    Si alguno falla, lanzamos excepción (y no intentamos rollback interno aquí, porque se asumió validación).
        for (Long idVuelo : programacion.getIdsVueloRuta()) {
            Vuelo vuelo = this.vuelos.get(idVuelo);
            if (vuelo == null) {
                throw new IllegalStateException("VueloEntidad inexistente al añadir ruta: idVuelo=" + idVuelo);
            }
            boolean pudo = vuelo.reservarCapacidad(1);//vuelo.ocuparCapacidad(cantidad);
//            if(loggingReport!=null)
            Bitacora.escribir("anadirRutaSolucion: Ocupar cantidad "+cantidad+" en vuelo: "
                    +vuelo+" Pudo? "+pudo);
            if (!pudo) {
                // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
                // Lanzamos excepción para que el llamador decida rollback/handling.
                throw new IllegalStateException("VueloEntidad sin capacidad al añadir ruta (inconsistencia). idVuelo=" + idVuelo +
                    " cantidad a poner deseada=" + cantidad + " capacidadSinOcuparActual=" + vuelo.getCapacidadSinOcupar());
            }

        }

    }
    public void anadirVariasProgramacionesSolucion(List<Programacion>programaciones){
        for (Programacion programacion : programaciones) {
            anadirProgramacionSolucion(programacion);
        }
    }
    public void anadirProducto(Producto producto) {
        productos.put(producto.getUuid(), producto);
    }


    public List<Vuelo> obtenerVariosVuelosPorIds(List<Long> idsVuelosEnOrden){
        List<Vuelo> vuelosAObtener = new ArrayList<>();
        for(Long id: idsVuelosEnOrden){
            vuelosAObtener.add(vuelos.get(id));
        }
        return vuelosAObtener;
    }

    /**Si no existe o aún no está satifecho, retorna false; de otro modo true*/
    public boolean eliminarPedidoYaSatisfecho(Long idPedido) {
        Pedido p = pedidos.get(idPedido);
        if (p == null ) {
//            p.setEstado(EstadoPedido.ENTREGADO);
            pedidos.remove(idPedido);
            // al card todavía
//            pedidos.get(idPedido).set
            return false; // safarlo?
        }
        int remaining = p.getCantidadProductosPendientes();
        if (remaining <= 0) {
//            pedidos.remove(idPedido); //<- mejor no removamos esto porque el estado global debe poder responder
            p.setEstado(EstadoPedido.ENTREGADO);
            return true;
        }
        return false;
    }


    public Set<Long> devolverIdsAlmacenesNoInfinitos(){
        return almacenes.values().stream()
                .filter(a -> !a.isEsInfinito())
                .map(Almacen::getId)
                .collect(Collectors.toSet());
    }

    public List<Almacen> devolverAlmacenesInfinitosOConStockDisponible(){
        return almacenes.values().stream()
                .filter(a -> a.isEsInfinito()
                        || a.getCapacidadOcupada()  > 0)
                .toList();
    }



    /**
     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o con stock"
     * hacia destinos que NO son infinitos y que, además, tienen pedidos pendientes.
     *
     * Filtra vuelos que no tengan capacidad disponible o que ya partieron, asegura encadenamiento temporal
     * (next.inicio >= prev.fin) y evita ciclos por vuelo/almacén. Devuelve rutas representadas por
     * LinkedList<Long> de ids de vuelo (no por referencias a objetos mutables).
     * NO INCLUYE RUTAS QUE TENGAN UN DESFASE MENOR A UNA HORA
     */
    public List<LinkedList<Long>> generarRutasParaPedidosPendientesBFS(Instant ahora) {
        Bitacora.escribir("Generando rutas candidatas (inicio)");
        // Snapshot local para consistencia durante la generación
        Map<Long, Vuelo> vuelosSnapshot = new HashMap<>(this.vuelos);
        Map<Long, Almacen> almacenesSnapshot = new HashMap<>(this.almacenes);
        Map<Long, Pedido> pedidosSnapshot = new HashMap<>(this.pedidos);

        // 1) destinos: sólo almacenes no infinitos que tengan pedidos pendientes
        Set<Long> idsDestinos = pedidosSnapshot.values().stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getCantidadProductosPendientes() > 0)
                .map(Pedido::getIdAlmacenDestino)
                .filter(Objects::nonNull)
                .filter(id -> {
                    Almacen a = almacenesSnapshot.get(id);
                    return a != null && !a.isEsInfinito();
                })
                .collect(Collectors.toSet());

        if (idsDestinos.isEmpty()) {
            Bitacora.escribir("No hay destinos no infinitos con pedidos pendientes -> no genero rutas.");
            return Collections.emptyList();
        }

        // 2) orígenes candidatos
        List<Almacen> origenes = devolverAlmacenesInfinitosOConStockDisponible();

        // 3) index vuelos por origen (preordenados por inicio para eficiencia)
        Map<Long, List<Vuelo>> vuelosPorAlmacenOrigenId = vuelosSnapshot.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Vuelo::getIdAlmacenOrigen,
                        Collectors.mapping(Function.identity(),
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    list.sort(Comparator.comparing(Vuelo::getInicio, Comparator.nullsLast(Comparator.naturalOrder())));
                                    return list;
                                }))
                ));

        List<LinkedList<Long>> resultado = new ArrayList<>();
        Set<String> rutasVistas = new HashSet<>(); // para unicidad por signature "id1-id2-..."

        for (Long destId : idsDestinos) {
            int rutasEncontradasParaDestino = 0;
            for (Almacen origen : origenes) {
                if (rutasEncontradasParaDestino >= MAX_RUTAS_POR_DESTINO) break;

                List<Vuelo> iniciales = vuelosPorAlmacenOrigenId.getOrDefault(origen.getId(), Collections.emptyList());
                Queue<List<Vuelo>> q = new ArrayDeque<>();

                // semilla: vuelos iniciales válidos
                for (Vuelo v : iniciales) {
                    if (v == null) continue;
                    if (v.getCapacidadDisponibleParaReserva() <= 0) continue;
                    if (v.yaPartio(ahora)) continue;
                    // opcional: ignora vuelos con destino que sea igual al origen (no tiene sentido)
                    List<Vuelo> p = new ArrayList<>();
                    p.add(v);
                    q.add(p);
                }

                int rutasPorOrigen = 0;
                while (!q.isEmpty() && rutasPorOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasEncontradasParaDestino < MAX_RUTAS_POR_DESTINO) {

                    List<Vuelo> path = q.poll();
                    if (path == null || path.isEmpty()) continue;

                    Vuelo last = path.get(path.size() - 1);

                    // check llegada al destino
                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
                        String signature = path.stream().map(vf -> String.valueOf(vf.getId())).collect(Collectors.joining("-"));
                        if (!rutasVistas.contains(signature)) {
                            // convertir a Programacion usando ids
                            LinkedList<Long> ids = path.stream().map(Vuelo::getId).collect(Collectors.toCollection(LinkedList::new));
                            LinkedList<Long> ruta = new LinkedList<Long>(ids);
                            resultado.add(ruta);
                            rutasVistas.add(signature);
                            rutasPorOrigen++;
                            rutasEncontradasParaDestino++;
                        }
                        // no expandir más este path
                        continue;
                    }

                    // límite de escalas/tramos
                    if (path.size() >= MAX_LEGS) continue;

                    // expandir
                    List<Vuelo> siguientes = vuelosPorAlmacenOrigenId.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
                    for (Vuelo next : siguientes) {
                        if (next == null) continue;
                        if (next.getCapacidadDisponibleParaReserva() <= 0) continue;
                        if (next.yaPartio(ahora)) continue;

                        // temporal: next.inicio >= last.fin
                        if (next.getInicio() != null && last.getFin() != null && next.getInicio().isBefore(last.getFin())) {
                            continue;
                        }

                        // evitar repetir vuelo
                        boolean repetido = path.stream().anyMatch(u -> Objects.equals(u.getId(), next.getId()));
                        if (repetido) continue;

                        // evitar ciclos por volver a mismo almacen varias veces (conservador)
                        boolean vuelveMismoAlmacen = path.stream().anyMatch(u -> Objects.equals(u.getIdAlmacenDestino(), next.getIdAlmacenDestino()));
                        if (vuelveMismoAlmacen) continue;

                        // crear nuevo candidato y encolar
                        List<Vuelo> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        q.add(newPath);
                    }
                } // end BFS per origin
            } // end origins
        } // end destinos

        Bitacora.escribir("EstadoGlobalMutableProblemaPlanificacion: Rutas para pedidos pendientes, cantidad:" + resultado.size());
//        resultado.forEach(r -> {
//            Bitacora.escribir("EstadoGlobalMutableProblemaPlanificacion: Ruta:");
//            imprimirVuelosDetalladosDeRuta(r);
//        }
//        );
        return resultado;
    }

    public List<LinkedList<Long>> generarRutasParaPedidosPendientesACO(Instant ahora) {

        return null;
    }


    public void imprimirVuelosDetalladosDeRuta(LinkedList<Long> r){
        r.forEach(v -> Bitacora
                .escribir("VueloEntidad en ruta: "+vuelos.get(v)));
    }

    public Map.Entry<Almacen,Integer> simularAlmacenHastaInstanteIlegalmente(Almacen alm, Instant instante){
        Almacen almacenSimuladoHastaInstante = new Almacen(alm);
        long idAlmacenSimulado = alm.getId();
        int maxDiferenciaColapso=0;
        for(Programacion programacion : programaciones){
            List<Vuelo> vuelitos = obtenerVariosVuelosPorIds(programacion.getIdsVueloRuta());
            int cantProdsRuta = 1;
            // procesar cada vuelo: salida en origen, llegada en destino

            for (int i = 0; i < vuelitos.size(); i++) {
                Vuelo vuelo = vuelitos.get(i);
                if (vuelo == null) continue;

                Instant inicio = vuelo.getInicio();
                Instant fin = vuelo.getFin();

                Producto productoASacarOPoner = productos.get(programacion.getUuidProducto());

                // 1) salida: si este almacén es origen y la salida ya ocurrió (instante >= inicio)
                if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.quitarProductoIlegalmente(productoASacarOPoner);
                    }
                }

                // 2) llegada: si este almacén es destino y la llegada ya ocurrió (instante >= fin)
                if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)/*vuelo.getIdAlmacenDestino() == idAlmacenSimulado*/) {
                    if (!instante.isBefore(fin)) { // instante >= fin
                        almacenSimuladoHastaInstante.agregarProductoIlegalmente(productoASacarOPoner);
                        //esto solo para efectos de mostrar el colapso expresamente
                    }
                    int dif = almacenSimuladoHastaInstante.getCapacidadOcupada()-almacenSimuladoHastaInstante.getCapacidadMaxima();
                    if(dif>0){
                        maxDiferenciaColapso =Math.max(maxDiferenciaColapso,dif);
                    }
                    // 3) si es el último vuelo de la ruta, aplicar pickup (liberación tras ventana)
                    if (i == vuelitos.size() - 1) {
                        Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);
                        if (!instante.isBefore(instantePickup)) { // instante >= fin + ventana
                            almacenSimuladoHastaInstante.quitarProductoIlegalmente(productoASacarOPoner);
                        }
                    }
                }
            }
            if((almacenSimuladoHastaInstante.getCapacidadOcupada()-
                    almacenSimuladoHastaInstante.getCapacidadMaxima())>0)
            Bitacora.escribir(
                    "simularAlmacenHastaInstanteYDevolverMaxCantidadColapsada: Máximo de diferencia colapsada: "+
                    maxDiferenciaColapso);
        }
        return Map.entry(almacenSimuladoHastaInstante,maxDiferenciaColapso);
    }



    public static HashMap<Long, PedidoParaAxel> pedidosDesdeEstadoGlobal(EstadoGlobal estadoGlobal,
                                                                         List<Programacion> programaciones) {
        HashMap<Long, PedidoParaAxel> result = new HashMap<>();
        if (estadoGlobal == null) return result;

        // 1) Inicializar entrada para TODOS los pedidos conocidos en el estado (incluso si no tienen rutas)
        Map<Long, Pedido> pedidosMapa = estadoGlobal.getPedidos();
        if (pedidosMapa != null) {
            for (Map.Entry<Long, Pedido> e : pedidosMapa.entrySet()) {
                Long idPedido = e.getKey();
                Pedido p = e.getValue();
                if (idPedido == null || p == null) continue;
                result.put(idPedido, new PedidoParaAxel(p));
            }
        }

        // 2) Iterar rutas generadas por el algoritmo y agruparlas por idPedidoAsociado
        List<Programacion> rutas = new ArrayList<>( programaciones );
        if (rutas == null || rutas.isEmpty()) {
            // no hay rutas: devolvemos mapa con pedidos y listas vacías
            return result;
        }

        for (Programacion ruta : rutas) {
            if (ruta == null) continue;
            long idPedidoAsoc = ruta.getIdPedido();

            // caso defensivo: id inválido (ej.: -1L) -> log y saltar
            if (idPedidoAsoc <= 0) {
                continue;
            }

            // obtener/crear entrada en el map
            PedidoParaAxel entry = result.get(idPedidoAsoc);
            if (entry == null) {
                // el pedido no estaba en estadoGlobal.getPedidos() (inconsistencia):
                Pedido pFromState = estadoGlobal.getPedidos().get(idPedidoAsoc);
                if (pFromState != null) {
                    entry = new PedidoParaAxel(pFromState);
                    result.put(idPedidoAsoc, entry);
                } else {

                    continue;
                }
            }

            // Añadir la ruta a la lista del pedido
            entry.getMiniPedidos().add(ruta);
        }

        return result;
    }

    public List<Pedido> obtenerPedidosPendientesDeEntregaYProgram(){
        return this.getPedidos().values()
                .stream()
                .filter(Pedido -> Pedido.getCantidadProductosPendientes()>0)
                .collect(Collectors.toList());
    }



    public void crearIndiceIdsRutasPorAlmacenDestino(List<LinkedList<Long>> rutasPosibles) {
        HashMap<Long, List<LinkedList<Long>>> indice = new HashMap<>();
        for(Almacen almacen : almacenes.values()){
            List<LinkedList<Long>> rutasDelAlmacen = rutasPosibles
                    .stream()
                    .filter(idsVuelosRuta ->
                                    almacenes.get(
                                            vuelos.get(idsVuelosRuta.getLast())
                                                    .getIdAlmacenDestino())
                                            .getId()==almacen.getId()
                    )
                    .toList();
            indice.put(almacen.getId(),rutasDelAlmacen);
        }

        rutasPorIdAlmacenDestino =indice;
//        loggingReport.appendReport("El índice de rutas por almacén es: " + rutasPorIdAlmacenDestino);
    }



    public List<Producto> obtenerProductosAlmacenOrigenEnRuta(LinkedList<Long> ruta) {
        // Dividir los prods del almacen origen en prods intercontinentales y no intercont
        Vuelo primerVuelo = vuelos.get(ruta.getFirst());
        Almacen almacenOrigen =  almacenes.get(primerVuelo.getIdAlmacenOrigen());

        loggingReport.appendReport("Almacén origen ahora: "+almacenOrigen);

        Almacen almacenOrigenAlInicioRuta = getAlmacenEnInstante(almacenOrigen,primerVuelo.getInicio());
        loggingReport.appendReport("Almacén origen al inicio de la ruta: "+almacenOrigenAlInicioRuta);
        almacenOrigenAlInicioRuta.getIdsProductosExistentes().forEach(idProductosExistente -> {
//            loggingReport.appendReport(" - Producto: "+ idProductosExistente + "-" +productos.get(idProductosExistente));
//            System.out.println(" - Producto: "+ idProductosExistente +"-"+productos.get(idProductosExistente));
        });

        List<Producto> prods = almacenOrigenAlInicioRuta.getIdsProductosExistentes()
                .stream()
                .map(uuid -> productos.get(uuid))
                .filter(Objects::nonNull)
                .toList();

        if(almacenOrigenAlInicioRuta.getIdsProductosExistentes().size() != prods.size()){
            loggingReport.appendReport(
                    "Advertencia: Algunos productos del almacén origen no " +
                            "se encontraron en el mapa de productos.");
            for(UUID uuid : almacenOrigenAlInicioRuta.getIdsProductosExistentes()){
                if(!productos.containsKey(uuid)){
                    loggingReport.appendReport(" - Producto faltante: "+ uuid);
                }
            }
        }

        return prods;
    }

    public Almacen getAlmacenEnInstante(Almacen almacen, Instant instante) { // nuevo

        Almacen almacenSimuladoHastaInstante = new Almacen(almacen);
        long idAlmacenSimulado = almacen.getId();

        for(Programacion programacionProd : programaciones){
            List<Vuelo> vuelitos = obtenerVariosVuelosPorIds(programacionProd.getIdsVueloRuta());
//            int cantProdsRuta = 1;
            Producto productoProgramado = productos.get(programacionProd.getUuidProducto());
            if(productoProgramado==null) {
                System.out.println("getAlmacenEnInstante: productoProgramado es null para programacion ");
                loggingReport.appendReport("getAlmacenEnInstante: productoProgramado es null");
                continue;
            }
            // procesar cada vuelo: salida en origen, llegada en destino
            for (int i = 0; i < vuelitos.size(); i++) {
                Vuelo vuelo = vuelitos.get(i);
                if (vuelo == null) continue;

                Instant inicio = vuelo.getInicio();
                Instant fin = vuelo.getFin();

                // 1) salida: si este almacén es origen y la salida ya ocurrió (instante >= inicio)
                if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.quitarProducto(productoProgramado);
                    }
                }

                // 2) llegada: si este almacén es destino y la llegada ya ocurrió (instante >= fin)
                if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)/*vuelo.getIdAlmacenDestino() == idAlmacenSimulado*/) {
                    if (!instante.isBefore(fin)) { // instante >= fin
                        almacenSimuladoHastaInstante.agregarProducto(productoProgramado);
                        //esto solo para efectos de mostrar el colapso expresamente
                    }

                    // 3) si es el último vuelo de la ruta, aplicar pickup (liberación tras ventana)
                    if (i == vuelitos.size() - 1) {
                        Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);
                        if (!instante.isBefore(instantePickup)) { // instante >= fin + ventana
                            almacenSimuladoHastaInstante.quitarProducto(productoProgramado);
                        }
                    }
                }
            }
        }
        return almacenSimuladoHastaInstante;
    }

    /* log para get almacen en instante
    * loggingReport.appendReport("Simulando almacen id=" + idAlmacenSimulado + " hasta " + instante +
                ". Programaciones totales: " + programaciones.size());

        int hits = 0;
        for (Programacion p : programaciones) {
            for (Long vid : p.getIdsVueloRuta()) {
                Vuelo v = vuelos.get(vid);
                if (v == null) continue;
                if (Objects.equals(v.getIdAlmacenOrigen(), idAlmacenSimulado) || Objects.equals(v.getIdAlmacenDestino(), idAlmacenSimulado)) {
                    loggingReport.appendReport(" -> Programacion " + p.getUuidProducto() + " ruta " + p.getIdsVueloRuta() +
                            " touch: origen=" + v.getIdAlmacenOrigen() + " dest=" + v.getIdAlmacenDestino() +
                            " inicio=" + v.getInicio() + " fin=" + v.getFin());
                    hits++;
                    break;
                }
            }
        }
        loggingReport.appendReport("Programaciones que tocan este almacen: " + hits);*/
    public Almacen buscarAlmacen(Long id)
    {
        return this.almacenes.get(id);
    }

    // Para facilitarle la vida a la simulación (ctx)

    public Almacen obtenerAlmacenPorId(long idAlmacenDestino) {
        return almacenes.get(idAlmacenDestino);
    }
    public Producto obtenerProductoPorUuid(UUID uuid) {
        return productos.get(uuid);
    }
    public Vuelo obtenerVueloPorId(long  id) {
        return vuelos.get(id);
    }

    public boolean entregarProductoEnPedido(long idPedido, @NotNull Producto producto){
        Pedido pedidoEnCuestion = pedidos.get(idPedido);
        if (!pedidoEnCuestion.agregarProductoEntregado(producto)){ // <- muta
            return false;
        }
        producto.setEntregado(true);

//        Continente continenteLlegada = almacenes.get(producto.getIdAlmacenInfinitoOrigen()).getContinente();
//        if(!pedidoEnCuestion.getContinenteDestino().equals(continenteLlegada)){
//            pedidoEnCuestion.setIntercontinentalAhora(true); // normal si lo era o no antes.
//        }

        return true;
    }

    public List<AbstractMap.SimpleEntry< LinkedList<Vuelo>, Integer >> obtenerRutasDePedido(long idPedido){
        List<Programacion> programacionesDelPedido = programaciones.stream()
                .filter(programacion -> programacion.getIdPedido() == idPedido)
                .toList();

        Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = programacionesDelPedido.stream()
                    .collect(Collectors.groupingBy(Programacion::getIdsVueloRuta));

        return programacionesPorRuta.keySet().stream().map(
                longs ->  new AbstractMap.SimpleEntry<>(
                        new LinkedList<>( longs.stream().map(aLong -> vuelos.get(aLong)).toList()),
                        programacionesPorRuta.get(longs).size() // numero de programaciones de la ruta, o sea número de productos
                )
                )
                .toList();
    }

    public LinkedList<Almacen> obtenerAlmacenesPorRuta(LinkedList<Vuelo> vuelos){
        LinkedList<Almacen> almacenesRuta = new LinkedList<>();

        for(Vuelo vuelo : vuelos){
            if(vuelos.getFirst().equals(vuelo)){
                almacenesRuta.add( almacenes.get( vuelo.getIdAlmacenOrigen() ));
            }
            almacenesRuta.add( almacenes.get( vuelo.getIdAlmacenDestino() ));
        }

        return almacenesRuta;
    }

//    public boolean pedidoFueReprogramado(Pedido pedido){
//         return programaciones.stream().anyMatch(
//                 programacion -> programacion.getIdPedido() == pedido.getId() && !programacion.isActivo()
//         ); // Devolvemos si alguna progrmacion
//    }

    public List<Programacion> obtenerProgramacionesQueUsanRuta(LinkedList<Long> ruta) {

        return programaciones.stream().filter(programacion ->
                        programacion.getIdsVueloRuta().equals(ruta) && programacion.isActivo())
                .collect(Collectors.toList());

    }

    public List<Producto> obtenerProductosQueUsanRutaActiva(LinkedList<Long> ruta) {

        return obtenerProgramacionesQueUsanRuta(ruta).stream().map(programacion ->
                productos.get(programacion.getIdsVueloRuta())).collect(Collectors.toList());

    }

    public List<RutaProgramadaListadaDTO> obtenerRutasProgramadas(){

        Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = programaciones.stream()
                .collect(Collectors.groupingBy(Programacion::getIdsVueloRuta));

        return programacionesPorRuta.keySet().stream().map(
                longs -> {
                    return new RutaProgramadaListadaDTO(
                            new LinkedList<>(
                            longs.stream().map(aLong -> {
                                Vuelo vuelo = vuelos.get(aLong);
                                Almacen almOrigen = almacenes.get(vuelo.getIdAlmacenOrigen());
                                Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
                                return new VueloResumidoDTO(
                                        vuelo.getId(),
                                        almOrigen.getNombreCiudad(),
                                        almDestino.getNombreCiudad()
                                );
                            }).toList())
                            ,longs
                    );
                }
        ).collect(Collectors.toList());
    }

    public EstadoGlobal obtenerDatosParaAlgoritmoDesdeMemoria(Instant instanteProgramado, ContextoSimulacion ctx) {
        // -- PREPARAR DATOS FILTRADOS PARA EL ALGORITMO --
        Map<Long, Vuelo> vuelosBase = getVuelos();
        Map<Long, Vuelo> vuelosParaAlgoritmo = vuelosBase.entrySet().stream()
                .filter(longVueloEntry -> {
                    Vuelo vuelo = vuelosBase.get(longVueloEntry.getKey());
                    return
                        !vuelo.isCancelado()
                                && vuelo.getFin().isBefore(instanteProgramado.plus(3, ChronoUnit.DAYS))
                                && !vuelo.getInicio().isBefore(ctx.getInicioSimulacion())
                                && vuelo.getInicio().isAfter(ctx.obtenerElAhora().plus(2, ChronoUnit.MINUTES));
                        // El vuelo no está cancelado y llega antes del instante en que se planificará más 3 días
                        // (ya que se toman los pedidos solo hasta ahora! Si eso cambia, acá también deberíamos cambiar)
                        // Además se toman solo los vuelos desde que inició la simulación (posiblemente en curso y que
                        // traerán productos interesantes)
                        // ADEMÁS VUELOS QUE EMPIECEN AL MENOS 2 HORAS DESPUÉS DEL AHORA DE LA SIMULACIÓN <- DANIEL, TA BIEN?
                    }
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue
//                        e -> new Vuelo(e.getValue()) // copy constructor
                ));
        Map<Long, Almacen> almacenesParaAlgoritmo = getAlmacenes().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue
//                        e -> new Almacen(e.getValue()) // copy constructor
                ));

        Map<Long, Pedido> pedidosBase = getPedidos();
        Map<Long, Pedido> pedidosParaAlgoritmo = pedidosBase.entrySet().stream()
                .filter(longPedidoEntry ->
                        {
                            Pedido pedido = pedidosBase.get(longPedidoEntry.getKey());
                            return !pedido.getInstanteRegistro().isBefore(ctx.getInicioSimulacion())
                                    && pedido.getInstanteRegistro().isBefore(instanteProgramado)
                                    && pedido.getCantidadProductosEntregados()<pedido.getCantidadProductosPedidos();
                        }
                        // pedido que se haya registrado
                        // después o igual al inicio de la simu pero antes del instante en que se planifica.
                ).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue)
                );
        Map<UUID, Producto> prodsBase = ctx.getEstado().getProductos(); // <- necesario hacerle deep copy? tal vez

        // -- TERMINAR PREPARACIÓN DATOS --
        ctx.log("EventoTriggerPlanificacion: Datos preparados para el algoritmo - " +
                pedidosParaAlgoritmo.size() + " pedidos, " +
                vuelosParaAlgoritmo.size() + " vuelos, " +
                almacenesParaAlgoritmo.size() + " almacenes."+
                prodsBase.size() + " productos."
                );

        System.out.println("========= DATOS PLANIFICACION =========");
        System.out.println("Pedidos: " +  pedidosParaAlgoritmo.size());
        System.out.println("Vuelos: " +  vuelosParaAlgoritmo.size());
        System.out.println("Almacenes: " +  almacenesParaAlgoritmo.size());
        System.out.println("Productos: " +  prodsBase.size());
        System.out.println("=========================================\n");

        return new EstadoGlobal(almacenesParaAlgoritmo,vuelosParaAlgoritmo, pedidosParaAlgoritmo, null, prodsBase);
    }

    @Override
    public String toString() {
        return "Estado{" +
                ", pedidos=" + pedidos.size() +
                ", vuelos=" + vuelos.size() +
                ", almacenes=" + almacenes.size() +
                ", programaciones="+programaciones.size() +
                ", productos=" + productos.size() +
//                ", estado=" + estado +
                '}';
    }

}

