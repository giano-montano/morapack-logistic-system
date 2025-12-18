package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.*;

@Getter
public class EstadoGlobal implements Serializable {

    @NotNull
    @Setter
    private List<Programacion> programaciones;
    @NotNull
    @Setter
    private HashMap<Long, Almacen> almacenes;
    @NotNull
    private HashMap<Long, Vuelo> vuelos;
    @NotNull
    private HashMap<Long, Pedido> pedidos;
    @NotNull
    private HashMap<UUID, Producto> productos;
    private HashMap<Long, List<Ruta>> adyacencia;

    @Setter
    transient LoggingReport lr; //ojala borrar algun dia

    /*
     * Constructor principal
     */
    public EstadoGlobal(Map<Long, Almacen> almacenes,
            Map<Long, Vuelo> vuelos,
            Map<Long, Pedido> pedidos,
            List<Programacion> programaciones,
            Map<UUID, Producto> productos)
    {
        this.almacenes = new HashMap<>(almacenes);
        this.vuelos = new HashMap<>(vuelos);
        this.pedidos = new HashMap<>(pedidos);
        this.programaciones = new LinkedList<>(programaciones);
        this.productos = new HashMap<>(productos);
    }

    /*
     * Constructor de copia profunda usando serialización.
     * Garantiza que la copia sea totalmente independiente del original.
     */
    public EstadoGlobal(EstadoGlobal estadoGlobal) {
        EstadoGlobal copia = deepCopy(estadoGlobal);
        this.almacenes = copia.almacenes;
        this.vuelos = copia.vuelos;
        this.pedidos = copia.pedidos;
        this.programaciones = copia.programaciones;
        this.productos = copia.productos;
        this.adyacencia = copia.adyacencia;
        this.lr = estadoGlobal.getLr();
    }

