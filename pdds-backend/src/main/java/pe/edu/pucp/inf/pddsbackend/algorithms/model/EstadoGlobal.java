package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.MAX_RUTAS_POR_DESTINO;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.MAX_RUTAS_POR_ORIGEN;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aspectj.weaver.ast.Test;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Testeador;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Continente;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.EstadoPedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.io.Serializable;
import java.time.Duration;
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
    private HashMap<Almacen, List<LinkedList<Vuelo>>> adyacencia;

    /*
    Obtiene los datos desde el EstadoGlobal del ctx y los filtra para el dado. Osea, elimina todos los Productos no existentes y devuelve el EstadoGlobal para el instante instanteAlgoritmo
    Se asume que las programaciones que ya llegaron a su destino final y que ya pasaron 2 horas son eliminadas y no llegan aquí
    */
    public static EstadoGlobal obtenerEstadoGlobalEnInstante_v2(EstadoGlobal estadoGlobalOriginal, Instant instanteAlgoritmo)
    {
        EstadoGlobal estadoGlobalAlgoritmo;

        estadoGlobalAlgoritmo = new EstadoGlobal(estadoGlobalOriginal);
        estadoGlobalAlgoritmo.persistirProductos();
        estadoGlobalAlgoritmo.convservarProgramacionesEnInstanteAlgoritmo(instanteAlgoritmo);
        estadoGlobalAlgoritmo.conservarPedidos();
        estadoGlobalAlgoritmo.conservarVuelos(instanteAlgoritmo);

        return estadoGlobalAlgoritmo;
    }

    private void persistirProductos()
    {   
        List<UUID> productosEnVuelo;
        List<Producto> productosSinProgramacion, productosEnProgramaciones;
        Producto producto;
        Map<Vuelo, List<Producto>> productosPorVuelo = new HashMap<>();
        Map<Almacen, List<Producto>> productosPorAlmacen = new HashMap<>();
        
        productosEnProgramaciones = this.programaciones.stream()
                .map(Programacion::getUuidProducto)          
                .map(uuid -> this.productos.get(uuid))       
                .filter(Objects::nonNull)                   
                .toList();

        for(Vuelo vuelo : this.vuelos.values())
        {
            productosEnVuelo = vuelo.getIdsProductosContenidos();

            for(UUID idProducto : productosEnVuelo)
            {
                producto = this.productos.get(idProducto);

                if(!productosEnProgramaciones.contains(producto))
                {
                    productosPorVuelo.computeIfAbsent(vuelo, listaProductos -> new ArrayList<>())
                            .add(producto);    
                }
            }
        }

        for(Almacen almacen : this.almacenes.values())
        {
            productosEnVuelo = almacen.getIdsProductosExistentes();

            for(UUID idProducto : productosEnVuelo)
            {
                producto = this.productos.get(idProducto);

                if(!productosEnProgramaciones.contains(producto))
                {
                    productosPorAlmacen.computeIfAbsent(almacen, listaProductos -> new ArrayList<>())
                            .add(producto);    
                }
            }
        }

        limpiarInventarios();
        reconstruirInventario(productosPorVuelo, productosPorAlmacen);
    }

    /*
     * Inicializa los atributos nuevos de las clases importantes. Copia los productosEntregados de los pedidos
    */
    private void limpiarInventarios()
    {
        Set<UUID> idsProductosProgramados, idsProductosEntregados;
        Producto productoEnPedido;

        for(Almacen almacen : this.almacenes.values())
        {
            almacen.setIdsProductosExistentes(new ArrayList<>());
            almacen.setIdsProductosFuturos(new ArrayList<>());
            almacen.setCambios(new TreeMap<>());
        }

        for(Vuelo vuelo : this.vuelos.values())
        {
            vuelo.setIdsProductosContenidos(new ArrayList<>());
        }

        for(Producto producto : this.productos.values())
        {
            producto.setProntoParaEntrega(false);
            producto.setExiste(false);
            producto.setPlanificado(false);
            producto.setInstanteDeDisponibilidad(null);
        }

        for(Pedido pedido : this.pedidos.values())
        {
            idsProductosProgramados = pedido.getIdsProductosProgramados();
            idsProductosEntregados = pedido.getIdsProductosEntregados();

            if(idsProductosProgramados.size() + idsProductosEntregados.size() == pedido.getCantidadProductosPedidos())
            {   // ya fue programado completamente privamente
                if(idsProductosProgramados.size() == pedido.getCantidadProductosPedidos())
                {   // recien se va a cumplir
                    pedido.setIdsProductosProgramados(new HashSet<>());
                }
            }else if(idsProductosProgramados.size() == 0 && idsProductosEntregados.size() == 0)
            {   // es un pedido nuevo
                continue;
            }else
            {   // pedido programado parcialmente
                String mensaje = "ERROR (Obtener datos algoritmo): Hay pedidos programados parcialmente";
                Bitacora.escribir(mensaje);
                //throw new IllegalStateException(mensaje);
            }
        }
    }


    /*
     * Reconstruye los inventarios con los productos que no están en ninguna programación
     */
    private void reconstruirInventario(Map<Vuelo,List<Producto>> productosPorVuelo, Map<Almacen,List<Producto>> productosPorAlmacen)
    {
        boolean valido;
        Vuelo vuelo;
        Almacen almacen;
        List<Producto> productosSinProgramacion;

        for (Map.Entry<Vuelo, List<Producto>> entrada : productosPorVuelo.entrySet())
        {
            vuelo = entrada.getKey();
            productosSinProgramacion = entrada.getValue();

            valido = vuelo.registrarInventario_v2(productosSinProgramacion);
            
            if(!valido)
            {
                String mensaje = "ERROR (Obtener estado): Inventario de vuelo desbordado";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }
        }

        for (Map.Entry<Almacen, List<Producto>> entrada : productosPorAlmacen.entrySet())
        {
            almacen = entrada.getKey();
            productosSinProgramacion = entrada.getValue();

            for(Producto producto : productosSinProgramacion)
            {
                valido = almacen.registrarProductoExistente_v2(producto);    

                if(!valido)
                {
                    String mensaje = "ERROR (Obtener estado): Inventario de almacen desbordado";
                    Bitacora.escribir(mensaje);
                    throw new IllegalStateException(mensaje);
                }
            }
        }
    }
    /*
    Sobre el estadoGlobal copiado de ctx, se depuran todas las programaciones, y sus productos, según los casos comentados en los bloques de código respectivos. Aparte de depurar las programaciones también inicializa los almacenes, los vuelos y los pedidos
    */
    private void convservarProgramacionesEnInstanteAlgoritmo(Instant instanteAlgoritmo)
    {
        boolean enVuelo, esperandoSiguiente, vueloActualEsUltimo, esConsistente;
        long minutosDesdeLlegada;
        Instant instanteSalidaPrimero, instanteSalida, instanteLlegada, instanteLlegadaUltimoVuelo;
        Vuelo primerVuelo, vuelo, ultimoVuelo;
        Programacion programacion;
        Producto producto;
        Pedido pedido;
        Almacen almacenDestino, almacenOrigen, almacenFinal;
        LinkedList<Long> idsRuta;
        Iterator<Programacion> it = this.programaciones.iterator();

        while (it.hasNext())
        {
            programacion = it.next();
            idsRuta = programacion.getIdsVueloRuta();
            primerVuelo = this.vuelos.get(idsRuta.get(0));
            producto = this.productos.get(programacion.getUuidProducto());
            pedido = this.pedidos.get(programacion.getIdPedido());
            instanteSalidaPrimero = primerVuelo.getInicio();

            if (instanteSalidaPrimero.isAfter(instanteAlgoritmo))
            {   // Esta esperando su primer vuelo. Eliminar programación y producto
                this.productos.remove(producto.getUuid());
                it.remove();
            }else
            {   //su primer vuelo ya salió
                enVuelo = false;
                esperandoSiguiente = false;

                for (int i = 0; i < idsRuta.size(); i++)
                {
                    vuelo = this.vuelos.get(idsRuta.get(i));
                    almacenDestino = this.almacenes.get(vuelo.getIdAlmacenDestino());
                    almacenOrigen = this.almacenes.get(vuelo.getIdAlmacenOrigen());
                    instanteSalida = vuelo.getInicio();
                    instanteLlegada = vuelo.getFin();

                    if (!instanteAlgoritmo.isBefore(instanteSalida) && instanteAlgoritmo.isBefore(instanteLlegada))
                    {  
                        enVuelo = true;
                        vueloActualEsUltimo = (i == idsRuta.size() - 1);

                        if(vueloActualEsUltimo)
                        {   // esta en su último vuelo. Conservar programación. Agregar producto al vuelo, marcar como existente (ya debería estar marcado así), planificado y prontaEntrega
                            vuelo.registrarInventario_v2(producto);
                            producto.setExiste(true);
                            producto.setPlanificado(true);
                            producto.setProntoParaEntrega(true);
                            programacion.setAPuntoDeCumplirse(true);
                            //añadir prod a pedido
                        }
                        else
                        {   // esta en un vuelo intermedio. Eliminar programación. Agregar a producto al vuelo, marcar como existente (ya debería estar marcado así) y no planificado (ya debería estar marcado así)
                            vuelo.registrarInventario_v2(producto);
                            producto.setExiste(true);
                            producto.setPlanificado(false);
                            producto.setProntoParaEntrega(false);
                            it.remove();
                        }

                        break;
                    }

                    if (instanteAlgoritmo.isBefore(instanteSalida))
                    {
                        if (i > 0)
                        {   //Esta en un almacén intermedio. Eliminar programación. Agregar producto al almacén, marcar como existente (ya debería estar marcado así) y no planificado (ya debería estar marcado así)
                            esperandoSiguiente = true;
                            almacenDestino.registrarProductoExistente_v2(producto);
                            producto.setExiste(true);
                            producto.setPlanificado(false);
                            producto.setProntoParaEntrega(false);
                            it.remove();
                        }

                        break;
                    }
                }

                if (!enVuelo && !esperandoSiguiente)
                {
                    ultimoVuelo = this.vuelos.get(idsRuta.get(idsRuta.size() - 1));
                    instanteLlegadaUltimoVuelo = ultimoVuelo.getFin();
                    minutosDesdeLlegada = Duration.between(instanteLlegadaUltimoVuelo, instanteAlgoritmo).toMinutes();
                    almacenFinal = this.almacenes.get(ultimoVuelo.getIdAlmacenDestino());

                    if (minutosDesdeLlegada >= 0 && minutosDesdeLlegada <= HORAS_ESPERA_PARA_RECOJO * 60)
                    {   //Esta en su almacen final a punto de ser recogido. Conservar programación. Agregar producto al almacén, marcar como existente (ya debería estar marcado así), planificado y prontaEntrega
                        almacenFinal.registrarProductoExistente_v2(producto);
                        producto.setExiste(true);
                        producto.setPlanificado(true);
                        producto.setProntoParaEntrega(true);
                        programacion.setAPuntoDeCumplirse(true);
                    }
                    else if (minutosDesdeLlegada > HORAS_ESPERA_PARA_RECOJO * 60)
                    {   //Ya fue recogido. Eliminar programacion y producto. Actualizar pedido
                        this.productos.remove(producto.getUuid());
                        pedido.registrarProducto_v2();
                        it.remove();
                    }
                }
            }
        }
    }

    /*
    Conserva los pedidos que no están satisfechos
    */
    private void conservarPedidos()
    {
        this.pedidos.entrySet().removeIf(
                entry -> entry.getValue().getCantidadProductosPendientes() <= 0
        );
    }


    /*
    Conserva los vuelos que sean usables en el instanteAlgoritmo
    */
    private void conservarVuelos(Instant instanteAlgoritmo)
    {
        this.vuelos.entrySet().removeIf(entry -> {
            Instant fin = entry.getValue().getFin();
            return fin.isBefore(instanteAlgoritmo);
        });
    }

    /*
     * Inicializa el estado global. Se considera que el EstadoGlobal que llega al algoritmo contiene los almacenes, con los productos existentes en su respectivo almacén, en el instanteActual. Además, los vuelos tienen productos existentes en tránsito, de los cuales una cantidad tiene asociados programaciones que no se pueden cancelar. 
     *
     * Reemplazo de inicializar.
     */
    public void inicializar_v2(Instant instanteActual)
    {
        //depurarProductos_v2(); //deberia eliminar 0 siempre
        inicializarVuelosEnTransito_v2(instanteActual);
        inicializarProgramacionesIncancelables_v2(instanteActual);
        calcularPuntajesDePedidos_v2(instanteActual);
    }

    /*
     * Esta función recorre todas los vuelos y registra los cambios en los almacenes correspondientes, actualizando sus productos futuros
     */
    private void inicializarVuelosEnTransito_v2(Instant instanteActual)
    {
        boolean valido;
        Almacen almacenDestino;
        Instant instanteSalida, instanteLlegada;
        List<Producto> productosFuturos;

        for (Vuelo vuelo : this.vuelos.values())
        {
            instanteSalida  = vuelo.getInicio();
            instanteLlegada = vuelo.getFin();
            almacenDestino = this.almacenes.get(vuelo.getIdAlmacenDestino());

            if (!instanteLlegada.isBefore(instanteActual))
            {   // el vuelo esta en tránsito o todavía no sale
                if (instanteSalida.isBefore(instanteActual))
                {   // el vuelo está en tránsito 
                    valido = true;  
                    productosFuturos = vuelo.getIdsProductosContenidos().stream()
                            .map(uuid -> this.productos.get(uuid))
                            .toList();

                    for(Producto producto : productosFuturos)
                    {
                        valido &= almacenDestino.registrarProductoFuturo_v2(producto, instanteLlegada);

                        if(!valido)
                        {
                            Bitacora.escribir("ERROR: (Inicialización): Registro de productos futuros inválidos");
                        }
                    }
                }
                else
                {   // el vuelo todavía no ha salido
                    continue;
                }
            }
        }
    }

    /*
     * Esta función itera sobre las programaciones para registrar el recojo de los productos de los almacenes (osea, un cambio más) a las 2 horas
     */ 
    private void inicializarProgramacionesIncancelables_v2(Instant instanteActual)
    {
        boolean valido;
        Vuelo ultimoVuelo;
        Instant llegada, recojo;
        Almacen almacenDestino;
        Producto producto;
        LinkedList<Vuelo> ruta;

        for (Programacion programacion : this.programaciones)
        {
            if (programacion.isAPuntoDeCumplirse())
            {

                ruta = programacion.getRuta();
                ultimoVuelo = ruta.getLast();//this.vuelos.get(ruta.getLast());
                llegada = ultimoVuelo.getFin();
                almacenDestino = this.almacenes.get(ultimoVuelo.getIdAlmacenDestino());
                producto = this.productos.get(programacion.getUuidProducto());
                valido = almacenDestino.registrarRecojoDeProductos_v2(producto, llegada, true, null);

                if(!valido)
                {
                    Bitacora.escribir("ERROR: (Inicialización): No se puede registrar el recojo de los productos");
                    valido = almacenDestino.registrarRecojoDeProductos_v2(producto, llegada, true, null);
                }
            }else{
                Bitacora.escribir("ERROR: (Inicialización): Existe una programación que se puede cancelar");
            }
        }
    }

    /*
     * Esta función calcula los puntajes de los pedidos (los puntajes ahora son un atributo de la clase Pedido)
     */
    private void calcularPuntajesDePedidos_v2(Instant instanteActual)
    {
        Double puntaje;

        for (Pedido pedido : this.pedidos.values())
        {
            puntaje = CalculadorDeFitness.asignarPuntajesPedidos_v2(pedido, instanteActual);
            
            pedido.setPuntaje(puntaje);
        }
    }

    /*
     * Corre un algoritmo BFS para la generación de rutas y crea su lista de adyacencia
     * 
     * Remplazo de generarRutasParaPedidosPendientesBFS
     */
    public List<LinkedList<Vuelo>> calcularRutas_v2(Instant instanteActual)
    {
        List<LinkedList<Vuelo>> rutas;
        List<Almacen> origenes;
        List<Vuelo> path, nuevoPath, sucesores;
        Set<String> firmas;
        Set<Almacen> destinos;
        Map<Long, List<Vuelo>> vuelosPorOrigen;
        Queue<List<Vuelo>> cola;
        Vuelo ultimo;
        Almacen destinoUltimo;

        int rutasParaDestino, rutasParaOrigen;

        rutas = new ArrayList<>();
        firmas = new HashSet<>();
        destinos = obtenerAlmacenesDestino_v2();
        origenes = obtenerAlmacenesOrigen_v2();
        vuelosPorOrigen = obtenerVuelosPorOrigen_v2();

        for (Almacen almacenDestino : destinos)
        {
            rutasParaDestino = 0;

            for (Almacen origen : origenes)
            {
                if (rutasParaDestino >= MAX_RUTAS_POR_DESTINO)
                {
                    break;
                }

                cola = inicializarCola_v2(origen, vuelosPorOrigen, instanteActual);
                rutasParaOrigen = 0;

                while (!cola.isEmpty()
                        && rutasParaOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasParaDestino < MAX_RUTAS_POR_DESTINO)
                {
                    path = cola.poll();
                    ultimo = path.get(path.size() - 1);
                    destinoUltimo = this.almacenes.get(ultimo.getIdAlmacenDestino());

                    if (ultimo.getIdAlmacenDestino() == almacenDestino.getId())
                    {
                        if (rutaSinInfinitosIntermedios_v2(path))
                        {
                            String firma = crearFirmaRuta_v2(path);
                            if (firmas.add(firma))
                            {
                                rutas.add(new LinkedList<>(path));
                                rutasParaOrigen++;
                                rutasParaDestino++;
                            }
                        }
                        continue;
                    }

                    if (path.size() >= MAX_LEGS || destinoUltimo.isEsInfinito())
                    {
                        continue;
                    }

                    sucesores = vuelosPorOrigen.getOrDefault(ultimo.getIdAlmacenDestino(), Collections.emptyList());

                    for (Vuelo siguiente : sucesores)
                    {
                        if (!esVueloAdmisibleComoSiguiente_v2(path, ultimo, siguiente, instanteActual))
                        {
                            continue;
                        }

                        nuevoPath = new ArrayList<>(path);
                        nuevoPath.add(siguiente);
                        cola.add(nuevoPath);
                    }
                }
            }
        }

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
                .map(this::destinoPedido_v2)
                .filter(almacen -> !almacen.isEsInfinito())
                .collect(Collectors.toSet());
    }

    /*
     * Obtiene los almacenes que seam infinitos o tengan stock. Es una lista porque itera sobre this.almacenes, que solo posee una copia de cada almacen
     * 
     * Remplazo de devolverAlmacenesInfinitosOConStockDisponible
     */
    private List<Almacen> obtenerAlmacenesOrigen_v2()
    {
        return this.almacenes.values().stream()
                .filter(almacen -> almacen.isEsInfinito() || !almacen.getIdsProductosFuturos().isEmpty() || !almacen.getIdsProductosExistentes().isEmpty())
                .collect(Collectors.toList());
    }

    private Map<Long, List<Vuelo>> obtenerVuelosPorOrigen_v2()
    {
        return this.vuelos.values().stream()
                .collect(Collectors.groupingBy(
                        Vuelo::getIdAlmacenOrigen,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                lista -> {
                                    lista.sort(Comparator.comparing(
                                            Vuelo::getInicio,
                                            Comparator.nullsLast(Comparator.naturalOrder())));
                                    return lista;
                                })));
    }

    private Queue<List<Vuelo>> inicializarCola_v2(Almacen origen, Map<Long, List<Vuelo>> vuelosPorOrigen, Instant instanteActual)
    {
        Queue<List<Vuelo>> cola;
        List<Vuelo> path, iniciales;
        Vuelo vueloInicial;

        cola = new ArrayDeque<>();
        iniciales = vuelosPorOrigen.getOrDefault(origen.getId(), Collections.emptyList());

        for (Vuelo v : iniciales)
        {
            vueloInicial = v;

            if (vueloInicial.obtenerCapacidadDisponible_v2() <= 0 || vueloInicial.yaPartio_v2(instanteActual) || (!origen.isEsInfinito() && !almacenTieneStockEnInstante(origen, vueloInicial.getInicio())))
            {
                continue;
            }

            path = new ArrayList<>();
            path.add(vueloInicial);
            cola.add(path);
        }

        return cola;
    }

    private boolean rutaSinInfinitosIntermedios_v2(List<Vuelo> path)
    {
        return path.stream()
                .skip(1)
                .map(vuelo -> this.almacenes.get(vuelo.getIdAlmacenDestino()))
                .noneMatch(Almacen::isEsInfinito);
    }

    public String crearFirmaRuta_v2(List<Vuelo> path)
    {
        return path.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.joining("-"));
    }

    private boolean esVueloAdmisibleComoSiguiente_v2(List<Vuelo> path, Vuelo ultimo, Vuelo siguiente, Instant instanteActual)
    {
        boolean valido;

        valido =// tiene capacidad 
                siguiente.obtenerCapacidadDisponible_v2() > 0
                // no ha partido
                && !siguiente.yaPartio_v2(instanteActual)
                // respeta la espera mínima entre vuelos
                && !siguiente.getInicio().isBefore(
                        ultimo.getFin().plus(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS))
                // no repetir vuelo en la ruta
                && path.stream().noneMatch(v -> v.getId() == siguiente.getId())
                // no repetir almacén destino en la ruta
                && path.stream().noneMatch(
                        v -> v.getIdAlmacenDestino() == siguiente.getIdAlmacenDestino())
                // el destino del siguiente no es infinito (intermedio)
                && !this.almacenes.get(siguiente.getIdAlmacenDestino()).isEsInfinito();

        return valido;
                
    }

    /*
     * En base a las rutas computadas, calcula la lista de adyacencia de almacenes con 
     * 
     * Remplazo de crearIndiceIdsRutasPorAlmacenDestino
     */
    private void calcularAdyacenciaRutasPorAlmacen_v2(List<LinkedList<Vuelo>> rutasPosibles)
    {
        HashMap<Almacen, List<LinkedList<Vuelo>>> indice;
        List<LinkedList<Vuelo>> rutasDelAlmacen;

        indice = new HashMap<>();

        for (Almacen almacen : this.almacenes.values())
        {
            rutasDelAlmacen = rutasPosibles.stream()
                    .filter(ruta -> ruta.getLast().getIdAlmacenDestino() == almacen.getId())
                    .toList();

            indice.put(almacen, rutasDelAlmacen);
        }

        this.adyacencia = indice;
    }


    /*
     * Verifica si es que todos los pedidos pendientes se han satisfecho en base a su idsProductosProgramados
     *
     * Remplazo de hayPedidosPendientesPorProgramar
     */
    public boolean hayPedidosPendientes_v2()
    {
        return pedidos.values().stream()
                .anyMatch(pedido -> pedido.cantidadProductosFaltantes_v2() > 0);
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
                .filter(Pedido -> Pedido.cantidadProductosFaltantes_v2() > 0)
                .collect(Collectors.toList());
    }

    /*
     * Obtiene las rutas validas para el pedido tomando en cuenta los plazos y el destino. Esta función no asegura que se retorne rutas con capacidad (esto último se verifica en elegirRuta_v2)
     *
     * Remplazo de obtenerRutasConMismoDestinoQuePedido y filtrarRutasSegunPlazoPedido
     */
    public List<LinkedList<Vuelo>> obtenerRutasValidas_v2(Pedido pedidoElegido)
    {
        Almacen almacenDestino;
        List<LinkedList<Vuelo>> rutasValidas;
        Instant instanteRegistro, instanteLimite;
        
        almacenDestino = destinoPedido_v2(pedidoElegido);
        rutasValidas = this.adyacencia.get(almacenDestino);
        instanteRegistro = pedidoElegido.getInstanteRegistro();
        instanteLimite   = pedidoElegido.instanteMaximoLlegadaUltimoVuelo_v2();
        rutasValidas = new ArrayList<>(rutasValidas.stream()
                .filter(ruta -> {
                    Instant salidaPrimero  = ruta.getFirst().getInicio();
                    Instant llegadaUltimo  = ruta.getLast().getFin();

                    return !salidaPrimero.isBefore(instanteRegistro)
                            && !llegadaUltimo.isAfter(instanteLimite);
                })
                .toList());

        if(rutasValidas.isEmpty())
        {
            Bitacora.escribir("ERROR (Rutas validas): No hay rutas validas después de aplicar filtros");
        }

        return rutasValidas;
    }

    /*
     * Devuelve la lista de productos que están disponibles en un almacen para programar a partir de un instanteDemandado. Si el almacén es infinito, devuelve un 
     */
    public List<Producto> obtenerProductosDisponibles_v2(Almacen almacen, Instant instanteDemandado)
    {
        List<Producto> productosExistentes, productosFuturos, productosDisponibles;

        productosDisponibles = new ArrayList<>();

        if(!almacen.isEsInfinito())
        {
            productosExistentes = almacen.getIdsProductosExistentes().stream()
                    .map(productos::get)
                    .filter(producto -> producto.isExiste() && !producto.isPlanificado() && !producto.isProntoParaEntrega())
                    .collect(Collectors.toList());
            productosFuturos = almacen.getIdsProductosFuturos().stream()
                    .map(productos::get)
                    .filter(producto -> producto.isExiste() && !producto.isPlanificado() && !producto.isProntoParaEntrega() &&producto.estaDisponible_v2(instanteDemandado))
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
    public int obtenerCapacidadRuta_v2(LinkedList<Vuelo> rutaElegida, int capacidadAlmacen)
    {
        int capacidadMaxima, entradaMaxima, salidaValida, capacidadVuelo;
        Almacen almacenSalida, almacenEntrada;

        capacidadMaxima = 0;
        salidaValida = capacidadAlmacen;

        for(Vuelo vuelo : rutaElegida)
        {
            almacenSalida = origenVuelo_v2(vuelo);            
            almacenEntrada = destinoVuelo_v2(vuelo);

            if(true)//almacenSalida.verificarSalida_v2(vuelo.getInicio(), salidaValida))
            {   //la cantidad de productos que se puede sacar del almacen es consistente
                capacidadVuelo = vuelo.obtenerCapacidadDisponible_v2();

                if(capacidadVuelo > 0)
                {   //el vuelo tiene capacidad
                    entradaMaxima = almacenEntrada.calcularEntradaMaximaEnInstante_v2(vuelo.getFin());

                    if(almacenEntrada.verificarEntrada_v2(vuelo.getFin(), entradaMaxima))
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
    public boolean registrarNuevosProgramacionesYProductos_v2(LinkedList<Vuelo> ruta, List<Producto> productos, List<Programacion> programaciones, Instant instanteActual)
    {
        boolean valido;
        Instant instanteLlegadUltimoVuelo;
        Almacen almacenDestino;

        valido = true;
        almacenDestino = destinoRuta(ruta);
        instanteLlegadUltimoVuelo = ruta.getLast().getFin();

        for(Producto producto : productos)
        {
            valido &= almacenDestino.registrarRecojoDeProductos_v2(producto, instanteLlegadUltimoVuelo, false, instanteActual);
            this.productos.put(producto.getUuid(), producto);
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
     * Devuelve el almacén destino del pedido
     */
    public Almacen destinoPedido_v2(Pedido pedido)
    {
        long idAlmacenDestino;

        idAlmacenDestino = pedido.getIdAlmacenDestino();

        return this.almacenes.get(idAlmacenDestino);
    }

    /*
     * Devuelve el almacén origen de un vuelo
     */
    public Almacen origenVuelo_v2(Vuelo vuelo)
    {
        long idAlmacenOrigen;

        idAlmacenOrigen = vuelo.getIdAlmacenOrigen();

        return this.almacenes.get(idAlmacenOrigen);
    }

    /*
     * Devuelve el almacén destino de un vuelo
     */
    public Almacen destinoVuelo_v2(Vuelo vuelo)
    {
        long idAlmacenDestino;

        idAlmacenDestino = vuelo.getIdAlmacenDestino();

        return this.almacenes.get(idAlmacenDestino);
    }

    /*
     * Devuelve un almacén según su id
     */
    public Vuelo buscarVuelo_v2(Long id)
    {
        return this.vuelos.get(id);
    }

    /*
     * Devuelve un almacén según su id
     */
    public Pedido buscarPedido_v2(Long id)
    {
        return this.pedidos.get(id);
    }


    public Almacen origenRuta(LinkedList<Vuelo> ruta)
    {
        Vuelo primerVuelo;

        primerVuelo = ruta.getFirst();

        return this.almacenes.get(primerVuelo.getIdAlmacenOrigen());
    }

    public Almacen destinoRuta(LinkedList<Vuelo> ruta)
    {
        Vuelo ultimoVuelo;

        ultimoVuelo = ruta.getLast();

        return this.almacenes.get(ultimoVuelo.getIdAlmacenDestino());
    }






































/* LEGACY */

    @Setter
    transient LoggingReport lr; // mientras usamos la bitácora

    // índices
    HashMap<Long, List<Long>> idsVuelosPorOrigen; // No se usa
    HashMap<Long, List<Long>> idsVuelosPorDestino; // No se usa
    HashMap<Long, List<Long>> idsPedidosPorDestino; // No se usa
    HashMap<Long, List<Long>> idsVuelosDondeApareceAlmacenOrdenados = new HashMap<>(); // Se usa
    HashMap<Long, List<Programacion>> programacionesPorIdVueloIncluido = new HashMap<>(); // Se usa - no creo ah
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

    
    public List<Vuelo> obtenerVariosVuelosPorIds(List<Long> idsVuelosEnOrden)
    {
        List<Vuelo> vuelosAObtener = new LinkedList<>();
        for (Long id : idsVuelosEnOrden)
        {
            Vuelo v = vuelos.get(id);
            if (v == null)
            {
                lr.appendReport("Vuelo no encontrado con " + id);

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
    public EstadoGlobal obtenerEstadoGlobalEnInstante(Instant instanteAlgoritmo,
            ContextoSimulacion ctx) {

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

    private Map<UUID, Producto> obtenerProductosParaAlgoritmoMemoria(Instant instanteAlgoritmo)
    {
        // 1. Simular productos en el futuro (ubicaciones, flags):
        Map<UUID, Producto> prodsSimulados = simularProductosEnInstante(
                productos, programaciones, instanteAlgoritmo
        );

// 2. Filtrar solo productos REPROGRAMABLES:
        Map<UUID, Producto> prodsUsables = prodsSimulados.values().stream()
                .filter(p ->
                        p.isExiste() && !p.isEntregado()
//                        &&
//                        !p.isEntregado()
//                                &&
//                                !p.isProntoParaEntrega() // ??!!
                )
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
                    Instant recojo = ultimoVuelo.getFin()
                        .plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));

                    // No completada: último vuelo aún no llegó o llegó hace menos de 2h
                    return prog.isAPuntoDeCumplirse() && !recojo.isBefore(instanteAlgoritmo);
                    //return /*!prod.isEntregado() &&*/
                            // prog.isAPuntoDeCumplirse()
                           // && ! ultimoVuelo.getFin()/*.plus(2, ChronoUnit.HOURS)*/
                            //.isBefore(ctx.getAhora());
                })
                .toList();

        return progsAlgoritmo;
    }

    private Map<Long, Pedido> obtenerPedidosParaAlgoritmoMemoria(
            Instant instanteAlgoritmo,
            ContextoSimulacion ctx,
            List<Programacion> programacionesParaAlgoritmo)
    {         
        Map<Long, Pedido> pedidosBase = getPedidos();
        Map<Long, Pedido> pedidosParaAlgoritmo = pedidosBase.values().stream()
                .map(pedido -> simularPedido(pedido, ctx.obtenerElAhora(),  instanteAlgoritmo,programacionesParaAlgoritmo) )
                .filter(pedido -> {
                            if( pedido.getId() == 3589228L){
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
                                    ||
                    // Vuelo considerado en anteriores programaciones (para evitar NPE en algor)
                        (
//                                vuelo.yaLlego(instanteAlgoritmo) &&
                                !vuelo.getInicio().isBefore(
                                        instanteAlgoritmo.minus(4L *HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX, ChronoUnit.HOURS)
                                ) // solo así me funcó no sé pq 😿😿😿
                        )
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
        if(p.getId() == 3589228L){
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


            if (
                // Si ya salió el último vuelo, considerar ENTREGADO
                    ( !inicio.isAfter(instanteAlgoritmo) && inicio.isAfter(instantePrevioSimulado) )
                // Si el vuelo SOLO salió ANTES pero NO LLEGÓ aún en la simu REAL, considerar ya ENTREGADO
                || ( inicio.isBefore(instantePrevioSimulado) && llegada.isAfter(instantePrevioSimulado) )
            ) { // instanteAlgoritmo >= inicio
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

        Map<UUID, Producto> prodsAntiPlanificados = productosParam.values().stream()
                .map(producto -> {
                    Producto nuevo = new Producto(producto);
                    nuevo.desestablecerQueEstaPlanificadoParaAlgoritmo();
                    return nuevo;
                })
                .collect(Collectors.toMap(Producto::getUuid, o -> o));

        HashMap<UUID, Producto> simularProductos = new HashMap<>(prodsAntiPlanificados);
        // se asegura de que tenga todos los prods de base y luego solo se sobreescriba

        for(Programacion programacion : programacionesParam) {
            Producto producto = productosParam.get(programacion.getUuidProducto());
            Producto simulado = new Producto(producto);

//            //WORKAROUND:
//            simulado.desestablecerQueEstaPlanificadoParaAlgoritmo();

            List<Vuelo> vuelosRuta = programacion.getIdsVueloRuta().stream().map(aLong -> vuelos.get(aLong)).toList();
            // en qué situaciones el producto cambia su estado?
            //^^ Solo cuando su último vuelo sale o llega y el cliente lo recoge.
            Vuelo primerVuelo = vuelosRuta.get(0);
            Vuelo ultimoVuelo = vuelosRuta.get(vuelosRuta.size()-1);
            /* */
            if(!primerVuelo.yaPartio(instante))
            {   //primer vuelo no ha salido, osea no existe
                simulado.setExiste(false);
            }else
            {   //primer vuelo ya salio, osea que existe
                simulado.setExiste(true);

                if(ultimoVuelo.yaPartio(instante))
                {
                    simulado.setPlanificado(true);
                    simulado.setProntoParaEntrega(true);
                    Instant instanteRecojo = ultimoVuelo.getFin().plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));

                    if(instanteRecojo.isAfter(instante)) //Verificar esta condicion
                    {   //el produto ya fue recogido
                        simulado.setEntregado(true);
                    }
                }
            }
            /* */
            /*
            if(primerVuelo.yaPartio(instante))
            {   //O ESTA EN UN ALMACEN INTERMEDIO O ESTA EN UN VUELO
                if(!simulado.isExiste())
                    simulado.setExiste(true); // porsia
            }
            if(ultimoVuelo.yaPartio(instante))
            {   //SI ES QUE YA ESTA EN SU ULTUMO VUELO
                simulado.marcarProntoParaEntrega(); // porsia lo marco en ambos casos
                if(ultimoVuelo.yaLlego(instante))
                {   //
                    simulado.setEntregado(true);
                }else{
                    //PRONTO PARA ENTREGA
                //    simulado.marcarComoProgramado(instante); // q
                }
            }
            */
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
//depurarProductos_v2();
        // Para tener los productos en los almacenes debido a los vuelos EN TRANSCURSO que van a llegar
        for(Vuelo vuelo: vuelos.values()) {
            if( !vuelo.getIdsProductosContenidos().isEmpty()){ // Este vuelo está en tránsito y trae prods
                // ¿este vuelo es parte de una programación previa incancelable? Esos vuelos se procesan dsp no aqui
                boolean esVueloIntermedio = programaciones.stream()
                        .anyMatch(prog -> {
                            LinkedList<Long> ruta = prog.getIdsVueloRuta(); // El vuelo está en la ruta PERO NO es el último
                            return ruta.contains(vuelo.getId())
                                    && !ruta.getLast().equals(vuelo.getId());
                        });
                if(esVueloIntermedio) {
                    Almacen almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
                    List<Producto> prods = vuelo.getIdsProductosContenidos().stream().map(uuid -> productos.get(uuid))
                            .toList();

                    prods.forEach(producto -> {
                        producto.setInstanteDeDisponibilidad(vuelo.getFin());
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
            Vuelo vuelo = /*ctx.*/ getVuelos().get(idUltimoVuelo); // PARA QUE ME DEJE DE DAR NULOS >:v <- ya no
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

/* WORKAROUND */
    private List<LinkedList<Long>> convertirRutasAVuelosId(List<LinkedList<Vuelo>> rutasVuelos)
    {
        List<LinkedList<Long>> rutasIds = new ArrayList<>(rutasVuelos.size());

        for (LinkedList<Vuelo> ruta : rutasVuelos)
        {
            LinkedList<Long> idsRuta = ruta.stream()
                    .map(Vuelo::getId)
                    .collect(Collectors.toCollection(LinkedList::new));

            rutasIds.add(idsRuta);
        }

        return rutasIds;
    }
}



