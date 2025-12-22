package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.*;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.text.Position.Bias;

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
     * Lanza una excepción con un mensaje formateado
     */
    public static void lanzarExcepcion(String metodo, String mensaje) throws Exception {
        String mensajeCompleto = "ERROR estado(" + metodo + "): " + mensaje;
        Bitacora.escribir(mensajeCompleto);
        throw new IllegalStateException(mensajeCompleto);
    }

    /*
     * Constructor principal
     */
    public EstadoGlobal(Map<Long, Almacen> almacenes,
            Map<Long, Vuelo> vuelos,
            Map<Long, Pedido> pedidos,
            List<Programacion> programaciones,
            Map<UUID, Producto> productos)
    {
        this.almacenes = almacenes != null ?
            new HashMap<>(almacenes) : new HashMap<>();
        this.vuelos = vuelos != null ?
            new HashMap<>(vuelos) : new HashMap<>();
        this.pedidos = pedidos != null ?
            new HashMap<>(pedidos) : new HashMap<>();
        this.programaciones = programaciones != null ?
            new LinkedList<>(programaciones) : new LinkedList<>();
        this.productos = productos != null ?
            new HashMap<>(productos) : new HashMap<>();
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

    /*************************/
    /* Métodos del simulador */ //Modifica el ctx
    /*************************/

    /*
     * Agrega nuevos pedidos al estado global. Si hay un pedido duplicado, no lo sobrescribe
     */
    public void agregarPedidosNuevos(List<Pedido> pedidosNuevos) {
        Map<Long, Pedido> pedidosFiltrados = pedidosNuevos.stream()
                .filter(pedido -> !this.pedidos.containsKey(pedido.getId()))
                .collect(Collectors.toMap(Pedido::getId, Function.identity()));
        
        this.pedidos.putAll(pedidosFiltrados);
    }

    /*
     * Borra los pedidos con antiguedad de DIAS_MAX_EN_MEMORIA
     */
    public boolean borrarPedidosViejos(Instant instanteActual) {
        Set<Long> pedidosViejos = pedidos.values().stream()
                .filter(pedido -> {
                    Instant hace1Semana = instanteActual.minus(Duration.ofDays(Hiperparametros.DIAS_MAX_EN_MEMORIA));
                    return pedido.getInstanteRegistro().isBefore(hace1Semana);
                }).map(Pedido::getId).collect(Collectors.toSet());

        return pedidos.keySet().removeAll(pedidosViejos);
    }

    /*
     * Agrega nuevos vuelos al estado global. Si hay un vuelo duplicado, no lo sobrescribe
     */
    public void agregarVuelosNuevos(List<Vuelo> vuelosNuevos)
    {
        Map<Long, Vuelo> vuelosFiltrados = vuelosNuevos.stream()
                .filter(vuelo -> !this.vuelos.containsKey(vuelo.getId()))
                .collect(Collectors.toMap(Vuelo::getId, Function.identity()));
        
        this.vuelos.putAll(vuelosFiltrados);
    }

    /*
     * Borra los vuelos con antiguedad de DIAS_MAX_EN_MEMORIA
     */
    public boolean borrarVuelosViejos(Instant instanteActual) {
        Set<Long> vuelosViejos = vuelos.values().stream()
                .filter(vuelo -> {
                    Instant instanteLimite = instanteActual.minus(Duration.ofDays(Hiperparametros.DIAS_MAX_EN_MEMORIA));
                    return vuelo.verificarLlegada(instanteLimite);
                }).map(Vuelo::getId).collect(Collectors.toSet());

        return vuelos.keySet().removeAll(vuelosViejos);
    }


    /*
     * Verifica si hay pedidos pendientes de programar (con programaciones faltantes > 0)
     */
    public boolean hayPedidosPendientes()
    {
        return pedidos.values().stream()
                .anyMatch(pedido -> pedido.obtenerCantidadProgramacionesFaltantes() > 0);
    }

    /************************/
    /* Método del algoritmo */
    /************************/
    
    /*
     * Inicializa el estado global. Se considera que el EstadoGlobal que llega al algoritmo contiene los almacenes, con los productos existentes en su respectivo almacén, en el instanteActual. Además, los vuelos tienen productos existentes en tránsito, de los cuales una cantidad tiene asociados programaciones que no se pueden cancelar. 
     * 
     * Productos D = 0 y Productos C = 0
     */
    public void inicializar(Instant instanteActual, Instant momentoMaximoIndispensable) throws Exception {
        for(Almacen almacen : this.almacenes.values()) {
            almacen.limpiarCambiosYProductosFuturos(); //BORRAR METODO ESTO ESPO RSI LAS MOSCAS
        }
        inicializarProgramacionesIncancelables(instanteActual, momentoMaximoIndispensable);
        inicializarProgramacionesColadas(instanteActual, momentoMaximoIndispensable);
        inicializarVuelosEnTransito(instanteActual, momentoMaximoIndispensable);

        calcularPuntajesDePedidos(instanteActual);
//Testeador.verificarConsistenciasEnCambiosTEST(this, "Después de inicializar el estado global");
    }

    private void inicializarProgramacionesColadas(Instant instanteActual, Instant momentoMaximoIndispensable) {
        for (Programacion programacion : this.programaciones) {
            if (!programacion.validarIncancelable_I(instanteActual)) {
                // Todas las que se trajeron adicionalmente, las coladas, no incancelables
                // Su último vuelo está en curso o ya desembarcó.
//                Bitacora.escribir("Nos ha tocado una progra colada de tipo: " + programacion.getEstado());
                Ruta ruta = programacion.getRuta();
                Producto producto = programacion.getProducto();

                for(Vuelo vuelo : ruta.getVuelos()){
                    Instant instanteSalida = vuelo.getInstanteSalida();
                    Instant instanteLlegada = vuelo.getInstanteLlegada();
                    Almacen almacenSalida  = vuelo.getAlmacenSalida();
                    Almacen almacenDestino = vuelo.getAlmacenDestino();

                    // ¿USAR ABIERTO O CERRADO? No estoy seguro aún

                    if( !instanteSalida.isAfter(momentoMaximoIndispensable) && instanteSalida.isAfter(instanteActual) ) {
                        // Su salida está antes del TOPE
                        almacenSalida.registrarSalidaIllegal(instanteSalida, 1);
                        // Entregamos prematuramente también el pedido, si su vuelo último ya salió.
//                        if( vuelo.getId() == ruta.obtenerUltimoVuelo().getId())
//                            this.pedidos.get(programacion.getPedido().getId()).registrarProductoEntregadoIlegalmente(producto);
                    }
                    if( !instanteLlegada.isAfter(momentoMaximoIndispensable)  && instanteLlegada.isAfter(instanteActual) ) {
                        // Su llegada está antes del TOPE
                        almacenDestino.registrarEntradaIlegalmente(instanteLlegada, 1);
                    }
                    if( vuelo.getId() == ruta.obtenerUltimoVuelo().getId()
                            && !ruta.obtenerInstanteRecojo().isAfter(momentoMaximoIndispensable)
                            && ruta.obtenerInstanteRecojo().isAfter(instanteActual)) {
                        // Este es el último de su programación respectiva y el instante de recojo es antes del TOPE
                        almacenDestino.registrarSalidaIllegal(ruta.obtenerInstanteRecojo(), 1);
                    }
                }

            }
        }
    }

    /*
     * Esta función recorre todas los vuelos y registra los cambios en los almacenes correspondientes, actualizando sus productos futuros
     */
    private void inicializarVuelosEnTransito(Instant instanteActual, Instant momentoMaximoIndispensable) throws Exception {
        int totalVuelosEnTransito = 0;
        int totalProductosEnTransito = 0;

        List<Programacion> prograsNoIncancelables = programaciones.stream().filter(
                programacion -> !programacion.validarIncancelable_I(instanteActual)).collect(Collectors.toList());
        List<UUID> idsProdsFichadosNoIncancelables = prograsNoIncancelables.stream()
                .map(programacion -> programacion.getProducto().getId()).collect(Collectors.toList());
        // NO SON TODOS LOS "NO INCANCELABLES" PERO SÍ UNA PARTE

        for (Vuelo vuelo : this.vuelos.values()) {
            Almacen almacenDestino = vuelo.getAlmacenDestino();
            if(vuelo.estaEnTransito(instanteActual)) {
                // Vuelo en tránsito
                boolean valido = true; 
                List<Producto> productosFuturos = vuelo.getInventario();

                for(Producto producto : productosFuturos) {
                    // Algunos de estos prods serán solo cambios; y otros serán prods futuros (utilizables) y con cambios.

                    if(!producto.validarIncancelable_B()){ // Si no es un incancelable de los que ya pusimos antes.
                        // Podemos reutilizar, se marca en inventario futuro, no solo una simple entrada de 1.
                        valido &= almacenDestino.registrarProductoFuturoIlegalmente(producto, vuelo.getInstanteLlegada());
//                        valido &= almacenDestino.registrarSalidaIllegal(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), 1);

                        if(!valido) {
                            int inventarioActual = almacenDestino.getInventario().size();
                            int inventarioFuturo = almacenDestino.obtenerProductos(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO))).size();
                            Bitacora.escribir("ERROR al registrar recojo - Almacén ID=%d, Instante Recojo=%s, Inventario Actual=%d, Inventario Futuro en ese instante=%d, Producto=%s",
                                    almacenDestino.getId(), vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), inventarioActual, inventarioFuturo, producto.getId());

                            lanzarExcepcion("Inicializacion", "No se puede registrar el recojo de los productos");
                        }
                    }else{ // Producto tipo a, se puede reutilizar y se registra en inventario futuro...
                        // ...SALVO QUE SEA PARTE DE UNA PROGRAMACIÓN QUE HA VENIDO COLADA!
//                        if ( idsProdsFichadosNoIncancelables.contains(producto.getId()) ) {
//                            List<Programacion> prograsDelVuelo = prograsNoIncancelables.stream().filter(
//                                    programacion -> programacion.getRuta().tieneVuelo(vuelo.getId())
//                            ).collect(Collectors.toList());
//                            for(Programacion programacion : prograsDelVuelo) {
//                                // No tengo idea de lo que se debe hacer...
//                            }
//                        }else {
//                            valido &= almacenDestino.registrarProductoFuturoIlegalmente(producto, vuelo.getInstanteLlegada());
                            //^^^^^^ registrado ilegalmente debido a la asincronía de actualización de cambios positivos y negativos
                            // en los almacenes
//                        }
                    }
                    if(!valido) {
                        lanzarExcepcion("inicializarVuelosEnTransito", "No se pudo registrar el producto futuro en el almacén destino del vuelo ID=" + vuelo.getId());
                    }
                }
            }
        }
    }

    /*
     * Esta función itera sobre las programaciones para registrar el recojo de los productos de los almacenes (osea, un cambio más) a las 2 horas
     */ 
    private void inicializarProgramacionesIncancelables(Instant instanteActual, Instant momentoMaximoIndispensable) throws Exception {
        for (Programacion programacion : this.programaciones) {
            if (programacion.validarIncancelable_I(instanteActual)) {
                // Su último vuelo está en curso o ya desembarcó.
                Ruta ruta = programacion.getRuta();
                Vuelo ultimoVuelo = ruta.obtenerUltimoVuelo();
                Almacen almacenDestino = ultimoVuelo.getAlmacenDestino();
                Producto producto = programacion.getProducto();
                Instant instanteRecojo = ruta.obtenerInstanteRecojo();
                Instant instanteLlegada = ultimoVuelo.getInstanteLlegada();
                 
                Pedido atendidoPrematuro = this.pedidos.get(programacion.getPedido().getId());
                if(atendidoPrematuro!=null){
                    atendidoPrematuro.registrarProductoEntregado(producto);
                }else{
                    System.out.println("QUE"); Bitacora.escribir("Pedido no encontrado: "+programacion.getPedido() );}

                // Determinar si la programación está en el último vuelo o en el almacén
                if (instanteActual.isBefore(instanteLlegada)) {
                    // Producto en tránsito (en el último vuelo)
                    almacenDestino.registrarEntradaIlegalmente(instanteLlegada, 1);
                    almacenDestino.registrarSalidaIllegal(instanteRecojo, 1);
                    continue;
                } // Si pasa de aquí, está ya en el almacén, no es necesario poner la entrada

                // Producto ya llegó al almacén destino (instanteActual >= instanteLlegada)
                almacenDestino.registrarSalidaIllegal(instanteRecojo, 1);

            }else{ // Sería una progra colada, pero mejor la vemos separadamente.
//                lanzarExcepcion("Inicializacion", "Existe una programación que se puede cancelar");// todas las progras pasan uu
            }
        }
    }

    /*
     * Esta función calcula los puntajes de los pedidos (los puntajes ahora son un atributo de la clase Pedido)
     */
    private void calcularPuntajesDePedidos(Instant instanteActual) {
        Double puntaje;

        for (Pedido pedido : this.pedidos.values()) {
            puntaje = CalculadorDeFitness.asignarPuntajesPedidos(pedido, instanteActual);
            
            pedido.setPuntaje(puntaje);
        }
    }

    /*
     * Corre un algoritmo BFS para la generación de rutas y crea su lista de adyacencia
     */
    public List<Ruta> calcularRutas(Instant instanteActual) throws Exception {
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

        int rutasDesdeOrigen, maxRutas, rutasParaDestino;

        rutas = new ArrayList<>();
        firmas = new HashSet<>();
        destinos = obtenerAlmacenesDestino();
        origenes = obtenerAlmacenesOrigen();
        vuelosPorOrigen = obtenerVuelosPorOrigen();

        for (Almacen almacenDestino : destinos) {
            rutasParaDestino = 0;

            for (Almacen origen : origenes) {
                if (rutasParaDestino >= Hiperparametros.MAX_RUTAS_POR_DESTINO) {
                    break;
                }

                cola = inicializarCola(origen, vuelosPorOrigen, instanteActual);
                rutasDesdeOrigen = 0;
                maxRutas = origen.isInfinito() ? Hiperparametros.MAX_RUTAS_DESDE_ORIGEN : Hiperparametros.MAX_RUTAS_DESDE_ORIGEN_NO_INFINITO;
                
                while (!cola.isEmpty() && rutasDesdeOrigen < maxRutas && rutasParaDestino < Hiperparametros.MAX_RUTAS_POR_DESTINO) {
                    path = cola.poll();
                    ultimo = path.obtenerUltimoVuelo();
                    destinoUltimo = path.obtenerAlmacenDestino();

                    if (ultimo.getAlmacenDestino().equals(almacenDestino)) {
                        if (rutaSinInfinitosIntermedios(path.getVuelos())) {
                            String firma = crearFirmaRuta(path.getVuelos());
                            if (firmas.add(firma)) {
                                rutas.add(new Ruta(path, true));
                                rutasDesdeOrigen++;
                                rutasParaDestino++;
                            }
                        }
                        continue;
                    }

                    if (path.obtenerCantidadVuelos() >= Hiperparametros.MAX_VUELOS || destinoUltimo.isInfinito()) {
                        continue;
                    }

                    sucesores = vuelosPorOrigen.getOrDefault(ultimo.getAlmacenDestino().getId(), Collections.emptyList());

                    for (Vuelo siguiente : sucesores) {
                        if (esVueloAdmisibleComoSiguiente(path, ultimo, siguiente, instanteActual)) {
                            LinkedList<Vuelo> nuevosVuelos = new LinkedList<>(path.getVuelos());
                            nuevosVuelos.add(siguiente);
                            nuevoPath = new Ruta(nuevosVuelos);
                            cola.add(nuevoPath);
                        }
                    }
                }
            }
        }

        calcularAdyacenciaRutasPorAlmacen(rutas);

        return rutas;
    }

    /*
     * Corre un algoritmo BFS para la generación de rutas y crea su lista de adyacencia
     */
    public List<Ruta> calcularRutasv2(Instant instanteActual) throws Exception {
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

        int rutasDesdeOrigen, maxRutas, rutasParaDestino;

        rutas = new ArrayList<>();
        firmas = new HashSet<>();
        destinos = obtenerAlmacenesDestino();
        origenes = obtenerAlmacenesOrigenv2();
        vuelosPorOrigen = obtenerVuelosPorOrigen();

        for (Almacen almacenDestino : destinos) {
            rutasParaDestino = 0;

            for (Almacen origen : origenes) {
                if (rutasParaDestino >= Hiperparametros.MAX_RUTAS_POR_DESTINO) {
                    break;
                }

                cola = inicializarCola(origen, vuelosPorOrigen, instanteActual);
                rutasDesdeOrigen = 0;
                maxRutas = origen.isInfinito() ? Hiperparametros.MAX_RUTAS_DESDE_ORIGEN : Hiperparametros.MAX_RUTAS_DESDE_ORIGEN_NO_INFINITO;

                while (!cola.isEmpty() && rutasDesdeOrigen < maxRutas && rutasParaDestino < Hiperparametros.MAX_RUTAS_POR_DESTINO) {
                    path = cola.poll();
                    ultimo = path.obtenerUltimoVuelo();
                    destinoUltimo = path.obtenerAlmacenDestino();

                    if (ultimo.getAlmacenDestino().equals(almacenDestino)) {
                        if (rutaSinInfinitosIntermedios(path.getVuelos())) {
                            String firma = crearFirmaRuta(path.getVuelos());
                            if (firmas.add(firma)) {
                                rutas.add(new Ruta(path, true));
                                rutasDesdeOrigen++;
                                rutasParaDestino++;
                            }
                        }
                        continue;
                    }

                    if (path.obtenerCantidadVuelos() >= Hiperparametros.MAX_VUELOS || destinoUltimo.isInfinito()) {
                        continue;
                    }

                    sucesores = vuelosPorOrigen.getOrDefault(ultimo.getAlmacenDestino().getId(), Collections.emptyList());

                    for (Vuelo siguiente : sucesores) {
                        if (esVueloAdmisibleComoSiguiente(path, ultimo, siguiente, instanteActual)) {
                            LinkedList<Vuelo> nuevosVuelos = new LinkedList<>(path.getVuelos());
                            nuevosVuelos.add(siguiente);
                            nuevoPath = new Ruta(nuevosVuelos);
                            cola.add(nuevoPath);
                        }
                    }
                }
            }
        }

        calcularAdyacenciaRutasPorAlmacen(rutas);

        return rutas;
    }
    /*
     * Obtiene los almacenes no infinitos que tengan pedidos pendientes. Es un set porque los pedidos pueden repetir destino
     */
    private Set<Almacen> obtenerAlmacenesDestino() {
        return this.pedidos.values().stream()
                .filter(pedido -> pedido.obtenerCantidadProgramacionesFaltantes() > 0)
                .map(Pedido::getAlmacenDestino)
                .filter(almacen -> !almacen.isInfinito())
                .collect(Collectors.toSet());
    }

    /*
     * Obtiene los almacenes que seam infinitos o tengan stock. Es una lista porque itera sobre this.almacenes, que solo posee una copia de cada almacen. 
     * Retorna la lista ordenada con los almacenes infinitos primero.
     */
    private List<Almacen> obtenerAlmacenesOrigen() {
        return this.almacenes.values().stream()
                .filter(almacen -> almacen.isInfinito()
                        || !almacen.getInventarioFuturo().isEmpty()
                        || !almacen.getInventario().isEmpty())
                .sorted(Comparator.comparing(Almacen::isInfinito).reversed())
                .collect(Collectors.toList());
    }

    private List<Almacen> obtenerAlmacenesOrigenv2() {
        return this.almacenes.values().stream()
                .filter(almacen -> almacen.isInfinito()
                        || !almacen.getInventarioFuturo().isEmpty()
                        || !almacen.getInventario().isEmpty())
                .sorted(Comparator.comparing(Almacen::isInfinito).reversed())
                .collect(Collectors.toList());
    }

    /*
     * Agrupa los vuelos por almacén de origen y ordena cada grupo por instante de salida.
     * Retorna un mapa donde la clave es el ID del almacén de origen y el valor es una lista
     * de vuelos ordenados cronológicamente. Lanza excepción si encuentra vuelos con instanteSalida null.
     */
    private Map<Long, List<Vuelo>> obtenerVuelosPorOrigen() throws Exception {
        Map<Long, List<Vuelo>> vuelosPorOrigen = new HashMap<>();
        
        for (Vuelo vuelo : this.vuelos.values()) {
            if (vuelo.getInstanteSalida() == null) {
                lanzarExcepcion("obtenerVuelosPorOrigen", "Vuelo con instanteSalida es null");
            }
            
            Long idAlmacenSalida = vuelo.getAlmacenSalida().getId();
            vuelosPorOrigen.computeIfAbsent(idAlmacenSalida, k -> new ArrayList<>()).add(vuelo);
        }
        
        for (Map.Entry<Long, List<Vuelo>> entry : vuelosPorOrigen.entrySet()) {
            entry.getValue().sort(Comparator.comparing(Vuelo::getInstanteSalida));
        }
        
        return vuelosPorOrigen;
    }

    /*
     * Inicializa la cola del BFS con rutas de un solo vuelo que parten del almacén origen.
     * Filtra los vuelos que cumplen las restricciones:
     * - Tienen capacidad disponible
     * - No han partido aún
     * - El almacén origen tiene stock disponible en el instante de salida (si no es infinito)
     */
    private Queue<Ruta> inicializarCola(Almacen origen, Map<Long, List<Vuelo>> vuelosPorOrigen, Instant instanteActual) {
        Queue<Ruta> cola = new ArrayDeque<>();
        List<Vuelo> iniciales = vuelosPorOrigen.getOrDefault(origen.getId(), Collections.emptyList());

        for (Vuelo vuelo : iniciales) {
            boolean sinCapacidad = vuelo.obtenerEspacioVacio() <= 0;
            boolean yaPartio = vuelo.verificarSalida(instanteActual);
            boolean origenSinStock = !origen.isInfinito() && 
                    origen.getInventario().isEmpty() && 
                    origen.getInventarioFuturo().isEmpty();

            if (sinCapacidad || yaPartio || origenSinStock) {
                continue;
            }

            LinkedList<Vuelo> vuelosRuta = new LinkedList<>();
            vuelosRuta.add(vuelo);
            Ruta rutaInicial = new Ruta(vuelosRuta);
            cola.add(rutaInicial);
        }

        return cola;
    }

    private boolean rutaSinInfinitosIntermedios(List<Vuelo> path) {
        return path.stream()
                .skip(1)
                .map(Vuelo::getAlmacenDestino)
                .noneMatch(Almacen::isInfinito);
    }

    public String crearFirmaRuta(List<Vuelo> path) {
        return path.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.joining("-"));
    }

    private boolean esVueloAdmisibleComoSiguiente(
            Ruta path,
            Vuelo ultimo,
            Vuelo siguiente,
            Instant instanteActual) {
        boolean valido;

        valido =// tiene capacidad 
                siguiente.obtenerEspacioVacio() > 0
                // no ha partido
                && !siguiente.verificarSalida(instanteActual)
                // respeta la espera mínima entre vuelos
                && !siguiente.getInstanteSalida().isBefore(
                        ultimo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS)))
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
     */
    private void calcularAdyacenciaRutasPorAlmacen(List<Ruta> rutasPosibles) {
        HashMap<Long, List<Ruta>> indice = new HashMap<>();

//Bitacora.escribir("=== CALCULANDO LISTA DE ADYACENCIA ===");
//Bitacora.escribir("Total de rutas computadas: %d", rutasPosibles.size());

        for (Almacen almacen : this.almacenes.values()) {
            List<Ruta> rutasDelAlmacen = rutasPosibles.stream()
                    .filter(ruta ->
                            ruta.obtenerAlmacenDestino().getId() == almacen.getId())
                    .collect(Collectors.toList());
/*/
if (!rutasDelAlmacen.isEmpty()) {
    // Contar rutas por tipo de origen
    long rutasDesdeInfinito = rutasDelAlmacen.stream()
            .filter(ruta -> ruta.obtenerAlmacenOrigen().isInfinito())
            .count();
    long rutasDesdeNoInfinito = rutasDelAlmacen.size() - rutasDesdeInfinito;

    Bitacora.escribir("Almacén Destino: '%s' (ID=%d) | Total rutas=%d | Desde Infinito=%d | Desde No-Infinito=%d",
            almacen.getNombreCiudad(),
            almacen.getId(),
            rutasDelAlmacen.size(),
            rutasDesdeInfinito,
            rutasDesdeNoInfinito);
} 
*/
            indice.put(almacen.getId(), rutasDelAlmacen);
        }
//Bitacora.escribir("=== FIN CÁLCULO LISTA DE ADYACENCIA ===");
        this.adyacencia = indice;
    }

    /*
     * Obtiene los Pedidos con cantidadProductosPendientes sea mayor a 0
     */
    public List<Pedido> obtenerPedidosPendientes() {
        return this.getPedidos().values()
                .stream()
                .filter(pedido -> pedido.obtenerCantidadProgramacionesFaltantes() > 0)
                .collect(Collectors.toList());
    }

    /*
     * Obtiene las rutas validas para el pedido tomando en cuenta los plazos y el destino.
     * Esta función no asegura que se retorne rutas con capacidad (esto último se verifica en elegirRuta)
     */
    public List<Ruta> obtenerRutasValidas(Pedido pedidoElegido) throws Exception {
        Almacen almacenDestino;
        List<Ruta> rutasValidas;
        Instant instanteRegistro, instanteLimite;
        
        almacenDestino = pedidoElegido.getAlmacenDestino();
        rutasValidas = this.adyacencia.get(almacenDestino.getId());
//Bitacora.escribir("RUTAS VÁLIDAS SACADAS DE ADYACENCIA PARA ALMACÉN ID: "+almacenDestino+"\n"+
//        PrettyPrinter.printList(rutasValidas));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "LA LISTA DE ADYACENCIA NO TIENE ORIGENES INFINITOS");
        instanteRegistro = pedidoElegido.getInstanteRegistro();
        instanteLimite = pedidoElegido.obtenerInstanteMaximoLlegadaUltimoVuelo();
        rutasValidas = new ArrayList<>(rutasValidas.stream()
                .filter(ruta -> ruta.verificarRutaNoEmpieza(instanteRegistro) 
                        && ruta.verificarUltimoVueloAterrizado(instanteLimite))
                .collect(Collectors.toList()));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "DEPSUES DE FILTROS FLAKO");
        if(rutasValidas.isEmpty()) {
            lanzarExcepcion("Rutas invalidas", "No se encontraron rutas validas para el pedido");
        }

        return rutasValidas;
    }

    public List<Ruta> obtenerRutasValidasv2(Pedido pedidoElegido) throws Exception {
        Almacen almacenDestino;
        List<Ruta> rutasValidas;
        Instant instanteRegistro, instanteLimite;

        almacenDestino = pedidoElegido.getAlmacenDestino();
        rutasValidas = this.adyacencia.get(almacenDestino.getId());
//Bitacora.escribir("RUTAS VÁLIDAS SACADAS DE ADYACENCIA PARA ALMACÉN ID: "+almacenDestino+"\n"+
//        PrettyPrinter.printList(rutasValidas));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "LA LISTA DE ADYACENCIA NO TIENE ORIGENES INFINITOS");
        instanteRegistro = pedidoElegido.getInstanteRegistro();
        instanteLimite = pedidoElegido.obtenerInstanteMaximoLlegadaUltimoVuelo();
//        rutasValidas = new ArrayList<>(rutasValidas.stream()
//                .filter(ruta -> ruta.verificarRutaNoEmpieza(instanteRegistro)
//                        && ruta.verificarUltimoVueloAterrizado(instanteLimite))
//                .collect(Collectors.toList()));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "DEPSUES DE FILTROS FLAKO");
        if(rutasValidas.isEmpty()) {
            lanzarExcepcion("Rutas invalidas", "No se encontraron rutas validas para el pedido");
        }

        return rutasValidas;
    }

    /*
     * Obtiene la capacidad máxima que puede transportar una ruta mediante el método de las sumas parciales
     */
    public int obtenerCapacidadRuta(Ruta rutaElegida, int capacidadAlmacen) throws Exception {
        int capacidadMaxima, entradaMaxima, salidaValida, capacidadVuelo;

        capacidadMaxima = 0;
        salidaValida = capacidadAlmacen;

        for(Vuelo vuelo : rutaElegida.getVuelos())
        {
            Almacen almacenSalida = vuelo.getAlmacenSalida();
            Almacen almacenEntrada = vuelo.getAlmacenDestino();
            capacidadVuelo = vuelo.obtenerEspacioVacio();

            if(capacidadVuelo > 0)
            {   //el vuelo tiene capacidad
                entradaMaxima = almacenEntrada.calcularEspacioVacioMaximoEnInstante(vuelo.getInstanteLlegada());

                if(almacenEntrada.verificaEntrada(vuelo.getInstanteLlegada(), entradaMaxima))
                {   // la salida y la entrada son validas y el vuelo tiene capacidad
                    capacidadMaxima = Math.min(salidaValida, capacidadVuelo);
                    capacidadMaxima = Math.min(entradaMaxima, capacidadMaxima);
                    salidaValida = capacidadMaxima;
                }else{
                    lanzarExcepcion("Capacidad Ruta", "Los productos no pueden entrar por incosistencias en la entrada");   
                }
            }else{
                return capacidadVuelo;
            }
        }

        return capacidadMaxima;
    }

    public int obtenerCapacidadRutav2(Ruta rutaElegida, int capacidadAlmacen) throws Exception {
        int capacidadMaxima, entradaMaxima, salidaValida, capacidadVuelo;

        capacidadMaxima = 0;
        salidaValida = capacidadAlmacen;

        for(Vuelo vuelo : rutaElegida.getVuelos())
        {
            Almacen almacenSalida = vuelo.getAlmacenSalida();
            Almacen almacenEntrada = vuelo.getAlmacenDestino();
            capacidadVuelo = vuelo.obtenerEspacioVaciov2();

            if(capacidadVuelo > 0)
            {   //el vuelo tiene capacidad
                entradaMaxima = almacenEntrada.calcularEspacioVacioMaximoEnInstantev2(vuelo.getInstanteLlegada());

                if(almacenEntrada.verificaEntradav2/*verificaEntrada*/(vuelo.getInstanteLlegada(), entradaMaxima))
                {   // la salida y la entrada son validas y el vuelo tiene capacidad
                    capacidadMaxima = Math.min(salidaValida, capacidadVuelo);
                    capacidadMaxima = Math.min(entradaMaxima, capacidadMaxima);
                    salidaValida = capacidadMaxima;
                }else{
                    lanzarExcepcion("Capacidad Ruta", "Los productos no pueden entrar por incosistencias en la entrada");
                }
            }else{
                return capacidadVuelo;
            }
        }

        return capacidadMaxima;
    }

    /*
     * Añade los nuevos productos y las nuevas programaciones a sus respectivas colecciones. Ademas, registra el recojo de los productos en el almacen destino del pedido. Solo se pueden registrar productos D o C
     */
    public boolean registrarNuevosProgramacionesYProductos(Ruta ruta, List<Producto> productos, List<Programacion> programaciones, Instant instanteActual) throws Exception
    {
        boolean valido;
        Instant instanteLlegadUltimoVuelo;
        Almacen almacenDestino;

        valido = true;
        almacenDestino = ruta.obtenerAlmacenDestino();
        instanteLlegadUltimoVuelo = ruta.obtenerUltimoVuelo().getInstanteLlegada();

        for(Producto producto : productos) {
            valido &= almacenDestino.registrarRecojoDeProductosv2(producto, instanteLlegadUltimoVuelo);

            if(producto.validarNoPlanificado_A()){
                producto.transNoPlanificado_A_PlanificadoExistente_D();
            }

            if(producto.validarPlanificadoExistente_D() || producto.validarPlanificadoNoExistente_C()) {
                this.productos.put(producto.getId(), producto);    
            }else{
                lanzarExcepcion("registrarProgramaciones", "Solo se pueden registrar productos D o C");
            }
            
        }
        
        this.programaciones.addAll(programaciones);

        return valido;
    }

    /*********************/
    /* Métodos del front */
    /*********************/

    /*
     * Obtiene las rutas usadas en un pedido junto con la cantidad de productos asignados a cada ruta
     */
    public List<AbstractMap.SimpleEntry<Ruta, Integer>> obtenerRutasDePedido(long idPedido) {
        List<Programacion> programacionesDelPedido = programaciones.stream()
                .filter(programacion -> programacion.getPedido().getId() == idPedido)
                .collect(Collectors.toList());

        Map<LinkedList<Vuelo>, List<Programacion>> programacionesPorRuta = programacionesDelPedido.stream()
                .collect(Collectors.groupingBy(programacion -> programacion.getRuta().getVuelos()));

        return programacionesPorRuta.keySet().stream()
                .map(vuelos -> {
                    Ruta ruta = new Ruta(vuelos);
                    Integer cantidadProductos = programacionesPorRuta.get(vuelos).size();
                    return new AbstractMap.SimpleEntry<>(ruta, cantidadProductos);
                }).collect(Collectors.toList());
    }

    /*
     * Obtiene las programaciones que usan una ruta específica
     */
    public List<Programacion> obtenerProgramacionesQueUsanRuta(LinkedList<Long> ruta)
    {
        return this.programaciones.stream()
                .filter(programacion -> programacion.getRuta().getVuelos()
                        .stream().map(Vuelo::getId).collect(Collectors.toList()).equals(ruta))
                .collect(Collectors.toList());
    }

    /*
     * Obtiene los productos que usan una ruta específica
     */
    public List<Producto> obtenerProductosQueUsanRutaActiva(LinkedList<Long> ruta)
    {
        return obtenerProgramacionesQueUsanRuta(ruta).stream()
                .map(Programacion::getProducto)
                .collect(Collectors.toList());

    }

    /*
     * Obtiene las rutas programadas en el estado global
     */
    public List<RutaProgramadaListadaDTO> obtenerRutasProgramadas() {

        Map<LinkedList<Vuelo>, List<Programacion>> programacionesPorRuta = programaciones.stream()
                .collect(Collectors.groupingBy(programacion -> programacion.getRuta().getVuelos()));

        return programacionesPorRuta.keySet().stream()
                .map(vuelos -> {
                    LinkedList<VueloResumidoDTO> vuelosResumidos = vuelos.stream()
                            .map(vuelo -> new VueloResumidoDTO(
                                    vuelo.getId(),
                                    vuelo.getAlmacenSalida().getNombreCiudad(),
                                    vuelo.getAlmacenDestino().getNombreCiudad()))
                            .collect(Collectors.toCollection(LinkedList::new));
                    
                    LinkedList<Long> idsVuelos = vuelos.stream()
                            .map(Vuelo::getId)
                            .collect(Collectors.toCollection(LinkedList::new));
                    
                    return new RutaProgramadaListadaDTO(vuelosResumidos, idsVuelos);
                }).collect(Collectors.toList());
    }

    /*********************/
    /* Helpers */
    /*********************/

    /*
     * Busca un almacén por su ID y devuelve la referencia al objeto
     */
    public Almacen buscarAlmacenPorId(Long id) {
        return this.almacenes.get(id);
    }

    /*
     * Busca un vuelo por su ID y devuelve la referencia al objeto
     */
    public Vuelo buscarVueloPorId(Long id) {
        return this.vuelos.get(id);
    }

    /*
     * Busca un pedido por su ID y devuelve la referencia al objeto
     */
    public Pedido buscarPedidoPorId(Long id) {
        return this.pedidos.get(id);
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



