package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.PrettyPrinter;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX;

@Getter
public class EstadoGlobal implements Serializable
{
    @NotNull
    @Setter
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
    LoggingReport lr; // mientras usamos la bitácora

    // índices
    HashMap<Long, List<Long>> idsVuelosPorOrigen; // No se usa
    HashMap<Long, List<Long>> idsVuelosPorDestino; // No se usa
    HashMap<Long, List<Long>> idsPedidosPorDestino; // No se usa
    HashMap<Long, List<Long>> idsVuelosDondeApareceAlmacenOrdenados = new HashMap<>(); // Se usa
    HashMap<Long, List<Programacion>> programacionesPorIdVueloIncluido = new HashMap<>(); // Se usa
    HashMap<Long, List<LinkedList<Long>>> rutasPorIdAlmacenDestino = new HashMap<>(); // Se usa


    // Set<Almacen> almacenesInfinitosOConStock = new HashSet<>();

    private final int HORAS_PARA_RECOGER_PEDIDO = 2;
    private final long SEGUNDOS_PARA_RECOGER_PEDIDO = HORAS_PARA_RECOGER_PEDIDO * 3600L;
    private static final int MAX_LEGS = 4; // número máximo de tramos por ruta (incluye primer vuelo)


    public EstadoGlobal(Map<Long, Almacen> almacenes,
            Map<Long, Vuelo> vuelos,
            Map<Long, Pedido> pedidos,
            List<Programacion> programaciones,
            Map<UUID, Producto> productos)
    {
        this.almacenes = almacenes != null ? new HashMap<>(almacenes) : new HashMap<>();
        this.vuelos = vuelos != null ? new HashMap<>(vuelos) : new HashMap<>();
        this.pedidos = pedidos != null ? new HashMap<>(pedidos) : new HashMap<>();
        this.programaciones = programaciones != null
                ? new LinkedList<>(programaciones)
                : new LinkedList<>();
        this.productos = productos != null ? new HashMap<>(productos) : new HashMap<>(); // ⚠️
                                                                                         // IMPORTANTE:
                                                                                         // inicializar
                                                                                         // el
                                                                                         // HashMap
                                                                                         // de
                                                                                         // productos

        // A partir de acá, inicializar índices necesarios:
        this.inicializarIndices();
    }

    public EstadoGlobal(EstadoGlobal estadoGlobal)
    { // clonación
        HashMap<Long, Almacen> copiaAlmacenes = new HashMap<>();
        HashMap<Long, Almacen> originalAlmacenes = estadoGlobal.getAlmacenes();
        for (Map.Entry<Long, Almacen> entry : originalAlmacenes.entrySet())
        {
            Long newKey = entry.getKey();
            Almacen newValue = new Almacen(entry.getValue());
            copiaAlmacenes.put(newKey, newValue);
        }

        HashMap<Long, Vuelo> copiaVuelos = new HashMap<>();
        HashMap<Long, Vuelo> originalVuelos = estadoGlobal.getVuelos();
        for (Map.Entry<Long, Vuelo> entry : originalVuelos.entrySet())
        {
            Long newKey = entry.getKey();
            Vuelo newValue = new Vuelo(entry.getValue());
            copiaVuelos.put(newKey, newValue);
        }

        HashMap<Long, Pedido> copiaPedidos = new HashMap<>();
        HashMap<Long, Pedido> originalPedidos = estadoGlobal.getPedidos();
        for (Map.Entry<Long, Pedido> entry : originalPedidos.entrySet())
        {
            Long newKey = entry.getKey();
            Pedido newValue = new Pedido(entry.getValue());
            copiaPedidos.put(newKey, newValue);
        }

        List<Programacion> copiaProgramaciones = estadoGlobal.getProgramaciones().stream()
                .map(Programacion::new)
                .collect(Collectors.toCollection(LinkedList::new)); // <- Antes lo creaba como
                                                                    // inmutable, mal mal,
        // debe ser mutable y concreto con la LinkedList, el stream toList es inmutable

        HashMap<UUID, Producto> copiaProds = new HashMap<>();
        HashMap<UUID, Producto> originalProds = estadoGlobal.getProductos();
        for (Map.Entry<UUID, Producto> entry : originalProds.entrySet())
        {
            UUID newKey = entry.getKey();
            Producto newValue = new Producto(entry.getValue());
            copiaProds.put(newKey, newValue);
        }

        almacenes = copiaAlmacenes;
        vuelos = copiaVuelos;
        pedidos = copiaPedidos;
        programaciones = copiaProgramaciones;
        productos = copiaProds; // XDDDDDnew HashMap<>(); // ⚠️ IMPORTANTE: inicializar productos
                                // también en constructor de copia
        lr = estadoGlobal.getLr();
        inicializarIndices();
    }

    private void inicializarIndices()
    {
        // ...
        List<Vuelo> vuelosOrdenadosPorInicio = vuelos.values()
                .stream()
                .sorted(Comparator.comparing(Vuelo::getInicio))
                .toList();

        for (Vuelo v : vuelosOrdenadosPorInicio)
        {
            idsVuelosDondeApareceAlmacenOrdenados
                    .computeIfAbsent(v.getIdAlmacenOrigen(), k -> new LinkedList<>())
                    .add(v.getId());
            idsVuelosDondeApareceAlmacenOrdenados
                    .computeIfAbsent(v.getIdAlmacenDestino(), k -> new LinkedList<>())
                    .add(v.getId());
        }
    }

    public boolean hayPedidosPendientesPorProgramar()
    {
        if (pedidos == null || pedidos.isEmpty())
        {
            return false;
        }

        return pedidos.values().stream().anyMatch(pedido -> pedido.getCantidadProductosPendientes() > 0);
    }

    public int contarPedidosPendientes()
    {
        if (pedidos == null || pedidos.isEmpty())
            return 0;
        int c = 0;
        for (Pedido p : pedidos.values())
        {
            if (p == null)
                continue;
            // int total = p.getCantidadProductosPedidos() == null ? 0 :
            // p.getCantidadProductosPedidos();
            // int entregados = p.getCantidadProductosEntregados() == null ? 0 :
            // p.getCantidadProductosEntregados();
            // int programados = p.getCantidadProductosProgramados() == null ? 0 :
            // p.getCantidadProductosProgramados();
            int remaining = p.getCantidadProductosPendientes();
            if (remaining > 0)
                c++;
        }
        return c;
    }

    /**
     * Añade a la mesa (estado global) una programación ya validada y actualiza
     * todos los estados respectivos. IMPORTANTE: se asume que la programación fue
     * validada previamente contra el estado actual (capacidad de vuelos/almacenes,
     * conectividad temporal, etc.). Si hay una inconsistencia (por ejemplo, falta
     * de capacidad en un vuelo) lanzamos IllegalStateException para detectar
     * condiciones de carrera o errores lógicos. MÁS IMPORTANTE: no usar esta
     * función en cualquier contexto fuera del algoritmo, ya que el algoritmo usa
     * como artificios el mutar estados como la capacidad ocupada de vuelos =
     * reservados
     */
    public void anadirProgramacionSolucion(Programacion programacion, Instant instanteProgra) {
        if (programacion == null)
            return;

        // Protección simple: si ya existe la misma instancia no la volvemos a añadir
        if (this.programaciones.contains(programacion))
            return; // ya añadida, nada que hacer

        this.programaciones.add(programacion);

        Pedido pedido = this.pedidos.get(programacion.getIdPedido());

        asignarProductoAPedido_Ruta_Almacenes_Vuelos(pedido, programacion.getIdsVueloRuta(), programacion, instanteProgra);

    }

    /*
     * Operacion atomica de asignacion de una lista de productos a Pedido, Ruta,
     * Almacenes y Vuelos
     */
    public boolean asignarProductoAPedido_Ruta_Almacenes_Vuelos(
            Pedido pedido,
            LinkedList<Long> rutaAAsignar,
            Programacion programacion,
            Instant instante    )    {
        Producto productoAAsignar = productos.get(programacion.getUuidProducto());
        if(productoAAsignar == null) throw new RuntimeException("EL PRODUCTO ES NULO, PQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ");
        Almacen origen = almacenes.get(productoAAsignar.getIdAlmacenInfinitoOrigen());

        boolean asignadoCorrectamente, a,b;

        asignadoCorrectamente = true;

        asignadoCorrectamente &= this.asignarProgramacionARuta(rutaAAsignar, programacion);
        if (!asignadoCorrectamente){
            Bitacora.escribir("Algo fallo en la asignación del producto a su ruta");
            throw new IllegalStateException("NO ASIGNADO CORRECTAMENTE EL PROD A A LA SOLUCIÓN, RAZÓN: RUTA");
            // SI QUIERES QUE FUNQUE LA SIMU SUPERFICIALMENTE COMENTA ESTO^^ ESTÁ FALLANDO XD
        }

        asignadoCorrectamente &= pedido.agregarProductoProgramadoEnAlgoritmo(productoAAsignar, origen.getContinente());
        if (!asignadoCorrectamente){
            Bitacora.escribir("Algo fallo en la asignación del producto a sus almacenes, a sus vuelos o a su pedido");
            throw new IllegalStateException("NO ASIGNADO CORRECTAMENTE EL PROD A A LA SOLUCIÓN, RAZÓN: PEDIDO");
        }

        productoAAsignar.marcarComoProgramado(instante);

        return asignadoCorrectamente;
    }

