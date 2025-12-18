package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.GeneradorAleatorio;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.PrettyPrinter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Testeador;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.*;

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
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) throws Exception {
        SalidaProblemaPlanificacion solucion;
        List<Programacion> programaciones;

        try {
            // Inicialización
            inicializacion(entrada.getEstadoGlobalCopia(), entrada.getInstanteActual());

            // Generación de rutas
            this.estadoGlobal.calcularRutas_v2(this.instanteActual);
//Testeador.generacionRutasTest(this.estadoGlobal, this.instanteActual);

            // Bucle de pedidos
            bucleSobrePedidos_v2();    
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Bitacora.escribir("ERROR (Bucle de pedidos): " + sw.toString());

            programaciones = this.estadoGlobal.getProgramaciones();
            solucion = new SalidaProblemaPlanificacion(programaciones, e.toString());

            lr.appendReport(Arrays.toString(e.getStackTrace()));
            lr.appendReport(e.toString());
            lr.writeReportFile("Reporte-GRASP-error-" + this.estadoGlobal.getProgramaciones().size() + "-");


            return solucion;
        }
        
        // Verificación de solución completa
        solucion = verificarSolucion();

        lr.writeReportFile("Reporte-GRASP-" + this.estadoGlobal.getProgramaciones().size() + "-");

        return solucion;
    }

    /*
     * Se llama a la función inicializar del EstadoGlobal que registra los cambios en los almacenes
     */
    private void inicializacion(EstadoGlobal estadoOriginal, Instant instanteActual) {
        this.estadoGlobal = estadoOriginal;
        this.estadoGlobal.setLr(lr); // Esto es la salvación.
        this.instanteActual = instanteActual;
        this.estadoGlobal.inicializar_v2(this.instanteActual);
//Testeador.cantidadProductosConsistenteTest(this.estadoGlobal);
//Testeador.cantidadDeProgramacionesPlanificadasTest(this.estadoGlobal);
//Testeador.verificarCambiosAlmacenes(this.estadoGlobal, this.instanteActual);

//lr.appendReport("VUELOS: \n" + PrettyPrinter.printList( this.estadoGlobal.getVuelos().values().stream().toList() ));
    }

    /*
     * Verifica que una respuesta del algoritmo satisfaga todos los pedidos
     */
    private SalidaProblemaPlanificacion verificarSolucion() {
        SalidaProblemaPlanificacion solucion;
        Map<UUID, Producto> productos;
        List<Programacion> programaciones;

        programaciones = this.estadoGlobal.getProgramaciones();
        productos = this.estadoGlobal.getProductos();
        solucion = new SalidaProblemaPlanificacion(programaciones, productos);

        if(this.estadoGlobal.hayPedidosPendientes_v2())
        {
            solucion.setColapsado(true);
        }

        return solucion;
    }

    /*
     * Para cada iteración, elige un pedido, sus rutas validas (que cumplen destino y plazo) y genera las programaciones que satisfacen totalmente ese pedido en un bucle for que culmina cuando todo el pedido es totalmente atendido. La iteración de pedidos culmina cuando ya no hay más pedidos pendientes o si llega la máximo de iteraciones permitido 
     * 
     *
     * Remplazo de realizarCicloDePedidos y realizarCicloVariosProductosDePedido
     */
    private void bucleSobrePedidos_v2() {
        int intentos;
        Pedido pedidoElegido;
        List<Programacion> nuevasProgramaciones;
        List<Pedido> pedidosPendientes;
        List<Ruta> rutasValidas;
        
        while(this.estadoGlobal.hayPedidosPendientes_v2()) {   //este bucle selecciona un pedido
            pedidosPendientes = this.estadoGlobal.obtenerPedidosPendientes_v2();
            pedidoElegido = elegirPedido_v2(pedidosPendientes);

            for(intentos = 0;
                pedidoElegido.obtenerCantidadProductosFaltantes() > 0 && intentos < MAX_INTENTOS_PROGRAMAR_PEDIDO;
                intentos++) {   //este bucle satisface una porción del pedido

                rutasValidas = this.estadoGlobal.obtenerRutasValidas_v2(pedidoElegido); 
Testeador.verificarRutasConAlmacenInfinitoComoOrigen(this.estadoGlobal, rutasValidas);
                nuevasProgramaciones = construirProgramaciones_v2(rutasValidas, pedidoElegido);

                if(!nuevasProgramaciones.isEmpty()) {
                    persistirProgramaciones_v2(nuevasProgramaciones);
                    intentos--;
                }
//Bitacora.escribir("INTENTO TERMINADO");                
            }

            if(intentos == MAX_INTENTOS_PROGRAMAR_PEDIDO) {
                String mensaje = "ERROR (Bucle de pedidos): No se pudo programar un pedido, se alcanzó el número máximo de intentos";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
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
    private List<Pedido> construirListaRestringidaDePedidos_v2(List<Pedido> pedidosPendientes) {
        double puntaje, puntajeMaximo, puntajeMinimo, umbral;
        List<Pedido> listaRestringida;
        
        puntajeMaximo = Double.NEGATIVE_INFINITY;
        puntajeMinimo = Double.POSITIVE_INFINITY;

        for (Pedido pedido : pedidosPendientes) {
            puntaje = pedido.getPuntaje();

            puntajeMaximo = Math.max(puntajeMaximo, puntaje);
            puntajeMinimo = Math.min(puntajeMinimo, puntaje);
        }

        umbral = puntajeMinimo + UMBRAL_RCL_PEDIDOS * (puntajeMaximo - puntajeMinimo);
        listaRestringida = pedidosPendientes.stream()
                .filter(pedido -> {
                    if (Double.isNaN(pedido.getPuntaje()) || pedido.getPuntaje() == 0.0) {
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
    private List<Programacion> construirProgramaciones_v2(
            List<Ruta> rutasValidas,
            Pedido pedidoElegido) {

        boolean esIntercontinental;
        int demandaMaxima;
        Programacion programacion;
        Instant instanteMaximoEntrega;
        Ruta rutaElegida;
        RutaYProductos rutaYProductos;
        List<Producto> productosElegidos;
        List<Programacion> nuevasProgramaciones;
  
        demandaMaxima = pedidoElegido.obtenerCantidadProductosFaltantes();
        esIntercontinental = pedidoElegido.obtenerSiPedidoEsIntercontinental();
        instanteMaximoEntrega = pedidoElegido.getInstanteLimite();

        rutaYProductos = obtenerRutaYProductos_v2(rutasValidas, demandaMaxima, esIntercontinental, instanteMaximoEntrega);

        nuevasProgramaciones = new ArrayList<>();
        productosElegidos = rutaYProductos.productosElegidos();
        rutaElegida = rutaYProductos.rutaElegida();
        Ruta rutaOficial = new Ruta(rutaElegida);

        for(Producto producto : productosElegidos) {
            programacion = new Programacion(pedidoElegido, producto, rutaOficial);
            nuevasProgramaciones.add(programacion);
        }
               
        return nuevasProgramaciones;
    }

    /*
     * Dado una lista de rutasValidas y un pedido (los parametros demandaMaxima y esIntercontinental le pertenecen al pedidoElegido) intentar asignarle una ruta y productos. Primero verifica que la ruta tenga capacidad y luego verifica que hayan productos en ese almacen para retornar esos valores. Aqui no se persiste. DemandaMaxima nunca va a ser 0, o al menos eso se espera
     *
     * Remplazo de obtenerRutaYProgramacion
     */
    private RutaYProductos obtenerRutaYProductos_v2(List<Ruta> rutasValidas, int demandaMaxima, boolean esIntercontinental, Instant instanteMaximoEntrega)
    {
        int contador, capacidadRuta, capacidadAlmacen, cantidadProgramaciones;
        Instant instanteInicioRuta;
        Almacen almacenOrigen, almacenDestino;
        Ruta rutaElegida;
        List<Producto> productosEnAlmacen, productosElegidos;

int a = 0, b = 0;

//Testeador.verificarRutasConAlmacenInfinitoComoOrigen(this.estadoGlobal, rutasValidas);
        for(contador = 0; contador != MAX_INTENTOS_CONSTRUIR_PROGRAMACION && rutasValidas.size() != 0; contador++)
        {   // primero se elige la ruta y se verifica que haya capacidad
            rutaElegida = elegirRuta_v2(rutasValidas, instanteMaximoEntrega);
            almacenOrigen = this.estadoGlobal.origenRuta(rutaElegida);
            almacenDestino = this.estadoGlobal.destinoRuta(rutaElegida);
            instanteInicioRuta = rutaElegida.obtenerPrimerVuelo().getInstanteSalida();
            productosEnAlmacen = this.estadoGlobal.obtenerProductosDisponibles_v2(almacenOrigen, instanteInicioRuta);
            capacidadAlmacen = almacenOrigen.isInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();

            if(capacidadAlmacen > 0)
            {   // el almacen tiene productos disponibles para programar
                capacidadRuta = this.estadoGlobal.obtenerCapacidadRuta_v2(rutaElegida, capacidadAlmacen);

                if(capacidadRuta > 0)
                {   // la ruta tiene capacidad y el almacen suficientes productos
                    cantidadProgramaciones = Math.min(capacidadRuta, demandaMaxima);
                    productosElegidos = elegirProductos_v2(almacenOrigen, almacenDestino, esIntercontinental, productosEnAlmacen, cantidadProgramaciones);

                    return new RutaYProductos(productosElegidos, rutaElegida);
                }else
                {   // la ruta no tiene capacidad
b++;
//Bitacora.escribir(rutaElegida, "Ruta eliminada" );
                   borrarRuta(rutasValidas, rutaElegida);
                }
            }else
            {   // no hay productos en ese almacen, se tiene que borrar las rutas en las que sea origen
//Bitacora.escribir( "Rutas eliminadas con origen en " + almacenOrigen.getCodigoCiudadEn4Letras());
a++;
                borrarRutasConOrigenEn(rutasValidas, almacenOrigen);
            }
        }
//Bitacora.escribir("?????????????????????????");
        if(contador == MAX_INTENTOS_CONSTRUIR_PROGRAMACION)
        {
            String xd = "borrarRutasConOrigenEn: " + a + "borrarRuta: " + b;
            Bitacora.escribir("ERROR (Construccion programacion): No se han encontrado rutas con almacenes validos " + xd);
        }

        if(rutasValidas.size() == 0)
        {
            String mensaje = "ERROR (Elegir ruta): Las rutas validas estan vacias";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje); 
        }
    
        return new RutaYProductos(new ArrayList<>(), new Ruta(new LinkedList<>())); // constructor vacío deja todo en null y vacíos
    }

    /*
     * En base a las rutas validas disponibles (osea, rutas que cumplen el plazo),selecciona una aleatoriamente
     *
     * Remplazo de seleccionarRutaDesdeRCL
     */
    private Ruta elegirRuta_v2(List<Ruta> rutasValidas, Instant instanteMaximoEntrega)
    {
        int limiteSuperior, indiceAleatorio;
        List<Ruta> pedidosCandidatos;

        pedidosCandidatos = construirListaRestringidaDeRutas_v2(rutasValidas, instanteMaximoEntrega);
        limiteSuperior = pedidosCandidatos.size() - 1;

        if(limiteSuperior < 0)
        {
            String mensaje = "ERROR (Elegir ruta): El RCL esta vacío";
            pedidosCandidatos = construirListaRestringidaDeRutas_v2(rutasValidas, instanteMaximoEntrega);
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
        
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return pedidosCandidatos.get(indiceAleatorio);
    }

    /*
     * En base a las rutas validas, construye una lista restringida de rutas candidatas
     *
     * Remplazo de construirRCLDeRutasConAlMenosUnaParaCadaAlmacen
     */
    private List<Ruta> construirListaRestringidaDeRutas_v2(List<Ruta> rutasValidas, Instant instanteMaximoEntrega)
    {
        double puntaje, puntajeMaximo, puntajeMinimo, umbral;
        List<Ruta> listaRestringida;
        Map<Ruta, Double> puntajes;
        
        puntajeMaximo = Double.NEGATIVE_INFINITY;
        puntajeMinimo = Double.POSITIVE_INFINITY;
        puntajes = new HashMap<>();

        for (Ruta ruta : rutasValidas)
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
    private List<Ruta> agregarRutasPorOrigen(List<Ruta> listaRestringida, List<Ruta> rutasValidas, Map<Ruta,Double> puntajes)
    {
        Almacen almacenOrigen;
        Double mejorPuntaje, puntaje;
        Set<Ruta> listaRestringidaUnica;
        Map<Almacen, Ruta> mejorRutaPorAlmacen;
        Map<Almacen, Double> puntajeMejorRutaPorAlmacen;

        mejorRutaPorAlmacen = new HashMap<>();
        puntajeMejorRutaPorAlmacen = new HashMap<>();
        listaRestringidaUnica = new LinkedHashSet<>(listaRestringida);

        for (Ruta ruta : rutasValidas)
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

        for (Ruta mejorRuta : mejorRutaPorAlmacen.values())
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

        if(almacenOrigen.isInfinito())
        {
            for(int i = 0; i != cantidadProductos; i++)
            {
                productoNuevo = new Producto(almacenOrigen);
 
                productosElegidos.add(productoNuevo);
            }
        }else{ 
            productosContinentales = productosEnAlmacen.stream()
                    .filter(producto -> {
                        Almacen origen = producto.getAlmacenOrigen();
                        return origen.getContinente().equals(almacenDestino.getContinente());
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            productosIntercontinentales = productosEnAlmacen.stream()
                    .filter(producto -> {
                        Almacen origen = producto.getAlmacenOrigen();
                        return !origen.getContinente().equals(almacenDestino.getContinente());
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            for (int i = 0; i < cantidadProductos; i++)
            {
                if (!productosContinentales.isEmpty() && !productosIntercontinentales.isEmpty())
                {   //hay productos disponibles en ambas listas
                    probabilidadAleatoria = GeneradorAleatorio.decimal();
                    umbral = esIntercontinental ? Hiperparametros.UMBRAL_INTERCONTI_SI_LO_ERA : Hiperparametros.UMBRAL_INTERCONTI_SI_NO_LO_ERA;

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
    private void borrarRutasConOrigenEn(List<Ruta> rutasValidas, Almacen almacenInsuficiente)
    {
        if(almacenInsuficiente.isInfinito())
        {
            String mensaje = "ERROR (Elegir ruta): Se estan borrando rutas con origen en almacenes infinitos";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje); 
        }

        rutasValidas.removeIf(ruta -> ruta.obtenerPrimerVuelo().getAlmacenSalida().getId() == almacenInsuficiente.getId());
    }

    /*
     * Elimina una ruta especifica de las rutas validas
     */
    private void borrarRuta(List<Ruta> rutasValidas, Ruta rutaSinCapacidad)
    {
        rutasValidas.removeIf(ruta -> ruta.equals(rutaSinCapacidad));
    }

    /*
     * Guardar en el estadoGlobal las programaciones que ha construido en  construirProgramaciones_v2. Esta operacion es delicada. Las programaciones que llegna a esta función tienen la caracteristica de que comparten la ruta. Cada programación corresponde a un producto.
     *
     * Un vuelo tiene un almacenSalida (origen) y un almacenEntrada (llegada).
     * El pedido tiene un almacenDestino, que es el almacenEntrada del ultimo vuelo
     */
    private void persistirProgramaciones_v2(List<Programacion> nuevasProgramaciones) {
        boolean valido, intercontinental;
        int nProgramaciones;
        Ruta ruta;
        Pedido pedido;
        Almacen almacenSalida, almacenEntrada, almacenDestino;
        List<Producto> productos;

        nProgramaciones = nuevasProgramaciones.size();
        ruta = nuevasProgramaciones.get(0).getRuta();
        pedido = nuevasProgramaciones.get(0).getPedido();

        productos = nuevasProgramaciones.stream()
                .map(Programacion::getProducto)
                .collect(Collectors.toList()); 

        for(Vuelo vuelo : ruta.getVuelos()) {
            // registro de los cambios de salida en el almacen
            almacenSalida = vuelo.getAlmacenSalida();
            valido = almacenSalida.registrarSalida(vuelo.getInstanteSalida(), nProgramaciones);

            if(!valido && !almacenSalida.isInfinito()) {
                String mensaje = "ERROR (Persitir programaciones): Registro ilegal en almacen de llegada de un vuelo de la ruta de las programaciones";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }

            // registro del inventario del vuelo
            valido = vuelo.registrarProducto(productos);

            if(!valido) {
                String mensaje = "ERROR (Persitir programaciones): Inventario de vuelo desbordado";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }

            // registro de los cambios de entrada del almacen
            almacenEntrada = vuelo.getAlmacenDestino();
            valido = almacenEntrada.registrarEntrada(vuelo.getInstanteLlegada(), nProgramaciones);

            if(!valido) {
                String mensaje = "ERROR (Persitir programaciones): Registro ilegal en almacen de llegada de un vuelo de la ruta de las programaciones";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }
        }

        //registro de salida de los productos por recojo y persistir en estado global
        valido = this.estadoGlobal.registrarNuevosProgramacionesYProductos_v2(ruta, productos, nuevasProgramaciones, this.instanteActual);

        if(!valido) {
            String mensaje = "ERROR (Persitir programaciones): No se puede marcar el recojo de los productos";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }

        // registro de los productos al pedido
        intercontinental = verificarIntercontinental(pedido, productos);
        valido = pedido.registrarProductoProgramado(productos);

        if(!valido) {
            String mensaje = "ERROR (Persitir programaciones): Registro ilegal de productos en el pedido";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
    }

    /*
     * Recorre todos los productos, obtiene su almacen origen y verifica la intecontinentalidad del pedido
     */
    private boolean verificarIntercontinental(Pedido pedido, List<Producto> productos)
    {
        Almacen almacenOrigen, almacenDestino;
        Continente continenteOrigen, continenteDestino; 
        
        almacenDestino = pedido.getAlmacenDestino();
        continenteDestino = almacenDestino.getContinente();

        for(Producto producto : productos){
            almacenOrigen = producto.getAlmacenOrigen();
            continenteOrigen = almacenOrigen.getContinente();    

            if(!continenteDestino.equals(continenteOrigen)) {
                return true;
            }
        }

        return false;
    }
}
