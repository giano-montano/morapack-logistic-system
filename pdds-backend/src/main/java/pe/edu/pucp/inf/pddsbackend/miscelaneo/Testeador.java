package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

public final class Testeador
{
    private Testeador()
    {
        throw new AssertionError("No se inicializa el Testeador");
    }

    /*
     * Verifica las ecuaciones (1), (2) y (3) para un E cualquiera 
     */
    private static boolean paraUnEcualquiera(EstadoGlobal estado){
        int numProdsExistentes = estado.getProductos().values().stream()
                .filter(Producto::isExistente).toList().size();

        int noPlanifsExistentesA = estado.getProductos().values().stream()
                .filter(Producto::validarNoPlanificadoExistente).toList().size();

        int totalmenteIncancelablesB = estado.getProductos().values().stream()
                .filter(Producto::validarIncancelable).toList().size();

        int planifsExistentesD = estado.getProductos().values().stream()
                .filter(Producto::validarPlanificadoExistente).toList().size();

        //validacion de la ecuación (1)
        if(validarProdsExistentes(numProdsExistentes, noPlanifsExistentesA, totalmenteIncancelablesB, planifsExistentesD)){
            //validacion de la ecuacion (2)
            if(validarProgramaciones(estado, totalmenteIncancelablesB, planifsExistentesD)){
                //validacion de la ecuacion (3)
                if(validarProdsEnAlmacenesYVuelos(estado, noPlanifsExistentesA, totalmenteIncancelablesB, planifsExistentesD)){
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Valida la ecuacion (1)
     */
    private static  boolean validarProdsExistentes(
            int numProdsExistentes,
            int noPlanifsExistentesA,
            int totalmenteIncancelablesB,
            int planifsExistentesD) {
        if(numProdsExistentes == noPlanifsExistentesA + totalmenteIncancelablesB + planifsExistentesD){
            return true;    
        }

        String error = String.format("ERROR (Test): El E no cumple la ecuación (1) [existentes=%d; a=%d; b=%d; c=%d]",numProdsExistentes, noPlanifsExistentesA, totalmenteIncancelablesB, planifsExistentesD);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Valida la ecuación (2)
     */
    private static boolean validarProgramaciones(EstadoGlobal estado, int totalmenteIncancelablesB, int planifsExistentesD) {
        int cantProgsI = (int) estado.getProgramaciones().stream()
                .filter(pg -> pg.getProducto().validarIncancelable()).count();

        int cantProgsE = (int) estado.getProgramaciones().stream()
                .filter(pg -> pg.getProducto().validarPlanificadoExistente()).count();

        if(cantProgsI == totalmenteIncancelablesB){
            if(cantProgsE == planifsExistentesD){
                return true;
            }
        }

        String error = String.format("ERROR (Test): El E no cumple la ecuación (2) [progI = %d; progE=%d; b=%d; d=%d]", cantProgsI, cantProgsE, totalmenteIncancelablesB, planifsExistentesD);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Valida la ecuacion (3)
     */
    private static boolean validarProdsEnAlmacenesYVuelos(EstadoGlobal estado, int noPlanifsExistentesA, int totalmenteIncancelablesB, int planifsExistentesD) {
        AtomicInteger prodsEnAlmacenes = new AtomicInteger();
        estado.getAlmacenes().values().forEach(almacen -> {
            prodsEnAlmacenes.addAndGet(almacen.getInventario().size());
        });

        AtomicInteger prodsEnVuelos = new AtomicInteger();
        estado.getVuelos().values().forEach(vuelo -> {
            prodsEnVuelos.addAndGet(vuelo.getInventario().size());
        });

        int sumaInventarios = prodsEnAlmacenes.get() + prodsEnVuelos.get();
        int sumaProdsExistente = noPlanifsExistentesA + totalmenteIncancelablesB + planifsExistentesD;

        if(sumaInventarios == noPlanifsExistentesA + totalmenteIncancelablesB + planifsExistentesD){
            return true;
        }

        String error = String.format("ERROR (Test): El E no cumple la ecuación (3) [sumaInventarios = %d, sumaProdsExistente=%d]", sumaInventarios, sumaProdsExistente);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    
    /*
     * Verifica la ecuacion (4) para un E_k. Ademas verifica para un E cualquiera
     */
    public static void paraUnEkCualquiera(Instant instanteK, EstadoGlobal estado) {
        Bitacora.escribir("=== Test E_k ===");

        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estado)){
            //validacion de la ecuacion (4)
            if(validarProdsPedidosComoSumaDeProgs(estado, instanteK)){
                return;
            }
        }
    }

    /*
     * Valida la ecuacion (4)
     */
    private static boolean validarProdsPedidosComoSumaDeProgs(EstadoGlobal estado, Instant instante) {
        int prodsPedidosPorAtender = (int) estado.getPedidos().values().stream()
                .collect(Collectors.summarizingInt(value -> value.getCantidadProductos()-value.getCantidadProductosSatisfechos()))
                .getSum();

        int prograsCreacionC = (int) estado.getProgramaciones().stream()
                .filter(programacion -> {
                    boolean primerVueloNoSaleAun = !programacion.getRuta().obtenerPrimerVuelo().yaPartio_v2(instante);
                    boolean prodNoExisteAun = programacion.getProducto().validarPlanificadoNoExistente();
                    return primerVueloNoSaleAun && prodNoExisteAun;
                }).count();

        int prograsIncancelablesI = (int) estado.getProgramaciones().stream()
                .filter(programacion -> {
                    return programacion.seriaIncancelable(instante);
                }).count();

        int prograsExistenteE = (int) estado.getProgramaciones().stream()
                .filter(programacion -> {
                    return programacion.getProducto().validarPlanificadoExistente();
                }).count();

        int sumaProgras = prograsCreacionC + prograsIncancelablesI + prograsExistenteE;

        if(sumaProgras == prodsPedidosPorAtender){
            return true;
        }
        
        String error = String.format("ERROR (Test): El E_k no cumple la ecuación (4) [prodsPedidosPorAtender=%d; progC=%d; progI=%d; progE=%d]", prodsPedidosPorAtender, prograsCreacionC, prograsIncancelablesI, prograsExistenteE);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    
    /*
     * Verifica la ecuacion (5) para un E'_k+1 cualquiera. Ademas verifica para un E cualquiera
     */
    public static void paraUnEPrimaCualquiera(EstadoGlobal estadoPrima, EstadoGlobal estadoPrevio, Instant instanteK) {
        Bitacora.escribir("=== Test E'_k+1 ===");
        
        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estadoPrima)){
            //validacion de la ecuacion (5)
            if(validarProdsEstados(estadoPrevio, estadoPrima)){
                //validacion de la ecuacion (6)
                if(validarProdsEnTransicionesDeProgras(estadoPrevio, estadoPrima)){
                    return;
                }
            }
        }
    }

    /*
     * Valida la ecuacion (5)
     */
    private static boolean validarProdsEstados(EstadoGlobal estadoPrevio, EstadoGlobal estadoPrima) {
        int prodsNoPlanificadosPrevio = (int) estadoPrevio.getProductos().values().stream()
                .filter(producto -> producto.validarNoPlanificadoExistente()).count();

        int prodsNoPlanificadosPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(producto -> producto.validarNoPlanificadoExistente()).count();

        if(prodsNoPlanificadosPrevio == prodsNoPlanificadosPrima){
            return true;    
        }
        
        String error = String.format("ERROR (Test): El E_k no cumple la ecuación (5) [aE_k = %d; aE'k+1 = %d]", prodsNoPlanificadosPrevio, prodsNoPlanificadosPrima);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Valida la ecuacion (6)
     */
    private static boolean validarProdsEnTransicionesDeProgras(EstadoGlobal estadoPrevio, EstadoGlobal estadoPrima) {
        int prodsExistentesPrevio = (int) estadoPrevio.getProductos().values().stream()
                .filter(Producto::isExistente).count();

        int prodsExistentesPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();

        int deCreadasAIncancelabes=0;
        int deCreadasAExistentes = 0;
        int deIncancelablesATerminadas = 0;

        List<Programacion> previos = estadoPrevio.getProgramaciones();
        List<Programacion> primas = estadoPrima.getProgramaciones();
        
        for(Programacion pg : previos){
            //No puede asumir que las programaciones esten en el mismo orden
            throw new IllegalStateException("UNSUPPORTED!");
        }

        int sumaProds =  prodsExistentesPrevio + deCreadasAIncancelabes + deCreadasAExistentes + deIncancelablesATerminadas;

        if( sumaProds == prodsExistentesPrima){
            return true;
        }

        String error = String.format("ERROR (Test): El E'_k+1 no cumple la ecuación (6) [prodsExistentesDelPrima=%d; prodsExistentesDelPrevio=%d; progC_I=%d; progC_E=%d; progI_T=%d]", prodsExistentesPrima, prodsExistentesPrevio, deCreadasAIncancelabes, deCreadasAExistentes, deIncancelablesATerminadas);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }


    /*
     * Verifica la ecuacion (7) para un E''_k+1 cualquiera. Ademas verifica para un E cualquiera
     */
    public static void paraUnEdosPrimaCualquiera(EstadoGlobal estadoPrima, EstadoGlobal estadoDosPrima){
        int planifsExistentesD = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarPlanificadoExistente).toList().size();
        int planifsNoExistentesC = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarPlanificadoNoExistente).toList().size();
        int noPlanifsExistentesA = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarNoPlanificadoExistente).toList().size();
        int totalmenteIncancelablesB = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarIncancelable).toList().size();

        Bitacora.escribir("=== Test E''_k+1 ===");

        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estadoPrima)){
            //validacion de la ecuacion (7)
            if(validarPlanificadosNoIncancelablesCero(planifsNoExistentesC, planifsExistentesD)){
                //validacion de la ecuacion (8)
                if(validarExistentesDosPrimaPrima(estadoPrima, estadoDosPrima,noPlanifsExistentesA , totalmenteIncancelablesB)){
                    return;
                }
            }
        }
    }

    /*
     * Valida la ecuacion (7)
     */
    private static boolean validarPlanificadosNoIncancelablesCero(int planifsNoExistentesC, int planifsExistentesD) {
        if(planifsNoExistentesC == 0 &&  planifsExistentesD == 0){
            return true;
        }

        String error = String.format("ERROR (Test): El E''_k+1 no cumple la ecuación (7) [c=%d; d=%d]", planifsNoExistentesC, planifsExistentesD);
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Valida la ecuacion (8)
     */
    private static boolean validarExistentesDosPrimaPrima(EstadoGlobal estadoPrima, EstadoGlobal estadoDosPrima, int noPlanifsExistentesA, int totalmenteIncancelablesB) {
        int prodsExistentesDosPrima = (int) estadoDosPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();
        int prodsExistentesPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();

        if(prodsExistentesDosPrima != prodsExistentesPrima ){
            if(prodsExistentesPrima != noPlanifsExistentesA + totalmenteIncancelablesB){
                return true;
            }
        }

        String error = String.format("ERROR (Test): El E''_k+1 no cumple la ecuación (8)  [productosExistentesDosPrima=%d; productosExistentesPrima=%d; a=%d; b=%d]", prodsExistentesDosPrima, prodsExistentesPrima, noPlanifsExistentesA, totalmenteIncancelablesB );
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

//============================================================================================================










    /**
     * Verifica que, dentro de la lista de rutas válidas, exista al menos una
     * ruta cuyo almacén de origen sea infinito.
     */
    public static void verificarRutasConAlmacenInfinitoComoOrigen(EstadoGlobal estado, List<Ruta> rutasValidas)
    {
        if (rutasValidas == null || rutasValidas.isEmpty())
        {
            Bitacora.escribir("TEST RUTAS ORIGEN INFINITO: lista de rutas vacía o nula");
            return;
        }

        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        boolean hayInfinito = false;
        int rutasConOrigenInfinito = 0;
        TreeSet<Long> idsAlmacenesInfinitos = new TreeSet<>();

        for (Ruta ruta : rutasValidas)
        {
            if (ruta == null || ruta.estaVacia())
            {
                continue;
            }

            Vuelo primerVuelo = ruta.obtenerPrimerVuelo();
            Almacen origen = almacenes.get(primerVuelo.getAlmacenSalida().getId());

            if (origen != null && origen.isInfinito())
            {
                hayInfinito = true;
                rutasConOrigenInfinito++;
                idsAlmacenesInfinitos.add(origen.getId());
            }
        }

        if (!hayInfinito)
        {
            String mensaje = "TEST ERROR: No se encontraron rutas con almacenes infinitos como origen";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje); 
        }
    }

    public static void cantidadProductosConsistenteTest(EstadoGlobal estado)
    {
        int productosEnAlmacenes = 0;
        int productosEnVuelos = 0;
        int productosExistentes = 0;

        // ====== 1) Productos en ALMACENES (solo EXISTENTES) ======
        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        for (Almacen a : almacenes.values())
        {
            productosEnAlmacenes += a.getInventario().size();
        }


        // ====== 2) Productos en VUELOS ======
        Map<Long, Vuelo> vuelos = estado.getVuelos();
        
        for (Vuelo v : vuelos.values())
        {
            productosEnVuelos += v.getInventario().size();
        }

        // ====== 3) Total existentes (existe=true) ======
        Map<UUID, Producto> productos = estado.getProductos();
        for (Producto p : productos.values())
        {
            if (p.isExistente())
            {
                productosExistentes++;
            }
        }
        int totalUbicados = productosEnAlmacenes + productosEnVuelos;

        // ====== 4) Log + assert ======
        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST CONSISTENCIA PRODUCTOS ===\n");
        sb.append("Productos en almacenes (existentes): ").append(productosEnAlmacenes).append("\n");
        sb.append("Productos en vuelos: ").append(productosEnVuelos).append("\n");
        sb.append("Total ubicados (almacenes+vuelos): ").append(totalUbicados).append("\n");
        sb.append("Total productos existentes (existe=true): ").append(productosExistentes).append("\n");
        sb.append("Consistente: ").append(totalUbicados == productosExistentes).append("\n");

        if (totalUbicados != productosExistentes)
        {
            sb.append("ERROR: No coincide.\n");
        }

        Bitacora.escribir(sb.toString());
/* 
        if (totalUbicados != productosExistentes)
        {
            throw new IllegalStateException(
                    "Inconsistencia productos: ubicados=" + totalUbicados + " vs existentes=" + productosExistentes);
        }
        */
    }
    public static void cantidadDeProgramacionesPlanificadasTest(EstadoGlobal estado)
    {
        // ====== Programaciones ======
        int nProgIncancelables = 0;   // aPuntoDeCumplirse = true
        int nProgCancelables = 0;     // aPuntoDeCumplirse = false

        List<Programacion> programaciones = estado.getProgramaciones();
        if (programaciones != null)
        {
            for (Programacion p : programaciones)
            {
                if (p != null && p.getProducto().isIncancelable())
                {
                    nProgIncancelables++;
                }
                else
                {
                    nProgCancelables++;
                }
            }
        }

        // ====== Productos ======
        int nProdExistentes = 0;          // existe = true
        int nProdInexistentes = 0;        // existe = false
        int nProdIncancelables = 0;       // prontoParaEntrega = true
        int nProdPlanificados = 0;        // planificado = true

        HashMap<UUID, Producto> productos = estado.getProductos();
        if (productos != null)
        {
            for (Producto prod : productos.values())
            {
                if (prod == null) continue;

                if (prod.isExistente()) nProdExistentes++;
                else nProdInexistentes++;

                if (prod.isIncancelable()) nProdIncancelables++;

                if (prod.isPlanificado()) nProdPlanificados++;
            }
        }

        // ====== Log ======
        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST: Conteos de Programaciones y Productos ===\n");

        sb.append("--- PROGRAMACIONES ---\n");
        sb.append("  Total: ").append(programaciones == null ? 0 : programaciones.size()).append("\n");
        sb.append("  Incancelables (aPuntoDeCumplirse=true): ").append(nProgIncancelables).append("\n");
        sb.append("  Cancelables (aPuntoDeCumplirse=false): ").append(nProgCancelables).append("\n");

        sb.append("--- PRODUCTOS ---\n");
        sb.append("  Total: ").append(productos == null ? 0 : productos.size()).append("\n");
        sb.append("  Existentes (existe=true): ").append(nProdExistentes).append("\n");
        sb.append("  Inexistentes (existe=false): ").append(nProdInexistentes).append("\n");
        sb.append("  Incancelables (prontoParaEntrega=true): ").append(nProdIncancelables).append("\n");
        sb.append("  Planificados (planificado=true): ").append(nProdPlanificados).append("\n");

        Bitacora.escribir(sb.toString());
    }


    public static void probarPersistirProgramacionesEnRuta(Ruta ruta_original, EstadoGlobal estado)
    {
        Ruta ruta;
        Vuelo vuelo;
        Almacen almacenOrigen, almacenDestino, almacenOrigen_original, almacenDestino_original;

        List<Vuelo> vuelosRuta = new LinkedList<>();

        for(Vuelo vuelo_original : ruta_original.getVuelos()) {
            almacenDestino_original = vuelo_original.getAlmacenDestino();
            almacenOrigen_original = vuelo_original.getAlmacenSalida();
            
            almacenDestino = deepCopy(almacenDestino_original);
            almacenOrigen = deepCopy(almacenOrigen_original);
            vuelo = deepCopy(vuelo_original);
            vuelosRuta.add(vuelo);
        }
        
        ruta = new Ruta(vuelosRuta);

        Instant instanteInicioRuta = ruta.obtenerPrimerVuelo().getInstanteSalida();
        almacenOrigen = estado.destinoRuta(ruta);
        
        List<Producto> productosEnAlmacen = estado.obtenerProductosDisponibles_v2(almacenOrigen, instanteInicioRuta);

        int capacidadAlmacen = almacenOrigen.isInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();
        int capacidadRuta = estado.obtenerCapacidadRuta(ruta, capacidadAlmacen);

        boolean valido;

        List<Producto> productos = new ArrayList<>();

        for(int i = 0; i != capacidadRuta; i++){
            productos.add(new Producto(almacenOrigen, instanteInicioRuta));
        }

        for(Vuelo V : ruta.getVuelos())
        {
            Almacen almacenSalida = estado.origenVuelo_v2(V);
            valido = almacenSalida.registrarSalida_v2(V.getInstanteSalida(), capacidadAlmacen);

            if(!valido && !almacenSalida.isInfinito())
            {
                Bitacora.escribir("MAL! FUCK");
            }

            valido = V.registrarInventario_v2(productos);

            if(!valido)
            {
                Bitacora.escribir("MAL!");
            }

            Almacen almacenEntrada = V.getAlmacenDestino();
            valido = almacenEntrada.registrarEntrada_v2(V.getInstanteLlegada(), capacidadAlmacen);

            if(!valido)
            {
                Bitacora.escribir("MAL! ");
            }
        }

    }

    /**
     * Helper para clonar profundamente un objeto Serializable usando serialización.
     */
    @SuppressWarnings("unchecked")
    private static <T extends java.io.Serializable> T deepCopy(T objeto)
    {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos))
        {
            oos.writeObject(objeto);
            oos.flush();
            byte[] bytes = bos.toByteArray();

            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
                 java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis))
            {
                return (T) ois.readObject();
            }
        }
        catch (java.io.IOException | ClassNotFoundException e)
        {
            throw new RuntimeException("Error en deepCopy de EstadoGlobal", e);
        }
    }
    /*
     * Verifica que los cambios esten bien realizados
     */
    public static void verificarCambiosAlmacenes(EstadoGlobal estado, Instant instanteActual)
    {
        boolean valido;
        Map<Long, Almacen> almacenes;
        Map<Long, Vuelo> vuelos;
        List<Programacion> programaciones;
        Map<UUID, Producto> productos;

        almacenes     = estado.getAlmacenes();
        vuelos        = estado.getVuelos();
        programaciones = estado.getProgramaciones();
        productos     = estado.getProductos();

        valido = verificarCambiosPorVuelosEnTransito(almacenes, vuelos, productos, instanteActual);
        valido &= verificarCambiosPorProgramaciones(almacenes, vuelos, programaciones);

        if(valido)
        {
            Bitacora.escribir("TEST OK: cambios en almacenes consistentes con vuelos y programaciones.");
        }else{
            Bitacora.escribir("===TEST CAMBIOS===\n NO COINCIDE");
        }
    }

    private static boolean verificarCambiosPorVuelosEnTransito(
        Map<Long, Almacen> almacenes,
        Map<Long, Vuelo> vuelos,
        Map<UUID, Producto> productos,
        Instant instanteActual)
    {
        // esperado[idAlmacen][instanteLlegada] = total productos que deben entrar
        Map<Long, Map<Instant, Integer>> cambiosEsperados = new HashMap<>();

        // 1. Construir los cambios esperados por vuelos en tránsito
        for (Vuelo vuelo : vuelos.values())
        {
            Instant instanteSalida  = vuelo.getInstanteSalida();
            Instant instanteLlegada = vuelo.getInstanteLlegada();

            // vuelo en tránsito: salida < ahora <= llegada
            if (instanteSalida.isBefore(instanteActual)
                    && !instanteLlegada.isBefore(instanteActual))
            {
                int cantidadEnVuelo = vuelo.getInventario().size();
                if (cantidadEnVuelo == 0)
                {
                    // Este vuelo no aporta cambios de inventario
                    continue;
                }

                long idAlmacenDestino = vuelo.getAlmacenDestino().getId();
                cambiosEsperados
                        .computeIfAbsent(idAlmacenDestino, k -> new HashMap<>())
                        .merge(instanteLlegada, cantidadEnVuelo, Integer::sum);

                // Verificar también productos futuros e instantes de disponibilidad
                Almacen almacenDestino = almacenes.get(idAlmacenDestino);

                for (Producto producto : vuelo.getInventario())
                {
//                    Producto producto = productos.get(idProd);
                    boolean estaEnFuturos =
                            almacenDestino.getInventario().contains(producto);

                    if (!estaEnFuturos)
                    {
                        Bitacora.escribir(
                                "TEST ERROR (Vuelos en tránsito): Producto %s del vuelo %d "
                                        + "no está en productoFuturo del almacén %d",
                                producto.getId(), vuelo.getId(), almacenDestino.getId());
                        return false;
                    }

//                    if (
//                            /*!instanteLlegada.equals(producto.getInstanteDeDisponibilidad()*/))
//                    {
//                        Bitacora.escribir(
//                                "TEST ERROR (Vuelos en tránsito): Producto %s debería tener "
//                                        + "instanteDeDisponibilidad = %s, pero tiene %s",
//                                idProd,
//                                instanteLlegada,
//                                producto.getInstanteDeDisponibilidad());
//                        return false;
//                    }
                }
            }
        }

        // 2. Comparar cambiosEsperados con los cambios reales en cada almacén
        for (Map.Entry<Long, Map<Instant, Integer>> entryAlmacen : cambiosEsperados.entrySet())
        {
            Long idAlmacen = entryAlmacen.getKey();
            Almacen almacenDestino = almacenes.get(idAlmacen);
            Map<Instant, Integer> cambiosReales = almacenDestino.getCambios();

            for (Map.Entry<Instant, Integer> entryCambio : entryAlmacen.getValue().entrySet())
            {
                Instant instanteLlegada = entryCambio.getKey();
                int esperado = entryCambio.getValue();
                int real = cambiosReales.getOrDefault(instanteLlegada, 0);

                if (real != esperado)
                {
                    Bitacora.escribir(
                            "TEST ERROR (Vuelos en tránsito): Almacén %d, instante %s. "
                                    + "cambios[%s] = %d, esperado = %d",
                            idAlmacen, instanteLlegada, instanteLlegada, real, esperado);
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean verificarCambiosPorProgramaciones(Map<Long, Almacen> almacenes, Map<Long, Vuelo> vuelos, List<Programacion> programaciones)
    {
        Ruta ruta;
        long idUltimoVuelo, idAlmacenDestino;
        Vuelo ultimoVuelo;
        Instant llegada, instanteRecojo;
        Almacen almacenDestino;
        Integer deltaRecojo;

        for (Programacion programacion : programaciones)
        {
            if (!programacion.getProducto().isIncancelable())
            {
                Bitacora.escribir("TEST ERROR (Programaciones): Existe una programación que no es incancelable. "
                        + "Producto=%s, pedido=%d", programacion.getProducto().getId(), programacion.getPedido().getId()
                );
                
                return false;
            }

            ruta = programacion.getRuta(); //.getVuelosRuta();
            if (ruta.estaVacia())
            {
                Bitacora.escribir("TEST ERROR (Programaciones): Programación sin ruta. Producto=%s, pedido=%d",
                        programacion.getProducto().getId(), programacion.getPedido().getId());
                
                return false;
            }

            idUltimoVuelo   = ruta.obtenerUltimoVuelo().getId();
            ultimoVuelo     = vuelos.get(idUltimoVuelo);
            llegada         = ultimoVuelo.getInstanteLlegada();
            instanteRecojo  = llegada.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));
            idAlmacenDestino = ultimoVuelo.getAlmacenDestino().getId();
            almacenDestino   = almacenes.get(idAlmacenDestino);

            deltaRecojo = almacenDestino.getCambios().get(instanteRecojo);

            // Cada programación incancelable debería aportar -1 en el instante de recojo
            if (deltaRecojo == null || deltaRecojo >= 0)
            {
                Bitacora.escribir( "TEST ERROR (Programaciones): En almacén %d, para producto=%s (pedido=%d), "
                        + "no se encontró cambio negativo en cambios[%s]",
                        idAlmacenDestino, programacion.getProducto().getId(), programacion.getPedido().getId(), instanteRecojo);

                return false;
            }
        }

        return true;
    }

    private static List<LinkedList<Long>> convertirRutasAVuelosId(List<Ruta> rutasVuelos)
    {
        List<LinkedList<Long>> rutasIds = new ArrayList<>(rutasVuelos.size());

        for (Ruta ruta : rutasVuelos)
        {
            LinkedList<Long> idsRuta = ruta.getVuelos().stream()
                    .map(Vuelo::getId)
                    .collect(Collectors.toCollection(LinkedList::new));

            rutasIds.add(idsRuta);
        }

        return rutasIds;
    }

    private static void compararRutasIds(List<LinkedList<Long>> rutasOriginal,
                                         List<LinkedList<Long>> rutasV2)
    {
        Set<String> firmasOriginal, firmasV2, soloOriginal, soloV2;

        firmasOriginal = rutasOriginal.stream()
                .map(Testeador::crearFirmaRutaIds)
                .collect(Collectors.toSet());

        firmasV2 = rutasV2.stream()
                .map(Testeador::crearFirmaRutaIds)
                .collect(Collectors.toSet());

        Bitacora.escribir("Rutas original: %d, rutas v2: %d",
                firmasOriginal.size(), firmasV2.size());

        soloOriginal = new HashSet<>(firmasOriginal);
        soloOriginal.removeAll(firmasV2);

        soloV2 = new HashSet<>(firmasV2);
        soloV2.removeAll(firmasOriginal);

        if (soloOriginal.isEmpty() && soloV2.isEmpty())
        {
            Bitacora.escribir("Ambas implementaciones generan exactamente las mismas rutas.");
        }
        else
        {
            Bitacora.escribir("Diferencias encontradas:");
            if (!soloOriginal.isEmpty())
            {
                Bitacora.escribir("  - Rutas que SOLO están en la versión original:");
                soloOriginal.forEach(firma -> Bitacora.escribir("    %s", firma));
            }
            if (!soloV2.isEmpty())
            {
                Bitacora.escribir("  - Rutas que SOLO están en la versión v2:");
                soloV2.forEach(firma -> Bitacora.escribir("    %s", firma));
            }
        }
    }

    private static String crearFirmaRutaIds(List<Long> ruta)
    {
        return ruta.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
    }

    private static boolean sonRutasIgualesEntreCorridas(List<Ruta> rutasCorridaAnterior, List<Ruta> rutasCorridaActual)
    {
        Set<String> firmasAnterior = firmarRutasVuelo(rutasCorridaAnterior);
        Set<String> firmasActual   = firmarRutasVuelo(rutasCorridaActual);

        if (firmasAnterior.equals(firmasActual))
        {
            Bitacora.escribir(
                    "Las rutas de esta corrida son IGUALES a las de la corrida anterior. (total rutas = %d)",
                    firmasActual.size());
            return true;
        }

        Set<String> soloAnterior = new HashSet<>(firmasAnterior);
        soloAnterior.removeAll(firmasActual);

        Set<String> soloActual = new HashSet<>(firmasActual);
        soloActual.removeAll(firmasAnterior);

        Bitacora.escribir("Las rutas cambiaron respecto a la corrida anterior.");
        Bitacora.escribir("  - Rutas SOLO en corrida anterior: %d", soloAnterior.size());
        for (String firma : soloAnterior)
        {
            Bitacora.escribir("    anterior: %s", firma);
        }

        Bitacora.escribir("  - Rutas SOLO en corrida actual: %d", soloActual.size());
        for (String firma : soloActual)
        {
            Bitacora.escribir("    actual:   %s", firma);
        }

        return false;
    }

    private static String crearFirmaRutaVuelo(Ruta ruta)
    {
        return ruta.getVuelos().stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.joining("-"));
    }

    private static Set<String> firmarRutasVuelo(List<Ruta> rutas)
    {
        return rutas.stream()
                .map(Testeador::crearFirmaRutaVuelo)
                .collect(Collectors.toSet());
    }

    private static String imprimirRutasVuelos(List<Ruta> rutas)
    {
        StringBuilder sb;
        int indiceRuta, cantidadMostrada;

        sb = new StringBuilder();
        cantidadMostrada = Math.min(10, rutas.size());

        sb.append("===== RUTAS (por VUELOS) =====\n");
        sb.append("Total rutas: ").append(rutas.size()).append("\n");
        sb.append("Mostrando primeras ").append(cantidadMostrada).append(" rutas\n\n");

        indiceRuta = 1;

        for (Ruta ruta : rutas)
        {
            if (indiceRuta > 10)
            {
                break;
            }

            sb.append("Ruta #").append(indiceRuta++)
              .append(" (tramos = ").append(ruta.getVuelos().size()).append(")\n");

            for (int i = 0; i < ruta.getVuelos().size(); i++)
            {
                Vuelo vuelo = ruta.getVuelos().get(i);
                sb.append("  [")
                  .append(i + 1)
                  .append("] Vuelo ")
                  .append(vuelo.getId())
                  .append(" (")
                  .append(vuelo.getAlmacenSalida().getId())
                  .append(" -> ")
                  .append(vuelo.getAlmacenDestino())
                  .append(") ")
                  .append("inicio=")
                  .append(vuelo.getInstanteSalida())
                  .append(", fin=")
                  .append(vuelo.getInstanteLlegada())
                  .append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