    /*
     * Asigna un producto a la Ruta
     */
    private Boolean asignarProgramacionARuta(List<Long> ruta, Programacion prog){
        List<Vuelo> vuelitos = ruta.stream().map(v -> this.vuelos.get(v)).toList();

        Boolean asignadoCorrectamente;

        asignadoCorrectamente = true;

        for (Vuelo vuelo : vuelitos){
            Almacen almOrigen = almacenes.get(vuelo.getIdAlmacenOrigen());
            Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());

            asignarProgAVuelo(vuelo, prog); // gestiona su propio error
            
            if (!almOrigen.isEsInfinito() && !almOrigen.registrarCambioNegativo(vuelo.getInicio(), 1) ){
                return false;
            }

            if (!almDestino.isEsInfinito() && !almDestino.registrarCambioPositivo(vuelo.getFin(), 1)){
                return false;
            }

            // Si es el último vuelo de la ruta:
            if (vuelo.equals(vuelitos.get(vuelitos.size() - 1))) {
                Instant recojo = vuelo.getFin().plus(HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS);
                if (!almDestino.registrarCambioNegativo(recojo, 1)) {
                    return false;
                }
            }

            // Actualiza mi índice
            programacionesPorIdVueloIncluido
                    .computeIfAbsent(vuelo.getId(), k -> new LinkedList<>())
                    .add(prog);
        }

