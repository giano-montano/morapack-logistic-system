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
    private HashMap<Long, List<Ruta>> adyacenciaDestinos;
    private HashMap<Long, List<Vuelo>> adyacenciaOrigenes;

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
        this.adyacenciaDestinos = copia.adyacenciaDestinos;
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
    public void inicializar(Instant instanteActual) throws Exception {
        for(Almacen almacen : this.almacenes.values()) {
            almacen.limpiarCambiosYProductosFuturos(); //BORRAR METODO ESTO ESPO RSI LAS MOSCAS
        }
        inicializarVuelosEnTransito(instanteActual);
        inicializarProgramacionesIncancelables(instanteActual);
        calcularAdyacenciaOrigenesPorAlmacen();
        forzarConsistencia(instanteActual);
        calcularPuntajesDePedidos(instanteActual);
        
Testeador.verificarConsistenciasEnCambiosTEST(this, "Después de inicializar el estado global");
    }

    /*
     * Esta función recorre todas los vuelos y registra los cambios en los almacenes correspondientes, actualizando sus productos futuros
     */
    private void inicializarVuelosEnTransito(Instant instanteActual) throws Exception {
        int totalVuelosEnTransito = 0;
        int totalProductosEnTransito = 0;

        
        for (Vuelo vuelo : this.vuelos.values()) {
            Almacen almacenDestino = vuelo.getAlmacenDestino();

            if(vuelo.verificarSalida(instanteActual) && !vuelo.verificarLlegada(instanteActual)) {
                // Vuelo en tránsito
                boolean valido = true; 
                List<Producto> productosFuturos = vuelo.getInventario();

                for(Producto producto : productosFuturos) {
                    if(producto.validarIncancelable_B()){ // Producto incancelable, solo registrado en cambios
                        valido &= almacenDestino.registrarEntradaIlegalmente(vuelo.getInstanteLlegada(), 1);
                        valido &= almacenDestino.registrarSalidaIllegal(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), 1);

                        if(!valido) {
                            int inventarioActual = almacenDestino.getInventario().size();
                            int inventarioFuturo = almacenDestino.obtenerProductos(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO))).size();
                            Bitacora.escribir("ERROR al registrar recojo - Almacén ID=%d, Instante Recojo=%s, Inventario Actual=%d, Inventario Futuro en ese instante=%d, Producto=%s",
                                    almacenDestino.getId(), vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), inventarioActual, inventarioFuturo, producto.getId());

                            lanzarExcepcion("Inicializacion", "No se puede registrar el recojo de los productos");
                        }
                    }else{ // Producto tipo a, se puede reutilizar y se registra en inventario futuro
                        valido &= almacenDestino.registrarProductoFuturoIlegalmente(producto, vuelo.getInstanteLlegada());
//
                        //^^^^^^ registrado ilegalmente debido a la asincronía de actualización de cambios positivos y negativos
                        // en los almacenes
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
    private void inicializarProgramacionesIncancelables(Instant instanteActual) throws Exception {
        for (Programacion programacion : this.programaciones) {
            if (programacion.validarIncancelable_I(instanteActual)) {
                Ruta ruta = programacion.getRuta();
                Vuelo ultimoVuelo = ruta.obtenerUltimoVuelo();
                Almacen almacenDestino = ultimoVuelo.getAlmacenDestino();
                Producto producto = programacion.getProducto();
                Instant instanteRecojo = ruta.obtenerInstanteRecojo();
                Instant instanteLlegada = ultimoVuelo.getInstanteLlegada();
                 
                this.pedidos.get(programacion.getPedido().getId()).registrarProductoEntregado(producto);

                // Determinar si la programación está en el último vuelo o en el almacén
                if (instanteActual.isBefore(instanteLlegada)) {
                    // Producto en tránsito (en el último vuelo)
                    continue;
                }
                
                // Producto ya llegó al almacén destino (instanteActual >= instanteLlegada)
                almacenDestino.registrarSalidaIllegal(instanteLlegada.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), 1);

            }else{
                lanzarExcepcion("Inicializacion", "Existe una programación que se puede cancelar");
            }
        }
    }

    /*
     * Calcula la lista de adyacencia de vuelos por almacén de origen.
     * Para cada almacén, mapea una lista de vuelos que tienen ese almacén como origen,
     * ordenados por instante de salida.
     */
    private void calcularAdyacenciaOrigenesPorAlmacen() {
        HashMap<Long, List<Vuelo>> adyacencia = new HashMap<>();
        
        for (Almacen almacen : this.almacenes.values()) {
            List<Vuelo> vuelosDelAlmacen = this.vuelos.values().stream()
                    .filter(vuelo -> vuelo.getAlmacenSalida().equals(almacen))
                    .sorted(Comparator.comparing(Vuelo::getInstanteSalida))
                    .collect(Collectors.toList());
            
            adyacencia.put(almacen.getId(), vuelosDelAlmacen);
        }
        
        this.adyacenciaOrigenes = adyacencia;
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
     * Clase auxiliar para asociar un almacén con su instante de colapso.
     * Implementa Comparable para permitir el ordenamiento en PriorityQueue.
     */
    private static class AlmacenConColapso implements Comparable<AlmacenConColapso> {
        final Almacen almacen;
        final Instant instanteColapso;
        final int cantidadColapso;
        
        AlmacenConColapso(Almacen almacen, Instant instanteColapso, int cantidadColapso) {
            this.almacen = almacen;
            this.instanteColapso = instanteColapso;
            this.cantidadColapso = cantidadColapso;
        }
        
        @Override
        public int compareTo(AlmacenConColapso otro) {
            return this.instanteColapso.compareTo(otro.instanteColapso);
        }
    }

    /*
     * Fuerza la consistencia de los almacenes verificando que la suma acumulada de cambios
     * no exceda el rango [0;C] donde C es la capacidad del almacén.
     * Itera sobre los almacenes inconsistentes intentando repararlos hasta que todos sean
     * consistentes o hasta que el método arreglarAlmacen falle.
     */
    private void forzarConsistencia(Instant instanteActual) throws Exception {
        Map<Long, Instant> instanteColapsoDeAlmacen = new HashMap<>();
        PriorityQueue<AlmacenConColapso> cola = new PriorityQueue<>();
        
        // Inicializar el mapa y la cola con todos los almacenes que tienen colapso
        for (Almacen almacen : this.almacenes.values()) {
            AlmacenConColapso almacenConColapso = encontrarInstanteColapso(almacen);
            
            if (almacenConColapso != null) {
                instanteColapsoDeAlmacen.put(almacen.getId(), almacenConColapso.instanteColapso);
                cola.offer(almacenConColapso);
            }
        }
        
        while (!cola.isEmpty()) {
            AlmacenConColapso almacenConColapso = cola.poll();
            Almacen almacen = almacenConColapso.almacen;
            Instant instanteColapso = almacenConColapso.instanteColapso;
            int cantidadColapso = almacenConColapso.cantidadColapso;
            
            boolean reparado = arreglarAlmacen(almacen, instanteActual, instanteColapso, cantidadColapso, instanteColapsoDeAlmacen);
            
            if (!reparado) {
                lanzarExcepcion("forzarConsistencia", 
                    "No se pudo reparar el almacén ID=" + almacen.getId() + 
                    " (" + almacen.getNombreCiudad() + ")");
            }
            
            // Recalcular el instante de colapso del almacén arreglado
            AlmacenConColapso nuevoAlmacenConColapso = encontrarInstanteColapso(almacen);
            
            if (nuevoAlmacenConColapso == null) {
                // El almacén ya es consistente, remover del mapa
                instanteColapsoDeAlmacen.remove(almacen.getId());
            } else {
                // El almacén sigue teniendo colapso, actualizar el mapa y encolar nuevamente
                instanteColapsoDeAlmacen.put(almacen.getId(), nuevoAlmacenConColapso.instanteColapso);
                cola.offer(nuevoAlmacenConColapso);
            }
        }
    }

    /*
     * Encuentra el primer instante donde la suma acumulada de cambios excede el rango [0;C].
     * Retorna un objeto AlmacenConColapso con el almacén, instante y cantidad de colapso,
     * o null si el almacén es consistente.
     */
    private AlmacenConColapso encontrarInstanteColapso(Almacen almacen) {
        Map<Instant, Integer> cambios = almacen.getCambios();

        if (cambios.isEmpty()) {
            return null;
        }
        
        int capacidad = almacen.getCapacidad();
        int inventarioInicial = almacen.getInventario().size();
        int sumaAcumulada = inventarioInicial;
        
        // TreeMap ya está ordenado cronológicamente
        for (Map.Entry<Instant, Integer> cambio : cambios.entrySet()) {
            sumaAcumulada += cambio.getValue();
            
            // Verificar si la suma acumulada excede el rango [0; capacidad]
            if (sumaAcumulada < 0 || sumaAcumulada > capacidad) {
                int cantidadColapso;
                if (sumaAcumulada < 0) {
                    cantidadColapso = -sumaAcumulada;
                } else {
                    cantidadColapso = sumaAcumulada - capacidad;
                }
                return new AlmacenConColapso(almacen, cambio.getKey(), cantidadColapso);
            }
        }
        
        return null;
    }

    /*
     * Intenta reparar las inconsistencias en el almacén.
     * Busca vuelos candidatos que puedan ayudar a redistribuir la carga y evitar el colapso.
     * Llama recursivamente hasta que se hayan creado suficientes programaciones para
     * reducir cantidadColapso a 0 o negativo.
     */
    private boolean arreglarAlmacen(Almacen almacenAArreglar, Instant instanteActual, Instant instanteColapso, int cantidadColapso, Map<Long, Instant> mapaColapsos) {
        int cantidadRestante = cantidadColapso;
        
        while (cantidadRestante > 0) {
            LinkedList<Vuelo> pathVuelos = new LinkedList<>();
            int programacionesCreadas = arreglarAlmacenRecursivo(almacenAArreglar, instanteActual, mapaColapsos, pathVuelos, 0);
            
            if (programacionesCreadas == 0) {
                // No se pudo crear ninguna programación, fallo en la reparación
                return false;
            }
            
            cantidadRestante -= programacionesCreadas;
        }
        
        return true;
    }
    
    /*
     * Implementación recursiva de arreglarAlmacen con búsqueda en amplitud.
     * Intenta redistribuir productos del almacén usando vuelos candidatos para satisfacer pedidos.
     * Retorna la cantidad de programaciones creadas.
     */
    private int arreglarAlmacenRecursivo(Almacen almacenAArreglar, Instant instanteActual, Map<Long, Instant> mapaColapsos, LinkedList<Vuelo> pathVuelos, int profundidad) {
        if (profundidad >= Hiperparametros.MAX_PROFUNDIDAD_ARREGLO_ALMACEN) {
            return 0;
        }
        
        List<Vuelo> vuelosCandidatos = obtenerVuelosCandidatosParaArreglar(almacenAArreglar, instanteActual, mapaColapsos, pathVuelos);
        
        if (vuelosCandidatos.isEmpty()) {
            return 0;
        }
        
        // Primer nivel: intentar satisfacer pedidos directamente desde los vuelos candidatos
        for (Vuelo vuelo : vuelosCandidatos) {
            Pedido pedidoSatisfacible = buscarPedidoSatisfacible(vuelo);
            
            if (pedidoSatisfacible != null) {
                LinkedList<Vuelo> pathCompleto = new LinkedList<>(pathVuelos);
                pathCompleto.add(vuelo);
                int programacionesCreadas = crearYPersistirProgramacion(pathCompleto, pedidoSatisfacible, instanteActual);
                if (programacionesCreadas > 0) {
                    return programacionesCreadas;
                }
            }
        }
        
        // Segundo nivel: recursión sobre los destinos de los vuelos candidatos
        for (Vuelo vuelo : vuelosCandidatos) {
            Almacen almacenDestino = vuelo.getAlmacenDestino();
            LinkedList<Vuelo> nuevoPath = new LinkedList<>(pathVuelos);
            nuevoPath.add(vuelo);
            int programacionesCreadas = arreglarAlmacenRecursivo(almacenDestino, instanteActual, mapaColapsos, nuevoPath, profundidad + 1);
            if (programacionesCreadas > 0) {
                return programacionesCreadas;
            }
        }
        
        return 0;
    }
    
    /*
     * Obtiene los vuelos candidatos para reparar un almacén usando la lista de adyacenciaOrigenes.
     * Dos casos:
     * 1. Primera llamada (pathVuelos vacío): [instanteActual, instanteColapso) - el almacén sí o sí tiene colapso
     * 2. Llamadas recursivas (pathVuelos con contenido): [instanteLlegada + MINIMA_ESPERA, instanteX)
     *    donde instanteX = instanteColapso si existe, o sin límite derecho si no existe
     */
    private List<Vuelo> obtenerVuelosCandidatosParaArreglar(
            Almacen almacenAArreglar, 
            Instant instanteActual, 
            Map<Long, Instant> mapaColapsos,
            LinkedList<Vuelo> pathVuelos) {
        
        List<Vuelo> vuelosCandidatos = new ArrayList<>();
        
        // Obtener vuelos desde la adyacenciaOrigenes (ya ordenados cronológicamente)
        List<Vuelo> vuelosDesdeOrigen = this.adyacenciaOrigenes.getOrDefault(almacenAArreglar.getId(), new ArrayList<>());
        
        // Determinar el instante de inicio del filtro
        Instant instanteInicio;
        if (pathVuelos.isEmpty()) {
            // Caso 1: Primera llamada
            instanteInicio = instanteActual;
            Instant instanteColapso = mapaColapsos.get(almacenAArreglar.getId());
            
            // Filtrar: [instanteActual, instanteColapso)
            for (Vuelo vuelo : vuelosDesdeOrigen) {
                Instant instanteSalida = vuelo.getInstanteSalida();
                if (!instanteSalida.isBefore(instanteInicio) && instanteSalida.isBefore(instanteColapso)) {
                    vuelosCandidatos.add(vuelo);
                }
            }
        } else {
            // Caso 2: Llamadas recursivas
            Vuelo ultimoVuelo = pathVuelos.getLast();
            instanteInicio = ultimoVuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS));
            Instant instanteColapso = mapaColapsos.get(almacenAArreglar.getId());
            
            if (instanteColapso != null) {
                // El almacén tiene colapso: filtrar [instanteInicio, instanteColapso)
                for (Vuelo vuelo : vuelosDesdeOrigen) {
                    Instant instanteSalida = vuelo.getInstanteSalida();
                    if (!instanteSalida.isBefore(instanteInicio) && instanteSalida.isBefore(instanteColapso)) {
                        vuelosCandidatos.add(vuelo);
                    }
                }
            } else {
                // El almacén NO tiene colapso: filtrar desde [instanteInicio, ∞)
                for (Vuelo vuelo : vuelosDesdeOrigen) {
                    Instant instanteSalida = vuelo.getInstanteSalida();
                    if (!instanteSalida.isBefore(instanteInicio)) {
                        vuelosCandidatos.add(vuelo);
                    }
                }
            }
        }
        
        return vuelosCandidatos;
    }

    /*
     * Busca un pedido que pueda ser satisfecho por el vuelo dado.
     * Considera que el pedido debe tener como destino el almacén de llegada del vuelo
     * y que el instante de recojo debe ser <= al instante límite del pedido.
     */
    private Pedido buscarPedidoSatisfacible(Vuelo vuelo) {
        Almacen almacenDestino = vuelo.getAlmacenDestino();
        Instant instanteLlegada = vuelo.getInstanteLlegada();
        Instant instanteRecojo = instanteLlegada.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
        
        for (Pedido pedido : this.pedidos.values()) {
            if (pedido.obtenerCantidadProgramacionesFaltantes() > 0) {
                if (pedido.getAlmacenDestino().equals(almacenDestino)) {
                    if (!instanteRecojo.isAfter(pedido.getInstanteLimite())) {
                        return pedido;
                    }
                }
            }
        }
        
        return null;
    }
    
    /*
     * Crea y persiste una programación que redistribuye un producto del almacén de origen
     * usando la ruta de vuelos dada para satisfacer el pedido.
     * Retorna 1 si se creó exitosamente, 0 si falló.
     */
    private int crearYPersistirProgramacion(LinkedList<Vuelo> vuelosRuta, Pedido pedido, Instant instanteActual) {
        try {
            if (vuelosRuta.isEmpty()) {
                return 0;
            }
            
            // Obtener el almacén origen desde el primer vuelo
            Vuelo primerVuelo = vuelosRuta.getFirst();
            Almacen almacenOrigen = primerVuelo.getAlmacenSalida();
            
            // Obtener un producto del almacén origen
            List<Producto> productosDisponibles = almacenOrigen.obtenerProductos(instanteActual);
            if (productosDisponibles.isEmpty()) {
                return 0;
            }
            
            Producto producto = productosDisponibles.get(0);
            
            // Crear la ruta
            Ruta ruta = new Ruta(vuelosRuta);
            
            // Crear la programación
            Programacion programacion = new Programacion(pedido, producto, ruta);
            List<Programacion> programaciones = new ArrayList<>();
            programaciones.add(programacion);
            
            // Persistir la programación (similar a EstrategiaGraspHibrido.persistirProgramaciones)
            List<Producto> productos = new ArrayList<>();
            productos.add(producto);
            
            // Registrar cambios en cada vuelo de la ruta
            for (Vuelo vuelo : vuelosRuta) {
                Almacen almacenSalida = vuelo.getAlmacenSalida();
                Almacen almacenEntrada = vuelo.getAlmacenDestino();
                Instant instanteSalida = vuelo.getInstanteSalida();
                Instant instanteLlegada = vuelo.getInstanteLlegada();
                
                // Registrar la salida en el almacén de salida
                boolean valido = almacenSalida.registrarSalida(instanteSalida, 1);
                if (!valido && !almacenSalida.isInfinito()) {
                    return 0;
                }
                
                // Registrar el producto en el inventario del vuelo
                valido = vuelo.registrarProducto(productos);
                if (!valido) {
                    return 0;
                }
                
                // Registrar la entrada en el almacén de entrada
                valido = almacenEntrada.registrarEntrada(instanteLlegada, 1);
                if (!valido) {
                    return 0;
                }
            }
            
            // Registrar el recojo en el almacén destino del pedido
            boolean valido = this.registrarNuevosProgramacionesYProductos(ruta, productos, programaciones, instanteActual);
            if (!valido) {
                return 0;
            }
            
            // Registrar el producto programado en el pedido
            valido = pedido.registrarProductoProgramado(productos);
            if (!valido) {
                return 0;
            }
            
            return 1;
            
        } catch (Exception e) {
            Bitacora.escribir("Error al crear y persistir programación en arreglarAlmacen: " + e.getMessage());
            return 0;
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
                        if (esVueloAdmisibleComoSiguiente(path.getVuelos(), ultimo, siguiente, instanteActual)) {
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
            LinkedList<Vuelo> path,
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
                && path.stream().noneMatch(v -> v.getId() == siguiente.getId())
                // no repetir almacén destino en la ruta
                && path.stream().noneMatch(
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
                    .toList();
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
        this.adyacenciaDestinos = indice;
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
        rutasValidas = this.adyacenciaDestinos.get(almacenDestino.getId());
//Bitacora.escribir("RUTAS VÁLIDAS SACADAS DE ADYACENCIA PARA ALMACÉN ID: "+almacenDestino+"\n"+
//        PrettyPrinter.printList(rutasValidas));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "LA LISTA DE ADYACENCIA NO TIENE ORIGENES INFINITOS");
        instanteRegistro = pedidoElegido.getInstanteRegistro();
        instanteLimite = pedidoElegido.obtenerInstanteMaximoLlegadaUltimoVuelo();
        rutasValidas = new ArrayList<>(rutasValidas.stream()
                .filter(ruta -> ruta.verificarRutaNoEmpieza(instanteRegistro) 
                        && ruta.verificarUltimoVueloAterrizado(instanteLimite))
                .toList());
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
            valido &= almacenDestino.registrarRecojoDeProductos(producto, instanteLlegadUltimoVuelo);

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
                .toList();

        Map<LinkedList<Vuelo>, List<Programacion>> programacionesPorRuta = programacionesDelPedido.stream()
                .collect(Collectors.groupingBy(programacion -> programacion.getRuta().getVuelos()));

        return programacionesPorRuta.keySet().stream()
                .map(vuelos -> {
                    Ruta ruta = new Ruta(vuelos);
                    Integer cantidadProductos = programacionesPorRuta.get(vuelos).size();
                    return new AbstractMap.SimpleEntry<>(ruta, cantidadProductos);
                }).toList();
    }

    /*
     * Obtiene las programaciones que usan una ruta específica
     */
    public List<Programacion> obtenerProgramacionesQueUsanRuta(LinkedList<Long> ruta)
    {
        return this.programaciones.stream()
                .filter(programacion -> programacion.getRuta().getVuelos()
                        .stream().map(Vuelo::getId).toList().equals(ruta))
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



