package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.persistence.criteria.CriteriaBuilder.In;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.algorithms.EstrategiaGraspHibrido;
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
    private Map<Ruta, List<Pedido>> pedidosParaArreglar;
    private Map<Ruta, Integer> capacidadesRutasParaArreglar;

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
        this.pedidosParaArreglar = new HashMap<>();
        this.capacidadesRutasParaArreglar = new HashMap<>();
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
     * Retorna la cantidad de pedidos pendientes de programar (con programaciones faltantes > 0)
     */
    public int hayPedidosPendientes()
    {
        return (int) pedidos.values().stream()
                .filter(pedido -> pedido.obtenerCantidadProgramacionesFaltantes() > 0)
                .count();
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
        
//Testeador.verificarConsistenciasEnCambiosTEST(this, "Después de inicializar el estado global", true);
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
                    if(producto.validarIncancelable_B()){
                        // Producto incancelable, solo registrado en cambios
                        valido &= almacenDestino.registrarEntradaIlegalmente(vuelo.getInstanteLlegada(), 1);
                        valido &= almacenDestino.registrarSalidaIllegal(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), 1);

                        if(!valido) {
/*
int inventarioActual = almacenDestino.getInventario().size();
int inventarioFuturo = almacenDestino.obtenerProductos(vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO))).size();
Bitacora.escribir("ERROR al registrar recojo - Almacén ID=%d, Instante Recojo=%s, Inventario Actual=%d, Inventario Futuro en ese instante=%d, Producto=%s",
almacenDestino.getId(), vuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)), inventarioActual, inventarioFuturo, producto.getId());
*/
                            lanzarExcepcion("Inicializacion", "No se puede registrar el recojo de los productos");
                        }
                    }else{
                        // Producto tipo a, se puede reutilizar y se registra en inventario futuro
                        valido &= almacenDestino.registrarProductoFuturoIlegalmente(producto, vuelo.getInstanteLlegada());
                        //^^^^^^ registrado ilegalmente debido a la asincronía de actualización de cambios positivos y negativos
                        // en los almacenes
                        if(!valido) {
                            lanzarExcepcion("inicializarVuelosEnTransito", "No se pudo registrar el producto futuro en el almacén destino del vuelo ID=" + vuelo.getId());
                        }
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
                if(ruta.getVuelos()==null || ruta.getVuelos().isEmpty()) {
                    continue;
                }
                Vuelo ultimoVuelo = ruta.obtenerUltimoVuelo();
                Almacen almacenDestino = ultimoVuelo.getAlmacenDestino();
                Producto producto = programacion.getProducto();
                Instant instanteRecojo = ruta.obtenerInstanteRecojo();
                Instant instanteLlegada = ultimoVuelo.getInstanteLlegada();

                Pedido wa = this.pedidos.get(programacion.getPedido().getId());
                if (wa!=null) wa.registrarProductoEntregadov2(producto);

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
     * Fuerza la consistencia de los almacenes verificando que la suma acumulada de cambios
     * no exceda el rango [0;C] donde C es la capacidad del almacén.
     * Itera sobre los almacenes inconsistentes intentando repararlos hasta que todos sean
     * consistentes o hasta que el método arreglarAlmacen falle.
     */
    private void forzarConsistencia(Instant instanteActual) throws Exception {
        Map<Long, Instant> instanteColapsoDeAlmacen = new HashMap<>();
        
        // Inicializar el mapa con todos los almacenes que tienen colapso
        calcularInstantesDeColapso(instanteColapsoDeAlmacen);
/*
Bitacora.escribir("\n╔══════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ FORZAR CONSISTENCIA - Inicio");
Bitacora.escribir("║ Total de almacenes colapsados: %d", instanteColapsoDeAlmacen.size());
for (Map.Entry<Long, Instant> entry : instanteColapsoDeAlmacen.entrySet()) {
    Almacen alm = this.almacenes.get(entry.getKey());
    Bitacora.escribir("║   - Almacén ID=%d (%s) colapsa en: %s", entry.getKey(), alm.getNombreCiudad(), entry.getValue());
}
Bitacora.escribir("╚══════════════════════════════════════════════════════════════════════════════╝");
*/
        
        while (!instanteColapsoDeAlmacen.isEmpty()) {
            // Buscar el almacén con el instante de colapso más temprano
            Map.Entry<Long, Instant> entradaMinima = instanteColapsoDeAlmacen.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);
            Long idAlmacen = entradaMinima.getKey();
            Instant instanteColapso = entradaMinima.getValue();
            Almacen almacenAArreglar = this.almacenes.get(idAlmacen);
            int cantidadColapso = calcularCantidadColapsoEnInstante(almacenAArreglar, instanteColapso);
/*
Bitacora.escribir("\n╔══════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ ARREGLAR ALMACÉN - Iteración");
Bitacora.escribir("║ Almacén ID=%d (%s)", almacenAArreglar.getId(), almacenAArreglar.getNombreCiudad());
Bitacora.escribir("║ Instante colapso: %s", instanteColapso);
Bitacora.escribir("║ Cantidad colapso: %d", cantidadColapso);
Bitacora.escribir("║ Almacén es infinito: %s", almacenAArreglar.isInfinito());
Bitacora.escribir("╚══════════════════════════════════════════════════════════════════════════════╝");
*/
            
            boolean reparado = arreglarAlmacen(almacenAArreglar, instanteActual, instanteColapso, cantidadColapso, instanteColapsoDeAlmacen);
/*
Bitacora.escribir("║ Resultado de arreglarAlmacen: %s", reparado ? "ÉXITO" : "FALLÓ");
*/
            
            if (!reparado) {
//                lanzarExcepcion("forzarConsistencia",
//                    "No se pudo reparar el almacén ID=" + almacenAArreglar.getId() +
//                    " (" + almacenAArreglar.getNombreCiudad() + ")");
                break; // !!!!!
            }
            
            // Recalcular todos los instantes de colapso
            instanteColapsoDeAlmacen.clear();
            calcularInstantesDeColapso(instanteColapsoDeAlmacen);
/*
Bitacora.escribir("║ Almacenes colapsados restantes después de recalcular: %d", instanteColapsoDeAlmacen.size());
*/
        }
    }
    
    /*
     * Calcula y llena el mapa con los instantes de colapso de todos los almacenes.
     * Solo agrega al mapa los almacenes que tienen colapso.
     */
    private void calcularInstantesDeColapso(Map<Long, Instant> instanteColapsoDeAlmacen) {
        for (Almacen almacen : this.almacenes.values()) {
            Instant instanteColapso = encontrarInstanteColapso(almacen);
            
            if (instanteColapso != null) {
                instanteColapsoDeAlmacen.put(almacen.getId(), instanteColapso);
            }
        }
    }

    /*
     * Encuentra el primer instante donde la suma acumulada de cambios excede el rango [0;C].
     * Retorna el instante de colapso o null si el almacén es consistente.
     */
    private Instant encontrarInstanteColapso(Almacen almacen) {
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
                return cambio.getKey();
            }
        }
        
        return null;
    }
    
    /*
     * Calcula la cantidad de colapso de un almacén en un instante específico.
     * Retorna cuánto excede el rango [0;C] en ese instante.
     * Si no hay colapso en ese instante, retorna 0.
     */
    private int calcularCantidadColapsoEnInstante(Almacen almacen, Instant instanteColapso) throws Exception {
        Map<Instant, Integer> cambios = almacen.getCambios();
        int capacidad = almacen.getCapacidad();
        int inventarioInicial = almacen.getInventario().size();
        int sumaAcumulada = inventarioInicial;
        
        // Recorrer cambios hasta el instante de colapso
        for (Map.Entry<Instant, Integer> cambio : cambios.entrySet()) {
            sumaAcumulada += cambio.getValue();
            
            // Si llegamos al instante de colapso, calcular cuánto excede
            if (cambio.getKey().equals(instanteColapso)) {
                if (sumaAcumulada < 0) {
//                    lanzarExcepcion("calcularCantidadColapso", "Colapso negativo, esto no deberia pasar");
//                    return -sumaAcumulada;
                    return -sumaAcumulada;
                } else if (sumaAcumulada > capacidad) {
                    return sumaAcumulada - capacidad;
                } else {
                    return 0; // Ya no hay colapso en este instante
                }
            }
        }
        
        // Si no encontramos el instante, retornar 0
        return 0;
    }

    /*
     * Intenta reparar las inconsistencias en el almacén.
     * Busca vuelos candidatos que puedan ayudar a redistribuir la carga y evitar el colapso.
     * Llama recursivamente hasta que se hayan creado suficientes programaciones para
     * reducir cantidadColapso a 0 o negativo.
     */
    private boolean arreglarAlmacen(Almacen almacenAArreglar, Instant instanteActual, Instant instanteColapso, int cantidadColapso, Map<Long, Instant> mapaColapsos) throws Exception {
        int cantidadRestante = cantidadColapso;
        List<Vuelo> vuelosCandidatos = obtenerVuelosCandidatosIniciales(almacenAArreglar, instanteActual, instanteColapso, mapaColapsos);
        
        if(vuelosCandidatos.isEmpty()) {
            lanzarExcepcion("arreglarAlmacen", "No se puede arreglar Almacen por falta de vuelos");
        }

        for (Vuelo vueloInicial : vuelosCandidatos) {
            if (cantidadRestante <= 0) {
                break;
            }
            
            LinkedList<Vuelo> pathVuelos = new LinkedList<>();
            pathVuelos.add(vueloInicial);
            List<Producto> productosEnAlmacen = almacenAArreglar.obtenerProductos(vueloInicial.getInstanteSalida());
            int entradaMaxima = vueloInicial.getAlmacenDestino().calcularEspacioVacioMaximoEnInstanteConColapso(instanteActual, instanteColapso);      
            int capacidadMaxima = Math.min(productosEnAlmacen.size(), vueloInicial.obtenerEspacioVacio());
            capacidadMaxima = Math.min(capacidadMaxima, entradaMaxima);
            Ruta rutaGenerada = generarRutaRecursivo(mapaColapsos, pathVuelos, capacidadMaxima, 0, instanteActual);
            
            if (rutaGenerada != null) {
                List<Programacion> programacionesCreadas = crearProgramacionesParaReparacion(rutaGenerada, productosEnAlmacen, almacenAArreglar);
                boolean persistido = persistirProgramacionesParaReparacion(programacionesCreadas, instanteActual);

                if (!persistido) {
                    lanzarExcepcion("arreglarAlmacen", "No se puede persistir las programaciones");
                }

                cantidadRestante -= programacionesCreadas.size();
            }
        }
        
        return cantidadRestante <= 0;
    }
    
    /*
     * Implementación recursiva para generar una ruta con búsqueda en amplitud.
     * Objetivo: Encontrar una Ruta que pueda satisfacer pedidos.
     * Retorna la Ruta generada o null si no se puede generar.
     */
    private Ruta generarRutaRecursivo(Map<Long, Instant> mapaColapsos, LinkedList<Vuelo> pathVuelos, int entradaMaximaAnterior, int profundidad, Instant instanteActual) {
        if (profundidad >= Hiperparametros.MAX_PROFUNDIDAD_ARREGLO_ALMACEN || entradaMaximaAnterior == 0) {
            return null;
        }
        
        // PRIMER NIVEL: Verificar si se pueden satisfacer pedidos en el almacén de llegada
        Vuelo ultimoVuelo = pathVuelos.getLast();
        Almacen almacenActual = ultimoVuelo.getAlmacenDestino();
        Instant instanteMinimoRecojo = ultimoVuelo.getInstanteLlegada().plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO));
        Instant instanteColapsoDestino = mapaColapsos.get(ultimoVuelo.getAlmacenDestino().getId());
        
        // Verificar que el instante de recojo no sea mayor al instante de colapso
        Instant instanteMaximoRecojo;
        if (instanteColapsoDestino != null) {
            if (!instanteMinimoRecojo.isAfter(instanteColapsoDestino)) {
                instanteMaximoRecojo = instanteColapsoDestino;
            } else {
                // El recojo es después del colapso, no se puede satisfacer pedidos aquí
                instanteMaximoRecojo = null;
            }
        } else {
            // No hay colapso, usar tiempo máximo de 1 día
            instanteMaximoRecojo = instanteMinimoRecojo.plus(Duration.ofHours(Hiperparametros.HORAS_MAXIMAS_BUSQUEDA_PEDIDOS_SIN_COLAPSO));
        }
        
        // Si hay un límite válido, buscar pedidos que se puedan satisfacer
        if (instanteMaximoRecojo != null) {
            List<Pedido> pedidosSatisfacibles = buscarPedidosSatisfacibles(almacenActual, instanteMinimoRecojo, instanteMaximoRecojo);
            
            if (!pedidosSatisfacibles.isEmpty()) {
                // Crear ruta y registrarla en pedidosParaArreglar
                Ruta rutaGenerada = new Ruta(pathVuelos);
                this.capacidadesRutasParaArreglar.put(rutaGenerada, entradaMaximaAnterior);
                this.pedidosParaArreglar.put(rutaGenerada, pedidosSatisfacibles);
                return rutaGenerada;
            }

            // SEGUNDO NIVEL: Recursión sobre los destinos de los vuelos candidatos
            List<Vuelo> vuelosCandidatos = obtenerVuelosCandidatosRecursivos(almacenActual, mapaColapsos, pathVuelos, instanteMaximoRecojo);
            
            for (Vuelo vuelo : vuelosCandidatos) {
                LinkedList<Vuelo> nuevoPath = new LinkedList<>(pathVuelos);
                nuevoPath.add(vuelo);  
                
                // Obtener el instante de colapso del NUEVO destino (no del anterior)
                Instant instanteColapsoNuevoDestino = mapaColapsos.get(vuelo.getAlmacenDestino().getId());
                int entradaMaxima = vuelo.getAlmacenDestino().calcularEspacioVacioMaximoEnInstanteConColapso(instanteActual, instanteColapsoNuevoDestino);
                int nuevaEntradaMaxima = Math.min(entradaMaximaAnterior, vuelo.obtenerEspacioVacio()); 
                nuevaEntradaMaxima = Math.min(nuevaEntradaMaxima, entradaMaxima);
                
                // No recursar si la capacidad calculada es 0
                if (nuevaEntradaMaxima == 0) {
                    continue;
                }
                
                Ruta rutaGenerada = generarRutaRecursivo(mapaColapsos, nuevoPath, nuevaEntradaMaxima, profundidad + 1, instanteActual);
                
                if (rutaGenerada != null) {
                    return rutaGenerada;
                }
            }
        }
        

        
        return null;
    }
    
    /*
     * Crea programaciones para la reparación del almacén usando la ruta generada.
     * Asigna productos del almacén a los pedidos correspondientes según la ruta.
     * Retorna una lista de programaciones creadas.
     */
    private List<Programacion> crearProgramacionesParaReparacion(Ruta rutaGenerada, List<Producto> productosEnAlmacen, Almacen almacenAArreglar) {
        List<Pedido> pedidosAsignables = this.pedidosParaArreglar.get(rutaGenerada);
        Integer capacidadRuta = this.capacidadesRutasParaArreglar.get(rutaGenerada);
        List<Programacion> nuevasProgramaciones = new ArrayList<>();
/*
Bitacora.escribir("\n╔══════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ CREAR PROGRAMACIONES PARA REPARACIÓN");
Bitacora.escribir("║ Almacén origen ruta: %s (ID=%d, Infinito=%s)", rutaGenerada.obtenerAlmacenOrigen().getNombreCiudad(), rutaGenerada.obtenerAlmacenOrigen().getId(), rutaGenerada.obtenerAlmacenOrigen().isInfinito());
Bitacora.escribir("║ Almacén destino ruta: %s (ID=%d)", rutaGenerada.obtenerAlmacenDestino().getNombreCiudad(), rutaGenerada.obtenerAlmacenDestino().getId());
Bitacora.escribir("║ Productos disponibles en almacén: %d", productosEnAlmacen.size());
Bitacora.escribir("║ Capacidad ruta: %d", capacidadRuta);
Bitacora.escribir("║ Pedidos asignables: %d", pedidosAsignables.size());
Bitacora.escribir("╚══════════════════════════════════════════════════════════════════════════════╝");
*/

        for (Pedido pedido : pedidosAsignables) {
            if(capacidadRuta == 0) {
                break;
            }

            int programacionesFaltantes = pedido.obtenerCantidadProgramacionesFaltantes();
            int cantidadProgramaciones = Math.min(programacionesFaltantes, capacidadRuta);
/*
Bitacora.escribir("\n║ ═══════════════════════════════════════════════════════════════════════════");
Bitacora.escribir("║ PEDIDO ID=%d | Destino=%s", pedido.getId(), pedido.getAlmacenDestino().getNombreCiudad());
Bitacora.escribir("║   Cantidad Total: %d", pedido.getCantidadProductos());
Bitacora.escribir("║   Programados: %d", pedido.obtenerCantidadProductosProgramados());
Bitacora.escribir("║   Entregados: %d", pedido.obtenerCantidadProductosEntregados());
Bitacora.escribir("║   Faltantes: %d", programacionesFaltantes);
Bitacora.escribir("║   Se crearán: %d programaciones", cantidadProgramaciones);
*/
int programacionesAntesParaEstePedido = nuevasProgramaciones.size();

            // Elegir productos UNA SOLA VEZ para todas las programaciones del pedido
            List<Producto> productosElegidos = EstrategiaGraspHibrido.elegirProductos(
                rutaGenerada.obtenerAlmacenOrigen(), 
                rutaGenerada.obtenerAlmacenDestino(),
                false, 
                productosEnAlmacen, 
                cantidadProgramaciones);
/*                
Bitacora.escribir("║ Productos elegidos: %d", productosElegidos.size());
for(Producto prod : productosElegidos) {
    String tipo = prod.validarNoPlanificado_A() ? "A-NoPlanif" : (prod.validarIncancelable_B() ? "B-Incancelable" : (prod.validarPlanificadoExistente_D() ? "D-PlanifExist" : "C-PlanifNoExist"));
    Bitacora.escribir("║   - Producto ID=%s, Tipo=%s, Origen=%s (ID=%d)", prod.getId().toString().substring(0,8), tipo, prod.getAlmacenOrigen().getCodigoCiudadEn4Letras(), prod.getAlmacenOrigen().getId());
}
*/

            // Crear una programación por cada producto elegido
            for(Producto producto : productosElegidos) {
                Programacion programacion = new Programacion(pedido, producto, rutaGenerada);
                nuevasProgramaciones.add(programacion);
            }
            
            // Remover productos ya asignados para evitar duplicados
            productosEnAlmacen.removeAll(productosElegidos);
            
            capacidadRuta -= cantidadProgramaciones;
int programacionesCreadasParaEstePedido = nuevasProgramaciones.size() - programacionesAntesParaEstePedido;
/*
Bitacora.escribir("║   ✓ Total programaciones CREADAS para pedido ID=%d: %d", pedido.getId(), programacionesCreadasParaEstePedido);
*/
        }
/*
Bitacora.escribir("║ ═══════════════════════════════════════════════════════════════════════════");
Bitacora.escribir("║ Total programaciones creadas (todos los pedidos): %d", nuevasProgramaciones.size());
Bitacora.escribir("╚══════════════════════════════════════════════════════════════════════════════╝");
*/

        return nuevasProgramaciones;
    }
    
    /*
     * Persiste las programaciones de reparación en el estado global.
     * Registra los cambios en almacenes, vuelos y pedidos correspondientes.
     * Retorna true si la persistencia fue exitosa, false en caso contrario.
     */
    private boolean persistirProgramacionesParaReparacion(List<Programacion> nuevasProgramaciones, Instant instanteActual) throws Exception {
        boolean valido;
        int nProgramaciones;
        Ruta ruta;
        Almacen almacenSalida, almacenEntrada;
        List<Producto> productos;

        nProgramaciones = nuevasProgramaciones.size();
        ruta = nuevasProgramaciones.get(0).getRuta();
        productos = nuevasProgramaciones.stream()
                .map(Programacion::getProducto)
                .collect(Collectors.toList()); 

        for(Vuelo vuelo : ruta.getVuelos()) {
            // registro de los cambios de salida en el almacen            
            almacenSalida = vuelo.getAlmacenSalida();
            valido = almacenSalida.registrarSalidaIllegal(vuelo.getInstanteSalida(), nProgramaciones);

            if(!valido && !almacenSalida.isInfinito()) {
                lanzarExcepcion("Persistir programaciones", "Registro ilegal en almacen de salida de un vuelo de la ruta de las programaciones");
            }

            // registro del inventario del vuelo
            valido = vuelo.registrarProducto(productos);

            if(!valido) {
                lanzarExcepcion("Persistir programaciones", "Inventario de vuelo desbordado");
            }

            // registro de los cambios de entrada del almacen
            almacenEntrada = vuelo.getAlmacenDestino();
            valido = almacenEntrada.registrarEntradaIlegalmente(vuelo.getInstanteLlegada(), nProgramaciones);

            if(!valido) {
                lanzarExcepcion("Persistir programaciones", "Registro ilegal en almacen de llegada de un vuelo de la ruta de las programaciones");
            }
        }

        //registro de salida de los productos por recojo y persistir en estado global
        valido = registrarNuevosProgramacionesYProductosDeReparacion(ruta, productos, nuevasProgramaciones, instanteActual);

        if(!valido) {
            lanzarExcepcion("Persitir programaciones", "No se puede marcar el recojo de los productos");
        }

        // registro de los productos al pedido
/*
Bitacora.escribir("\n╔════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ REGISTRAR PRODUCTOS A PEDIDOS - Inicio");
Bitacora.escribir("║ Total programaciones a registrar: %d", nuevasProgramaciones.size());
Bitacora.escribir("╚════════════════════════════════════════════════════════════════════════════╝");
*/

        for(Programacion programacion : nuevasProgramaciones) {
            Pedido pedido = programacion.getPedido();
            Producto producto = programacion.getProducto();
            valido = pedido.registrarProductoProgramado(producto);

            if(!valido) {
/*
Bitacora.escribir("║ ERROR: No se pudo registrar producto ID=%s en pedido ID=%d", producto.getId().toString().substring(0,8), pedido.getId());
*/
                lanzarExcepcion("Persitir programaciones", "Se excedería la capacidad del pedido");    
            }
        }
/*
Bitacora.escribir("║ REGISTRAR PRODUCTOS A PEDIDOS - Fin exitoso");
*/

        
       return valido;
    }

    /*
     * Añade los nuevos productos y las nuevas programaciones a sus respectivas colecciones. Ademas, registra el recojo de los productos en el almacen destino del pedido. Solo se pueden registrar productos D o C
     */
    public boolean registrarNuevosProgramacionesYProductosDeReparacion(Ruta ruta, List<Producto> productos, List<Programacion> programaciones, Instant instanteActual) throws Exception
    {
        boolean valido;
        Instant instanteLlegadUltimoVuelo;
        Almacen almacenDestino;

        valido = true;
        almacenDestino = ruta.obtenerAlmacenDestino();
        instanteLlegadUltimoVuelo = ruta.obtenerUltimoVuelo().getInstanteLlegada();
/*
Bitacora.escribir("\n╔══════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ REGISTRAR PROGRAMACIONES DE REPARACIÓN");
Bitacora.escribir("║ Total productos a registrar: %d", productos.size());
Bitacora.escribir("║ Total programaciones: %d", programaciones.size());
*/

        for(Producto producto : productos) {
            valido &= almacenDestino.registrarRecojoDeProductosIlegalmente(producto, instanteLlegadUltimoVuelo);
/*
String tipoProducto = producto.validarNoPlanificado_A() ? "A-NoPlanif" : (producto.validarIncancelable_B() ? "B-Incancelable" : (producto.validarPlanificadoExistente_D() ? "D-PlanifExist" : (producto.validarPlanificadoNoExistente_C() ? "C-PlanifNoExist" : "DESCONOCIDO")));
Bitacora.escribir("║ Procesando producto ID=%s, Tipo=%s, Origen=%s (ID=%d, Infinito=%s)", producto.getId().toString().substring(0,8), tipoProducto, producto.getAlmacenOrigen().getCodigoCiudadEn4Letras(), producto.getAlmacenOrigen().getId(), producto.getAlmacenOrigen().isInfinito());
*/

            if(producto.validarNoPlanificado_A()){
                producto.transNoPlanificado_A_PlanificadoExistente_D();
                this.productos.put(producto.getId(), producto);
/*
Bitacora.escribir("║   -> Transición A->D exitosa");  
*/
            }else{
/*
Bitacora.escribir("║   -> ERROR: Producto NO es tipo A, es tipo %s", tipoProducto);
*/
                lanzarExcepcion("Registrar productos de reparacion", "Producto no es de tipo A");
            }
        }
        
        this.programaciones.addAll(programaciones);

        return valido;
    }

    /*
     * Obtiene los vuelos candidatos iniciales para reparar un almacén.
     * Caso 1: Primera llamada desde arreglarAlmacen.
     * Filtra vuelos en el intervalo [instanteActual, instanteColapso) y que no lleguen
     * a almacenes destino después de su instante de colapso.
     */
    private List<Vuelo> obtenerVuelosCandidatosIniciales(
            Almacen almacenAArreglar, 
            Instant instanteActual, 
            Instant instanteColapso,
            Map<Long, Instant> mapaColapsos) {
        
        List<Vuelo> vuelosCandidatos = new ArrayList<>();
        
        // Obtener vuelos desde la adyacenciaOrigenes (ya ordenados cronológicamente)
        List<Vuelo> vuelosDesdeOrigen = this.adyacenciaOrigenes.getOrDefault(almacenAArreglar.getId(), new ArrayList<>());
        
        // Filtrar: [instanteActual, instanteColapso)
        for (Vuelo vuelo : vuelosDesdeOrigen) {
            Instant instanteSalida = vuelo.getInstanteSalida();
            if (!instanteSalida.isBefore(instanteActual) && instanteSalida.isBefore(instanteColapso)) {
                // Verificar colapso del almacén destino
                Almacen almacenDestino = vuelo.getAlmacenDestino();
                Instant instanteColapsoDestino = mapaColapsos.get(almacenDestino.getId());
                
                if (instanteColapsoDestino != null) {
                    // Si el almacén destino está colapsado, verificar que la llegada sea antes del colapso
                    if (vuelo.getInstanteLlegada().isBefore(instanteColapsoDestino)) {
                        vuelosCandidatos.add(vuelo);
                    }
                } else {
                    // Si el almacén destino no está colapsado, entra directamente
                    vuelosCandidatos.add(vuelo);
                }
            }
        }
        
        return vuelosCandidatos;
    }
    
    /*
     * Obtiene los vuelos candidatos para llamadas recursivas.
     * Caso 2: Llamadas recursivas desde generarRutaRecursivo.
     * Filtra vuelos que sean admisibles como siguiente en la ruta, que no superen el instanteMaximoRecojo,
     * y que no lleguen a almacenes destino después de su instante de colapso.
     */
    private List<Vuelo> obtenerVuelosCandidatosRecursivos(Almacen almacenActual, Map<Long, Instant> mapaColapsos, LinkedList<Vuelo> pathVuelos, Instant instanteMaximoRecojo)   {
        List<Vuelo> vuelosCandidatos = new ArrayList<>();
        List<Vuelo> vuelosDesdeOrigen = this.adyacenciaOrigenes.getOrDefault(almacenActual.getId(), new ArrayList<>());
        
        for (Vuelo vuelo : vuelosDesdeOrigen) {
            // Usar esVueloAdmisibleComoSiguiente
            if (esVueloAdmisibleComoSiguiente(pathVuelos, vuelo)) {
                // Verificar que la salida no sea mayor al instanteMaximoRecojo
                if (!vuelo.getInstanteSalida().isAfter(instanteMaximoRecojo)) {
                    // Verificar colapso del almacén destino
                    Almacen almacenDestino = vuelo.getAlmacenDestino();
                    Instant instanteColapsoDestino = mapaColapsos.get(almacenDestino.getId());
                
                    if (instanteColapsoDestino != null) {
                        // Si el almacén destino está colapsado, verificar que la llegada sea antes del colapso
                        if (vuelo.getInstanteLlegada().isBefore(instanteColapsoDestino)) {
                            vuelosCandidatos.add(vuelo); 
                        }
                    } else {
                        // Si el almacén destino no está colapsado, entra directamente
                        vuelosCandidatos.add(vuelo);
                    }
                }
            }
        }
        
        return vuelosCandidatos;
    }
    
    /*
     * Busca todos los pedidos que se puedan satisfacer en un almacén
     * dentro de un rango de tiempo específico.
     * Retorna la lista de pedidos que cumplen con las condiciones.
     */
    public List<Pedido> buscarPedidosSatisfacibles(Almacen almacen, Instant limiteInferior, Instant limiteSuperior) {
        List<Pedido> pedidosSatisfacibles = new ArrayList<>();
        
        for (Pedido pedido : this.pedidos.values()) {
            if (pedido.obtenerCantidadProgramacionesFaltantes() > 0) {
                if (pedido.getAlmacenDestino().equals(almacen)) {
                    // Verificar que el instanteLimite del pedido esté en el intervalo abierto (limiteInferior, limiteSuperior)
                    if (limiteInferior.isBefore(pedido.getInstanteLimite()) && pedido.getInstanteLimite().isBefore(limiteSuperior)) {
                        pedidosSatisfacibles.add(pedido);
                    }
                }
            }
        }
        
        return pedidosSatisfacibles;
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
                        if (esVueloAdmisibleComoSiguiente(path.getVuelos(), siguiente)) {
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
    public List<Almacen> obtenerAlmacenesOrigen() {
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

    public boolean esVueloAdmisibleComoSiguiente(LinkedList<Vuelo> path, Vuelo siguiente) {
        boolean valido;
        Vuelo ultimo = path.getLast();

        valido =// tiene capacidad 
                siguiente.obtenerEspacioVacio() > 0
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
        this.adyacenciaDestinos = indice;
    }

    /*
     * Obtiene los Pedidos con cantidadProductosPendientes sea mayor a 0
     * Escluye los no planificados
     */
    public List<Pedido> obtenerPedidosPendientesExcluyendoMarcados( List<Pedido> pedidosNoPlanificados ) {
        List<Long> idsMarcados = pedidosNoPlanificados.stream().map(Pedido::getId).collect(Collectors.toList());
        return this.getPedidos().values()
                .stream()
                .filter(pedido -> pedido.obtenerCantidadProgramacionesFaltantes() > 0)
                .filter( pedido -> !idsMarcados.contains(pedido.getId() ))
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
                .collect(Collectors.toList()));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "DEPSUES DE FILTROS FLAKO");
        if(rutasValidas.isEmpty()) {
            lanzarExcepcion("Rutas invalidas", "No se encontraron rutas validas para el pedido");
        }

        return rutasValidas;
    }
    public List<Ruta> obtenerRutasValidas2(Pedido pedidoElegido) throws Exception {
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
                .collect(Collectors.toList()));
//Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this, rutasValidas, "DEPSUES DE FILTROS FLAKO");
        if(rutasValidas.isEmpty()) {
//            lanzarExcepcion("Rutas invalidas", "No se encontraron rutas validas para el pedido");
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

                if(almacenEntrada.verificaEntradav2(vuelo.getInstanteLlegada(), entradaMaxima))
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

    public void agregarProgramacion(Programacion programacion) {
        this.programaciones.add(programacion);
    }
}