        return asignadoCorrectamente;
    }

    private boolean asignarProgAVuelo(Vuelo vuelo, Programacion programacion){
        boolean pudo = vuelo.reservarCapacidad(programacion.getUuidProducto());// vuelo.ocuparCapacidad(cantidad);
        if (lr != null && !pudo)
            lr.appendReport("anadirRutaSolucion: Ocupar cantidad " + 1 + " en vuelo: "
                    + vuelo + " Pudo? " + pudo);
        if (!pudo){
            // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
            // Lanzamos excepción para que el llamador decida rollback/handling.
            throw new IllegalStateException(
                    "VueloEntidad sin capacidad al añadir ruta (inconsistencia). vuelo=" + vuelo
                            + " cantidad a poner deseada=" + 1
                            + " capacidadSinOcuparActual=" + vuelo.getCapacidadSinOcupar());
        }
        return pudo;
    }


    public void anadirProducto(Producto producto){
        productos.put(producto.getUuid(), producto);
    }

    
    public List<Vuelo> obtenerVariosVuelosPorIds(List<Long> idsVuelosEnOrden,
            EntradaProblemaPlanificacion e)
    {
        List<Vuelo> vuelosAObtener = new LinkedList<>();
        for (Long id : idsVuelosEnOrden)
        {
            Vuelo v = vuelos.get(id);
            if (v == null)
            {
                lr.appendReport("Vuelo no encontrado con " + id);
                if (e != null)
                {
                    lr.appendReport(" ids de la entrada:");
                    e.getEstadoGlobal().getVuelos().keySet()
                            .forEach(aLong -> lr.appendReport(aLong.toString()));
                }
            }
            vuelosAObtener.add(v);
        }
        return vuelosAObtener;
    }

    /** Si no existe o aún no está satifecho, retorna false; de otro modo true */
    public boolean eliminarPedidoYaSatisfecho(Map<Pedido, Double> puntajes, Long idPedido) {
        Pedido p = pedidos.get(idPedido);
        if (p == null) {
            // p.setEstado(EstadoPedido.ENTREGADO);
            pedidos.remove(idPedido);
            // al card todavía
            // pedidos.get(idPedido).set
            return false; // safarlo?
        }
        int remaining = p.getCantidadProductosPendientes();
        if (remaining <= 0) {
            pedidos.remove(idPedido); // <- mejor no removamos esto porque el estado global debe
                                      // poder responder
            puntajes.remove(p);
            p.setEstado(EstadoPedido.ENTREGADO);
            return true;
        }
        return false;
    }

    public Set<Long> devolverIdsAlmacenesNoInfinitos()
    {
        return almacenes.values().stream()
                .filter(a -> !a.isEsInfinito())
                .map(Almacen::getId)
                .collect(Collectors.toSet());
    }

    public List<Almacen> devolverAlmacenesInfinitosOConStockDisponible(Instant ahora)
    {
        return almacenes.values().stream()
                .filter(a ->
                                !productos.isEmpty()  ||
                                a.isEsInfinito()
//                            ||  almacenTieneStockEnInstante(a, ahora)
                // no tiene senttido discriminar aquí porque puede que uno vacío ahora tenga
                // stock a futuro sin embargo, no encuentro la forma de no dar tantos aberrantes incapaces...
                )
                .toList();
    }

    /**
     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o
     * con stock" hacia destinos que NO son infinitos y que, además, tienen pedidos
     * pendientes.
     *
     * Filtra vuelos que no tengan capacidad disponible o que ya partieron, asegura
     * encadenamiento temporal (next.inicio >= prev.fin +
     * Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS) y evita ciclos por vuelo/almacén. Devuelve rutas
     * representadas por LinkedList<Long> de ids de vuelo (no por referencias a
     * objetos mutables). NO INCLUYE RUTAS QUE TENGAN UN DESFASE MENOR A UNA HORA.
     *
     * Cambios clave: - uso consistente de snapshots (vuelosSnapshot,
     * almacenesSnapshot, pedidosSnapshot). - semilla de vuelos iniciales: se
     * descartan si origen no es infinito y no tiene stock en instante de salida. -
     * no expandir rutas cuyo último destino sea un almacén infinito (evita
     * infinitos como intermedios). - al añadir ruta final, se descarta si contiene
     * almacén infinito en posiciones intermedias.
     */
    public List<LinkedList<Long>> generarRutasParaPedidosPendientesBFS(Instant ahora) {
//        Bitacora.escribir("========= GENERACION DE RUTAS =========");

        // Snapshot local para consistencia durante la generación
        Map<Long, Vuelo> vuelosSnapshot = new HashMap<>(this.vuelos);
        Map<Long, Almacen> almacenesSnapshot = new HashMap<>(this.almacenes);
        Map<Long, Pedido> pedidosSnapshot = new HashMap<>(this.pedidos);

        // 1) destinos: sólo almacenes no infinitos que tengan pedidos pendientes
        Set<Long> idAlmacenesConDemadna = this.obtenerAlmacenesConDemanda(pedidosSnapshot, almacenesSnapshot);

        if (idAlmacenesConDemadna.isEmpty()) {
            lr.appendReport(
                    "No hay destinos no infinitos con pedidos pendientes -> no genero rutas.");
            return Collections.emptyList();
        }

        // 2) orígenes candidatos (la función la puedes mejorar, aquí la usamos tal
        // cual)
        List<Almacen> origenes = this.devolverAlmacenesInfinitosOConStockDisponible(ahora);

        // 3) index vuelos por origen (preordenados por inicio para eficiencia) — usando
        // snapshot
        Map<Long, List<Vuelo>> vuelosPorAlmacenOrigenId = vuelosSnapshot.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Vuelo::getIdAlmacenOrigen,
                        Collectors.mapping(Function.identity(),
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    list.sort(Comparator.comparing(Vuelo::getInicio,
                                            Comparator.nullsLast(Comparator.naturalOrder())));
                                    return list;
                                }))));

        List<LinkedList<Long>> resultado = new ArrayList<>();
        Set<String> rutasVistas = new HashSet<>(); // unicidad por signature "id1-id2-..."

        for (Long destId : idAlmacenesConDemadna) {
            int rutasEncontradasParaDestino = 0;

            for (Almacen origen : origenes) {
                if (rutasEncontradasParaDestino >= Hiperparametros.MAX_RUTAS_POR_DESTINO)
                    break;

                // vuelos que salen desde este origen (snapshot)
                List<Vuelo> iniciales = vuelosPorAlmacenOrigenId.getOrDefault(origen.getId(),
                        Collections.emptyList());
                Queue<List<Vuelo>> q = new ArrayDeque<>();

                // SEMILLA: construir paths de 1 tramo desde vuelos iniciales válidos
                for (Vuelo v : iniciales) {
                    if (v == null)
                        continue;
                    if (v.getCapacidadDisponibleParaReserva() <= 0)
                        continue;
                    if (v.yaPartio(ahora))
                        continue;

                    // Sondeo mínimo: comprobar el almacén origen en el instante de salida del
                    // PRIMER vuelo.
                    Almacen origenSnapshot = almacenesSnapshot.get(v.getIdAlmacenOrigen());
                    if (origenSnapshot == null)
                        continue;

                    // Permitir semilla si origen es infinito o si tiene stock en instante de salida
                    // (sondeo ligero)
                    if (!origenSnapshot.isEsInfinito()
                            && !almacenTieneStockEnInstante(origenSnapshot, v.getInicio())){
                        // descartamos seed cuya evidencia mínima indica que no habrá producto en la
                        // salida
                        continue;
                    }

                    List<Vuelo> p = new ArrayList<>();
                    p.add(v);
                    q.add(p);
                }

                int rutasPorOrigen = 0;
                while (!q.isEmpty() &&
                        rutasPorOrigen < Hiperparametros.MAX_RUTAS_POR_ORIGEN &&
                        rutasEncontradasParaDestino < Hiperparametros.MAX_RUTAS_POR_DESTINO) {

                    List<Vuelo> path = q.poll();
                    if (path == null || path.isEmpty())
                        continue;

                    Vuelo last = path.get(path.size() - 1);

                    // check llegada al destino
                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
                        // antes de grabar, validar que no existan infinitos en posiciones
                        // INTERMEDIAS
                        boolean tieneInfiniteIntermedio = false;
                        for (int i = 1; i < path.size(); i++) { // empieza en 1: permitimos origen infinito solo en pos 0
                            Vuelo vCheck = path.get(i);
                            Almacen destInter = almacenesSnapshot.get(vCheck.getIdAlmacenDestino());
                            if (destInter != null && destInter.isEsInfinito()) {
                                tieneInfiniteIntermedio = true;
                                break;
                            }
                        }
                        if (tieneInfiniteIntermedio) {
                            // no registrar rutas con infinitos intermedios (regla de negocio)
                            continue;
                        }

                        String signature = path.stream()
                                .map(vf -> String.valueOf(vf.getId()))
                                .collect(Collectors.joining("-"));
                        if (!rutasVistas.contains(signature)) {
                            LinkedList<Long> ids = path.stream().map(Vuelo::getId)
                                    .collect(Collectors.toCollection(LinkedList::new));
                            resultado.add(new LinkedList<>(ids));
                            rutasVistas.add(signature);
                            rutasPorOrigen++;
                            rutasEncontradasParaDestino++;
                        }
                        // no expandir más este path
                        continue;
                    }

                    // límite de escalas/tramos
                    if (path.size() >= MAX_LEGS)
                        continue;

                    // EXPANDIR: obtener vuelos que salen desde el último destino (snapshot)
                    List<Vuelo> siguientes = vuelosPorAlmacenOrigenId
                            .getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());

                    // regla: si el último destino es infinito, NO expandir (evita infinitos como
                    // intermedios)
                    Almacen ultimoDestinoSnapshot = almacenesSnapshot
                            .get(last.getIdAlmacenDestino());
                    if (ultimoDestinoSnapshot != null && ultimoDestinoSnapshot.isEsInfinito()){
                        continue; // no expandir desde un infinito intermedio
                    }

                    for (Vuelo next : siguientes){
                        if (next == null)
                            continue;
                        if (next.getCapacidadDisponibleParaReserva() <= 0)
                            continue;
                        if (next.yaPartio(ahora))
                            continue;

                        // exigir mínima espera entre vuelos (no menos que
                        // Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS)
                        if (next.getInicio() == null || last.getFin() == null)
                            continue;
                        if (next.getInicio().isBefore(last.getFin().plus(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS))) {
                            continue;
                        }

                        // evitar repetir vuelo en el mismo path
                        boolean repetido = path.stream()
                                .anyMatch(u -> Objects.equals(u.getId(), next.getId()));
                        if (repetido)
                            continue;

                        // evitar ciclos por volver al mismo almacen destino varias veces
                        boolean vuelveMismoAlmacen = path.stream().anyMatch(u -> Objects
                                .equals(u.getIdAlmacenDestino(), next.getIdAlmacenDestino()));
                        if (vuelveMismoAlmacen)
                            continue;

                        // además: evitar next cuyo destino sea un almacén infinito (intermedio)
                        Almacen nextDestinoSnapshot = almacenesSnapshot
                                .get(next.getIdAlmacenDestino());
                        if (nextDestinoSnapshot != null && nextDestinoSnapshot.isEsInfinito()) {
                            continue;
                        }

                        // crear nuevo candidato y encolar (no mutamos objetos originales)
                        List<Vuelo> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        q.add(newPath);
                    }
                } // end BFS per origin
            } // end origins
        } // end destinos

        Map<Integer, Integer> histogram = new HashMap<>();
        for (LinkedList<Long> r : resultado) {
            histogram.merge(r.size(), 1, Integer::sum);
        }
        lr.appendReport("Rutas por longitud: " + histogram);

        return resultado;
    }

    /*
     * Devuelve Los almacenes no infinitos que tengan pedidos pendientes
     */
    public Set<Long> obtenerAlmacenesConDemanda(Map<Long,Pedido> pedidos, Map<Long, Almacen> almacenes) {
        Set<Long>almacenesConDemanda;

        almacenesConDemanda = pedidos.values()
                .stream()
                .filter(Objects::nonNull)
                .filter(pedido -> pedido.getCantidadProductosPendientes() > 0)
                .map(Pedido::getIdAlmacenDestino)
                .filter(id -> {
                    Almacen almacen = almacenes.get(id);
                    return almacen != null && !almacen.isEsInfinito();
                })
                .collect(Collectors.toSet());

        return almacenesConDemanda;
    }

    /*
     * Devuelve Los almacenes infinitos 
     */
    public Set<Long> obtenerAlmacenesConStock(Map<Long, Almacen> almacenes) {
        Set<Long>almacenesinfinitos;

        almacenesinfinitos =  almacenes.values()
                .stream()
                .filter(Objects::nonNull)
                .filter(Almacen::isEsInfinito)
                .map(Almacen::getId)
                .collect(Collectors.toSet());

        return almacenesinfinitos;
    }


    private boolean almacenTieneStockEnInstante(Almacen almacen, Instant instante) {
        if (almacen == null)
            return false;
        if (almacen.isEsInfinito())
            return true; // infinitos siempre válidos
        // usar tu simulador local: getAlmacenEnInstante devolverá clone simulado
        List<Producto> prods = obtenerProductosNoAsignados(almacen, instante);
        if (prods == null)
            return false;
        // si idsProductosExistentes no es nulo, usar su size; sino fallback a
        // capacidadOcupada
        if (prods != null)
        {
            return !prods.isEmpty();
        }
        return false;
//        return simul.getCapacidadOcupada() > 0;
    }

    public List<LinkedList<Long>> generarRutasParaPedidosPendientesACO(Instant ahora)
    {

        return null;
    }

    public void imprimirVuelosDetalladosDeRuta(LinkedList<Long> r)
    {
        r.forEach(v -> Bitacora
                .escribir("VueloEntidad en ruta: " + vuelos.get(v)));
    }

    public static HashMap<Long, PedidoParaAxel> pedidosDesdeEstadoGlobal(EstadoGlobal estadoGlobal,
            List<Programacion> programaciones)
    {
        HashMap<Long, PedidoParaAxel> result = new HashMap<>();
        if (estadoGlobal == null)
            return result;

        // 1) Inicializar entrada para TODOS los pedidos conocidos en el estado (incluso
        // si no tienen rutas)
        Map<Long, Pedido> pedidosMapa = estadoGlobal.getPedidos();
        if (pedidosMapa != null)
        {
            for (Map.Entry<Long, Pedido> e : pedidosMapa.entrySet())
            {
                Long idPedido = e.getKey();
                Pedido p = e.getValue();
                if (idPedido == null || p == null)
                    continue;
                result.put(idPedido, new PedidoParaAxel(p));
            }
        }

        // 2) Iterar rutas generadas por el algoritmo y agruparlas por idPedidoAsociado
        List<Programacion> rutas = new ArrayList<>(programaciones);
        if (rutas == null || rutas.isEmpty())
        {
            // no hay rutas: devolvemos mapa con pedidos y listas vacías
            return result;
        }

        for (Programacion ruta : rutas)
        {
            if (ruta == null)
                continue;
            long idPedidoAsoc = ruta.getIdPedido();

            // caso defensivo: id inválido (ej.: -1L) -> log y saltar
            if (idPedidoAsoc <= 0)
            {
                continue;
            }

            // obtener/crear entrada en el map
            PedidoParaAxel entry = result.get(idPedidoAsoc);
            if (entry == null)
            {
                // el pedido no estaba en estadoGlobal.getPedidos() (inconsistencia):
                Pedido pFromState = estadoGlobal.getPedidos().get(idPedidoAsoc);
                if (pFromState != null)
                {
                    entry = new PedidoParaAxel(pFromState);
                    result.put(idPedidoAsoc, entry);
                }
                else
                {

                    continue;
                }
            }

            // Añadir la ruta a la lista del pedido
            entry.getMiniPedidos().add(ruta);
        }

        return result;
    }

    public List<Pedido> obtenerPedidosPendientesDeEntregaYProgram()
    {
        return this.getPedidos().values()
                .stream()
                .filter(Pedido -> Pedido.getCantidadProductosPendientes() > 0)
                .collect(Collectors.toList());
    }

    public void crearIndiceIdsRutasPorAlmacenDestino(List<LinkedList<Long>> rutasPosibles)
    {
        HashMap<Long, List<LinkedList<Long>>> indice;
        List<LinkedList<Long>> rutasDelAlmacen;

        indice = new HashMap<>();

        for (Almacen almacen : this.almacenes.values())
        {
            rutasDelAlmacen = rutasPosibles
                    .stream()
                    .filter(idsVuelosRuta -> almacenes
                            .get(vuelos.get(idsVuelosRuta.getLast()).getIdAlmacenDestino())
                            .getId() == almacen.getId())
                    .toList();
            indice.put(almacen.getId(), rutasDelAlmacen);
        }

        this.rutasPorIdAlmacenDestino = indice;
    }

    // Refactorizar para usar el instanteDeDisponibildiad que está en prod en almacén
    public List<Producto> obtenerProductosEscogiblesAlmacenOrigenEnRuta(LinkedList<Long> ruta)
    {
        // Dividir los prods del almacen origen en prods intercontinentales y no intercont
        Vuelo primerVuelo = vuelos.get(ruta.getFirst());
        Almacen almacenOrigen = almacenes.get(primerVuelo.getIdAlmacenOrigen());

        lr.appendReport("Revisando para el instante: " + primerVuelo.getInicio());

        List<Producto> productosExistentesEnAlmacenEnFuturoInstante =
                obtenerProductosNoAsignados(almacenOrigen, primerVuelo.getInicio());

//        System.out.println("Tal vez y solo tal vez necesites debuggear acá");
        lr.appendReport("Productos del origen en primer vuelo: " + productosExistentesEnAlmacenEnFuturoInstante);

        return productosExistentesEnAlmacenEnFuturoInstante;
    }

    public int obtenerCapacidadRutaEnEstadoActualSimulada(List<Long> idsVueloRuta) {
        List<Vuelo> vuelosRuta = idsVueloRuta.stream().map(id -> vuelos.get(id)).toList();
        if (vuelosRuta.isEmpty()) return 0;

        // upperBound = min(capacidad disponible de vuelos)
        int upper = vuelosRuta.stream().mapToInt(Vuelo::getCapacidadDisponibleParaReserva).min().orElse(0);
        if (upper <= 0) return 0;

        // limitar por disponibilidad inicial en el primer origen (si no infinito)
        Vuelo first = vuelosRuta.get(0);
        Almacen origen = almacenes.get(first.getIdAlmacenOrigen());
        if (!origen.isEsInfinito()) {
            int disponibles = obtenerProductosNoAsignados(origen, first.getInicio()).size();
            upper = Math.min(upper, disponibles);
            if (upper <= 0) return 0;
        }

        // binary search para máximo k factible
        int lo = 0, hi = upper;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (esKFactibleRutaConCambios(vuelosRuta, mid)) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private boolean esKFactibleRutaConCambios(List<Vuelo> ruta, int k) {
        if (k <= 0) return false;

        // construir, para cada almacen implicado, un TreeMap temporal con los cambios existentes
        Map<Long, NavigableMap<Instant,Integer>> mapaCambiosTmp = new HashMap<>();
        Set<Long> almacenesInvolucrados = ruta.stream()
                .flatMap(v -> Stream.of(v.getIdAlmacenOrigen(), v.getIdAlmacenDestino()))
                .collect(Collectors.toSet());

        for (Long aid : almacenesInvolucrados) {
            // copiar cambios base
            NavigableMap<Instant,Integer> tm = new TreeMap<>(almacenes.get(aid).getCambios()); // necesitas getter
            mapaCambiosTmp.put(aid, tm);
        }

        // ahora añadir los deltas provisionales del "k" para la ruta:
        for (int i=0; i<ruta.size(); i++) {
            Vuelo v = ruta.get(i);
            long idO = v.getIdAlmacenOrigen();
            long idD = v.getIdAlmacenDestino();
            // salida en inicio: -k
            mapaCambiosTmp.get(idO).merge(v.getInicio(), -k, Integer::sum);
            // llegada en fin: +k
            mapaCambiosTmp.get(idD).merge(v.getFin(), k, Integer::sum);
            // si es último tramo y consideras pickup -> add -k en fin + ventana (si aplica)
            if (i == ruta.size()-1) {
                Instant pickup = v.getFin().plus(HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS);
                mapaCambiosTmp.get(idD).merge(pickup, -k, Integer::sum);
            }
        }

        // para cada almacen involucrado, construir sumas parciales cronológicas y verificar rango
        for (Long aid : almacenesInvolucrados) {
            Almacen a = almacenes.get(aid);
            if (a.isEsInfinito()) continue; // infinito siempre ok

            int base = a.getIdsProductosExistentes().size() + a.getIdsProductosFuturos().size();
            NavigableMap<Instant,Integer> tm = mapaCambiosTmp.get(aid);
            int cumulative = base;
            // recorrer ordenado por instante
            for (Map.Entry<Instant,Integer> e : tm.entrySet()) {
                cumulative += e.getValue();
                if (cumulative < 0) return false;
                if (cumulative > a.getCapacidadMaxima()) return false;
            }
        }
        return true;
    }


    /*
     * Calcula en toda la Ruta cual es el maximo valor del espacio vacío.
     */
    public Integer obtenerCapacidadRutaEnEstadoActual(List<Long> idsVueloRuta){
        List<Vuelo> vuelitos = idsVueloRuta.stream().map(aLong -> vuelos.get(aLong)).toList();

        Integer espacioVacioMaximoAbsoluto, espacioVacioMaximoLocal, espacioVacioMaximoSalida,
                espacioVacioMaximoVuelo, espacioVacioLlegada;
        Almacen almacenOrigen, almacenDestino;

        espacioVacioMaximoAbsoluto = 0;

        for (Vuelo vuelo : vuelitos){
            almacenOrigen = almacenes.get(vuelo.getIdAlmacenOrigen());
            almacenDestino = almacenes.get(vuelo.getIdAlmacenDestino());

            lr.appendReport("obtenerCapacidadRutaEnEstadoActual: Evaluando para almacén origen "+almacenOrigen);

            espacioVacioMaximoSalida = almacenOrigen
                    .calcularEspacioVacio(vuelo.getInicio());
            lr.appendReport("Espacio vacío máximo salida: " + espacioVacioMaximoSalida);
            espacioVacioMaximoVuelo = vuelo.getCapacidadDisponibleParaReserva();
            lr.appendReport("Espacio vacío máximo vuelo: " + espacioVacioMaximoVuelo);
            espacioVacioLlegada = almacenDestino.calcularEspacioVacio(vuelo.getFin());
            lr.appendReport("Espacio vacío máximo llegada: " + espacioVacioLlegada);
            espacioVacioMaximoLocal = Math.min(espacioVacioMaximoSalida,
                    Math.min(espacioVacioMaximoVuelo, espacioVacioLlegada));
            lr.appendReport("Espacio vacío máximo local: " + espacioVacioMaximoLocal);

            if (espacioVacioMaximoAbsoluto == 0
                    || espacioVacioMaximoAbsoluto > espacioVacioMaximoLocal){
                espacioVacioMaximoAbsoluto = espacioVacioMaximoLocal;
            }
            lr.appendReport("Espacio vacío máximo absoluto: " + espacioVacioMaximoAbsoluto);
        }

        lr.appendReport("FINAL: Espacio vacío máximo absoluto"+espacioVacioMaximoAbsoluto);
        return espacioVacioMaximoAbsoluto;
    }




    public Almacen buscarAlmacen(Long id)
    {
        return this.almacenes.get(id);
    }

    // Para facilitarle la vida a la simulación (ctx)

    public Almacen obtenerAlmacenPorId(long idAlmacenDestino)
    {
        return almacenes.get(idAlmacenDestino);
    }

    public Producto obtenerProductoPorUuid(UUID uuid)
    {
        return productos.get(uuid);
    }

    public Vuelo obtenerVueloPorId(long id)
    {
        return vuelos.get(id);
    }

    /* Entre prematuramente producto en el pedido cuando el vuelo ya llega, evita que el algoritmo lo vea como pendiente*/
    public boolean entregarProductoEnPedidoSegunLlegadaVuelo(
            long idPedido,
            @NotNull Producto producto,
            Instant instanteProgramadoLlegadaVuelo)
    {
        Pedido pedidoEnCuestion = pedidos.get(idPedido);

        if (!instanteProgramadoLlegadaVuelo
                .plus(HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS)
                .isAfter(pedidoEnCuestion.getInstanteMaximoParaEntregar())){
            // si cuando llega el vuelo es antes del máximo
            Almacen aDestino = getAlmacenes().get(producto.getIdAlmacenInfinitoOrigen());
            boolean cambioIntercont;
            boolean esIntercont = pedidoEnCuestion.isIntercontinentalAhora();
            if (!pedidoEnCuestion.agregarProductoEntregado(producto, aDestino.getContinente())) { // <- muta
                return false;
            }
            cambioIntercont = esIntercont != pedidoEnCuestion.isIntercontinentalAhora();
            if (cambioIntercont)
                lr.appendReport("EL PEDIDO " + pedidoEnCuestion.getId() +
                        " CAMBIÓ OFICIALMENTE A INTERCONTINENTAL (debe ser true): "
                        + pedidoEnCuestion.isIntercontinentalAhora());

            producto.setEntregado(true);
            return true;
        }

        // Continente continenteLlegada =
        // almacenes.get(producto.getIdAlmacenInfinitoOrigen()).getContinente();
        // if(!pedidoEnCuestion.getContinenteDestino().equals(continenteLlegada)){
        // pedidoEnCuestion.setIntercontinentalAhora(true); // normal si lo era o no
        // antes.
        // }

        return false;
    }

    public List<AbstractMap.SimpleEntry<LinkedList<Vuelo>, Integer>> obtenerRutasDePedido(
            long idPedido)
    {
        List<Programacion> programacionesDelPedido = programaciones.stream()
                .filter(programacion -> programacion.getIdPedido() == idPedido)
                .toList();

        Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = programacionesDelPedido
                .stream()
                .collect(Collectors.groupingBy(Programacion::getIdsVueloRuta));

        return programacionesPorRuta.keySet().stream().map(
                longs -> new AbstractMap.SimpleEntry<>(
                        new LinkedList<>(longs.stream().map(aLong -> vuelos.get(aLong)).toList()),
                        programacionesPorRuta.get(longs).size() // numero de programaciones de la
                                                                // ruta, o sea número de productos
                ))
                .toList();
    }

    public LinkedList<Almacen> obtenerAlmacenesPorRuta(LinkedList<Vuelo> vuelos)
    {
        LinkedList<Almacen> almacenesRuta = new LinkedList<>();

        for (Vuelo vuelo : vuelos)
        {
            if (vuelos.getFirst().equals(vuelo))
            {
                almacenesRuta.add(almacenes.get(vuelo.getIdAlmacenOrigen()));
            }
            almacenesRuta.add(almacenes.get(vuelo.getIdAlmacenDestino()));
        }

        return almacenesRuta;
    }

    // public boolean pedidoFueReprogramado(Pedido pedido){
    // return programaciones.stream().anyMatch(
    // programacion -> programacion.getIdPedido() == pedido.getId() &&
    // !programacion.isActivo()
    // ); // Devolvemos si alguna progrmacion
    // }

    public List<Programacion> obtenerProgramacionesQueUsanRuta(LinkedList<Long> ruta)
    {

        return programaciones.stream()
                .filter(programacion -> programacion.getIdsVueloRuta().equals(ruta)
                        && programacion.isActivo())
                .collect(Collectors.toList());

    }

    public List<Producto> obtenerProductosQueUsanRutaActiva(LinkedList<Long> ruta)
    {

        return obtenerProgramacionesQueUsanRuta(ruta).stream()
                .map(programacion -> productos.get(programacion.getIdsVueloRuta()))
                .collect(Collectors.toList());

    }

    public List<RutaProgramadaListadaDTO> obtenerRutasProgramadas()
    {

        Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = programaciones.stream()
                .collect(Collectors.groupingBy(Programacion::getIdsVueloRuta));

        return programacionesPorRuta.keySet().stream().map(
                longs -> {
                    return new RutaProgramadaListadaDTO(
                            new LinkedList<>(
                                    longs.stream().map(aLong -> {
                                        Vuelo vuelo = vuelos.get(aLong);
                                        Almacen almOrigen = almacenes
                                                .get(vuelo.getIdAlmacenOrigen());
                                        Almacen almDestino = almacenes
                                                .get(vuelo.getIdAlmacenDestino());
                                        return new VueloResumidoDTO(
                                                vuelo.getId(),
                                                almOrigen.getNombreCiudad(),
                                                almDestino.getNombreCiudad());
                                    }).toList()),
                            longs);
                }).collect(Collectors.toList());
    }

    /* Método pensado para usarse desde el estado global de la SIMULACIÓN, devuelve una partecida del estado global
    *     con los datos que necesita el ALGORITMO para funcionar adecuadamente
    *   Toma datos del futuro, 4 horas para darle tiempo al algoritmo de ejecutarse, si es necesario
    * cambiarlo, cambia el hiperparámetro HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX
    * Pedidos, almacenes, vuelos, productos y programaciones como si fuera de aquí a 4 horas
    * También el instante que el algoritmo "piensa" que tiene es de aquí a 4 horas.
    *
    * @param instanteProgramado Instante en el que se planificará (SIN EL FUTURO CERCANO AGREGADO AÚN)
    * @param ctx Contexto de simulación, para obtener las programaciones acumuladas y loguear
    * @return EstadoGlobal con los datos filtrados para el algoritmo
    * */
    public EstadoGlobal obtenerDatosParaAlgoritmoDesdeMemoria(Instant instanteProgramado,
            ContextoSimulacion ctx) {
        // -- PREPARAR DATOS FILTRADOS PARA EL ALGORITMO --
        Instant instanteAlgoritmo = instanteProgramado.plus(HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX, ChronoUnit.HOURS);

        Map <Long, Vuelo> vuelosParaAlgoritmo =
                obtenerVuelosParaAlgoritmoMemoria(instanteAlgoritmo);

        Map<Long, Almacen> almacenesParaAlgoritmo =
                obtenerAlmacenesParaAlgoritmoMemoria(instanteAlgoritmo, ctx);

        List<Programacion> programacionesParaAlgoritmo =
                obtenerProgramacionesParaAlgoritmoMemoria(instanteAlgoritmo, ctx);

        Map<Long, Pedido> pedidosParaAlgoritmo =
                obtenerPedidosParaAlgoritmoMemoria(instanteAlgoritmo, ctx, programacionesParaAlgoritmo);

        Map<UUID, Producto> productosParaAlgoritmo =
                obtenerProductosParaAlgoritmoMemoria(instanteAlgoritmo);

        ctx.log("EventoTriggerPlanificacion: Datos preparados para el algoritmo - " +
                pedidosParaAlgoritmo.size() + " pedidos, " +
                vuelosParaAlgoritmo.size() + " vuelos, " +
                almacenesParaAlgoritmo.size() + " almacenes." +
                productosParaAlgoritmo.size() + " productos." +
                programacionesParaAlgoritmo.size() + " programaciones incancelables previas. "
        );
        System.out.println("========= DATOS PLANIFICACION =========");
        System.out.println("Pedidos: " + pedidosParaAlgoritmo.size());
        System.out.println("Vuelos: " + vuelosParaAlgoritmo.size());
        System.out.println("Almacenes: " + almacenesParaAlgoritmo.size());
        System.out.println("Productos: " + productosParaAlgoritmo.size());
        System.out.println("Programaciones previas incancelables: " + programacionesParaAlgoritmo.size());
        System.out.println("=========================================\n");
        // LUEGO ALGORITMO HARÁ DEEP COPY
        return new EstadoGlobal(almacenesParaAlgoritmo, vuelosParaAlgoritmo, pedidosParaAlgoritmo,
                programacionesParaAlgoritmo, productosParaAlgoritmo); // <- YA NO LE PASAMOS NULL EN PROGRAMACIONES
    }

    private Map<UUID, Producto> obtenerProductosParaAlgoritmoMemoria(Instant instanteAlgoritmo) {
        // 1. Simular productos en el futuro (ubicaciones, flags):
        Map<UUID, Producto> prodsSimulados = simularProductosEnInstante(
                productos, programaciones, instanteAlgoritmo
        );

// 2. Filtrar solo productos REPROGRAMABLES:
        Map<UUID, Producto> prodsUsables = prodsSimulados.values().stream()
//                .filter(p ->
////                        !p.isEntregado()
////                                &&
////                                !p.isProntoParaEntrega() // ??!!
//                )
                .collect(Collectors.toMap(Producto::getUuid, producto -> producto));

        return prodsUsables;
    }

    private List<Programacion> obtenerProgramacionesParaAlgoritmoMemoria(Instant instanteAlgoritmo, ContextoSimulacion ctx) {
        List<Programacion> progsBase = programaciones;

        // 3. Programaciones: solo incancelables NO completadas
        List<Programacion> progsAlgoritmo = progsBase.stream()
                .map(programacion -> simularProgramacionEnInstante(programacion, instanteAlgoritmo))
                .filter(prog -> {
//                    Producto prod = productos.get(prog.getUuidProducto()); // productos o productosSimulados
                    Vuelo ultimoVuelo = vuelos.get(prog.getIdsVueloRuta().getLast());
                    // No completada: último vuelo aún no llegó o llegó hace menos de 2h
                    return /*!prod.isEntregado() &&*/
                             prog.isAPuntoDeCumplirse()
                            && ! ultimoVuelo.getFin().plus(2, ChronoUnit.HOURS)
                            .isBefore(instanteAlgoritmo);
                })
                .toList();

        return progsAlgoritmo;
    }

    private Map<Long, Pedido> obtenerPedidosParaAlgoritmoMemoria(
            Instant instanteAlgoritmo,
            ContextoSimulacion ctx,
            List<Programacion> programacionesParaAlgoritmo) {
        Map<Long, Pedido> pedidosBase = getPedidos();
        Map<Long, Pedido> pedidosParaAlgoritmo = pedidosBase.values().stream()
                .map(pedido -> simularPedido(pedido, ctx.obtenerElAhora(),  instanteAlgoritmo,programacionesParaAlgoritmo) )
                .filter(pedido -> {
                            if( pedido.getId() == 5136971L){
                                lr.appendReport("El pedido simulado quedó: " + pedido);
                            }

                            return !pedido.getInstanteRegistro().isBefore(ctx.getInicioSimulacion())
                                    && pedido.getInstanteRegistro().isBefore(
                                    // TOMAMOS PEDIDOS DEL FUTURO, ASÍ COMO SI LAS WEBAS.
                                    instanteAlgoritmo
                                    )
                                    && pedido.getCantidadProductosEntregados() < pedido
                                    .getCantidadProductosPedidos();
                        }
                        // pedido que se haya registrado
                        // después o igual al inicio de la simu pero antes del instante en que se
                        // planifica.
                )
                .peek(pedido -> {
                    pedido.restablecerProductosProgramadosParaAlgoritmo();
                })
                .collect(Collectors.toMap(
                        o -> o.getId(),
                        longPedidoEntry -> new Pedido(longPedidoEntry)));


        return pedidosParaAlgoritmo;
    }

    private Map<Long, Almacen> obtenerAlmacenesParaAlgoritmoMemoria(Instant instanteAlgoritmo, ContextoSimulacion ctx) {
        return  getAlmacenes().entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                // Map.Entry::getValue
                                e ->{
                                    if(!e.getValue().isEsInfinito())
                                        return Almacen.obtenerAlmacenSimuladoConProductos2
                                                (e.getValue(), instanteAlgoritmo, ctx.getAhora(),
                                                        programaciones, productos, vuelos, ctx); // devuelve nueva instancia
                                    else
                                        return e.getValue();
                                }


                        )
                );
    }

    private Map<Long, Vuelo> obtenerVuelosParaAlgoritmoMemoria(Instant instanteAlgoritmo) {
        Map<Long, Vuelo> vuelosBase = getVuelos();
        Map<Long, Vuelo> vuelosParaAlgoritmo = vuelosBase.values().stream()
                .filter(vuelo -> {
                    Instant inicio = vuelo.getInicio();
                    Instant fin = vuelo.getFin();

                    return !vuelo.isCancelado() &&
                            (
                                    // Vuelo en tránsito (verificar productos después de simular)
                                    (vuelo.yaPartio(instanteAlgoritmo) && !vuelo.yaLlego(instanteAlgoritmo))
                                            ||
                                            // Vuelo futuro
                                            !vuelo.yaPartio(instanteAlgoritmo)
                            );
                })
                .map(vuelo -> Vuelo.obtenerVueloSimuladoConProductos(
                        vuelo,
                        instanteAlgoritmo,
                        programaciones,
                        productos))
                // ✅ NUEVO: Filtrar vuelos en tránsito SIN productos DESPUÉS de simular
//                .filter(vueloSimulado -> {
//                    if (vueloSimulado.yaPartio(instanteAlgoritmo) &&
//                            !vueloSimulado.yaLlego(instanteAlgoritmo)) {
//                        // Vuelo en tránsito: solo incluir si tiene productos
//                        return !vueloSimulado.getIdsProductosContenidos().isEmpty();
//                    }
//                    // Vuelo futuro: incluir siempre
//                    return true;
//                })
                .peek(vuelo -> {
                    if( !vuelo.yaPartio(instanteAlgoritmo)) //futuro
                        vuelo.restablecerProductosProgramadosParaAlgoritmo();
                })
                .collect(Collectors.toMap(Vuelo::getId, vuelo -> vuelo));
                    // El vuelo no está cancelado y llega antes del instante en que se acabará la planif (en 4h aprox)
                    // más 3 días
                    // (ya que se toman los pedidos solo hasta 4h en el futuro.
                    // Además se toman vuelos que hayan iniciado antes de ahora más 4h
                    // y que terminarán después de esta (posiblemente en curso y que traerán productos interesantes)
                    // EN RESUMEN, ES COMO SI AL ALGORITMO LO CORRIERAN 4 HORAS EN EL FUTURO CON EL ESTADO EN
                    // 4 HORAS EN EL FUTURO.

        return vuelosParaAlgoritmo;
    }

    // -------------------------------------------------------------
