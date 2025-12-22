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
import java.time.Duration;
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
Testeador.verificarConsistenciasEnCambiosTEST(this.estadoGlobal, "Finalizar bucle de pedidos");

// Imprimir todos los cambios de todos los almacenes
Bitacora.escribir("\n═══════════════════════════════════════════════════════════════");
Bitacora.escribir("CAMBIOS EN TODOS LOS ALMACENES - Después del bucle de pedidos");
Bitacora.escribir("═══════════════════════════════════════════════════════════════");
Map<Long, Almacen> almacenes = this.estadoGlobal.getAlmacenes();
for (Almacen almacen : almacenes.values()) {
    String infoAlmacen = String.format("Almacén ID=%d - %s - %s", 
        almacen.getId(), 
        almacen.getNombreCiudad(),
        almacen.isInfinito() ? "INFINITO" : "Capacidad: " + almacen.getCapacidad());
    Bitacora.escribir("\n%s", infoAlmacen);
    
    if (almacen.getCambios().isEmpty()) {
        Bitacora.escribir("  (Sin cambios registrados)");
    } else {
        Bitacora.escribir("  Cambios:");
        almacen.getCambios().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                Bitacora.escribir("    %s → %+d productos", entry.getKey(), entry.getValue());
            });
    }
}
Bitacora.escribir("═══════════════════════════════════════════════════════════════\n");
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
//Bitacora.escribir("\n \n Añadiendo "+ PrettyPrinter.printList( nuevasProgramaciones.stream().map(programacion -> "\n"+  programacion.toStringConRutaDetallada()).toList() )+ " al pedido: "+pedidoElegido);

                if(!nuevasProgramaciones.isEmpty()) {

                    persistirProgramaciones(nuevasProgramaciones);
totalProgramacionesCreadas += nuevasProgramaciones.size();
Testeador.verificarConsistenciasEnCambiosTEST(this.estadoGlobal, "PERSISTIR");
                    intentos--;
                } else{
//                    if( intentos + 1 == MAX_INTENTOS_PROGRAMAR_PEDIDO){ // si en la siguiente iteración ya va a morir...
//                    }
                    // Si el método estándar falla, usar A*
                    Bitacora.escribir("\n⚠ Método estándar no encontró rutas. Activando búsqueda A*...");
                    nuevasProgramaciones = buscarRutaConAEstrella(pedidoElegido);

                    if (!nuevasProgramaciones.isEmpty()) {
                        persistirProgramaciones(nuevasProgramaciones);
                        totalProgramacionesCreadas += nuevasProgramaciones.size();
                        Bitacora.escribir("✓ A* exitoso: %d programaciones creadas", nuevasProgramaciones.size());
                        intentos = 0; // Resetear intentos tras éxito
                    }
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
//            lanzarExcepcion("Construccion programacion", "No se han encontrado rutas con almacenes validos");
        }

        if(rutasValidas.size() == 0) {
//            lanzarExcepcion("Elegir ruta", "Las rutas validas estan vacias");
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
    public static List<Producto> elegirProductos(Almacen almacenOrigen, Almacen almacenDestino, boolean esIntercontinental, List<Producto> productosEnAlmacen, int cantidadProductos)
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





/*
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
========================================================================================================================
===========================                  FUNCIONES DEL ALGORITMO A*             ====================================
========================================================================================================================
========================================================================================================================
*/


// ============================================================================
// CLASE AUXILIAR PARA NODOS DEL A*
// ============================================================================
    private static class NodoAEstrella implements Comparable<NodoAEstrella> {
        LinkedList<Vuelo> path;
        double g;  // Costo acumulado (distancia real recorrida)
        double h;  // Heurística (distancia estimada al destino)
        double f;  // f = g + h
        int capacidadDisponible;  // Máxima capacidad que se puede transportar en esta ruta
        Almacen almacenActual;

        public NodoAEstrella(LinkedList<Vuelo> path, double g, double h, int capacidadDisponible, Almacen almacenActual) {
            this.path = path;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.capacidadDisponible = capacidadDisponible;
            this.almacenActual = almacenActual;
        }

        @Override
        public int compareTo(NodoAEstrella otro) {
            int comparacionF = Double.compare(this.f, otro.f);
            if (comparacionF != 0) return comparacionF;
            // Desempate: priorizar mayor capacidad
            return Integer.compare(otro.capacidadDisponible, this.capacidadDisponible);
        }
    }

// ============================================================================
// MÉTODO PRINCIPAL: BÚSQUEDA A* PARA ENCONTRAR RUTAS VÁLIDAS
// ============================================================================
    /**
     * Busca rutas válidas usando A* cuando los métodos estándar fallan.
     * Garantiza que se encuentre AL MENOS UNA ruta factible si existe.
     *
     * @param pedido El pedido a satisfacer
     * @return Lista de programaciones que satisfacen la demanda restante
     * @throws Exception Si no se encuentra ninguna ruta válida
     */
    private List<Programacion> buscarRutaConAEstrella(Pedido pedido) throws Exception {
        Bitacora.escribir("\n╔════════════════════════════════════════════════════════════════╗");
        Bitacora.escribir("║  INICIANDO BÚSQUEDA A* PARA PEDIDO ID=%d", pedido.getId());
        Bitacora.escribir("║  Productos faltantes: %d", pedido.obtenerCantidadProgramacionesFaltantes());
        Bitacora.escribir("║  Destino: %s", pedido.getAlmacenDestino().getNombreCiudad());
        Bitacora.escribir("╚════════════════════════════════════════════════════════════════╝");

        List<Almacen> almacenesOrigen = this.estadoGlobal.obtenerAlmacenesOrigen();
        List<Ruta> rutasEncontradas = new ArrayList<>();

        // Intentar desde cada almacén origen
        for (Almacen almacenOrigen : almacenesOrigen) {
            if (!almacenOrigen.isInfinito()) {
                // Verificar si tiene stock disponible en el momento del pedido
                List<Producto> productosDisponibles = almacenOrigen.obtenerProductos(
                        pedido.getInstanteLimite()         // !!! antes registro plus 24h??                         *
                );
                if (productosDisponibles.isEmpty()) {
                    continue;
                }
            }

            Bitacora.escribir("\n→ Explorando desde origen: %s (ID=%d, Infinito=%b)",
                    almacenOrigen.getNombreCiudad(),
                    almacenOrigen.getId(),
                    almacenOrigen.isInfinito());

            Ruta ruta = ejecutarAEstrella(almacenOrigen, pedido);

            if (ruta != null) {
                rutasEncontradas.add(ruta);
                Bitacora.escribir("  ✓ Ruta encontrada con %d vuelos, capacidad: %d",
                        ruta.obtenerCantidadVuelos(),
                        calcularCapacidadFinalRuta(ruta, almacenOrigen, pedido));
            }
        }

        if (rutasEncontradas.isEmpty()) {
            lanzarExcepcion("A* búsqueda",
                    "No se encontró ninguna ruta válida después de explorar todos los orígenes posibles");
        }

        // Seleccionar la mejor ruta (menor cantidad de vuelos, mayor capacidad)
        Ruta mejorRuta = seleccionarMejorRuta(rutasEncontradas, pedido);

        Bitacora.escribir("\n✓ MEJOR RUTA SELECCIONADA:");
        Bitacora.escribir("  - Vuelos: %d", mejorRuta.obtenerCantidadVuelos());
        Bitacora.escribir("  - Capacidad: %d", calcularCapacidadFinalRuta(mejorRuta, mejorRuta.obtenerAlmacenOrigen(), pedido));

        // Generar programaciones
        return generarProgramacionesDesdeRuta(mejorRuta, pedido);
    }

    // ============================================================================
// EJECUCIÓN DEL ALGORITMO A*
// ============================================================================
    private Ruta ejecutarAEstrella(Almacen origen, Pedido pedido) throws Exception {
        PriorityQueue<NodoAEstrella> frontera = new PriorityQueue<>();
        Set<String> visitados = new HashSet<>();

        Almacen destino = pedido.getAlmacenDestino();
        Instant instanteInicio = pedido.getInstanteRegistro();
        Instant instanteLimite = pedido.obtenerInstanteMaximoLlegadaUltimoVuelo();

        // Nodo inicial (sin vuelos aún)
        LinkedList<Vuelo> pathInicial = new LinkedList<>();
        double hInicial = calcularDistanciaHaversine(origen, destino);
        int capacidadInicial = origen.isInfinito() ? Integer.MAX_VALUE :
                Math.min(1000, origen.getInventario().size() + origen.getInventarioFuturo().size());

        NodoAEstrella nodoInicial = new NodoAEstrella(pathInicial, 0.0, hInicial, capacidadInicial, origen);
        frontera.add(nodoInicial);

        int nodosExplorados = 0;
        int maxNodosExplorar = 10000; // Límite de seguridad

        while (!frontera.isEmpty() && nodosExplorados < maxNodosExplorar) {
            NodoAEstrella actual = frontera.poll();
            nodosExplorados++;

            // ¿Llegamos al destino?
            if (actual.almacenActual.getId() == destino.getId()) {
                Bitacora.escribir("  ✓ Destino alcanzado después de explorar %d nodos", nodosExplorados);
                return new Ruta(actual.path);
            }

            // Crear firma del nodo para evitar revisitar
            String firma = crearFirmaNodo(actual);
            if (visitados.contains(firma)) {
                continue;
            }
            visitados.add(firma);

            // Expandir nodo: obtener vuelos candidatos desde almacén actual
            List<Vuelo> vuelosCandidatos = obtenerVuelosCandidatosDesde(
                    actual.almacenActual,
                    actual.path.isEmpty() ? instanteInicio : actual.path.getLast().getInstanteLlegada(),
                    instanteLimite
            );

            for (Vuelo vuelo : vuelosCandidatos) {
                // Verificar admisibilidad del vuelo
                if (!esVueloAdmisibleParaAEstrella(actual.path, vuelo, pedido)) {
                    continue;
                }

                // Calcular capacidad disponible si añadimos este vuelo
                int nuevaCapacidad = calcularCapacidadAlAgregarVuelo(
                        actual.path,
                        vuelo,
                        actual.capacidadDisponible,
                        actual.almacenActual
                );

                if (nuevaCapacidad <= 0) {
                    continue; // No hay capacidad suficiente
                }

                // Calcular costos
                double distanciaVuelo = calcularDistanciaHaversine(
                        vuelo.getAlmacenSalida(),
                        vuelo.getAlmacenDestino()
                );
                double nuevoG = actual.g + distanciaVuelo;
                double nuevoH = calcularDistanciaHaversine(vuelo.getAlmacenDestino(), destino);

                // Crear nuevo nodo
                LinkedList<Vuelo> nuevoPath = new LinkedList<>(actual.path);
                nuevoPath.add(vuelo);

                NodoAEstrella nuevoNodo = new NodoAEstrella(
                        nuevoPath,
                        nuevoG,
                        nuevoH,
                        nuevaCapacidad,
                        vuelo.getAlmacenDestino()
                );

                frontera.add(nuevoNodo);
            }
        }

        if (nodosExplorados >= maxNodosExplorar) {
            Bitacora.escribir("  ✗ Búsqueda interrumpida: límite de %d nodos alcanzado", maxNodosExplorar);
        }

        return null; // No se encontró ruta
    }

// ============================================================================
// FUNCIONES AUXILIARES
// ============================================================================

    /**
     * Calcula la distancia Haversine entre dos almacenes usando latitud/longitud
     */
    private double calcularDistanciaHaversine(Almacen a, Almacen b) {
        final double R = 6371.0; // Radio de la Tierra en km

        double lat1 = Math.toRadians(a.getLatitud());
        double lon1 = Math.toRadians(a.getLongitud());
        double lat2 = Math.toRadians(b.getLatitud());
        double lon2 = Math.toRadians(b.getLongitud());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double aa = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa));

        return R * c;
    }

    /**
     * Obtiene vuelos candidatos desde un almacén específico en un rango temporal
     */
    private List<Vuelo> obtenerVuelosCandidatosDesde(Almacen almacen, Instant desde, Instant hasta) {
        List<Vuelo> candidatos = new ArrayList<>();

        Map<Long, List<Vuelo>> adyacencia = this.estadoGlobal.getAdyacenciaOrigenes();
        List<Vuelo> vuelosDesdeAlmacen = adyacencia.getOrDefault(almacen.getId(), new ArrayList<>());

        for (Vuelo vuelo : vuelosDesdeAlmacen) {
            Instant salidaMinima = desde.plus(Duration.ofHours(Hiperparametros.MINIMA_ESPERA_ENTRE_VUELOS));

            if (!vuelo.getInstanteSalida().isBefore(salidaMinima) &&
                    vuelo.getInstanteLlegada().isBefore(hasta) &&
                    vuelo.obtenerEspacioVacio() > 0 &&
                    !vuelo.isCancelado()) {
                candidatos.add(vuelo);
            }
        }

        // Ordenar por instante de salida (más temprano primero)
        candidatos.sort(Comparator.comparing(Vuelo::getInstanteSalida));

        return candidatos;
    }

    /**
     * Verifica si un vuelo es admisible para añadir al path actual en A*
     */
    private boolean esVueloAdmisibleParaAEstrella(LinkedList<Vuelo> path, Vuelo vuelo, Pedido pedido) {
        // Si el path está vacío, solo verificar que el vuelo tenga capacidad
        if (path.isEmpty()) {
            return vuelo.obtenerEspacioVacio() > 0 && !vuelo.getAlmacenDestino().isInfinito();
        }

        // Usar el método existente de EstadoGlobal
        if (!this.estadoGlobal.esVueloAdmisibleComoSiguiente(path, vuelo)) {
            return false;
        }

        // Verificación adicional: el vuelo debe llegar antes del límite del pedido
        Instant instanteLimite = pedido.obtenerInstanteMaximoLlegadaUltimoVuelo();
        if (vuelo.getInstanteLlegada().isAfter(instanteLimite)) {
            return false;
        }

        return true;
    }

    /**
     * Calcula la capacidad disponible al agregar un nuevo vuelo al path
     * Considera: capacidad del vuelo, espacio en almacén destino, capacidad acumulada
     */
    private int calcularCapacidadAlAgregarVuelo(LinkedList<Vuelo> path, Vuelo nuevoVuelo,
                                                int capacidadActual, Almacen almacenActual) {
        try {
            // Capacidad del vuelo
            int capacidadVuelo = nuevoVuelo.obtenerEspacioVacio();

            // Capacidad que puede salir del almacén actual
            int capacidadSalida = capacidadActual;
            if (!almacenActual.isInfinito()) {
                // Verificar que el almacén puede hacer la salida
                if (!almacenActual.verificaEntrada(nuevoVuelo.getInstanteSalida(), -capacidadActual)) {
                    // No puede sacar todo, calcular cuánto sí puede
                    capacidadSalida = almacenActual.calcularEspacioVacioMaximoEnInstante(nuevoVuelo.getInstanteSalida());
                }
            }

            // Capacidad que puede entrar al almacén destino
            Almacen almacenDestino = nuevoVuelo.getAlmacenDestino();
            int capacidadEntrada = Integer.MAX_VALUE;

            if (!almacenDestino.isInfinito()) {
                capacidadEntrada = almacenDestino.calcularEspacioVacioMaximoEnInstante(
                        nuevoVuelo.getInstanteLlegada()
                );
            }

            // La capacidad resultante es el mínimo de todas las restricciones
            int capacidadResultante = Math.min(capacidadSalida, capacidadVuelo);
            capacidadResultante = Math.min(capacidadResultante, capacidadEntrada);

            return Math.max(0, capacidadResultante);

        } catch (Exception e) {
            Bitacora.escribir("Error al calcular capacidad: %s", e.getMessage());
            return 0;
        }
    }

    /**
     * Crea una firma única para un nodo (para evitar revisitar estados)
     */
    private String crearFirmaNodo(NodoAEstrella nodo) {
        if (nodo.path.isEmpty()) {
            return String.valueOf(nodo.almacenActual.getId());
        }
        return this.estadoGlobal.crearFirmaRuta(nodo.path) + "-" + nodo.almacenActual.getId();
    }

    /**
     * Calcula la capacidad final que puede transportar una ruta completa
     */
    private int calcularCapacidadFinalRuta(Ruta ruta, Almacen origen, Pedido pedido) {
        try {
            int capacidadOrigen = origen.isInfinito() ? Integer.MAX_VALUE :
                    origen.obtenerProductos(pedido.getInstanteRegistro()).size();

            return this.estadoGlobal.obtenerCapacidadRuta(ruta, capacidadOrigen);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Selecciona la mejor ruta de un conjunto basándose en múltiples criterios
     */
    private Ruta seleccionarMejorRuta(List<Ruta> rutas, Pedido pedido) throws Exception {
        return rutas.stream()
                .max(Comparator
                        .comparingInt((Ruta r) -> calcularCapacidadFinalRuta(r, r.obtenerAlmacenOrigen(), pedido))
                        .thenComparingInt(r -> -r.obtenerCantidadVuelos()) // Menos vuelos es mejor
                )
                .orElseThrow(() -> new Exception("No hay rutas para seleccionar"));
    }

    /**
     * Genera las programaciones necesarias desde una ruta encontrada
     */
    private List<Programacion> generarProgramacionesDesdeRuta(Ruta ruta, Pedido pedido) throws Exception {
        List<Programacion> programaciones = new ArrayList<>();

        Almacen origen = ruta.obtenerAlmacenOrigen();
        int capacidadRuta = calcularCapacidadFinalRuta(ruta, origen, pedido);
        int cantidadProgramar = Math.min(capacidadRuta, pedido.obtenerCantidadProgramacionesFaltantes());

        Bitacora.escribir("\n→ Generando %d programaciones para la ruta encontrada", cantidadProgramar);

        for (int i = 0; i < cantidadProgramar; i++) {
            Producto producto = new Producto(origen);
            Programacion programacion = new Programacion(pedido, producto, ruta);
            programaciones.add(programacion);
        }

        return programaciones;
    }







}
