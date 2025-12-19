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
     * Lanza una excepción con un mensaje formateado
     */
    public static void lanzarExcepcion(String metodo, String mensaje) throws Exception {
        String mensajeCompleto = "ERROR algoritmo(" + metodo + "): " + mensaje;
        Bitacora.escribir(mensajeCompleto);
        throw new IllegalStateException(mensajeCompleto);
    }

    /*
     * Preámbulo del algoritmo de planificación. Aquí se (1)inicializa el EstadoGlobal copiado, (2)se llama al generador de rutas, (3)se realiza un bucle sobre los pedidos para generarle programaciones y (4)se verifica que la solución satisfaga todos los pedidos
     */
    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada) throws Exception {
        SalidaProblemaPlanificacion solucion;
        List<Programacion> programaciones;

        try {
            
            // Inicialización
            inicializacion(entrada.getEstadoGlobal(), entrada.getInstanteActual());
            
            // Generación de rutas
            this.estadoGlobal.calcularRutas_v2(this.instanteActual);
            
            // Bucle de pedidos
            bucleSobrePedidos();
            Bitacora.escribir( "ALGORITMOOOOOO no falle");   
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Bitacora.escribir("ERROR (Bucle de pedidos): " + sw.toString());

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
    private void inicializacion(EstadoGlobal estadoOriginal, Instant instanteActual) throws Exception {
        this.estadoGlobal = estadoOriginal;
        this.instanteActual = instanteActual;
        this.estadoGlobal.inicializar(this.instanteActual);
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

        if(this.estadoGlobal.hayPedidosPendientes())
        {
            solucion.setColapsado(true);
        }

        return solucion;
    }

    /*
     * Para cada iteración, elige un pedido, sus rutas validas (que cumplen destino y plazo) y genera las programaciones que satisfacen totalmente ese pedido en un bucle for que culmina cuando todo el pedido es totalmente atendido. La iteración de pedidos culmina cuando ya no hay más pedidos pendientes o si llega la máximo de iteraciones permitido 
     */
    private void bucleSobrePedidos() throws Exception {
        int intentos;
        Pedido pedidoElegido;
        List<Programacion> nuevasProgramaciones;
        List<Pedido> pedidosPendientes;
        List<Ruta> rutasValidas;
        
        while(this.estadoGlobal.hayPedidosPendientes()) { 
            //este bucle selecciona un pedido
            pedidosPendientes = this.estadoGlobal.obtenerPedidosPendientes();
            pedidoElegido = elegirPedido_v2(pedidosPendientes);

            for(intentos = 0;
                pedidoElegido.obtenerCantidadProductosFaltantes() > 0 && intentos < MAX_INTENTOS_PROGRAMAR_PEDIDO;
                intentos++) {   //este bucle satisface una porción del pedido

                rutasValidas = this.estadoGlobal.obtenerRutasValidas(pedidoElegido); 
                nuevasProgramaciones = construirProgramaciones(rutasValidas, pedidoElegido);

                if(!nuevasProgramaciones.isEmpty()) {
                    persistirProgramaciones(nuevasProgramaciones);
                    intentos--;
                }              
            }

            if(intentos == MAX_INTENTOS_PROGRAMAR_PEDIDO) {
 
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
        
        pedidosCandidatos = construirListaRestringidaDePedidos(pedidosPendientes);
        limiteSuperior = pedidosCandidatos.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return pedidosCandidatos.get(indiceAleatorio);
    }

    /*
     * En base a los pedidos pendientes, construye una lista restringida de pedidos candidatos 
     */
    private List<Pedido> construirListaRestringidaDePedidos(List<Pedido> pedidosPendientes) {
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
     */
    private List<Programacion> construirProgramaciones(List<Ruta> rutasValidas, Pedido pedidoElegido) throws Exception {

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

        rutaYProductos = obtenerRutaYProductos(rutasValidas, demandaMaxima, esIntercontinental, instanteMaximoEntrega);

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
     */
    private RutaYProductos obtenerRutaYProductos(List<Ruta> rutasValidas, int demandaMaxima, boolean esIntercontinental, Instant instanteMaximoEntrega) throws Exception
    {
        int contador, capacidadRuta, capacidadAlmacen, cantidadProgramaciones;
        Instant instanteInicioRuta;
        Almacen almacenOrigen, almacenDestino;
        Ruta rutaElegida;
        List<Producto> productosEnAlmacen, productosElegidos;

        for(contador = 0; contador != MAX_INTENTOS_CONSTRUIR_PROGRAMACION && rutasValidas.size() != 0; contador++) {
            // primero se elige la ruta y se verifica que haya capacidad
            rutaElegida = elegirRuta(rutasValidas, instanteMaximoEntrega);
            almacenOrigen = rutaElegida.obtenerAlmacenOrigen();
            almacenDestino = rutaElegida.obtenerAlmacenDestino();
            instanteInicioRuta = rutaElegida.obtenerPrimerVuelo().getInstanteSalida();
            productosEnAlmacen =  almacenOrigen.obtenerProductos(instanteInicioRuta);
            capacidadAlmacen = almacenOrigen.isInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();

            if(capacidadAlmacen > 0) { 
                // el almacen tiene productos disponibles para programar
                capacidadRuta = this.estadoGlobal.obtenerCapacidadRuta(rutaElegida, capacidadAlmacen);

                if(capacidadRuta > 0)
                {   // la ruta tiene capacidad y el almacen suficientes productos
                    cantidadProgramaciones = Math.min(capacidadRuta, demandaMaxima);
                    productosElegidos = elegirProductos_v2(almacenOrigen, almacenDestino, esIntercontinental, productosEnAlmacen, cantidadProgramaciones);

                    return new RutaYProductos(productosElegidos, rutaElegida);
                }else
                {
                   borrarRuta(rutasValidas, rutaElegida);
                }
            }else
            { 
                borrarRutasConOrigenEn(rutasValidas, almacenOrigen);
            }
        }

        if(contador == MAX_INTENTOS_CONSTRUIR_PROGRAMACION)
        {
            lanzarExcepcion("Construccion programacion", "No se han encontrado rutas con almacenes validos");
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
     */
    private Ruta elegirRuta(List<Ruta> rutasValidas, Instant instanteMaximoEntrega)
    {
        int limiteSuperior, indiceAleatorio;
        List<Ruta> pedidosCandidatos;

        pedidosCandidatos = construirListaRestringidaDeRutas(rutasValidas, instanteMaximoEntrega);
        limiteSuperior = pedidosCandidatos.size() - 1;

        if(limiteSuperior < 0)
        {
            String mensaje = "ERROR (Elegir ruta): El RCL esta vacío";
            pedidosCandidatos = construirListaRestringidaDeRutas(rutasValidas, instanteMaximoEntrega);
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
        
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return pedidosCandidatos.get(indiceAleatorio);
    }

    /*
     * En base a las rutas validas, construye una lista restringida de rutas candidatas
     */
    private List<Ruta> construirListaRestringidaDeRutas(List<Ruta> rutasValidas, Instant instanteMaximoEntrega)
    {
        double puntaje, puntajeMaximo, puntajeMinimo, umbral;
        List<Ruta> listaRestringida;
        
        puntajeMaximo = Double.NEGATIVE_INFINITY;
        puntajeMinimo = Double.POSITIVE_INFINITY;

        for (Ruta ruta : rutasValidas)
        {
            puntaje = CalculadorDeFitness.asignarPuntajesRutas(ruta, this.instanteActual, instanteMaximoEntrega, this.estadoGlobal);
            ruta.setPuntaje(puntaje);
            puntajeMaximo = Math.max(puntajeMaximo, puntaje);
            puntajeMinimo = Math.min(puntajeMinimo, puntaje);
        }

        umbral = puntajeMinimo + UMBRAL_RCL_RUTAS * (puntajeMaximo - puntajeMinimo);
        listaRestringida = rutasValidas.stream()
                .filter(ruta -> ruta.getPuntaje() <= umbral)
                .collect(Collectors.toList());

        listaRestringida = agregarRutasPorOrigen(listaRestringida, rutasValidas);

        return listaRestringida;
    }

    /*
     * Se asegura que existan rutas con al menos una ruta para cada origen. No tiene sentido hacerlo para destinos porque las rutas validas solo consideran el destino final
     */
    private List<Ruta> agregarRutasPorOrigen(List<Ruta> listaRestringida, List<Ruta> rutasValidas)
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
            almacenOrigen = ruta.obtenerAlmacenOrigen();
            puntaje = ruta.getPuntaje();
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
        listaRestringida.sort(Comparator.comparing(Ruta::getPuntaje));

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
     * Guardar en el estadoGlobal las programaciones que ha construido en  construirProgramaciones. Esta operacion es delicada. Las programaciones que llegna a esta función tienen la caracteristica de que comparten la ruta. Cada programación corresponde a un producto.
     *
     * Un vuelo tiene un almacenSalida (origen) y un almacenEntrada (llegada).
     * El pedido tiene un almacenDestino, que es el almacenEntrada del ultimo vuelo
     * 
     * Se registra el inventario en cada vuelo de la ruta
     * Se registra la salida en el almacenSalida de cada vuelo
     * Se registra la entrada en el almacenDestino de cada vuelo
     * Se registra el recojo de los productos en el almacen destino del pedido (el almacenDestino registra cambios a las 2h aquí)
     * Se registran las programaciones y los productos en el estado global
     */
    private void persistirProgramaciones(List<Programacion> nuevasProgramaciones) {
        boolean valido;
        int nProgramaciones;
        Ruta ruta;
        Pedido pedido;
        Almacen almacenSalida, almacenEntrada;
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
        valido = this.estadoGlobal.registrarNuevosProgramacionesYProductos(ruta, productos, nuevasProgramaciones, this.instanteActual);

        if(!valido) {
            String mensaje = "ERROR (Persitir programaciones): No se puede marcar el recojo de los productos";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }

        // registro de los productos al pedido
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