// Método helper: actualiza los pedidos snapshot según programaciones
// -------------------------------------------------------------
    private Pedido simularPedido(
            Pedido p,
            Instant instantePrevioSimulado,
            Instant instanteAlgoritmo,
            List<Programacion> programacionesParaAlgoritmo // ← NUEVO PARÁMETRO
    ) {
        if(p.getId() == 5136971L){
            System.out.println("Debug pedido raro");
        }
        // tiempo de espera para que el cliente recoja el producto
        long horasPickup = Hiperparametros.HORAS_ESPERA_PARA_RECOJO;
        // ✅ Usar programaciones filtradas, no las originales
        List<Programacion> progsConPedido = programaciones.stream() // debe usar las progras normales...
                .filter(programacion -> programacion.getIdPedido() == p.getId())
                .toList();
//        lr.appendReport("progsConPedido del pedido " + p +"\n"+ progsConPedido);
        Pedido pedidoSimul = new Pedido(p);
//        for(Vuelo vuelo : vuelos.values()){
//            if(!vuelo.getInicio().isBefore(instanteAlgoritmo) ) {
//                // Vuelo salió
//
//            }
//        }

        for (Programacion prog : progsConPedido) {
            if (prog == null) continue;
            // producto asociado a la programación (puede venir en productosParaAlgoritmo)
            Producto producto = productos.get(prog.getUuidProducto());
            if (producto == null) {
                throw new IllegalStateException("No se encontro el producto programado para el pedido");
            }

            // obtener el último vuelo de la ruta para decidir si llegó / pickup pasó
            LinkedList<Long> ruta = prog.getIdsVueloRuta();
            if (ruta == null || ruta.isEmpty()) continue;
            Long idUltimoVuelo = ruta.getLast();
            Vuelo vueloUlt = vuelos.get(idUltimoVuelo);
            if (vueloUlt == null) {
                throw new IllegalStateException("No se encontro el vuelo programado para el pedido");
            }

            Instant inicio = vueloUlt.getInicio();
            Instant llegada = vueloUlt.getFin();
            Instant instantePickup = llegada.plus(horasPickup, ChronoUnit.HOURS);

            // calculamos cuántas unidades aún faltan en el pedido (según snapshot)
            int pendientesAntes = pedidoSimul.getCantidadProductosPedidos()-pedidoSimul.getCantidadProductosEntregados();
            // MATENME MATENMEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE
            if (pendientesAntes <= 0) continue; // nada que hacer

            // 1) Si ya salió el último vuelo, considerar ENTREGADO
            if (!inicio.isAfter(instanteAlgoritmo) && inicio.isAfter(instantePrevioSimulado)) { // instanteAlgoritmo >= inicio
                // determinamos continente origen para la función agregarProductoEntregado
                Almacen almacenOrigenProd = almacenes.get(producto.getIdAlmacenInfinitoOrigen());
                Continente continenteOrigen = (almacenOrigenProd != null ?
                        almacenOrigenProd.getContinente() : pedidoSimul.getContinenteDestino());
                // agregar, pero respetar límite (agregarProductoEntregado devuelve false si excede)
                boolean ok = pedidoSimul.agregarProductoEntregado(producto, continenteOrigen);
                if (!ok) {
                    lr.appendReport("No se pudo agregar producto en el pedido: \n Prod:"+producto+"\nPedido:"+pedidoSimul);
                    throw new IllegalStateException("No se puede agregar el producto");
                    // si falla por límite, no intentar más programaciones para este pedido
//                    continue;
                }
                // listo: contamos esto como entregado en el snapshot
                continue;
            }else
                if(!inicio.isAfter(instanteAlgoritmo) && llegada.isAfter(instanteAlgoritmo)){
                    /// si salió antes del momento y llegará después
                    // determinamos continente origen para la función agregarProductoEntregado
                    Almacen almacenOrigenProd = almacenes.get(producto.getIdAlmacenInfinitoOrigen());
                    Continente continenteOrigen = (almacenOrigenProd != null ?
                            almacenOrigenProd.getContinente() : pedidoSimul.getContinenteDestino());
                    // agregar, pero respetar límite (agregarProductoEntregado devuelve false si excede)
                    boolean ok = pedidoSimul.agregarProductoEntregado(producto, continenteOrigen);
                    if (!ok) {
                        lr.appendReport("No se pudo agregar producto en el pedido: \n Prod:"+producto+"\nPedido:"+pedidoSimul);
                        throw new IllegalStateException("No se puede agregar el producto");
                        // si falla por límite, no intentar más programaciones para este pedido
//                    continue;
                    }
                }

            // 2) Si no ha pasado ventana pickup pero la programación existe (incancelable o en vuelo):
            // marcar como PROGRAMADO (reserva)
//            if (pedidoSimul.getCantidadProductosPendientes() > 0) {
//                boolean okProg = pedidoSimul.agregarProductoProgramadoEnSimu(producto);
//                if (!okProg) {
//                    // si no pudo marcar (quizá por tope), ignoramos
//                }
//            }
        }

        return pedidoSimul;
    }

    private Map<UUID, Producto> simularProductosEnInstante(
            @NotNull HashMap<UUID, Producto> productosParam,
            @NotNull List<Programacion> programacionesParam,
            Instant instante) {
        HashMap<UUID, Producto> simularProductos = new HashMap<>(productosParam);
        // se asegura de que tenga todos los prods de base y luego solo se sobreescriba

        for(Programacion programacion : programacionesParam) {
            Producto producto = productosParam.get(programacion.getUuidProducto());
            Producto simulado = new Producto(producto);
            List<Vuelo> vuelosRuta = programacion.getIdsVueloRuta().stream().map(aLong -> vuelos.get(aLong)).toList();
            // en qué situaciones el producto cambia su estado?
            //^^ Solo cuando su último vuelo sale o llega y el cliente lo recoge.
            Vuelo ultimoVuelo = vuelosRuta.get(vuelosRuta.size()-1);
            if(ultimoVuelo.yaPartio(instante)) {
                simulado.marcarProntoParaEntrega(); // porsia lo marco en ambos casos
                if(ultimoVuelo.yaLlego(instante)) {
                    simulado.setEntregado(true);
                }else{
                    simulado.marcarComoProgramado(instante); // q
                }

            }
            simularProductos.put(programacion.getUuidProducto(), simulado);
        }
        return simularProductos;
    }

    private Programacion simularProgramacionEnInstante(
        Programacion programacion,
            Instant instanteAlgoritmo
    ){
        Programacion prograSimul = new Programacion(programacion);
        // Simulamos los cambios de estado para el instanteAlgoritmo
        List<Vuelo> vuelos = programacion.getIdsVueloRuta().stream().map(aLong -> getVuelos().get(aLong)).toList();
        Vuelo ultimoVuelo = vuelos.get(vuelos.size()-1);
        if(ultimoVuelo.yaPartio(instanteAlgoritmo)) {
            prograSimul.marcarComoAPuntoDeCumplirse();
//            lr.appendReport("simu de la simu: progra marcada: " + programacion );
            if(ultimoVuelo.yaLlego(instanteAlgoritmo)) {
//                prograSimul
            }
        }

        return prograSimul;

    }

    @Override
    public String toString()
    {
        return "Estado{" +
                ", pedidos=" + pedidos.size() +
                ", vuelos=" + vuelos.size() +
                ", almacenes=" + almacenes.size() +
                ", programaciones=" + programaciones.size() +
                ", productos=" + productos.size() +
                // ", estado=" + estado +
                '}';
    }

    // No valida duplicidad
    public void anadirVuelosNuevos(List<Vuelo> vuelosNuevos)
    {
        for (Vuelo v : vuelosNuevos)
        {
            vuelos.put(v.getId(), v);
        }
    }

    public boolean limpiarVuelosViejosSegunInstante(Instant instant){
        // Instant instante = instant.minus()
        Set<Long> idsDeVuelosViejos = vuelos.values().stream().filter(
                vuelo -> vuelo.getFin().isBefore(instant)
                        && vuelo.getFin().isBefore(instant.minus(6, ChronoUnit.DAYS)))
                .map(Vuelo::getId).collect(Collectors.toSet());
        System.out.println(" idsDeVuelosViejos (borrar): " + idsDeVuelosViejos);
        return vuelos.keySet().removeAll(idsDeVuelosViejos);
    }

    // No valida duplicidad
    public void anadirPedidosNuevos(List<Pedido> pedidosNuevos)
    {
        for (Pedido p : pedidosNuevos)
        {
            pedidos.put(p.getId(), p);
        }
    }

    public boolean limpiarPedidosViejosSegunInstante(Instant instant)
    {
        Set<Long> idsDeVuelosViejos = pedidos.values().stream().filter(
                pedido -> {
                    Instant hace1Semana = instant.minus(7, ChronoUnit.DAYS);
                    return pedido.getInstanteRegistro().isBefore(hace1Semana);
                }).map(Pedido::getId).collect(Collectors.toSet());

        return pedidos.keySet().removeAll(idsDeVuelosViejos);

    }

    public String imprimirRutaEnDetalle(List<Long> idsVuelos)
    {
        StringBuilder sb = new StringBuilder();
        for (Long id : idsVuelos)
        {
            Vuelo vuelo = vuelos.get(id);
            sb.append("Vuelo: " + vuelo + "\n");
            sb.append("\tAlmacén de origen y de destino: \n \t "
                    + almacenes.get(vuelo.getIdAlmacenOrigen()) + "\n \t"
                    + almacenes.get(vuelo.getIdAlmacenDestino()));
        }
        return sb.toString();
    }

    public void inicializar(Instant ahora) {
        // WORKAROUND
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();

        // Para tener los productos en los almacenes debido a los vuelos EN TRANSCURSO que van a llegar
        for(Vuelo vuelo: vuelos.values()) {
            if( !vuelo.getIdsProductosContenidos().isEmpty()){ // Este vuelo está en tránsito y trae prods
                // ¿este vuelo es parte de una programación previa incancelable? Esos vuelos se procesan dsp no aqui
                boolean esVueloIntermedio = programaciones.stream()
                        .anyMatch(prog -> {
                            LinkedList<Long> ruta = prog.getIdsVueloRuta();
                            // El vuelo está en la ruta PERO NO es el último
                            return ruta.contains(vuelo.getId())
                                    && !ruta.getLast().equals(vuelo.getId());
                        });

                if(esVueloIntermedio) {
                    Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
                    List<Producto> prods = vuelo.getIdsProductosContenidos().stream().map(uuid -> productos.get(uuid))
                            .toList();

                    prods.forEach(producto -> {
                        producto.establecerInstanteDeDisponibilidadEnUnicoAlmacen(vuelo.getFin());
                    });

                    almDestino.registrarCambioPositivo(vuelo.getFin(), vuelo.getIdsProductosContenidos().size());
                    almDestino.anadirProductosFuturos(prods.stream().map(Producto::getUuid).toList());
                    // Asumimos que no está aberrado y que no habrán almsDestino infinitos
                }
            }
        }

        for(Programacion p: programaciones) {
            if(!p.isAPuntoDeCumplirse()) {
                throw new IllegalStateException("Solo deben llegar programaciones incancelables");
            }

            long idUltimoVuelo = p.getIdsVueloRuta().getLast();
            Vuelo vuelo = ctx.getEstado().getVuelos().get(idUltimoVuelo); // PARA QUE ME DEJE DE DAR NULOS >:v
            Instant llegada =  vuelo.getFin();
            Instant recojo = llegada.plus(HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS);
            Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());

            // Registrar llegada si aún no llegó
            if(ahora.isBefore(llegada)) {
                almDestino.registrarCambioPositivo(llegada, 1);
            }

            // Registrar recojo si aún no se recogió
            if(ahora.isBefore(recojo)) {
                almDestino.registrarCambioNegativo(recojo, 1);
            }

            // Índice de programaciones por vuelo
            for(Long idVuelo: p.getIdsVueloRuta()) {
                programacionesPorIdVueloIncluido
                        .computeIfAbsent(idVuelo, k -> new LinkedList<>())
                        .add(p);
            }
        }