    /*
     * Realiza una copia profunda de un objeto Serializable usando serialización
     */
    public static <T extends Serializable> T deepCopy(T object) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
            oos.writeObject(object);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
            java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Error en copia profunda: " + e.getMessage(), e);
        }
    }


    /* Métodos del simulador. Modifica el ctx.estado */

    /*
     * Agrega nuevos pedidos al estado global. Si hay un pedido duplicado, no lo sobrescribe
     */
    public void agregarPedidosNuevos(List<Pedido> pedidosNuevos)
    {
        Map<Long, Pedido> pedidosFiltrados = pedidosNuevos.stream()
                .filter(pedido -> !this.pedidos.containsKey(pedido.getId()))
                .collect(Collectors.toMap(Pedido::getId, Function.identity()));
        
        this.pedidos.putAll(pedidosFiltrados);
    }

    /*
     * Borra los pedidos con antiguedad de DIAS_MEMORIA
     */
    public boolean borrarPedidosViejos(Instant instant)
    {
        Set<Long> pedidosViejos = pedidos.values().stream()
                .filter(pedido -> {
                    Instant hace1Semana = instant.minus(Duration.ofDays(Hiperparametros.DIAS_MANTENER_PEDIDOS_EN_MEMORIA));
                    return pedido.getInstanteRegistro().isBefore(hace1Semana);
                }).map(Pedido::getId).collect(Collectors.toSet());

        return pedidos.keySet().removeAll(pedidosViejos);
    }







































    /******REVISRA XD */

    /*
     * Inicializa el estado global. Se considera que el EstadoGlobal que llega al algoritmo contiene los almacenes, con los productos existentes en su respectivo almacén, en el instanteActual. Además, los vuelos tienen productos existentes en tránsito, de los cuales una cantidad tiene asociados programaciones que no se pueden cancelar. 
     *
     * Reemplazo de inicializar.
     */
    public void inicializar_v2(Instant instanteActual) {
        inicializarVuelosEnTransito_v2(instanteActual);
        inicializarProgramacionesIncancelables_v2(instanteActual);
        calcularPuntajesDePedidos_v2(instanteActual);
    }

    /*
     * Esta función recorre todas los vuelos y registra los cambios en los almacenes correspondientes, actualizando sus productos futuros
     */
    private void inicializarVuelosEnTransito_v2(Instant instanteActual) {
        boolean valido;
        Almacen almacenDestino;
        Instant instanteSalida, instanteLlegada;
        List<Producto> productosFuturos;

        for (Vuelo vuelo : this.vuelos.values()) {
            instanteSalida  = vuelo.getInstanteSalida();
            instanteLlegada = vuelo.getInstanteLlegada();
            almacenDestino = vuelo.getAlmacenDestino();

            if (!instanteLlegada.isBefore(instanteActual)) {   // el vuelo esta en tránsito o todavía no sale
                if (instanteSalida.isBefore(instanteActual)) {   // el vuelo está en tránsito
                    valido = true;  
                    productosFuturos = vuelo.getInventario();

                    for(Producto producto : productosFuturos) {
                        lr.appendReport("A punto de meter prod: " + producto);
                        valido &= almacenDestino.registrarProductoFuturo_v2(producto, instanteLlegada);

                        if(!valido) {
                            Bitacora.escribir("ERROR: (Inicialización): Registro de productos futuros inválidos");
                        }
                    }
                }
                else {   // el vuelo todavía no ha salido
                    continue;
                }
            }
        }
    }

    /*
     * Esta función itera sobre las programaciones para registrar el recojo de los productos de los almacenes (osea, un cambio más) a las 2 horas
     */ 
    private void inicializarProgramacionesIncancelables_v2(Instant instanteActual) {
        boolean valido;
        Vuelo ultimoVuelo;
        Instant llegada, recojo;
        Almacen almacenDestino;
        Producto producto;
        Ruta ruta;

        for (Programacion programacion : this.programaciones) {
            if (programacion.getProducto().isIncancelable()) {
                ruta = programacion.getRuta();
                ultimoVuelo = ruta.getVuelos().getLast();
                llegada = ultimoVuelo.getInstanteLlegada();
                almacenDestino = ultimoVuelo.getAlmacenDestino();
                producto = programacion.getProducto();

                valido = almacenDestino.registrarRecojoDeProductos_v2(producto, llegada, true, null);
                if(!valido) {
                    Bitacora.escribir("ERROR: (Inicialización): No se puede registrar el recojo de los productos");
                    throw new RuntimeException("No se puede registrar el recojo de los productos al inicializar progs incancelables");
//                    valido = almacenDestino.registrarRecojoDeProductos_v2(producto, llegada, true, null);
                }
            }else{
                Bitacora.escribir("ERROR: (Inicialización): Existe una programación que se puede cancelar");
                throw new RuntimeException("No puede haber llegado progra que no es incancelable");
            }
        }
    }

    /*
     * Esta función calcula los puntajes de los pedidos (los puntajes ahora son un atributo de la clase Pedido)
     */
    private void calcularPuntajesDePedidos_v2(Instant instanteActual) {
        Double puntaje;

        for (Pedido pedido : this.pedidos.values()) {
            puntaje = CalculadorDeFitness.asignarPuntajesPedidos_v2(pedido, instanteActual);
            
            pedido.setPuntaje(puntaje);
        }
    }

    /*
     * Corre un algoritmo BFS para la generación de rutas y crea su lista de adyacencia
     * 
     * Remplazo de generarRutasParaPedidosPendientesBFS
     */
    public List<Ruta> calcularRutas_v2(Instant instanteActual) {
        List<Ruta> rutas;
        List<Almacen> origenes;
        Ruta path, nuevoPath;
        List<Vuelo> sucesores;
        Set<String> firmas;
        Set<Almacen> destinos;
        Map<Long, List<Vuelo>> vuelosPorOrigen;
        Queue<Ruta> cola;
        Vuelo ultimo;
        Almacen destinoUltimo;

        int rutasParaDestino, rutasParaOrigen;

        rutas = new ArrayList<>();
        firmas = new HashSet<>();
        destinos = obtenerAlmacenesDestino_v2();
        origenes = obtenerAlmacenesOrigen_v2();
        vuelosPorOrigen = obtenerVuelosPorOrigen_v2();

//lr.appendReport("  destinos (count) = " + destinos.size() + " -> " + destinos.stream().map(Almacen::getId).collect(Collectors.toList()));
//lr.appendReport("  origenes (count)  = " + origenes.size() + " -> " + origenes.stream().map(Almacen::getId).collect(Collectors.toList()));
//lr.appendReport("  vuelos totales (count) = " + this.vuelos.size());
//lr.appendReport("  vuelosPorOrigen keys (count) = " + vuelosPorOrigen.keySet().size() + " -> " + vuelosPorOrigen.keySet());

        for (Almacen almacenDestino : destinos) {
            rutasParaDestino = 0;

            for (Almacen origen : origenes) {
                if (rutasParaDestino >= MAX_RUTAS_POR_DESTINO) {
                    break;
                }

                cola = inicializarCola_v2(origen, vuelosPorOrigen, instanteActual);
//lr.appendReport("Cola : " +cola);
                rutasParaOrigen = 0;

                while (!cola.isEmpty()
                        && rutasParaOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasParaDestino < MAX_RUTAS_POR_DESTINO) {
                    path = cola.poll();
                    ultimo = path.getVuelos().get(path.getVuelos().size() - 1);
                    destinoUltimo = ultimo.getAlmacenDestino();

                    if (ultimo.getAlmacenDestino().getId() == almacenDestino.getId()) {
                        if (rutaSinInfinitosIntermedios_v2(path.getVuelos())) {
                            String firma = crearFirmaRuta_v2(path.getVuelos());
                            if (firmas.add(firma)) {
                                rutas.add(path); // o new Ruta?
                                rutasParaOrigen++;
                                rutasParaDestino++;
                            }
                        }
                        continue;
                    }

                    if (path.getVuelos().size() >= Hiperparametros.MAX_VUELOS || destinoUltimo.isInfinito()) {
                        continue;
                    }

                    sucesores = vuelosPorOrigen.getOrDefault(ultimo.getAlmacenDestino().getId(), Collections.emptyList());

                    for (Vuelo siguiente : sucesores) {
                        if (!esVueloAdmisibleComoSiguiente_v2(path, ultimo, siguiente, instanteActual)) {
                            continue;
                        }

                        nuevoPath = new Ruta(path);
                        nuevoPath.getVuelos().add(siguiente);
                        cola.add(nuevoPath);
                    }
                }
            }
        }
lr.appendReport("Rutas generadas (count)=" + rutas.size());
        calcularAdyacenciaRutasPorAlmacen_v2(rutas);

        return rutas;
    }

    /*
     * Obtiene los almacenes no infinitos que tengan pedidos pendientes. Es un set porque los pedidos pueden repetir destino
     * 
     * Remplazo de obtenerAlmacenesConDemanda
     */
    private Set<Almacen> obtenerAlmacenesDestino_v2()
    {
        return this.pedidos.values().stream()
                .filter(pedido -> pedido.cantidadProductosFaltantes_v2() > 0)
                .map(Pedido::getAlmacenDestino)
                .filter(almacen -> !almacen.isInfinito())
                .collect(Collectors.toSet());
    }

    /*
     * Obtiene los almacenes que seam infinitos o tengan stock. Es una lista porque itera sobre this.almacenes, que solo posee una copia de cada almacen
     * 
     * Remplazo de devolverAlmacenesInfinitosOConStockDisponible
     */
    private List<Almacen> obtenerAlmacenesOrigen_v2() {
        return this.almacenes.values().stream()
                .filter(almacen -> almacen.isInfinito()
                        || !almacen.getInventarioFuturo().isEmpty()
                        || !almacen.getInventario().isEmpty())
                .collect(Collectors.toList());
    }

    private Map<Long, List<Vuelo>> obtenerVuelosPorOrigen_v2() {
        return this.vuelos.values().stream()
                .collect(Collectors.groupingBy(
                        o -> o.getAlmacenSalida().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                lista -> {
                                    lista.sort(Comparator.comparing(
                                            vuelo -> vuelo.getInstanteSalida(),
                                            Comparator.nullsLast(Comparator.naturalOrder())));
                                    return lista;
                                })));
    }

    private Queue<Ruta> inicializarCola_v2(
            Almacen origen,
            Map<Long, List<Vuelo>> vuelosPorOrigen,
            Instant instanteActual) {
        Queue<Ruta> cola;
        Ruta path;
        List<Vuelo> iniciales;
        Vuelo vueloInicial;

        cola = new ArrayDeque<>();
        iniciales = vuelosPorOrigen.getOrDefault(origen.getId(), Collections.emptyList());
//lr.appendReport("inicializarCola_v2: origenId=" + origen.getId() + " vuelosIniciales=" + iniciales.size());

        for (Vuelo v : iniciales) {
            vueloInicial = v;

            boolean cap0 = v.obtenerCapacidadDisponible_v2() <= 0;
            boolean partio = v.yaPartio_v2(instanteActual);
            boolean origenSinStock = (!origen.isInfinito() && !almacenTieneStockEnInstante(origen, v.getInstanteSalida()));

            if (cap0 || partio || origenSinStock) {
//lr.appendReport(String.format("descartando vuelo %d (cap0=%b, partio=%b, origenSinStock=%b, salida=%s)",
//        v.getId(), cap0, partio, origenSinStock, v.getInstanteSalida()));
                continue;
            }

            path = new Ruta();
            path.getVuelos().add(vueloInicial);
            cola.add(path);
        }

        return cola;
    }

    private boolean rutaSinInfinitosIntermedios_v2(List<Vuelo> path) {
        return path.stream()
                .skip(1)
                .map(Vuelo::getAlmacenDestino)
                .noneMatch(Almacen::isInfinito);
    }

    public String crearFirmaRuta_v2(List<Vuelo> path) {
        return path.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.joining("-"));
    }

    private boolean esVueloAdmisibleComoSiguiente_v2(
            Ruta path,
            Vuelo ultimo,
            Vuelo siguiente,
            Instant instanteActual) {
        boolean valido;

        valido =// tiene capacidad 
                siguiente.obtenerCapacidadDisponible_v2() > 0
                // no ha partido
                && !siguiente.yaPartio_v2(instanteActual)
                // respeta la espera mínima entre vuelos
                && !siguiente.getInstanteSalida().isBefore(
                        ultimo.getInstanteLlegada().plus(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS))
                // no repetir vuelo en la ruta
                && path.getVuelos().stream().noneMatch(v -> v.getId() == siguiente.getId())
                // no repetir almacén destino en la ruta
                && path.getVuelos().stream().noneMatch(
                        v -> v.getAlmacenDestino().getId() == siguiente.getAlmacenDestino().getId())
                // el destino del siguiente no es infinito (intermedio)
                && !siguiente.getAlmacenDestino().isInfinito();

        return valido;
                
    }

    /*
     * En base a las rutas computadas, calcula la lista de adyacencia de almacenes con 
     * 
     * Remplazo de crearIndiceIdsRutasPorAlmacenDestino
     */
    private void calcularAdyacenciaRutasPorAlmacen_v2(List<Ruta> rutasPosibles) {

        HashMap<Long, List<Ruta>> indice;
        List<Ruta> rutasDelAlmacen;

        indice = new HashMap<>();

        for (Almacen almacen : this.almacenes.values()) {
            rutasDelAlmacen = rutasPosibles.stream()
                    .filter(ruta ->
                            ruta.getVuelos().getLast().getAlmacenDestino().getId() == almacen.getId())
                    .toList();

//            lr.appendReport("Rutas del almacén destino "+almacen + "\n son: " + rutasDelAlmacen);

            indice.put(almacen.getId(), rutasDelAlmacen);
        }

        this.adyacencia = indice;
    }


    /*
     * Verifica si es que todos los pedidos pendientes se han satisfecho en base a su idsProductosProgramados
     *
     * Remplazo de hayPedidosPendientesPorProgramar
     * SE ESTÁ USANDO!!
     */
    public boolean hayPedidosPendientes_v2() {
        return pedidos.values().stream()
                .anyMatch(pedido -> pedido.obtenerCantidadProductosFaltantes() > 0);
    }

    /*
     * Obtiene los Pedidos con cantidadProductosPendientes sea mayor a 0
     * 
     * Remplazo de obtenerPedidosPendientesDeEntregaYProgram
     */
    public List<Pedido> obtenerPedidosPendientes_v2()
    {
        return this.getPedidos().values()
                .stream()
                .filter(pedido -> pedido.cantidadProductosFaltantes_v2() > 0)
                .collect(Collectors.toList());
    }

    /*
     * Obtiene las rutas validas para el pedido tomando en cuenta los plazos y el destino.
     * Esta función no asegura que se retorne rutas con capacidad (esto último se verifica en elegirRuta_v2)
     *
     * Remplazo de obtenerRutasConMismoDestinoQuePedido y filtrarRutasSegunPlazoPedido
     */
    public List<Ruta> obtenerRutasValidas_v2(Pedido pedidoElegido) {
        Almacen almacenDestino;
        List<Ruta> rutasValidas;
        Instant instanteRegistro, instanteLimite;
        
        almacenDestino = pedidoElegido.getAlmacenDestino();
        rutasValidas = this.adyacencia.get(almacenDestino.getId());
        instanteRegistro = pedidoElegido.getInstanteRegistro();
        instanteLimite   = pedidoElegido.instanteMaximoLlegadaUltimoVuelo_v2();
        rutasValidas = new ArrayList<>(rutasValidas.stream()
                .filter(ruta -> {
                    Instant salidaPrimero  = ruta.obtenerPrimerVuelo().getInstanteSalida();
                    Instant llegadaUltimo  = ruta.obtenerUltimoVuelo().getInstanteLlegada();

                    return !salidaPrimero.isBefore(instanteRegistro)
                            && !llegadaUltimo.isAfter(instanteLimite);
                })
                .toList());

        if(rutasValidas.isEmpty()) {
            Bitacora.escribir("ERROR (Rutas validas): No hay rutas validas después de aplicar filtros");
        }

        return rutasValidas;
    }

    /*
     * Devuelve la lista de productos que están disponibles en un almacen para programar a partir de un instanteDemandado.
     * Si el almacén es infinito, devuelve un... un... u... un grr, ¿no?
     */
    public List<Producto> obtenerProductosDisponibles_v2(Almacen almacen, Instant instanteDemandado) {
        List<Producto> productosExistentes, productosFuturos, productosDisponibles;

        productosDisponibles = new ArrayList<>();

        if(!almacen.isInfinito()) {
            productosExistentes = almacen.getInventario().stream()
//                    .map(productos::get)
                    .filter(producto ->
                            producto.isExistente()
                            && !producto.isPlanificado()
                                    && !producto.isIncancelable())
                    .collect(Collectors.toList());
            productosFuturos = almacen.getInventarioFuturo().keySet().stream() // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//                    .map(productos::get)
                    .filter(producto -> producto.isExistente()
                            && !producto.isPlanificado()
                            && !producto.isIncancelable()
                            &&  almacen.productoEstaDisponibleEnInstante(producto,instanteDemandado) /*producto.estaDisponible_v2(instanteDemandado)*/
                    )
                    .collect(Collectors.toList());
            productosDisponibles.addAll(productosExistentes);
            productosDisponibles.addAll(productosFuturos);
        }
        
        return productosDisponibles;
    }

    /*
     * Obtiene la capacidad máxima que puede transportar una ruta mediante el método de las sumas parciales
     *
     * Remplazo de obtenerCapacidadRutaEnEstadoActualSimulada
     */
    public int obtenerCapacidadRuta_v2(Ruta rutaElegida, int capacidadAlmacen)
    {
        int capacidadMaxima, entradaMaxima, salidaValida, capacidadVuelo;
        Almacen almacenSalida, almacenEntrada;

        capacidadMaxima = 0;
        salidaValida = capacidadAlmacen;

        for(Vuelo vuelo : rutaElegida.getVuelos())
        {
            almacenSalida = vuelo.getAlmacenSalida();
            almacenEntrada = vuelo.getAlmacenDestino();

            if(true)//almacenSalida.verificarSalida_v2(vuelo.getInstanteSalida(), salidaValida))
            {   //la cantidad de productos que se puede sacar del almacen es consistente
                capacidadVuelo = vuelo.obtenerCapacidadDisponible_v2();

                if(capacidadVuelo > 0)
                {   //el vuelo tiene capacidad
                    entradaMaxima = almacenEntrada.calcularEspacioVacioMaximoEnInstante(vuelo.getInstanteLlegada());

                    if(almacenEntrada.verificarEntrada_v2(vuelo.getInstanteLlegada(), entradaMaxima))
                    {   // la salida y la entrada son validas y el vuelo tiene capacidad
                        capacidadMaxima = Math.min(salidaValida, capacidadVuelo);
                        capacidadMaxima = Math.min(entradaMaxima, capacidadMaxima);
                        salidaValida = capacidadMaxima;
                    }else{
                        String mensaje = "ERROR (Capacidad Ruta): Los productos no pueden entrar por incosistencias en la entrada";
                        Bitacora.escribir(mensaje);
                        throw new IllegalStateException(mensaje);    
                    }
                }else{
                    return capacidadVuelo;
                }
            }else{
                String mensaje = "ERROR (Capacidad Ruta): Los productos no pueden salir por incosistencias en la salida";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }
        }

        return capacidadMaxima;
    }

    /*
     * Añade los nuevos productos y las nuevas programaciones a sus respectivas colecciones. Nada que verificar
     * 
     * Remplazo de anadirProducto
     */
    public boolean registrarNuevosProgramacionesYProductos_v2(Ruta ruta, List<Producto> productos, List<Programacion> programaciones, Instant instanteActual)
    {
        boolean valido;
        Instant instanteLlegadUltimoVuelo;
        Almacen almacenDestino;

        valido = true;
        almacenDestino = destinoRuta(ruta);
        instanteLlegadUltimoVuelo = ruta.obtenerUltimoVuelo().getInstanteLlegada();

        for(Producto producto : productos)
        {
            valido &= almacenDestino.registrarRecojoDeProductos_v2(producto, instanteLlegadUltimoVuelo, false, instanteActual);
            this.productos.put(producto.getId(), producto);
        }
        
        this.programaciones.addAll(programaciones);

        return valido;
    }
    /* =========================================================== */

    /*
     * Devuelve un almacén según su id
     */
    public Almacen buscarAlmacen_v2(Long id)
    {
        return this.almacenes.get(id);
    }


    /*
     * Devuelve el almacén origen de un vuelo
     */
    public Almacen origenVuelo_v2(Vuelo vuelo)
    {
        long idAlmacenOrigen;

        idAlmacenOrigen = vuelo.getAlmacenSalida().getId();

        return this.almacenes.get(idAlmacenOrigen);
    }


    public Almacen origenRuta(Ruta ruta)
    {
        Vuelo primerVuelo;

        primerVuelo = ruta.obtenerPrimerVuelo();

        return this.almacenes.get(primerVuelo.getAlmacenSalida().getId());
    }

    public Almacen destinoRuta(Ruta ruta)
    {
        Vuelo ultimoVuelo;

        ultimoVuelo = ruta.obtenerUltimoVuelo();

        return ultimoVuelo.getAlmacenDestino();
    }

    public List<AbstractMap.SimpleEntry<Ruta, Integer>> obtenerRutasDePedido(
            long idPedido)
    {
        List<Programacion> programacionesDelPedido = programaciones.stream()
                .filter(programacion -> programacion.getPedido().getId() == idPedido)
                .toList();

        Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = programacionesDelPedido
                .stream()
                .collect(Collectors.groupingBy(programacion -> new LinkedList<>(programacion.getRuta().getVuelos()
                        .stream().map(Vuelo::getId).toList())));

        return programacionesPorRuta.keySet().stream().map(
                longs -> new AbstractMap.SimpleEntry<>(
                        new Ruta(
                                longs.stream().map(aLong -> vuelos.get(aLong)).toList()
                        ),
                        programacionesPorRuta.get(longs).size() // numero de programaciones de la
                                                                // ruta, o sea número de productos
                ))
                .toList();
    }

    public LinkedList<Almacen> obtenerAlmacenesPorRuta(Ruta vuelos)
    {
        LinkedList<Almacen> almacenesRuta = new LinkedList<>();

        for (Vuelo vuelo : vuelos.getVuelos())
        {
            if (vuelos.obtenerPrimerVuelo().equals(vuelo))
            {
                almacenesRuta.add(almacenes.get(vuelo.getAlmacenSalida().getId()));
            }
            almacenesRuta.add(vuelo.getAlmacenDestino());
        }

        return almacenesRuta;
    }

    public List<Programacion> obtenerProgramacionesQueUsanRuta(LinkedList<Long> ruta)
    {
        return programaciones.stream()
                .filter(programacion -> programacion.getRuta().getVuelos()
                        .stream().map(Vuelo::getId).toList().equals(ruta)
                        )
                .collect(Collectors.toList());

    }

    public List<Producto> obtenerProductosQueUsanRutaActiva(LinkedList<Long> ruta)
    {
        return obtenerProgramacionesQueUsanRuta(ruta).stream()
                .map(Programacion::getProducto)
                .collect(Collectors.toList());

    }

    public List<RutaProgramadaListadaDTO> obtenerRutasProgramadas() {

        Map<LinkedList<Vuelo>, List<Programacion>> programacionesPorRuta = programaciones.stream()
                .collect(Collectors.groupingBy(programacion -> programacion.getRuta().getIdsVuelos()));

        return programacionesPorRuta.keySet().stream().map(
                longs -> {
                    return new RutaProgramadaListadaDTO(
                            new LinkedList<>(
                                    longs.stream().map(aLong -> {
                                        Vuelo vuelo = vuelos.get(aLong);
                                        Almacen almOrigen = vuelo.getAlmacenSalida();
                                        Almacen almDestino = vuelo.getAlmacenDestino();
                                        return new VueloResumidoDTO(
                                                vuelo.getId(),
                                                almOrigen.getNombreCiudad(),
                                                almDestino.getNombreCiudad());
                                    }).toList()),
                            longs);
                }).collect(Collectors.toList());
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
                vuelo -> vuelo.getInstanteLlegada().isBefore(instant)
                        && vuelo.getInstanteLlegada().isBefore(instant.minus(6, ChronoUnit.DAYS)))
                .map(Vuelo::getId).collect(Collectors.toSet());
        System.out.println(" idsDeVuelosViejos (borrar): " + idsDeVuelosViejos);
        return vuelos.keySet().removeAll(idsDeVuelosViejos);
    }




    @Override
    public String toString() {
        return "Estado{" +
                " pedidos=" + pedidos.size() +
                ", vuelos=" + vuelos.size() +
                ", almacenes=" + almacenes.size() +
                ", programaciones=" + programaciones.size() +
                ", productos=" + productos.size() +
                // ", estado=" + estado +
                '}';
    }
}



