package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

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

//    @Setter
//    LoggingReport loggingReport = new LoggingReport(); // mientras usamos la bitácora

    // índices
    HashMap<Long, List<Long>> idsVuelosPorOrigen;
    HashMap<Long, List<Long>> idsVuelosPorDestino;
    HashMap<Long, List<Long>> idsPedidosPorDestino;
    HashMap<Long, List<LinkedList<Long>> > rutasPorIdAlmacenDestino = new HashMap<>(); //??

    Set<Long> idsAlmacenesNoInfinitos = new HashSet<>();
    Set<Long> idsAlmacenesInfinitosOConStock = new HashSet<>();
//    Set<AlmacenParaAlgoritmo> almacenesInfinitosOConStock = new HashSet<>();

    private final int HORAS_PARA_RECOGER_PEDIDO = 2;
    private final long SEGUNDOS_PARA_RECOGER_PEDIDO = HORAS_PARA_RECOGER_PEDIDO * 3600L;
    private static final int MAX_LEGS = 10; // número máximo de tramos por ruta (incluye primer vuelo)
    private static final int MAX_RUTAS_POR_DESTINO = 25;
    private static final int MAX_RUTAS_POR_ORIGEN = 15;

//    public static EstadoGlobal desdeEntradaPlanificacion(EntradaProblemaPlanificacion entradaPlanificacion) {
//        return new EstadoGlobal(
//                entradaPlanificacion.almacenes,entradaPlanificacion.vuelos,entradaPlanificacion.pedidos);
//    }

    public EstadoGlobal(HashMap<Long, Almacen> almacenes,
                        HashMap<Long, Vuelo> vuelos,
                        HashMap<Long, Pedido> pedidos,
                        List<Programacion> programaciones
                        ) {
        this.almacenes = almacenes != null?new HashMap<>(almacenes):new HashMap<>();
        this.vuelos = vuelos != null?new HashMap<>(vuelos):new HashMap<>();
        this.pedidos = pedidos != null?new HashMap<>(pedidos):new HashMap<>();
        this.programaciones = programaciones!=null?new LinkedList<>(programaciones): new LinkedList<>();

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
                .map(Programacion::new) // Uses the copy constructor
                .toList();

        almacenes= copiaAlmacenes;
        vuelos= copiaVuelos;
        pedidos= copiaPedidos;
        programaciones= copiaProgramaciones;

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
    
    public boolean rutaTieneCapacidadEnEstadoActual(LinkedList<Long> rutaPlanificacion, Pedido pedido) {
        if (!validarCapacidadAvionesEnRuta(rutaPlanificacion, pedido)){
            return false;
        }
        if(!validarCapacidadAlmacenesEnLlegadasOSalidas(rutaPlanificacion)){
            return false;
        }
        if(!validarCapacidadAlmacenesEntremedioLlegadasOSalidas(rutaPlanificacion)){
            return false;
        }
        return true;

    }

    private boolean validarCapacidadAlmacenesEntremedioLlegadasOSalidas(LinkedList<Long> rutaPlanificacion) {
        int asignable = 1;
        int maxDiferenciaColapso = 0;
        List<Vuelo> vuelosRuta = rutaPlanificacion
                .stream()
                .map(id -> vuelos.get(id))
                .toList();
        for (Vuelo vuelo : vuelosRuta) {
            Map.Entry<Almacen,Integer> almacenPosiblementeColapsado;
            Producto señuelo = new Producto(0L, new LinkedList<>());
            if (vuelo != vuelosRuta.getLast()) {
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
            Bitacora.escribir(
                    "Simulación del almacén destino hasta siguiente inicio: "+almacenPosiblementeColapsado
            );
            int diferenciaQueHizoColapso =almacenPosiblementeColapsado.getValue(); /*almacenPosiblementeColapsado.getKey().getCapacidadOcupada()
                    -almacenPosiblementeColapsado.getKey().getCapacidadMaxima();*/
            if (diferenciaQueHizoColapso>0) {//colapsado
                Bitacora.escribir("El almacén colapsaría con una diferencia de: "
                        + diferenciaQueHizoColapso);
//                    asignable -= diferenciaQueHizoColapso;
                maxDiferenciaColapso=Math.max(maxDiferenciaColapso,diferenciaQueHizoColapso);
            }
        }
        if(maxDiferenciaColapso>0) {
            Bitacora.escribir("maxDiferenciaColapso " + maxDiferenciaColapso, "no se puede llevar " +
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
            if (simulOrigen.getCapacidadSinOcupar() < 1) {
                return false; // origen no puede suministrar en ese instante
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
        Bitacora.escribir("Programación añadida al estado global "+programacion);
        this.programaciones.add(programacion);
        // 2) Actualizar el pedido: incrementar cantidadProgramada
        Pedido pedido = this.pedidos.get(idPedido);
        if (pedido != null) {
            Almacen origen = almacenes.get(productoElegido.getIdAlmacenInfinitoOrigen());
            pedido.agregarProductoProgramado(productos.get(programacion.getUuidProducto()),origen.getContinente());
            //^^^esto actualiza el estado interno del pedido en el hashmap.
            int restante = pedido.getCantidadProductosPendientes();
            if (restante <= 0)
                Bitacora.escribir("Pedido id=" + pedido.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
            if(almacenes.get(pedido.getIdAlmacenDestino()).getContinente().equals( origen.getContinente() ) ){
                // algo pendiente...
            }
        } else {
            // si no existe el pedido algo anda mal en la lógica previa — lo dejamos claro lanzando excepción
            throw new IllegalStateException("Pedido inexistente al añadir ruta: idPedido=" + idPedido);
        }

        // 3) Ocupar capacidad en cada vuelo de la ruta (opera de forma sincronizada en VueloParaAlgoritmo)
        //    Si alguno falla, lanzamos excepción (y no intentamos rollback interno aquí, porque se asumió validación).
        for (Long idVuelo : programacion.getIdsVueloRuta()) {
            Vuelo vuelo = this.vuelos.get(idVuelo);
            if (vuelo == null) {
                throw new IllegalStateException("Vuelo inexistente al añadir ruta: idVuelo=" + idVuelo);
            }
            boolean pudo = vuelo.reservarCapacidad(1);//vuelo.ocuparCapacidad(cantidad);
//            if(loggingReport!=null)
            Bitacora.escribir("anadirRutaSolucion: Ocupar cantidad "+cantidad+" en vuelo: "
                    +vuelo+" Pudo? "+pudo);
            if (!pudo) {
                // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
                // Lanzamos excepción para que el llamador decida rollback/handling.
                throw new IllegalStateException("Vuelo sin capacidad al añadir ruta (inconsistencia). idVuelo=" + idVuelo +
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


    List<Vuelo> obtenerVariosVuelosPorIds(List<Long> idsVuelosEnOrden){
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
            pedidos.remove(idPedido);
            return false; // safarlo?
        }
        int remaining = p.getCantidadProductosPendientes();
        if (remaining <= 0) {
            pedidos.remove(idPedido);
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
    public List<LinkedList<Long>> generarRutasParaPedidosPendientes() {
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
        List<Almacen> origenes = devolverAlmacenesInfinitosOConStockDisponible(); // usa mesa (ya definida)

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
                    if (v.getCapacidadDisponibleParaReserva()>0) continue;
                    if (v.yaPartio()) continue;
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
                            // convertir a RutaProgramadaParaAlgoritmo usando ids
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
                        if (next.getCapacidadDisponibleParaReserva()>0) continue;
                        if (next.yaPartio()) continue;

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
        resultado.forEach(r -> {
            Bitacora.escribir("EstadoGlobalMutableProblemaPlanificacion: Ruta:");
            imprimirVuelosDetalladosDeRuta(r);
        }
        );
        return resultado;
    }
    public void imprimirVuelosDetalladosDeRuta(LinkedList<Long> r){
        r.forEach(v -> Bitacora
                .escribir("Vuelo en ruta: "+vuelos.get(v)));
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



    public static HashMap<Long, PedidoParaAxel> pedidosDesdeEstadoGlobal(EstadoGlobal estadoGlobal) {
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
        List<Programacion> rutas = new ArrayList<>(estadoGlobal.getProgramaciones() );
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
                .filter(pedidoParaAlgoritmo -> pedidoParaAlgoritmo.getCantidadProductosPendientes()>0)
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
    }

    public List<Producto> obtenerProductosAlmacenOrigenEnRuta(LinkedList<Long> ruta) {
        // Dividir los prods del almacen origen en prods intercontinentales y no intercont
        Vuelo primerVuelo = vuelos.get(ruta.getFirst());
        Almacen almacenOrigen =  almacenes.get(primerVuelo);


        return getAlmacenEnInstante(almacenOrigen,primerVuelo.getInicio()).getIdsProductosExistentes()
                .stream()
                .map(uuid -> productos.get(uuid))
                .toList();
    }

    public Almacen getAlmacenEnInstante(Almacen almacen, Instant instante) { // nuevo
        Almacen almacenSimuladoHastaInstante = new Almacen(almacen);
        long idAlmacenSimulado = almacen.getId();
        for(Programacion programacionProd : programaciones){
            List<Vuelo> vuelitos = obtenerVariosVuelosPorIds(programacionProd.getIdsVueloRuta());
//            int cantProdsRuta = 1;
            Producto productoProgramado = productos.get(programacionProd.getUuidProducto());
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

}