//        for(Programacion p: programaciones) {
//            if(p.isAPuntoDeCumplirse()){ // <- defensivo
//                long idUltimoVuelo = p.getIdsVueloRuta().getLast();
//                Vuelo vuelo = vuelos.get(idUltimoVuelo);
//                Instant llegada = vuelo.getFin();
//                Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
//                if(ahora.isBefore(vuelo.getFin()) && // <- defensivo
//                                ahora.isAfter(vuelo.getInicio())){
//                    // el vuelo todavía no ha llegado, está llegando se supoen
//                    almDestino.registrarCambioNegativo(
//                            llegada.plus(HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS),
//                            1
//                    );
//                }
//            }else{
//                System.out.println("No se supone que una programación que NO está a punto de cumplirse llegue aquí");
//                throw new IllegalStateException("No deben llegar programaciones que no están a punto de cumplirse.");
//            }
//
//            // Pa mantener consistencia en mi índice
//            List<Vuelo> vuelosParticipantes = p.getIdsVueloRuta().stream()
//                    .map(aLong -> vuelos.get(aLong)).toList();
//            for(Vuelo vuelo: vuelosParticipantes) {
//                programacionesPorIdVueloIncluido
//                        .computeIfAbsent(vuelo.getId(), k -> new LinkedList<>())
//                        .add(p);
//            }
//        }
    }

    /*
     * Recupera la lista de Productos del inventario que no estan asignados a ningún Pedido
     * CHAPA PRODUCTOS QUE AÚN NO HAN SIDO PLANIFICADOS, Y QUE ESTÁN DISPONIBLES PARA EL INSTANTE SOLICITADO;
     * EL INSTANTE SOLICITADO SE MATCHEA CON EL INSTANTE DE DISPONIBILIDAD.
     * EL INSTANTE DE DISPONIBILIDAD ES UNA PROPIEDAD SOLO DE PRODUCTOS QUE VAN A LLEGAR Y NADA MÁS, YA QUE ESOS,
     * JUNTO A LOS PRODS QUE YA SE ENCUENTRAN EN ALMACENES FÍSICAMENTE SON LOS ÚNICOS QUE PUEDO USAR
     * PARA ALMACENES INTERMEDIOS.
     */
    public List<Producto> obtenerProductosNoAsignados(Almacen almacenWA, Instant instanteActual) {
//        lr.appendReport("escarbando en: "+ almacenWA);
//        lr.appendReport("idsExistentes: " + almacenWA.getIdsProductosExistentes().size());
        List<Producto> existentes = almacenWA.getIdsProductosExistentes().stream().map(uuid -> productos.get(uuid))
                .collect(Collectors.toList());
//        lr.appendReport("   existentes: "+ PrettyPrinter.printList(existentes));
        List<Producto> futuros = almacenWA.getIdsProductosFuturos().stream().map(uuid -> productos.get(uuid))
                .toList();
//        lr.appendReport("   futuros: "+ PrettyPrinter.printList(futuros));
        List<Producto> inventario = new ArrayList<>( existentes ); // corrección para inmutabilidad
        inventario.addAll(futuros);

        List<Producto> productosNoAsignados = new ArrayList<>();
//        lr.appendReport("   inventario: "+ PrettyPrinter.printList(inventario));
        for (Producto producto : existentes){
            if (!producto.isPlanificado() ){
                productosNoAsignados.add(producto);
            }
        }

        for (Producto producto : futuros){
            if (!producto.isPlanificado() && producto.estaDisponible(instanteActual)){
                productosNoAsignados.add(producto);
            }
        }

        return productosNoAsignados;
    }
}




