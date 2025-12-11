package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.ConstruccionProgramacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.RutaYProductos;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.GeneradorAleatorio;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Testeador;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.UMBRAL_RCL_PEDIDOS;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.UMBRAL_RCL_RUTAS;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.MAX_INTENTOS_PROGRAMAR_PEDIDO;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.MAX_ITENTOS_CONSTRUIR_PROGRAMACION;


import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Component
@Primary
public class EstrategiaGraspHibrido extends EstrategiaPlanificacion
{
    private EstadoGlobal estadoGlobal;
    private Instant instanteActual;

    /*
     * Preámbulo del algoritmo de planificación. Aquí se (1)inicializa el EstadoGlobal copiado, (2)se llama al generador de rutas, (3)se realiza un bucle sobre los pedidos para generarle programaciones y (4)se verifica que la solución satisfaga todos los pedidos
     */
    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) throws Exception
    {
        SalidaProblemaPlanificacion solucion;
        List<Programacion> programaciones;
        
        // Inicialización
        inicializacion(entrada.getEstadoGlobalCopia(), entrada.getInstanteActual());

        // Generación de rutas
        this.estadoGlobal.calcularRutas_v2(this.instanteActual);
        //Testeador.generacionRutasTest(this.estadoGlobal, this.instanteActual);

        // Bucle de pedidos
        try {
            bucleSobrePedidos_v2();    
        } catch (Exception e) {
            Bitacora.escribir("ERROR (Bucle de pedidos): " + e.toString());

            programaciones = this.estadoGlobal.getProgramaciones();
            solucion = new SalidaProblemaPlanificacion(programaciones, e.toString());

            return solucion;
        }
        
        // Verificación de solución completa
        solucion = verificarSolucion();

        return solucion;
    }

    /*
     * Se llama a la función inicializar del EstadoGlobal que registra los cambios en los almacenes
     */
    private void inicializacion(EstadoGlobal estadoOriginal, Instant instanteActual)
    {
        this.estadoGlobal = estadoOriginal;
        this.instanteActual = instanteActual;
        estadoGlobal.inicializar_v2(this.instanteActual);
        this.estadoGlobal.setLr(lr); // Borrar porfavor
        //Testeador.verificarCambiosAlmacenes(this.estadoGlobal, this.instanteActual);
    }

    /*
     * Verifica que una respuesta del algoritmo satisfaga todos los pedidos
     */
    private SalidaProblemaPlanificacion verificarSolucion()
    {
        SalidaProblemaPlanificacion solucion;
        Map<UUID, Producto> productos;
        List<Programacion> programaciones;

        programaciones = this.estadoGlobal.getProgramaciones();
        productos = this.estadoGlobal.getProductos();
        solucion = new SalidaProblemaPlanificacion(programaciones, productos);

        if (this.estadoGlobal.hayPedidosPendientes_v2())
        {
            solucion.setColapsado(true);
        }

        return solucion;
    }

    /*
     * Para cada iteración, elige un pedido, sus rutas validas (que cumplen destino y plazo) y genera las programaciones que satisfacen totalmente ese pedido. Culmina cuando ya no hay más pedidos pendientes o si llega la máximo de iteraciones permitido 
     * 
     *
     * Remplazo de realizarCicloDePedidos y realizarCicloVariosProductosDePedido
     */
    private void bucleSobrePedidos_v2()
    {
        int intentos;
        Pedido pedidoElegido;
        List<Programacion> nuevasProgramaciones;
        List<Pedido> pedidosPendientes;
        List<LinkedList<Vuelo>> rutasValidas;
        
        while(this.estadoGlobal.hayPedidosPendientes_v2())
        {   //este bucle selecciona un pedido
            pedidosPendientes = this.estadoGlobal.obtenerPedidosPendientes_v2();
            pedidoElegido = elegirPedido_v2(pedidosPendientes);
            rutasValidas = this.estadoGlobal.obtenerRutasValidas_v2(pedidoElegido); 

            /* CREO QUE FUNCIONA? PARA HACER DETERMINISTA LA CORRIDA */
            rutasValidas = new ArrayList<>(rutasValidas);
            rutasValidas.sort(Comparator.comparing(this.estadoGlobal::crearFirmaRuta_v2));
            /* */
            
            for(intentos = 0; pedidoElegido.getCantidadProductosPendientes() > 0 || intentos == MAX_INTENTOS_PROGRAMAR_PEDIDO; intentos++)
            {   //este bucle satisface una porción del pedido
                /*Bloque legacy */
                int productosRestantes = pedidoElegido.getCantidadProductosPendientes();
                nuevasProgramaciones = construirVariasPrograsYPersistir4(convertirRutasAVuelosId(rutasValidas), pedidoElegido, productosRestantes);
                /* */

                //nuevasProgramaciones = construirProgramaciones_v2(rutasValidas, pedidoElegido);

                if(!nuevasProgramaciones.isEmpty())
                {
                    //persistirProgramaciones_v2(nuevasProgramaciones);
                    intentos--;
                }
            }

            if(intentos == MAX_INTENTOS_PROGRAMAR_PEDIDO)
            {
                Bitacora.escribir("ERROR (Bucle de pedidos): Se han alcanzado el número máximo de iteraciones");
                throw new IllegalStateException("No se pudo programar un pedido, se alcanzó el número máximo de intentos");
            }
        }
    }

    /*
     * En base a los pedidos pendientes, selecciona un pedido aleatoriamente
     *
     * Remplazo de elegirYProgramarParaPedido y seleccionarPedidoDesdeRCL
     */
    private Pedido elegirPedido_v2(List<Pedido> pedidosPendientes)
    {
        int limiteSuperior, indiceAleatorio;
        List<Pedido> pedidosCandidatos;
        
        pedidosCandidatos = construirListaRestringidaDePedidos_v2(pedidosPendientes);
        limiteSuperior = pedidosCandidatos.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return pedidosCandidatos.get(indiceAleatorio);
    }

    /*
     * En base a los pedidos pendientes, construye una lista restringida de pedidos candidatos 
     *
     * Remplazo de construirRCLDePedidos
     */
    private List<Pedido> construirListaRestringidaDePedidos_v2(List<Pedido> pedidosPendientes)
    {
        double puntaje, puntajeMaximo, puntajeMinimo, umbral;
        List<Pedido> listaRestringida;
        
        puntajeMaximo = Double.NEGATIVE_INFINITY;
        puntajeMinimo = Double.POSITIVE_INFINITY;

        for (Pedido pedido : pedidosPendientes)
        {
            puntaje = pedido.getPuntaje();

            puntajeMaximo = Math.max(puntajeMaximo, puntaje);
            puntajeMinimo = Math.min(puntajeMinimo, puntaje);
        }

        umbral = puntajeMinimo + UMBRAL_RCL_PEDIDOS * (puntajeMaximo - puntajeMinimo);
        listaRestringida = pedidosPendientes.stream()
                .filter(pedido -> {
                    if (pedido.getPuntaje() == null)
                    {
                        Bitacora.escribir("ERROR (Construir RLC): Puntaje es null para pedido");
                    }
                    return pedido.getPuntaje() <= umbral;
                })
                .collect(Collectors.toList());

        return listaRestringida;
    }

    /*
     * Elige una sola ruta para satisfacer el pedido. Dependiendo del origen puede tomar los productos existentes o crear nuevos productos. El numero maximo de programaciones será la demanda del pedido, la capacidad de la ruta o los productos existentes del almacén de origen de la ruta
     *
     * Remplazo de construirVariasPrograsYPersistir
     */
    private List<Programacion> construirProgramaciones_v2(List<LinkedList<Vuelo>> rutasValidas, Pedido pedidoElegido)
    {
        boolean esIntercontinental;
        int demandaMaxima;
        Programacion programacion;
        Instant instanteMaximoEntrega;
        LinkedList<Vuelo> rutaElegida;
        RutaYProductos rutaYProductos;
        List<Producto> productosElegidos;
        List<Programacion> nuevasProgramaciones;
  
        demandaMaxima = pedidoElegido.getCantidadProductosPendientes();
        esIntercontinental = pedidoElegido.isIntercontinentalAhora();
        instanteMaximoEntrega = pedidoElegido.getInstanteMaximoParaEntregar();

        rutaYProductos = obtenerRutaYProductos_v2(rutasValidas, demandaMaxima, esIntercontinental, instanteMaximoEntrega);

        nuevasProgramaciones = new ArrayList<>();
        productosElegidos = rutaYProductos.produtosElegidos();
        rutaElegida = rutaYProductos.rutaElegida();

        for(Producto producto : productosElegidos)
        {
            programacion = new Programacion(pedidoElegido, producto, rutaElegida);
            nuevasProgramaciones.add(programacion);
        }
               
        return nuevasProgramaciones;
    }

    /*
     * Dado una lista de rutasValidas y un pedido (los parametros demandaMaxima y esIntercontinental le pertenecen al pedidoElegido) intentar asignarle una ruta y productos. Primero verifica que la ruta tenga capacidad y luego verifica que hayan productos en ese almacen para retornar esos valores. Aqui no se persiste. DemandaMaxima nunca va a ser 0, o al menos eso se espera
     *
     * Remplazo de obtenerRutaYProgramacion
     */
    private RutaYProductos obtenerRutaYProductos_v2(List<LinkedList<Vuelo>> rutasValidas, int demandaMaxima, boolean esIntercontinental, Instant instanteMaximoEntrega)
    {
        int contador, capacidadRuta, capacidadAlmacen, cantidadProgramaciones;
        Instant instanteInicioRuta;
        Almacen almacenOrigen, almacenDestino;
        LinkedList<Vuelo> rutaElegida;
        List<Producto> productosEnAlmacen, productosElegidos;

        for(contador = 0; contador != MAX_ITENTOS_CONSTRUIR_PROGRAMACION; contador++)
        {   //primero se elige la ruta y se verifica que haya capacidad
            rutaElegida = elegirRuta_v2(rutasValidas, instanteMaximoEntrega);
            capacidadRuta = this.estadoGlobal.obtenerCapacidadRuta_v2(rutaElegida);

            cantidadProgramaciones = Math.min(demandaMaxima, capacidadRuta);

            if(cantidadProgramaciones > 0)
            {   //segundo se tiene que elegir los productos
                almacenOrigen = this.estadoGlobal.origenRuta(rutaElegida);
                almacenDestino = this.estadoGlobal.destinoRuta(rutaElegida);
                instanteInicioRuta = rutaElegida.getFirst().getInicio();
                productosEnAlmacen = this.estadoGlobal.obtenerProductosDisponibles_v2(almacenOrigen, instanteInicioRuta);
                capacidadAlmacen = almacenOrigen.isEsInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();

                cantidadProgramaciones = Math.min(cantidadProgramaciones, capacidadAlmacen);

                if(cantidadProgramaciones > 0)
                {   //la ruta tiene capacidad y el almacen suficientes productos
                    productosElegidos = elegirProductos_v2(almacenOrigen, almacenDestino, esIntercontinental, productosEnAlmacen, cantidadProgramaciones);

                    return new RutaYProductos(productosElegidos, rutaElegida);
                }else
                {   //no hay productos en ese almacen, se tiene que borrar las rutas que inician en ese almacen
                    borrarRutasConOrigenEn(rutasValidas, almacenOrigen);
                }
            }else
            {   //no hay capacidad en la ruta elegida, se tiene que borrar
                borrarRuta(rutasValidas, rutaElegida);
            }
        }

        if(contador == MAX_ITENTOS_CONSTRUIR_PROGRAMACION)
        {
            Bitacora.escribir("ERROR (Construccion programacion): No se han encontrado rutas con almacenes validos");
        }

        return new RutaYProductos(new ArrayList<>(), new LinkedList<>());
    }

    /*
     * En base a las rutas validas disponibles (osea, rutas que cumplen el plazo),selecciona una aleatoriamente
     *
     * Remplazo de seleccionarRutaDesdeRCL
     */
    private LinkedList<Vuelo> elegirRuta_v2(List<LinkedList<Vuelo>> rutasValidas, Instant instanteMaximoEntrega)
    {
        int limiteSuperior, indiceAleatorio;
        List<LinkedList<Vuelo>> pedidosCandidatos;
        
        pedidosCandidatos = construirListaRestringidaDeRutas_v2(rutasValidas, instanteMaximoEntrega);
        limiteSuperior = pedidosCandidatos.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return pedidosCandidatos.get(indiceAleatorio);
    }

    /*
     * En base a las rutas validas, construye una lista restringida de rutas candidatas
     *
     * Remplazo de construirRCLDeRutasConAlMenosUnaParaCadaAlmacen
     */
    private List<LinkedList<Vuelo>> construirListaRestringidaDeRutas_v2(List<LinkedList<Vuelo>> rutasValidas, Instant instanteMaximoEntrega)
    {
        double puntaje, puntajeMaximo, puntajeMinimo, umbral;
        List<LinkedList<Vuelo>> listaRestringida;
        Map<LinkedList<Vuelo>, Double> puntajes;
        
        puntajeMaximo = Double.NEGATIVE_INFINITY;
        puntajeMinimo = Double.POSITIVE_INFINITY;
        puntajes = new HashMap<>();

        for (LinkedList<Vuelo> ruta : rutasValidas)
        {
            puntaje = CalculadorDeFitness.asignarPuntajesRutas_v2(ruta, this.instanteActual, instanteMaximoEntrega, this.estadoGlobal);
            puntajes.put(ruta, puntaje);
            puntajeMaximo = Math.max(puntajeMaximo, puntaje);
            puntajeMinimo = Math.min(puntajeMinimo, puntaje);
        }

        umbral = puntajeMinimo + UMBRAL_RCL_RUTAS * (puntajeMaximo - puntajeMinimo);
        listaRestringida = rutasValidas.stream()
                .filter(ruta -> {
                    return puntajes.get(ruta) <= umbral;
                })
                .collect(Collectors.toList());

        listaRestringida = agregarRutasPorOrigen(listaRestringida, rutasValidas, puntajes);

        return listaRestringida;
    }

    /*
     * Se asegura que existan rutas con al menos una ruta para cada origen. No tiene sentido hacerlo para destinos porque las rutas validas solo consideran el destino final
     */
    private List<LinkedList<Vuelo>> agregarRutasPorOrigen(List<LinkedList<Vuelo>> listaRestringida, List<LinkedList<Vuelo>> rutasValidas, Map<LinkedList<Vuelo>,Double> puntajes)
    {
        Almacen almacenOrigen;
        Double mejorPuntaje, puntaje;
        Set<LinkedList<Vuelo>> listaRestringidaUnica;
        Map<Almacen, LinkedList<Vuelo>> mejorRutaPorAlmacen;
        Map<Almacen, Double> puntajeMejorRutaPorAlmacen;

        mejorRutaPorAlmacen = new HashMap<>();
        puntajeMejorRutaPorAlmacen = new HashMap<>();
        listaRestringidaUnica = new LinkedHashSet<>(listaRestringida);

        for (LinkedList<Vuelo> ruta : rutasValidas)
        {
            almacenOrigen = this.estadoGlobal.origenRuta(ruta);
            puntaje = puntajes.get(ruta);
            mejorPuntaje = puntajeMejorRutaPorAlmacen.get(almacenOrigen);

            if (mejorPuntaje == null || puntaje < mejorPuntaje)
            {
               puntajeMejorRutaPorAlmacen.put(almacenOrigen, puntaje);
                mejorRutaPorAlmacen.put(almacenOrigen, ruta);
            }
        }

        for (LinkedList<Vuelo> mejorRuta : mejorRutaPorAlmacen.values())
        {
            listaRestringidaUnica.add(mejorRuta);
        }

        listaRestringida = new ArrayList<>(listaRestringidaUnica);
        listaRestringida.sort(Comparator.comparing(ruta -> puntajes.get(ruta)));

        return listaRestringida;
    }

    /*
     * Elige una cantidad de productos de la lista de productosEnAlmacen. La cantidadProductos es igual a la cantidadProgramaciones. Si el almacen es infinito los crea
     *
     * Remplazo de escogerProductoEnRuta
     */
    private List<Producto> elegirProductos_v2(Almacen almacenOrigen, Almacen almacenDestino, boolean esIntercontinental, List<Producto> productosEnAlmacen, int cantidadProductos)
    {
        Producto productoNuevo;
        double probabilidadAleatoria, umbral;
        List<Producto> productosContinentales, productosIntercontinentales, productosElegidos;

        productosElegidos = new ArrayList<>();

        if(almacenOrigen.isEsInfinito())
        {
            for(int i = 0; i != cantidadProductos; i++)
            {
                productoNuevo = new Producto(almacenOrigen, this.instanteActual);
 
                productosElegidos.add(productoNuevo);
            }
        }else{ 
            productosContinentales = productosEnAlmacen.stream()
                    .filter(producto -> {
                        Almacen origen = this.estadoGlobal.buscarAlmacen_v2(producto.getIdAlmacenInfinitoOrigen());
                        return origen.getContinente().equals(almacenDestino.getContinente());
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            productosIntercontinentales = productosEnAlmacen.stream()
                    .filter(producto -> {
                        Almacen origen = this.estadoGlobal.buscarAlmacen_v2(producto.getIdAlmacenInfinitoOrigen());
                        return !origen.getContinente().equals(almacenDestino.getContinente());
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            for (int i = 0; i < cantidadProductos; i++)
            {
                if (!productosContinentales.isEmpty() && !productosIntercontinentales.isEmpty())
                {   //hay productos disponibles en ambas listas
                    probabilidadAleatoria = GeneradorAleatorio.decimal();
                    umbral = esIntercontinental ? UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA : UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA;

                    if (probabilidadAleatoria < umbral) {
                        productoNuevo = productosIntercontinentales.remove(0);
                    } else {
                        productoNuevo = productosContinentales.remove(0);
                    }
                } else if (!productosContinentales.isEmpty())
                {   //hay productos disponibles solo en productosContinentales
                    productoNuevo = productosContinentales.remove(0);
                } else
                {   //hay productos disponibles solo en productosIntercontinentales
                    productoNuevo = productosIntercontinentales.remove(0);
                }

                productosElegidos.add(productoNuevo);
            }
        }

        return productosElegidos;
    }

    /*
     * Elimina todas las rutas cuyo origen es el almacen insuficiente
     */
    private void borrarRutasConOrigenEn(List<LinkedList<Vuelo>> rutasValidas, Almacen almacenInsuficiente)
    {
        rutasValidas.removeIf(ruta -> ruta.getFirst().getIdAlmacenOrigen() == almacenInsuficiente.getId());
    }

    /*
     * Elimina una ruta especifica de las rutas validas
     */
    private void borrarRuta(List<LinkedList<Vuelo>> rutasValidas, LinkedList<Vuelo> rutaSinCapacidad)
    {
        rutasValidas.removeIf(ruta -> ruta.equals(rutaSinCapacidad));
    }

    /*
     * Guardar en el estadoGlobal las programaciones que ha construido en  construirProgramaciones_v2. Esta operacion es delicada
     */
    private void persistirProgramaciones_v2(List<Programacion> nuevasProgramaciones)
    {
        /*
            * AQUI SE PERSISTE
        //ESTO TIENE QUE LOGRAR QUE CantidadProductosPendientes sea 0
        //CUANDO CREA UN NUEVO PRODUCTO
        if (!productoAgarrado.isExiste()) {
                this.estadoGlobal.anadirProducto(productoAgarrado);
        }

        //PARA PERSISTIR UNA PROGRAMACION
        this.estadoGlobal.anadirProgramacionSolucion(prograARealizar, instanteActual);
        */
        throw new UnsupportedOperationException("Unimplemented method 'persistirProgramaciones_v2'");
    }













































    /* LEGACY */
    private static final double ALPHA_RUTAS = 0.8;
    private static final double ALPHA_PEDIDOS = 0.5; // por poner algo xd
    private static final int ITERACIONES_MAXIMAS_PRIMER_GRASP = 10000;
    private static final double UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA = 0.8;
    private static final double UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA = 0.2;
    private static int iteraciones=0;
    /*
     * Para ejecutar el algoritmo, solo renombrar esto por "planificar" y ponerle la
     * etiqueta Override
     */
  
    public SalidaProblemaPlanificacion planificar_v1(EntradaProblemaPlanificacion entrada)
            throws Exception {
        iteraciones++;

        // Inicialización
        this.estadoGlobal = entrada.getEstadoGlobalCopia();
        //this.entradaRecibida = entrada;
        this.estadoGlobal.setLr(lr);
        this.instanteActual = entrada.getInstanteActual();

        int numIteraciones;
        try {
            estadoGlobal.inicializar(instanteActual); // <- hace cosas
            setSemilla(entrada.getSemilla()); // repoio
            lr.appendReport("maldita sea");
            estadoGlobal.getVuelos().forEach((aLong, vuelo) -> {lr.appendReport("Vuelo: %s\n", vuelo);});

            // Obtener rutas a solo almacenes de destino y a partir de almacenes infinitos o
            // no infinitos con al menos 1 producto.git log --oneline -1
            List<LinkedList<Long>> // Una clase para ruta que sea lo mismo que una lista de vuelos? No// la necesité hasta ahora
            rutasPosibles = // recordar que no hay pedidos para almacenes infinitos hasta este punto (los filtramos antes).
                    this.estadoGlobal.generarRutasParaPedidosPendientesBFS(instanteActual); //
            // this.estadoGlobal.generarRutasParaPedidosPendientesACO(instanteActual); // <- chamba de Axel
            // lr.appendReport("Las rutas posibles son: " + PrettyPrinter.printList(rutasPosibles));
            
            this.estadoGlobal.crearIndiceIdsRutasPorAlmacenDestino(rutasPosibles); // a partir de aquí
            // tenemos el tan deseado índice.
            // asignar puntajes a pedidos pendientes.
            List<Pedido> pedidosPendientes = this.estadoGlobal
                    .obtenerPedidosPendientesDeEntregaYProgram();
            Map<Pedido, Double> puntajesPorPedido = CalculadorDeFitness.asignarPuntajesPedidos(pedidosPendientes,
                    this.instanteActual); // <- chamba de Axel

            if(iteraciones == 3){
                lr.appendReport("Wa");
                System.out.println("wa");
            }

            // Ciclo principal
            numIteraciones = realizarCicloDePedidos(rutasPosibles, puntajesPorPedido);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            lr.appendReport(Arrays.toString(ex.getStackTrace()));
            SalidaProblemaPlanificacion solution = new SalidaProblemaPlanificacion(
                    this.estadoGlobal.getProgramaciones(), ex.getStackTrace().toString());
            lr.appendReport(ex.toString());
            lr.writeReportFile(
                    "Reporte-GRASP-error-" + this.estadoGlobal.getProgramaciones().size() + "-");
            System.out.println("DEBUG after write file, buffer length: " + lr.getInternalLength());

            return solution;
        }
        lr.appendReport(
                "Planificación finalizada. Iteraciones GRASP realizadas: " + numIteraciones +
                        ". Programaciones creadas: "
                        + this.estadoGlobal.getProgramaciones().size());

        SalidaProblemaPlanificacion solution = new SalidaProblemaPlanificacion(
                this.estadoGlobal.getProgramaciones(), this.estadoGlobal.getProductos());

        if (this.estadoGlobal.hayPedidosPendientesPorProgramar()) {
            lr.appendReport("NO SE LOGRÓ PLANIFICAR TODO, COLAPSO LOGÍSTICO!!!!!!!!!!!!");
            solution.setColapsado(true);
        }
        lr.writeReportFile("Reporte-GRASP-" + this.estadoGlobal.getProgramaciones().size() + "-");
        System.out.println("DEBUG after write file, buffer length: " + lr.getInternalLength());

        this.estadoGlobal = null; // será necesario?

        return solution;
    }

    private int realizarCicloDePedidos(
            List<LinkedList<Long>> rutasPosibles,
            Map<Pedido, Double> puntajesPorPedido) {
        int numIteraciones = 0;
        while (this.estadoGlobal.hayPedidosPendientesPorProgramar()
                && numIteraciones < ITERACIONES_MAXIMAS_PRIMER_GRASP){
            lr.appendReport("planificar: Iteración %d: quedan %d pedidos pendientes",
                    numIteraciones, this.estadoGlobal.contarPedidosPendientes());

            List<Programacion> programacionesConstruidasGrasp = elegirYProgramarParaPedido(
                    rutasPosibles, puntajesPorPedido);

            if (programacionesConstruidasGrasp == null) {
                lr.appendReport("GRASP no pudo hacer una programación más, finalizando ciclo.");
                break;
            }
            // // Añadir el envío a la solución
            // this.estadoGlobal.anadirVariasProgramacionesSolucion(programacionesConstruidasGrasp);
            // lr.appendReport("Programaciones solución añadidas: " +
            // programacionesConstruidasGrasp);

            // Limpieza de pedidos completamente satisfechos en la lista global (para
            // acelerar próximas iteraciones)
            boolean removed = this.estadoGlobal.eliminarPedidoYaSatisfecho(
                    puntajesPorPedido,
                    programacionesConstruidasGrasp.get(0).getIdPedido());
            if (removed)
                lr.appendReport("Se eliminó el pedido "
                        + programacionesConstruidasGrasp.get(0).getIdPedido() +
                        " por estar totalmente programado / atendido.");

            // Guardar reporte parcial si quieres (puedes ajustar la frecuencia) no m lo
            // borres
            // if( iter % 100 == 0)
            // lr.writeReportFile("grasp-report-iter-" + iter+"-");

            numIteraciones++;
        }
        return numIteraciones;
    }

    private List<Programacion> elegirYProgramarParaPedido(
            List<LinkedList<Long>> rutas,
            Map<Pedido, Double> puntajesPorPedido) {
        if (rutas.isEmpty())
            return null;
        List<Pedido> rclPedidosCandidatos = construirRCLDePedidos(puntajesPorPedido, ALPHA_PEDIDOS);
        Pedido pedidoElegido = seleccionarPedidoDesdeRCL(rclPedidosCandidatos, puntajesPorPedido,
                generadorAleatorio, false);

        List<Programacion> programaciones = realizarCicloVariosProductosDePedido(pedidoElegido);

        if (programaciones == null || programaciones.isEmpty())
            return null;

        // Ahora actualizar puntajes si lo necesita, recordar que es mutable.
        // Para que la RCL se vuelva a armar considerando el siguiente.
        // puntajesPorPedido.remove(pedidoElegido); // <- ahora lo hago en el
        // eliminadorx

        return programaciones;
    }

    /*
     * Se asegura de darle una programación a cada producto que necesite el pedido;
     * de otra forma, retorna nulo.
     */
    private List<Programacion> realizarCicloVariosProductosDePedido(Pedido pedidoElegido) {
        List<Programacion> programaciones = new LinkedList<>();
        int numProductosPorAtender = pedidoElegido.getCantidadProductosPendientes();
        int numProductosAtendidosPedido = 0;
        List<LinkedList<Long>> rutasConDestinoCompartido = obtenerRutasConMismoDestinoQuePedido(
                pedidoElegido);
        // lr.appendReport("rutasConDestinoCompartido: "+rutasConDestinoCompartido);
        if (rutasConDestinoCompartido == null || rutasConDestinoCompartido.isEmpty())  { // <- vaya caso más raro, casi que nunca sucede
            lr.appendReport("Rutas con destino compartido dio null o empty, pedido: " + pedidoElegido);
            return null;
        }
        List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido = filtrarRutasSegunPlazoPedido(
                pedidoElegido, rutasConDestinoCompartido);
//        if (pedidoElegido.getId() == 3589147L)
//            System.out.println("debug pedido raro");
//        lr.appendReport("rutasFiltradasSegunPlazoPedido: " + rutasFiltradasSegunPlazoPedido.size());
        while (pedidoElegido.getCantidadProductosPendientes() > 0){
            /* numProductosPorAtender > numProductosAtendidosPedido*/
        // Programar para todo el pedido.
            int remaining = pedidoElegido.getCantidadProductosPendientes();
            List<Programacion> creadas = construirVariasPrograsYPersistir4( // <- 3 o 4
                    rutasFiltradasSegunPlazoPedido,
                    pedidoElegido, remaining);
            if (creadas == null || creadas.isEmpty())
            {
                lr.appendReport(
                        "No se pudo atender más productos del pedido " + pedidoElegido.getId());
                return null;
            }
            programaciones.addAll(creadas);
            numProductosAtendidosPedido += creadas.size();
        }
        // lr.appendReport("programaciones: "+ programaciones);
        return programaciones;
    }
    /* AQUI ACABA EL ALGORITMO */

    private ConstruccionProgramacion obtenerRutaYProgramacion(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido){
        Producto productoAgarrado = null;
        LinkedList<Long> rutaElegida = null;
        int capacidadRuta = 0;
        boolean rclValido;
        do{ // Medio rara esta lógica... Pero creo que es necesaria
          // lr.appendReport("A puntuar para crear una nueva RCL, tamaño de las rutas
          // filtradas por plazo: "+rutasFiltradasSegunPlazoPedido.size());
            Map<LinkedList<Long>, Double> puntajesPorRuta = asignarPuntajesRutas(
                    rutasFiltradasSegunPlazoPedido, this.instanteActual, pedidoElegido);
            lr.appendReport("puntajesPorRuta (ya validadas según plazo y destino del pedido), son "
                    + puntajesPorRuta.size() + "\n");
            // lr.appendMap(puntajesPorRuta);
            List<LinkedList<Long>> rclRutasCandidatas = construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
                    puntajesPorRuta);
            if (rclRutasCandidatas.isEmpty()){
                lr.appendReport("construccionGraspParaUnaProgramacion: RCL de rutas vacía");
                return null; // Lo más probable es que las rutas filtradas estén aberradas o nulas,
                             // no hay más que hacer.
            }
            rclValido = true;
            lr.appendReport("construccionGraspParaUnaProgramacion: Rutas que entraron a la RCL:  \n"
                    + rclRutasCandidatas.size());
            while (!rclRutasCandidatas.isEmpty()){ // Solo para asegurar ruta factible
                rutaElegida = seleccionarRutaDesdeRCL(rclRutasCandidatas, puntajesPorRuta, true);
//                lr.appendReport("rutaElegida: \n" + this.estadoGlobal.imprimirRutaEnDetalle(rutaElegida));
                capacidadRuta = this.estadoGlobal.obtenerCapacidadRutaEnEstadoActualSimulada (rutaElegida ); // gpt la rehizo
                // capacidades, no plazos. //OPERACION IMPORTANTE

                lr.appendReport("esRutaValida: " + (capacidadRuta > 0));
                if (capacidadRuta <= 0){
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no
                                                            // incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un
                                                                        // posible futuro puntaje.
                    continue; // el productoAgarrado no se define, queda en null aún.
                }
                productoAgarrado = escogerProductoEnRuta(rutaElegida, pedidoElegido);
                // ^^^^ asumimos que ya hay al menos 1, por lo que solo queda escoger
                if (productoAgarrado == null)
                { // throw new IllegalStateException("¡¿Cómo?!"); // xd
                    lr.appendReport("wtf, el producto agarrado fue nulo");
                    System.out.println("wtf, el producto agarrado fue nulo");
                    rclRutasCandidatas.remove(rutaElegida); // Actualizar RCL de rutas para no
                                                            // incluir la misma
                    rutasFiltradasSegunPlazoPedido.remove(rutaElegida); // Sacar de aquí para un
                                                                        // posible futuro puntaje.
                    continue;
                }
                break;
            }
            if (productoAgarrado == null){
                lr.appendReport(
                        "construccionGraspParaUnaProgramacion: Producto nulo, rcl invalido, nuevo rcl por generar");
                rclValido = false; // quiere decir qu.3e en toda la RCL no consiguió nada
            }
        }while (!rclValido && !rutasFiltradasSegunPlazoPedido.isEmpty());

        if (productoAgarrado == null)
            return null;
        // if (!productoAgarrado.isExiste()) { // OJO: Alteramos estado!!! Se supone que
        // entrará solo si es nuevo.
        // this.estadoGlobal.anadirProducto(productoAgarrado);
        // } <- en otro lado mutamos el estado, afuerita
        return new ConstruccionProgramacion(
                rutaElegida, productoAgarrado, capacidadRuta /*- 1*/ // ya que uno se va a usar ahora
        );
    }


    

    private List<Programacion> construirVariasPrograsYPersistir3(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido,
            int maxToCreate) {

        if (rutasFiltradasSegunPlazoPedido == null || rutasFiltradasSegunPlazoPedido.isEmpty() || maxToCreate <= 0)
            return Collections.emptyList();

        long idPedido = pedidoElegido.getId();
        LinkedList<Long> rutaAReutilizar = null;
        List<Programacion> prograsAPersistir = new ArrayList<>(Math.min(16, maxToCreate));
        Set<LinkedList<Long>> rutasDescartadas = new HashSet<>();

        int created = 0;

        // Nota: aquí asumimos que cp.capacidadRutaParaMasProds() debe representar
        // "capacidad adicional disponible EN ESTE MOMENTO incluyendo el producto que acabas
        // de seleccionar". Para evitar ambigüedades no usamos esa estimación para crear
        // directamente N programaciones: para cada programacion recalculamos la capacidad real.
        while (created < maxToCreate) {
            Producto productoAgarrado = null;
            Integer capacidadActualRuta = null;
            // Si no tengo ruta reutilizable, pido una nueva
            if (rutaAReutilizar == null) {
                ConstruccionProgramacion cp = obtenerRutaYProgramacion(rutasFiltradasSegunPlazoPedido, pedidoElegido);
                if (cp == null) {
                    break; // no hay más rutas válidas
                }
                rutaAReutilizar = cp.ruta();
                // si esta ruta ya fue descartada por alguna razón, ignórala
                if (rutasDescartadas.contains(rutaAReutilizar)) {
                    rutaAReutilizar = null;
                    continue;
                }
                productoAgarrado = cp.productoEscogido();
                capacidadActualRuta = cp.capacidadRutaParaMasProds();
            }

            // Antes de cada creación: re-calcular la capacidad REAL de la ruta en el estado actual
            capacidadActualRuta = capacidadActualRuta != null ? capacidadActualRuta: this.estadoGlobal.obtenerCapacidadRutaEnEstadoActualSimulada(rutaAReutilizar);
            if (capacidadActualRuta <= 0) {
                // ruta ya no tiene capacidad, descartar y buscar otra
                rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                rutasDescartadas.add(rutaAReutilizar);
                rutaAReutilizar = null;
                continue;
            }

            // Seleccionar el producto justo ahora (con la vista de estado actual)
            /*Producto*/ productoAgarrado = productoAgarrado!=null ? productoAgarrado : escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
            if (productoAgarrado == null) {
                // La ruta no tiene producto utilizable ahora -> descartarla
                rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                rutasDescartadas.add(rutaAReutilizar);
                rutaAReutilizar = null;
                continue;
            }

            // Crear UNA programacion (no un batch); iteraremos para crear más si sigue habiendo capacidad
            Programacion prograARealizar = new Programacion(idPedido, productoAgarrado.getUuid(), rutaAReutilizar);

            // Si es un producto "nuevo" regístralo (lo haces en tu flujo)
            if (!productoAgarrado.isExiste()) {
                this.estadoGlobal.anadirProducto(productoAgarrado);
            }

            // Intentar añadir a estado (esto muta vuelos, almacenes y pedido)
            try {
                this.estadoGlobal.anadirProgramacionSolucion(prograARealizar, instanteActual);
                // Si llega aquí, la programacion fue aceptada y el pedido/estado fueron actualizados.
                prograsAPersistir.add(prograARealizar);
                created++;
            } catch (RuntimeException ex) {
                // Si falla: descartamos la ruta y registramos para no volver a probarla
                lr.appendReport("Falló asignación al intentar persistir programacion: " + ex.getMessage());
                rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                rutasDescartadas.add(rutaAReutilizar);
                rutaAReutilizar = null;
                // No hacemos rollback adicional aquí porque anadirProgramacionSolucion debió
                // lanzar antes de mutar si la operación no era consistente. Si muta parcialmente,
                // debes implementar rollback explícito aquí.
                continue;
            }

            // tras asignar 1 unidad, la capacidad real de la ruta probablemente cambió;
            // recalculamos en la siguiente iteración. Si la ruta sigue con capacidad > 0, la mantengo.
            // De lo contrario la descartamos y volveremos a pedir otra ruta en la siguiente vuelta.
            int capacidadPosterior = this.estadoGlobal.obtenerCapacidadRutaEnEstadoActualSimulada(rutaAReutilizar);
            if (capacidadPosterior <= 0) {
                rutaAReutilizar = null; // forzar buscar otra en la próxima iteración
            }
        } // end while

        return prograsAPersistir;
    }


   // funca
    private List<Programacion> construirVariasPrograsYPersistir4(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido,
            int maxToCreate) {

        if (rutasFiltradasSegunPlazoPedido == null ||
                rutasFiltradasSegunPlazoPedido.isEmpty() ||
                maxToCreate <= 0)
            return Collections.emptyList();

        long idPedido = pedidoElegido.getId();
        LinkedList<Long> rutaAReutilizar = null;
        List<Programacion> prograsAPersistir = new ArrayList<>(Math.min(16, maxToCreate));
        Set<LinkedList<Long>> rutasDescartadas = new HashSet<>();

        int created = 0;

        while (created < maxToCreate){
            Producto productoAgarrado = null;

            // ✅ FIX: Siempre obtener nueva ruta si no hay reutilizable
            if (rutaAReutilizar == null) {
                ConstruccionProgramacion cp = obtenerRutaYProgramacion(
                        rutasFiltradasSegunPlazoPedido,
                        pedidoElegido
                );
                if (cp == null) {
                    break; // No hay más rutas válidas
                }

                rutaAReutilizar = cp.ruta();
                if (rutasDescartadas.contains(rutaAReutilizar)) {
                    rutaAReutilizar = null;
                    continue;
                }

                // ✅ FIX: Solo usar producto de CP en ESTA iteración
                productoAgarrado = cp.productoEscogido();
            } else {
                // ✅ FIX: Recalcular capacidad ANTES de escoger producto
                int capacidadActual = this.estadoGlobal
                        .obtenerCapacidadRutaEnEstadoActualSimulada(rutaAReutilizar);

                if (capacidadActual <= 0) {
                    // Ruta agotada, buscar nueva
                    rutasDescartadas.add(rutaAReutilizar);
                    rutaAReutilizar = null;
                    continue;
                }

                // ✅ FIX: Escoger NUEVO producto para esta iteración
                productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
                if (productoAgarrado == null) {
                    // No hay productos disponibles en esta ruta
                    rutasDescartadas.add(rutaAReutilizar);
                    rutaAReutilizar = null;
                    continue;
                }
            }

            // ✅ Validación final de capacidad
            int capacidadPrePersistencia = this.estadoGlobal
                    .obtenerCapacidadRutaEnEstadoActualSimulada(rutaAReutilizar);
            if (capacidadPrePersistencia <= 0) {
                rutasDescartadas.add(rutaAReutilizar);
                rutaAReutilizar = null;
                continue;
            }

            // Crear programación
            Programacion prograARealizar = new Programacion(
                    idPedido,
                    productoAgarrado.getUuid(),
                    rutaAReutilizar
            );

            // Registrar producto nuevo si aplica
            if (!productoAgarrado.isExiste()) {
                this.estadoGlobal.anadirProducto(productoAgarrado);
            }

            // ✅ Intentar persistir con manejo de errores

            this.estadoGlobal.anadirProgramacionSolucion(prograARealizar, instanteActual);
            prograsAPersistir.add(prograARealizar);
            created++;

            // ✅ Validar si la ruta aún tiene capacidad para próxima iteración
            int capacidadPostPersistencia = this.estadoGlobal
                    .obtenerCapacidadRutaEnEstadoActualSimulada(rutaAReutilizar);
            if (capacidadPostPersistencia <= 0) {
                rutaAReutilizar = null; // Forzar nueva ruta en próxima iteración
            }
        }

        return prograsAPersistir;
    }

    private Producto escogerProductoEnRuta(LinkedList<Long> ruta, Pedido pedido){
        // if(ruta.getFirst() == 1340L || ruta.getLast() == 1340L)
        // System.out.println("ruta a escogerle prod: "+ ruta);

        if (this.estadoGlobal.getVuelos().get(ruta.getFirst()) == null){
            lr.appendReport("Vuelo de ruta no está en estado global, debug");
        }

        Almacen almacenOrigen = this.estadoGlobal.getAlmacenes().get(
                this.estadoGlobal.getVuelos().get(ruta.getFirst()).getIdAlmacenOrigen());
        Almacen almacenDestino = this.estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        if (almacenOrigen == null)
            throw new IllegalStateException("¿Cómo llegó un almacén nulo aquí?");

        if (almacenOrigen.isEsInfinito()){ // Es un almacén no intermedio
            Producto productoNuevo;
            
            productoNuevo = new Producto(almacenOrigen.getId(), ruta, instanteActual);
//            almacenOrigen.agregarProducto(productoNuevo); // ¡¿Qué?!

            return productoNuevo;
        }

        // if(almacenOrigen.getId()==11) // COMOOOO
        // System.out.println("debug origen 11");

        // A partir de aquí, sí es un almacén intermedio. Veremos sus prods en el futuro
        // a ver cuál agarramos.
        lr.appendReport("Revisando prods intermedios para: " + almacenOrigen);
        List<Producto> productosDelOrigenEnPrimerVuelo = this.estadoGlobal
                .obtenerProductosEscogiblesAlmacenOrigenEnRuta(ruta);
        if(productosDelOrigenEnPrimerVuelo.isEmpty()){
            lr.appendReport("NO TIENE SENTIDO QUE ESTÉ VACÍO SI SE HA VALIDADO PREVIAMENTE QUE EL ALMACÉN TENDRÁ PRODS PARA LA RUTA"); // <- está llegando aquí
//            throw new IllegalStateException("NO TIENE SENTIDO QUE ESTÉ VACÍO SI SE HA VALIDADO PREVIAMENTE QUE EL ALMACÉN TENDRÁ PRODS PARA LA RUTA");
            // ^^^^ comentar para que funque SUPERFICIALMENTE.
        }
        // División entre continentales e intercontinentales
        Map<Boolean, List<Producto>> listaPartidaProds = productosDelOrigenEnPrimerVuelo.stream()
                .collect(Collectors.partitioningBy(producto -> {
                    Continente continenteOrigen = this.estadoGlobal.getAlmacenes()
                            .get(producto.getIdAlmacenInfinitoOrigen()).getContinente();
                    return continenteOrigen.equals(almacenDestino.getContinente()); // true si continental
                }));
        List<Producto> productosContinentales = listaPartidaProds.get(true);
        List<Producto> productosIntercontinentales = listaPartidaProds.get(false);

        // Si **ambas** listas están vacías (pese a la lista inicial no vacía),
        // defensiva:
        if ((productosContinentales == null || productosContinentales.isEmpty())
                && (productosIntercontinentales == null || productosIntercontinentales.isEmpty())){
            lr.appendReport(
                    "escogerProductoEnRuta: después de particionar, NO quedan productos útiles en ruta: "
                            + ruta);
            return null;
        }

        Producto productoAAgarrar;
        if (!productosIntercontinentales.isEmpty() && !productosContinentales.isEmpty()){
            Double aleatorio = generadorAleatorio.nextDouble(); // Sale de 0 a 1
            Double umbralIntercontinental = pedido.isIntercontinentalAhora() ? // asegurarse de que esto se mantenga act.
                    UMBRAL_INTERCONTINENTAL_SI_YA_LO_ERA : UMBRAL_INTERCONTINENTAL_SI_NO_LO_ERA;
            if (aleatorio < umbralIntercontinental){
                productoAAgarrar = productosIntercontinentales.get(0); // el primerito nomás,
                                                                       // cualquiera...
                // ¿o deberíamos hacerlo de forma más inteligente? (ejm: sacar de un continente
                // cercano) <- pto. de mejora
                return productoAAgarrar;
            }
            else{
                productoAAgarrar = productosContinentales.get(0);
                return productoAAgarrar;
            }
        }
        else{
            productoAAgarrar = !productosContinentales.isEmpty()
                    ? productosContinentales.get(0)
                    : productosIntercontinentales.get(0);
            return productoAAgarrar;
        }
    }

    private List<LinkedList<Long>> obtenerRutasConMismoDestinoQuePedido(Pedido pedido)
    {
        Almacen almacen = this.estadoGlobal.getAlmacenes().get(pedido.getIdAlmacenDestino());
        List<LinkedList<Vuelo>> rutasConDestinoCompartido = this.estadoGlobal
                .getAdyacencia().get(almacen);

        
        return convertirRutasAVuelosId(rutasConDestinoCompartido);
    }

    private List<LinkedList<Long>> filtrarRutasSegunPlazoPedido(Pedido pedido,
            List<LinkedList<Long>> rutasConDestinoCompartido){
        lr.appendReport("Pedido: " + pedido);

        List<LinkedList<Long>> rutas = rutasConDestinoCompartido.stream()
                .filter(ruta -> {
                    // if(debug) lr.appendReport("Ruta: " + ruta.toString());
                    // if(debug) lr.appendReport("Último vuelo: " + this.estadoGlobal
                    // .getVuelos().get(ruta.getLast()));
                    return this.estadoGlobal
                            .getVuelos().get(ruta.getLast())
                            .entregariaPedidoEnPlazoReal(pedido);
                }

                ).collect(Collectors.toList());
        return rutas; // menos eficiencia xdd pero pa que funque, porque el toList
        // de stream da listas inmutables
    }

    /*
     * Función que asigna puntajes a rutas según la siguiente formula: score = alfa1
     * * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial
     *
     * Se busca que score sea cercano a 0. En otras palabras, cuando score tiende a
     * 0 significa que la ruta es mejor valorada. Falta tunear los coeficientes
     * alfa1 y alfa2 También podría evaluar qué tan cargados estén los vuelos (esto
     * no esta ni implementado ni modelado en la ecuación). También podría evaluar
     * qué tanto espacio va a ocupar en almacenes escala (esto no esta ni
     * implementado ni modelado en la ecuación).
     *
     */
    private Map<LinkedList<Long>, Double> asignarPuntajesRutas(
            List<LinkedList<Long>> rutas,
            Instant instanteActual,
            Pedido pedido)
    {
        // lr.appendReport("me llegó para asignar puntaje: "+ rutas.size() + " rutas,
        // instante act: "+instanteActual
        // +" pedido: "+pedido);
        Double score, alfa1, alfa2, aptitudTemporal, aptitudLogística, aptitudEspacial;
        Pair<Double, Double> aptitudes;
        List<Vuelo> vuelos;
        Map<LinkedList<Long>, Double> scores = new HashMap<>();

        alfa1 = 0.5;
        alfa2 = 0.7;

        for (LinkedList<Long> ruta : rutas)
        {
            vuelos = this.estadoGlobal.obtenerVariosVuelosPorIds(ruta);
            // lr.appendReport("vuelos de ruta a asignar puntaje: "+ruta);
            aptitudTemporal = this.calcularAptitudTemporal(vuelos, instanteActual, pedido);
            aptitudes = this.calcularAptitudLogisticaYEspacial(vuelos, this.estadoGlobal);
            aptitudLogística = aptitudes.getLeft();
            aptitudEspacial = aptitudes.getRight();

            score = alfa1 * aptitudTemporal + alfa2 * aptitudLogística * aptitudEspacial;
            // lr.appendReport("score obtenido en ruta: "+score + " ruta");
            scores.put(ruta, score);
        }

        return scores;
    }

    /*
     * Calcula segun la formula: (instantePrimerVuelo - instanteActual) /
     * (instanteMaximoParaEntregar - instanteUltimoVuelo)
     *
     * Las ruta asume que el primer vuelo todavía no sale
     *
     * instantePrimerVuelo -> instante de salida del primer vuelo de la ruta
     * instanteUltimoVuelo -> instante de llegada del ultimo vuelo dela ruta
     * instanteActual -> instante en el que se solicito la planificación
     * instanteMaximoParaEntregar -> instante de entrega máximo
     *
     */
    private Double calcularAptitudTemporal(List<Vuelo> ruta, Instant instanteActual, Pedido pedido)
    {
        Double tiempoPartida, tiempoSobrante;
        Instant instantePrimerVuelo, instanteMaximoParaEntregar, instanteUltimoVuelo;

        if (ruta.get(0) == null)
        {
            lr.appendReport("No se encontrar la ruta");
        }
        instantePrimerVuelo = ruta.get(0).getInicio();
        instanteUltimoVuelo = ruta.get(ruta.size() - 1).getFin();
        instanteMaximoParaEntregar = pedido.getInstanteMaximoParaEntregar();
        tiempoPartida = Duration.between(instanteActual, instantePrimerVuelo).toMillis() / 1000.0;
        tiempoSobrante = Duration.between(instanteUltimoVuelo, instanteMaximoParaEntregar)
                .toMillis() / 1000.0;

        return (tiempoPartida) / (tiempoSobrante);
    }

    /*
     * Calcula según la formula: aptitudLogística = nVuelos / sum(sqrt(tiempoVuelo_i
     * ^ 2 + tiempoEspera ^ 2_i)) aptitudEspacial = (capacidadOcupada ) /
     * capacidadTotal
     *
     * nVuelos -> cantidad de vuelos que posee la ruta tiempoVuelo_i -> duración del
     * vuelo i-ésimo de la ruta evaluada tiempoEspera_i -> duración de la espera
     * i-ésima antes de abordar el siguiente vuelo capacidadOcupada -> capacidad
     * ocupada del almacén capacidadTotal capacidadTotal -> capacidad máxima del
     * almacén
     *
     */
    private Pair<Double, Double> calcularAptitudLogisticaYEspacial(List<Vuelo> ruta,
            EstadoGlobal estado)
    {
        Integer nVuelos;
        Double tiempoVuelo, tiempoEspera, espacioAlmacen, aptitudLogística, aptitudEspacial;
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
            espacioAlmacen = (double) almacenLlegada.getCapacidadOcupada()
                    / almacenLlegada.getCapacidadMaxima();

            if (nVuelos > 0)
            {
                vueloAnterior = ruta.get(nVuelos - 1);
                instanteSalida = vuelo.getInicio();
                instanteLlegada = vueloAnterior.getFin();
                tiempoEspera = Duration.between(instanteLlegada, instanteSalida).getSeconds()
                        / 3600.0;
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
     * Construye la RCL a partir del mapa ruta->score. Convención: score mayor =
     * PEOR. Garantiza que, para cada almacén destino no infinito (si existe alguna
     * ruta para él), al menos la mejor ruta (por score) quede incluida en la RCL
     * resultante.
     *
     * @param scores
     *            mapa ruta -> score (MENOR = mejor)
     * @return lista de rutas en la RCL (ordenada por score descendente)
     */ // DEUDA TÉCNICA, CREO QUE DA IGUAL LO DE AL MENOS UNA PARA CADA ALMACÉN
    // RCL de rutas: ahora score menor = mejor
    private List<LinkedList<Long>> construirRCLDeRutasConAlMenosUnaParaCadaAlmacen(
            Map<LinkedList<Long>, Double> scores)
    {

        if (scores == null || scores.isEmpty())
            return Collections.emptyList();

        // 0. obtener alpha (usar campo de clase o fallback)
        double alphaLocal = this.ALPHA_RUTAS;
        if (Double.isNaN(alphaLocal) || alphaLocal < 0.0 || alphaLocal > 1.0)
            alphaLocal = 0.1;

        // 1) calc min/max scores
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values())
        {
            if (v == null || v.isNaN())
                continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Double.isInfinite(min) || Double.isInfinite(max) || Double.isNaN(min)
                || Double.isNaN(max))
            return Collections.emptyList();

        // 2) Umbral: ahora score menor = mejor
        double threshold = min + alphaLocal * (max - min);

        // 3) RCL inicial por umbral (LinkedHashSet para evitar duplicados y mantener
        // determinismo)
        Set<LinkedList<Long>> rclSet = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN()
                        && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) Encontrar la mejor ruta por destino (ignorando destinos infinitos)
        Map<Long, LinkedList<Long>> bestByDestino = new HashMap<>();
        Map<Long, Double> bestScoreByDestino = new HashMap<>();

        for (Map.Entry<LinkedList<Long>, Double> e : scores.entrySet())
        {
            LinkedList<Long> ruta = e.getKey();
            Double score = e.getValue() == null || e.getValue().isNaN()
                    ? Double.POSITIVE_INFINITY
                    : e.getValue();

            if (ruta == null || ruta.isEmpty())
                continue;

            long ultimoVueloId = ruta.getLast();

            // obtener objeto vuelo
            Vuelo vueloUltimo = this.estadoGlobal.getVuelos().get(ultimoVueloId);
            if (vueloUltimo == null)
            {
                lr.appendReport(
                        "construirRCL: ruta contiene vuelo inexistente idVuelo=" + ultimoVueloId
                                + " -> se ignora ruta.");
                continue;
            }

            // obtener id almacen destino desde el vuelo y then almacen
            Long idAlmacenDestino = vueloUltimo.getIdAlmacenDestino();
            Almacen alm = this.estadoGlobal.getAlmacenes().get(idAlmacenDestino);
            if (alm == null)
            {
                lr.appendReport(
                        "construirRCL: vuelo id=" + ultimoVueloId + " apunta a almacenDestino id="
                                + idAlmacenDestino
                                + " que no existe en mesa -> se ignora ruta.");
                continue;
            }

            // ignorar destinos infinitos
            if (alm.isEsInfinito())
                continue;

            // actualizar mejor por destino: ahora menor score = mejor
            Double bestScore = bestScoreByDestino.get(idAlmacenDestino);
            if (bestScore == null || score < bestScore)
            {
                bestScoreByDestino.put(idAlmacenDestino, score);
                bestByDestino.put(idAlmacenDestino, ruta);
            }
        }

        // 5) Asegurar que la mejor ruta por destino esté en la RCL
        for (Map.Entry<Long, LinkedList<Long>> be : bestByDestino.entrySet())
        {
            LinkedList<Long> bestRuta = be.getValue();
            if (bestRuta != null)
                rclSet.add(bestRuta);
        }

        // 6) Ordenar por score ascendente (mejor primero) y devolver
        List<LinkedList<Long>> rcl = new ArrayList<>(rclSet);
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)));
        return rcl;
    }

    //
    // /**
    // * Selecciona aleatoriamente un pedido desde la RCL.
    // * @param rcl lista no vacía (puede ser vacía -> retorna null)
    // * @param scores mapa pedido->score (opcional si weighted=false)
    // * @param rng Random instance (si null, se crea una nueva)
    // * @param weighted si true selecciona ponderado por score; si false selección
    // uniforme
    // * @return pedido seleccionado o null si rcl vacío
    // */
    private Pedido seleccionarPedidoDesdeRCL(List<Pedido> rcl,
            Map<Pedido, Double> scores,
            Random rng,
            boolean weighted)
    {
        if (rcl == null || rcl.isEmpty())
            return null;
        if (rng == null)
            rng = generadorAleatorio;

        if (!weighted)
        {
            return rcl.get(rng.nextInt(rcl.size()));
        }
        else
        {
            // selección ponderada por score (aseguramos pesos positivos)
            double sum = 0.0;
            List<Double> weights = new ArrayList<>(rcl.size());
            for (Pedido p : rcl)
            {
                double s = scores == null ? 1.0 : scores.getOrDefault(p, 1.0);
                double w = Math.max(1e-6, s); // evita pesos 0
                weights.add(w);
                sum += w;
            }
            double pick = rng.nextDouble() * sum;
            double acc = 0.0;
            for (int i = 0; i < rcl.size(); i++)
            {
                acc += weights.get(i);
                if (pick <= acc)
                    return rcl.get(i);
            }
            // fallback
            return rcl.get(rcl.size() - 1);
        }
    }


    //
    // /**
    // * Construye la RCL de pedidos a partir de un mapa pedido->score.
    // * Convención: score MENOR = mejor.
    // *
    // * alpha in [0,1]. alpha = 0 => solo el mejor; alpha = 1 => todos.
    // */
    // RCL de pedidos: ahora score menor = mejor
    private List<Pedido> construirRCLDePedidos(Map<Pedido, Double> scores, double alpha)
    {
        if (scores == null || scores.isEmpty())
            return Collections.emptyList();

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double v : scores.values())
        {
            if (v == null || v.isNaN())
                continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // defensiva
        if (Double.isInfinite(min) || Double.isInfinite(max))
            return Collections.emptyList();

        // umbral: ahora score menor = mejor -> threshold parte de min hacia max
        double threshold = min + alpha * (max - min);

        List<Pedido> rcl = scores.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNaN()
                        && e.getValue() <= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // ordenar por score ascendente (mejor = menor primero)
        rcl.sort((a, b) -> Double.compare(
                scores.getOrDefault(a, Double.POSITIVE_INFINITY),
                scores.getOrDefault(b, Double.POSITIVE_INFINITY)));

        return rcl;
    }

    //
    // /**
    // * Selecciona una ruta desde la RCL.
    // * @param rcl lista de rutas candidatas (no vacía)
    // * @param scores mapa ruta->score (debe contener las rutas)
    // * @param rng Random
    // * @param weighted si true se selecciona ponderado por score; si false
    // seleccion uniforme
    // */
    private LinkedList<Long> seleccionarRutaDesdeRCL(
            List<LinkedList<Long>> rcl,
            Map<LinkedList<Long>, Double> scores,
            boolean weighted)
    {
        if (rcl == null || rcl.isEmpty())
            return null;
        Random rng = generadorAleatorio;

        if (!weighted)
        {
            return rcl.get(rng.nextInt(rcl.size()));
        }
        else
        {
            // calcular min/max entre las rutas de la RCL (defensivo)
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (LinkedList<Long> r : rcl)
            {
                double s = scores.getOrDefault(r, Double.POSITIVE_INFINITY);
                if (Double.isFinite(s))
                {
                    min = Math.min(min, s);
                    max = Math.max(max, s);
                }
            }
            boolean constant = !Double.isFinite(min) || !Double.isFinite(max) || min == max;

            // construir pesos: queremos mayor peso para menor score
            List<Double> ws = new ArrayList<>(rcl.size());
            double sum = 0.0;
            final double EPS = 1e-8;
            for (LinkedList<Long> r : rcl)
            {
                double s = scores.getOrDefault(r, Double.POSITIVE_INFINITY);
                double normalized;
                if (constant)
                {
                    normalized = 0.5; // todos iguales, peso uniforme
                }
                else
                {
                    // normalizar en [0,1] (0 = best=min, 1 = worst=max)
                    normalized = (s - min) / (max - min);
                    normalized = Math.max(0.0, Math.min(1.0, normalized));
                }
                // utilidad: invertir (best -> 1.0, worst -> 0.0)
                double util = 1.0 - normalized;
                double w = Math.max(EPS, util); // evitar ceros absolutos
                ws.add(w);
                sum += w;
            }

            double pick = rng.nextDouble() * sum;
            double acc = 0.0;
            for (int i = 0; i < rcl.size(); i++)
            {
                acc += ws.get(i);
                if (pick <= acc)
                    return rcl.get(i);
            }
            return rcl.get(rcl.size() - 1);
        }
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
 /**
     * Construye hasta 'maxToCreate' programaciones para el pedido dado, reusando
     * rutas cuando convenga. Devuelve la lista de Programacion creadas (puede ser
     * vacía si no se pudo crear ninguna).
     */
    private List<Programacion> construirVariasPrograsYPersistir2(
            List<LinkedList<Long>> rutasFiltradasSegunPlazoPedido,
            Pedido pedidoElegido,
            int maxToCreate){
        if (rutasFiltradasSegunPlazoPedido == null || rutasFiltradasSegunPlazoPedido.isEmpty()
                || maxToCreate <= 0)
            return Collections.emptyList();

        long idPedido = pedidoElegido.getId();
        LinkedList<Long> rutaAReutilizar = null;
        int capacidadDeLaRutaAReutilizar = 0;
        List<Programacion> prograsAPersistir = new ArrayList<>(Math.min(16, maxToCreate));

        int created = 0;
        // Mantener un conjunto local de rutas actualmente consideradas para evitar
        // repetir rutas ya descartadas
        Set<LinkedList<Long>> rutasDescartadas = new HashSet<>();

        while (created < maxToCreate){
            Producto productoAgarrado = null;
            // Si no hay capacidad en la ruta reutilizable, conseguir nueva ruta
            if (capacidadDeLaRutaAReutilizar <= 0 || rutaAReutilizar == null){
                // obtener nueva ruta válida (obtenerRutaYProgramacion ya remueve rutas
                // inválidas de la lista interna)
                ConstruccionProgramacion cp = obtenerRutaYProgramacion(
                        rutasFiltradasSegunPlazoPedido, pedidoElegido);
                if (cp == null){
                    // No hay más rutas válidas
                    break;
                }
                rutaAReutilizar = cp.ruta();
                capacidadDeLaRutaAReutilizar = cp.capacidadRutaParaMasProds();
                productoAgarrado = cp.productoEscogido();
                // si la ruta recién obtenida fue descartada por concurso anterior, evitar
                // volver a usarla
                if (rutasDescartadas.contains(rutaAReutilizar)){
                    rutaAReutilizar = null;
                    capacidadDeLaRutaAReutilizar = 0;
                    continue;
                }
            }
            else{
                // Reusar ruta existente: intentar escoger otro producto
                productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
                if (productoAgarrado == null){
                    // la ruta no tiene producto ya (o hubo cambio) -> descartarla y buscar otra
                    rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                    rutasDescartadas.add(rutaAReutilizar);
                    rutaAReutilizar = null;
                    capacidadDeLaRutaAReutilizar = 0;
                    continue;
                }
            }

            // Decidir cuántas programaciones usar de esta ruta: al menos 1, hasta
            // min(capacidadDeLaRutaAReutilizar, capAhora, remaining)
            int allowedFromRoute = Math.min(capacidadDeLaRutaAReutilizar,
                    Math.min(capacidadDeLaRutaAReutilizar, maxToCreate - created));
            // generamos programaciones **una por una** (porque cada adición muta
            // this.estadoGlobal y afecta siguiente disponibilidad)
            for (int i = 0; i < allowedFromRoute && created < maxToCreate; i++){
                Programacion prograARealizar = new Programacion(idPedido,
                        productoAgarrado.getUuid(), rutaAReutilizar);
                prograsAPersistir.add(prograARealizar);
                // si producto es "nuevo", registrarlo
                if (!productoAgarrado.isExiste()){
                    this.estadoGlobal.anadirProducto(productoAgarrado); //OPERACION IMPORTANTE
                }
                // reservar en el estado (esto decrementa capacidades en vuelos etc.)
                this.estadoGlobal.anadirProgramacionSolucion(prograARealizar, instanteActual); //OPERACION IMPORTANTE
                created++;
                capacidadDeLaRutaAReutilizar--; // hemos consumido una unidad de la capacidad
                                                // estimada

                // si aún queremos más y la ruta mantiene productos disponibles, preseleccionar
                // otro producto para siguiente iteración:
                if (i < allowedFromRoute - 1){
                    productoAgarrado = escogerProductoEnRuta(rutaAReutilizar, pedidoElegido);
                    if (productoAgarrado == null){
                        // la ruta ya no tiene más productos
                        rutasFiltradasSegunPlazoPedido.remove(rutaAReutilizar);
                        rutasDescartadas.add(rutaAReutilizar);
                        rutaAReutilizar = null;
                        capacidadDeLaRutaAReutilizar = 0;
                        break;
                    }
                }
            } // end for allowedFromRoute

            // si capacidadDeLaRutaAReutilizar llegó a 0, forzamos buscar otra en próximo
            // while
            if (capacidadDeLaRutaAReutilizar <= 0){
                rutaAReutilizar = null;
            }
        } // end while created < maxToCreate

        return prograsAPersistir;
    }
}
