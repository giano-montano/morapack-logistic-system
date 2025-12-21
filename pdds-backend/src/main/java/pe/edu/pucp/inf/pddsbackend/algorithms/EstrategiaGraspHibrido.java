package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.*;
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
            this.estadoGlobal.calcularRutas(this.instanteActual);
            
            // Bucle de pedidos
            bucleSobrePedidos();
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
        int totalProgramacionesCreadas = 0;
        int numeroPedido = 0;
        Pedido pedidoElegido;
        List<Programacion> nuevasProgramaciones;
        List<Pedido> pedidosPendientes;
        List<Ruta> rutasValidas;
        


        while(this.estadoGlobal.hayPedidosPendientes()) { 
            //este bucle selecciona un pedido
            pedidosPendientes = this.estadoGlobal.obtenerPedidosPendientes();
            pedidoElegido = elegirPedido(pedidosPendientes);
numeroPedido++;
            
            for(intentos = 0;
                pedidoElegido.obtenerCantidadProgramacionesFaltantes() > 0 && intentos < MAX_INTENTOS_PROGRAMAR_PEDIDO;
                intentos++) {
                //este bucle satisface una porción del pedido

                rutasValidas = this.estadoGlobal.obtenerRutasValidas(pedidoElegido); 
                nuevasProgramaciones = construirProgramaciones(rutasValidas, pedidoElegido);
Bitacora.escribir("\n \n Añadiendo "+ PrettyPrinter.printList( nuevasProgramaciones.stream()
        .map(programacion -> "\n"+  programacion.toStringConRutaDetallada()).toList() )
        + " al pedido: "+pedidoElegido);

                if(!nuevasProgramaciones.isEmpty()) {

                    persistirProgramaciones(nuevasProgramaciones);
totalProgramacionesCreadas += nuevasProgramaciones.size();

                    intentos--;
                }              
            }

            if(intentos == MAX_INTENTOS_PROGRAMAR_PEDIDO) {
                lanzarExcepcion("bucle de pedidos", "No se han podido programar todas las demandas del pedido ID=" + pedidoElegido.getId());
            }
        }
        
        Bitacora.escribir("\n═══════════════════════════════════════════════════════════════");
        Bitacora.escribir("FIN BUCLE DE PEDIDOS");
        Bitacora.escribir("Total de pedidos procesados: %d", numeroPedido);
        Bitacora.escribir("Total de programaciones creadas: %d", totalProgramacionesCreadas);
        Bitacora.escribir("Programaciones en estado global: %d", this.estadoGlobal.getProgramaciones().size());
        Bitacora.escribir("═══════════════════════════════════════════════════════════════");
    }

    /*
     * En base a los pedidos pendientes, selecciona un pedido aleatoriamente
     */
    private Pedido elegirPedido(List<Pedido> pedidosPendientes)
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
     * Elige una sola ruta para satisfacer el pedido.
     * Dependiendo del origen puede tomar los productos existentes o crear nuevos productos.
     * El numero maximo de programaciones será la demanda del pedido, la capacidad de la ruta o los productos existentes del almacén de origen de la ruta
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
  
        demandaMaxima = pedidoElegido.obtenerCantidadProgramacionesFaltantes();
        esIntercontinental = pedidoElegido.obtenerSiPedidoEsIntercontinental();
        instanteMaximoEntrega = pedidoElegido.getInstanteLimite();

        rutaYProductos = obtenerRutaYProductos(rutasValidas, demandaMaxima, esIntercontinental, instanteMaximoEntrega);

        nuevasProgramaciones = new ArrayList<>();
        productosElegidos = rutaYProductos.productosElegidos();
        rutaElegida = rutaYProductos.rutaElegida();

        for(Producto producto : productosElegidos) {
            programacion = new Programacion(pedidoElegido, producto, rutaElegida);
            nuevasProgramaciones.add(programacion);
        }
               
        return nuevasProgramaciones;
    }

    /*
     * Dado una lista de rutasValidas y un pedido (los parametros demandaMaxima y esIntercontinental le pertenecen al pedidoElegido)
     * intentar asignarle una ruta y productos.
     * Primero verifica que la ruta tenga capacidad y luego verifica que hayan productos en ese almacen para retornar esos valores.
     * Aqui no se persiste. DemandaMaxima nunca va a ser 0, o al menos eso se espera
     */
    private RutaYProductos obtenerRutaYProductos(
            List<Ruta> rutasValidas,
            int demandaMaxima,
            boolean esIntercontinental,
            Instant instanteMaximoEntrega) throws Exception {
        int contador, capacidadRuta, capacidadAlmacen, cantidadProgramaciones;
        Instant instanteInicioRuta;
        Almacen almacenOrigen, almacenDestino;
        Ruta rutaElegida;
        List<Producto> productosEnAlmacen, productosElegidos;


Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this.estadoGlobal, rutasValidas, "Inicio obtenerRutaYProductos");

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

                if(capacidadRuta > 0) {
                    // la ruta tiene capacidad y el almacen suficientes productos
                    cantidadProgramaciones = Math.min(capacidadRuta, demandaMaxima);
                    productosElegidos = elegirProductos(almacenOrigen, almacenDestino, esIntercontinental, productosEnAlmacen, cantidadProgramaciones);

                    return new RutaYProductos(productosElegidos, rutaElegida);
                }else{
                   borrarRuta(rutasValidas, rutaElegida);
Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this.estadoGlobal, rutasValidas, "Borrar ruta");
                }
            }else{ 
                borrarRutasConOrigenEn(rutasValidas, almacenOrigen);
Testeador.verificarRutasConAlmacenInfinitoComoOrigenTEST(this.estadoGlobal, rutasValidas, "Borrar rutas con origen en almacen"  );
            }
        }

        if(contador == MAX_INTENTOS_CONSTRUIR_PROGRAMACION) {
            lanzarExcepcion("Construccion programacion", "No se han encontrado rutas con almacenes validos");
        }

        if(rutasValidas.size() == 0) {
            lanzarExcepcion("Elegir ruta", "Las rutas validas estan vacias");
        }
    
        return new RutaYProductos(new ArrayList<>(), new Ruta(new LinkedList<>())); // constructor vacío deja todo en null y vacíos
    }

    /*
     * En base a las rutas validas disponibles (osea, rutas que cumplen el plazo),selecciona una aleatoriamente
     */
    private Ruta elegirRuta(List<Ruta> rutasValidas, Instant instanteMaximoEntrega) throws Exception
    {
        int limiteSuperior, indiceAleatorio;
        List<Ruta> rutasCandidatas;

        rutasCandidatas = construirListaRestringidaDeRutas(rutasValidas, instanteMaximoEntrega);
/*/
// Contar rutas por tipo de origen
long rutasDesdeInfinito = rutasCandidatas.stream()
        .filter(ruta -> ruta.obtenerAlmacenOrigen().isInfinito())
        .count();
long rutasDesdeNoInfinito = rutasCandidatas.size() - rutasDesdeInfinito;

Bitacora.escribir("RCL de rutas: Total=%d | Desde Infinito=%d | Desde No-Infinito=%d", rutasCandidatas.size(), rutasDesdeInfinito, rutasDesdeNoInfinito);
*/
        limiteSuperior = rutasCandidatas.size() - 1;

        if(limiteSuperior < 0)
        {
            lanzarExcepcion("Elegir ruta", "El RCL esta vacío");
        }
        
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return rutasCandidatas.get(indiceAleatorio);
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
     */
    private List<Producto> elegirProductos(Almacen almacenOrigen, Almacen almacenDestino, boolean esIntercontinental, List<Producto> productosEnAlmacen, int cantidadProductos)
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
    private void borrarRutasConOrigenEn(List<Ruta> rutasValidas, Almacen almacenInsuficiente) throws Exception
    {
        if(almacenInsuficiente.isInfinito())
        {
            lanzarExcepcion("Elegir ruta", "Se estan borrando rutas con origen en almacenes infinitos");
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
    private void persistirProgramaciones(List<Programacion> nuevasProgramaciones) throws Exception {
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

// ============ BITÁCORA DETALLADA DE PROGRAMACIONES A PERSISTIR ============
/* 
Bitacora.escribir("╔═══════════════════════════════════════════════════════════════════════════════╗");
Bitacora.escribir("║ PERSISTIR PROGRAMACIONES - Inicio del bucle de vuelos                        ║");
Bitacora.escribir("╠═══════════════════════════════════════════════════════════════════════════════╣");
Bitacora.escribir("║ Número de programaciones: %d", nProgramaciones);
Bitacora.escribir("║ Pedido ID: %d", pedido.getId());
Bitacora.escribir("║ Pedido destino: %s (Almacén ID=%d)", 
        pedido.getAlmacenDestino().getNombreCiudad(), 
        pedido.getAlmacenDestino().getId());
Bitacora.escribir("║ Cantidad solicitada: %d", pedido.getCantidadProductos());
Bitacora.escribir("║ Cantidad programada faltantes: %d", pedido.obtenerCantidadProgramacionesFaltantes());
Bitacora.escribir("║ Cantidad prods entregados: %d", pedido.obtenerCantidadProductosEntregados());
Bitacora.escribir("╠═══════════════════════════════════════════════════════════════════════════════╣");
Bitacora.escribir("║ RUTA - %d vuelos:", ruta.getVuelos().size());
int numVuelo = 0;
for(Vuelo v : ruta.getVuelos()) {
    numVuelo++;
    Bitacora.escribir("║   Vuelo %d: ID=%s | %s (%d) → %s (%d) | Salida: %s | Llegada: %s | Cap: %d/%d",
            numVuelo,
            v.getId(),
            v.getAlmacenSalida().getCodigoCiudadEn4Letras(),
            v.getAlmacenSalida().getId(),
            v.getAlmacenDestino().getCodigoCiudadEn4Letras(),
            v.getAlmacenDestino().getId(),
            v.getInstanteSalida(),
            v.getInstanteLlegada(),
            v.getInventario().size(),
            v.getCapacidad());
}
Bitacora.escribir("╠═══════════════════════════════════════════════════════════════════════════════╣");
Bitacora.escribir("║ PRODUCTOS - %d productos a programar:", productos.size());
int numProd = 0;
for(Producto p : productos) {
    numProd++;
    Bitacora.escribir("║   Producto %d: ID=%s | Tipo=%s | Origen=%s (ID=%d)",
            numProd,
            p.getId().toString().substring(0, 8),
            p.validarIncancelable_B() ? "B-Incancelable" : 
                (p.validarPlanificadoExistente_D() ? "D-PlanifExist" :
                (p.validarPlanificadoNoExistente_C() ? "C-PlanifNoExist" : "A-NoPlanif")),
            p.getAlmacenOrigen().getCodigoCiudadEn4Letras(),
            p.getAlmacenOrigen().getId());
}
Bitacora.escribir("╠═══════════════════════════════════════════════════════════════════════════════╣");
Bitacora.escribir("║ Ahora se procesarán los vuelos de la ruta...                                 ║");
Bitacora.escribir("╚═══════════════════════════════════════════════════════════════════════════════╝");
*/
// ============================================================================

        for(Vuelo vuelo : ruta.getVuelos()) {
            // registro de los cambios de salida en el almacen            
            almacenSalida = vuelo.getAlmacenSalida();
//Bitacora.escribir("Almacen salida:\n%s", almacenSalida.impresionDebug());
            valido = almacenSalida.registrarSalida(vuelo.getInstanteSalida(), nProgramaciones);

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
            valido = almacenEntrada.registrarEntrada(vuelo.getInstanteLlegada(), nProgramaciones);

            if(!valido) {
                lanzarExcepcion("Persistir programaciones", "Registro ilegal en almacen de llegada de un vuelo de la ruta de las programaciones");
            }
        }

        //registro de salida de los productos por recojo y persistir en estado global
        valido = this.estadoGlobal.registrarNuevosProgramacionesYProductos(ruta, productos, nuevasProgramaciones, this.instanteActual);

        if(!valido) {
            lanzarExcepcion("Persitir programaciones", "No se puede marcar el recojo de los productos");
        }

        // registro de los productos al pedido
        valido = pedido.registrarProductoProgramado(productos);

        if(!valido) {
            lanzarExcepcion("Persitir programaciones", "Se excedería la capacidad del pedido");
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