/*
*
public void anadirProgramacionSolucion(Programacion programacion) {
    if (programacion == null)
        return;

    // Protección simple: si ya existe la misma instancia no la volvemos a añadir
    if (this.programaciones.contains(programacion))
        return; // ya añadida, nada que hacer

    final int cantidad = 1;
    final long idPedido = programacion.getIdPedido();
    final Producto productoElegido = productos.get(programacion.getUuidProducto());
    // 1) Añadir la ruta al conjunto de rutas actuales (esto permite que las
    // simulaciones vean la nueva ruta)
    // loggingReport.appendReport("Programación añadida al estado global
    // "+programacion);
    this.programaciones.add(programacion);
    // 2) Actualizar el pedido: incrementar cantidadProgramada
    Pedido pedido = this.pedidos.get(idPedido);
    if (pedido != null) {
        Almacen origen = almacenes.get(productoElegido.getIdAlmacenInfinitoOrigen());
        pedido.agregarProductoProgramadoEnAlgoritmo(productoElegido, origen.getContinente());
        // ^^^esto actualiza el estado interno del pedido en el hashmap, incluyendo
        // prods y plazo si es intercontinental o no
        // Cosa rara: Cuando esta programación quede obsoleta por una nueva program, el
        // pedido no volverá a ser continental xD!
        int restante = pedido.getCantidadProductosPendientes();
        if (restante <= 0) {
            // Bitacora.escribir("PedidoEntidad id=" + pedido.getId()
            // + " está satisfecho (remaining=0) y se elimina de pendientes.");
        }
        if (almacenes.get(pedido.getIdAlmacenDestino()).getContinente()
                .equals(origen.getContinente())) {
            // algo pendiente... o no?
        }
    } else {
        // si no existe el pedido algo anda mal en la lógica previa — lo dejamos claro
        // lanzando excepción
        throw new IllegalStateException(
                "PedidoEntidad inexistente al añadir ruta: idPedido=" + idPedido);
    }

    asignarProductoAPedido_Ruta_Almacenes_Vuelos(pedido, programacion.getIdsVueloRuta(), productoElegido);
    // 3) Ocupar capacidad en cada vuelo de la ruta (opera de forma sincronizada en
    // Vuelo)
    // Si alguno falla, lanzamos excepción (y no intentamos rollback interno aquí,
    // porque se asumió validación).
    for (Long idVuelo : programacion.getIdsVueloRuta()){
        Vuelo vuelo = this.vuelos.get(idVuelo);
        if (vuelo == null){
            throw new IllegalStateException(
                    "VueloEntidad inexistente al añadir ruta: idVuelo=" + idVuelo);
        }
        boolean pudo = vuelo.reservarCapacidad(programacion.getUuidProducto());// vuelo.ocuparCapacidad(cantidad);
        if (lr != null && !pudo)
            lr.appendReport("anadirRutaSolucion: Ocupar cantidad " + cantidad + " en vuelo: "
                    + vuelo + " Pudo? " + pudo);
        if (!pudo){
            // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
            // Lanzamos excepción para que el llamador decida rollback/handling.
            throw new IllegalStateException(
                    "VueloEntidad sin capacidad al añadir ruta (inconsistencia). vuelo=" + vuelo
                            +
                            " cantidad a poner deseada=" + cantidad
                            + " capacidadSinOcuparActual=" + vuelo.getCapacidadSinOcupar());
        }

        // Sí pudo reservar, seguimos, ahora actualizar índice de programaciones por vuelo

    }

}
*
* */