package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

public final class Testeador
{
    /*
     * Lanza una excepción con un mensaje formateado
     */
    public static void lanzarExcepcion(String metodo, String mensaje) throws Exception {
        String mensajeCompleto = "ERROR test(" + metodo + "): " + mensaje;
        Bitacora.escribir(mensajeCompleto);
        throw new IllegalStateException(mensajeCompleto);
    }

    private Testeador()
    {
        throw new AssertionError("No se inicializa el Testeador");
    }

    /*
     * Verifica las ecuaciones (1), (2) y (3) para un E cualquiera 
     */
    private static boolean paraUnEcualquiera(EstadoGlobal estado) throws Exception {
        int numProdsExistentes = estado.getProductos().values().stream()
                .filter(Producto::isExistente).toList().size();

        int noPlanifsExistentesA = estado.getProductos().values().stream()
                .filter(Producto::validarNoPlanificado_A).toList().size();

        int totalmenteIncancelablesB = estado.getProductos().values().stream()
                .filter(Producto::validarIncancelable_B).toList().size();

        int planifsExistentesD = estado.getProductos().values().stream()
                .filter(Producto::validarPlanificadoExistente_D).toList().size();

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
     * 
     * pi_existentes = a + b + d
     */
    private static  boolean validarProdsExistentes(
            int numProdsExistentes,
            int noPlanifsExistentesA,
            int totalmenteIncancelablesB,
            int planifsExistentesD) throws Exception {
        if(numProdsExistentes == noPlanifsExistentesA + totalmenteIncancelablesB + planifsExistentesD){
            return true;    
        }

        String mensaje = String.format("El E no cumple la ecuación (1) [existentes=%d; a=%d; b=%d; c=%d]",numProdsExistentes, noPlanifsExistentesA, totalmenteIncancelablesB, planifsExistentesD);
        lanzarExcepcion("eq1", mensaje);
        return false;
    }

    /*
     * Valida la ecuación (2)
     * 
     * Pg_I = b y Pg_E = d
     */
    private static boolean validarProgramaciones(EstadoGlobal estado, int totalmenteIncancelablesB, int planifsExistentesD) throws Exception {
        int cantProgsI = (int) estado.getProgramaciones().stream()
                .filter(pg -> pg.getEstado() == 'I').count();

        int cantProgsE = (int) estado.getProgramaciones().stream()
                .filter(pg -> pg.getEstado() == 'E').count();

        if(cantProgsI == totalmenteIncancelablesB){
            if(cantProgsE == planifsExistentesD){
                return true;
            }
        }
        //ERROR test(eq2): El E no cumple la ecuación (2) [progI = 366; progE=2089; b=328; d=2089]
        String mensaje = String.format("El E no cumple la ecuación (2) [progI = %d; progE=%d; b=%d; d=%d]", cantProgsI, cantProgsE, totalmenteIncancelablesB, planifsExistentesD);
        lanzarExcepcion("eq2", mensaje);
        return false;
    }

    /*
     * Valida la ecuacion (3)
     * 
     * pi_existentes = a + b + d = pi_almacenes + pi_vuelos
     */
    private static boolean validarProdsEnAlmacenesYVuelos(EstadoGlobal estado, int noPlanifsExistentesA, int totalmenteIncancelablesB, int planifsExistentesD) throws Exception {
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

        String mensaje = String.format("El E no cumple la ecuación (3) [sumaInventarios = %d, sumaProdsExistente=%d]", sumaInventarios, sumaProdsExistente);
        lanzarExcepcion("eq3", mensaje);
        return false;
    }
    
    /*
     * Verifica la ecuacion (4) para un E_k. Ademas verifica para un E cualquiera
     */
    public static void paraUnEkCualquiera(
            Instant instanteK,
            EstadoGlobal estado,
            Instant instanteInicioOperaciones
    ) throws Exception {
        Bitacora.escribir("=== Test E_k ===");

        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estado)){
            //validacion de la ecuacion (4)
            if(validarProdsPedidosComoSumaDeProgs(estado, instanteK, instanteInicioOperaciones)){
                return;
            }
        }
    }

    /*
     * Valida la ecuacion (4)
     * 
     * Pd = Σ Pg_C + Σ Pg_I + Σ Pg_E
     */
//    public static Instant instanteInicioOperaciones = Instant.parse("2025-12-25T16:00:00.000Z");
    private static boolean validarProdsPedidosComoSumaDeProgs(
            EstadoGlobal estado,
            Instant instanteK,
            Instant instanteInicioOperaciones) throws Exception {
        int prodsPedidosPorAtender = (int) estado.getPedidos().values().stream()
                .filter(pedido -> !pedido.getInstanteRegistro().isBefore(instanteInicioOperaciones) 
                                   && !pedido.getInstanteRegistro().isAfter(instanteK))
                .collect(Collectors.summarizingInt(p -> p.obtenerCantidadProductosFaltantes()))
                .getSum();

        int prograsCreacionC = (int) estado.getProgramaciones().stream()
                .filter(programacion -> programacion.getEstado() == 'C')
                .count();

        int prograsIncancelablesI = (int) estado.getProgramaciones().stream()
                .filter(programacion -> programacion.getEstado() == 'I')
                .count();

        int prograsExistenteE = (int) estado.getProgramaciones().stream()
                .filter(programacion -> programacion.getEstado() == 'E')
                .count();

        int sumaProgras = prograsCreacionC + prograsIncancelablesI + prograsExistenteE;

        String mensajeBueno = String.format("El E_k SÍ cumple la ecuación (4) [prodsPedidosPorAtender=%d; progC=%d; progI=%d; progE=%d]",
                prodsPedidosPorAtender, prograsCreacionC, prograsIncancelablesI, prograsExistenteE);

        if(sumaProgras == prodsPedidosPorAtender){
            Bitacora.escribir(mensajeBueno);
            return true;
        }
        String mensajeMalo = String.format("El E_k no cumple la ecuación (4) [prodsPedidosPorAtender=%d; progC=%d; progI=%d; progE=%d]",
                prodsPedidosPorAtender, prograsCreacionC, prograsIncancelablesI, prograsExistenteE);

        lanzarExcepcion("eq4", mensajeMalo);
        return false;
    }
    
    /*
     * Verifica la ecuacion (5) y (6) para un E'_k+1 cualquiera. Ademas verifica para un E cualquiera
     */
    public static void paraUnEPrimaCualquiera(EstadoGlobal estadoPrevio, Instant instantePrevio, EstadoGlobal estadoPrima, Instant instantePrima) throws Exception {
        Bitacora.escribir("=== Test E'_k+1 ===");
        
        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estadoPrima)){
            //validacion de la ecuacion (5)
            if(validarProdsEstados(estadoPrevio, estadoPrima)){
                //validacion de la ecuacion (6)
                if(validarProdsEnTransicionesDeProgras(estadoPrevio, instantePrevio, estadoPrima, instantePrima)){
                    return;
                }
            }
        }
    }

    /*
     * Valida la ecuacion (5)
     * 
     * aE_k = aE'_k+1
     */
    private static boolean validarProdsEstados(EstadoGlobal estadoPrevio, EstadoGlobal estadoPrima) throws Exception {
        int prodsNoPlanificadosPrevio = (int) estadoPrevio.getProductos().values().stream()
                .filter(producto -> producto.validarNoPlanificado_A()).count();

        int prodsNoPlanificadosPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(producto -> producto.validarNoPlanificado_A()).count();

        if(prodsNoPlanificadosPrevio == prodsNoPlanificadosPrima){
            return true;    
        }
        

        String mensaje = String.format("El E_k no cumple la ecuación (5) [aE_k = %d; aE'k+1 = %d]", prodsNoPlanificadosPrevio, prodsNoPlanificadosPrima);
        lanzarExcepcion("eq5", mensaje);
        return false;
    }

    /*
     * Valida la ecuacion (6)
     * 
     * pi_existentes_E'_k+1 = pi_existentes_E_k + #(pgC -> pgI) + #(pgC -> pgE) - #(pgI -> pgT)
     * 
     * Debido a la maquina de estados planteada, y asumiendo que no se aparecen ni desaparecen programaciones
     * se puede resolver esta ecuacion con los conteos absolutos de programaciones en E'_k+1 y en E_k.
     * 
     * #(pgC -> pgI) + #(pgC -> pgE) - #(pgI -> pgT) = Σ pgC_E_k - Σ pgC_E'_k+1 - Σ pgT_E'_k+1 + Σ pgT_E_k
     */
    private static boolean validarProdsEnTransicionesDeProgras(EstadoGlobal estadoPrevio, Instant instantePrevio, EstadoGlobal estadoPrima, Instant instantePrima) throws Exception {
        int prodsExistentesPrevio = (int) estadoPrevio.getProductos().values().stream()
                .filter(Producto::isExistente).count();

        int prodsExistentesPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();

        // Σ pgT_E_k
        int prograsTerminadasIenEstadoPrevio = (int) estadoPrevio.getProgramaciones().stream()
                .filter(programacion -> programacion.validarTerminada_T(instantePrevio))
                .count();
        // Σ pgT_E'_k+1
        int prograsTerminadasIenEstadoPrima = (int) estadoPrima.getProgramaciones().stream()
                .filter(programacion -> programacion.validarTerminada_T(instantePrima))
                .count();
        // Σ pgC_E_k
        int prograsCreadasCenEstadoPrevio = (int) estadoPrevio.getProgramaciones().stream()
                .filter(programacion -> programacion.validarCreada_C(instantePrevio))
                .count();
        // Σ pgC_E'_k+1
        int prograsCreadasCenEstadoPrima = (int) estadoPrima.getProgramaciones().stream()
                .filter(programacion -> programacion.validarCreada_C(instantePrima))
                .count();

        int sumaProdsPrima =  prodsExistentesPrevio + (prograsCreadasCenEstadoPrevio - prograsCreadasCenEstadoPrima)
                + (prograsTerminadasIenEstadoPrevio - prograsTerminadasIenEstadoPrima) ;
        // ^^ Las progras creadas que disminuyen desde el estado previo al prima son productos que en prima serían existentes
        // Además, las diferencia de progras terminadas entre el previo y el prima dicen cuántos prods safan,
        // por eso van en negativo (terminadasPrevio - terminadasPrima)

        if( sumaProdsPrima == prodsExistentesPrima){
            return true;
        }

        String mensaje = String.format("El E'_k+1 no cumple la ecuación (6) [prodsExistentesDelPrima=%d; prodsExistentesDelPrevio=%d; pgCPrevio=%d; pgCPrima=%d; pgTPrima=%d; pgTPrevio=%d]",
                prodsExistentesPrima, prodsExistentesPrevio, prograsCreadasCenEstadoPrevio, prograsCreadasCenEstadoPrima, prograsTerminadasIenEstadoPrima, prograsTerminadasIenEstadoPrevio);
        lanzarExcepcion("eq6", mensaje);
        return false;
    }


    /*
     * Verifica la ecuacion (7) para un E''_k+1 cualquiera. Ademas verifica para un E cualquiera
     */
    public static void paraUnEdosPrimaCualquieraTEST(EstadoGlobal estadoPrima, EstadoGlobal estadoDosPrima) throws Exception{
        int planifsExistentesD = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarPlanificadoExistente_D).toList().size();
        int planifsNoExistentesC = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarPlanificadoNoExistente_C).toList().size();
        int noPlanifsExistentesA = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarNoPlanificado_A).toList().size();
        int totalmenteIncancelablesB = estadoDosPrima.getProductos().values().stream()
                .filter(Producto::validarIncancelable_B).toList().size();

        Bitacora.escribir("=== Test E''_k+1 ===");

        //validacion de las ecuaciones (1), (2) y (3)
        if(paraUnEcualquiera(estadoDosPrima)){
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
    private static boolean validarPlanificadosNoIncancelablesCero(int planifsNoExistentesC, int planifsExistentesD) throws Exception {
        if(planifsNoExistentesC == 0 &&  planifsExistentesD == 0){
            return true;
        }

        String mensaje = String.format("El E''_k+1 no cumple la ecuación (7) [c=%d; d=%d]", planifsNoExistentesC, planifsExistentesD);
        lanzarExcepcion("eq7", mensaje);
        return false;
    }

    /*
     * Valida la ecuacion (8)
     */
    private static boolean validarExistentesDosPrimaPrima(EstadoGlobal estadoPrima, EstadoGlobal estadoDosPrima, int noPlanifsExistentesA, int totalmenteIncancelablesB) throws Exception {
        int prodsExistentesDosPrima = (int) estadoDosPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();
        int prodsExistentesPrima = (int) estadoPrima.getProductos().values().stream()
                .filter(Producto::isExistente).count();


        if(prodsExistentesDosPrima == prodsExistentesPrima ){ // Estos patas estaban con != en vez de ==
            if(prodsExistentesPrima == (noPlanifsExistentesA + totalmenteIncancelablesB)){
                return true;
            }
        }

        String mensaje = String.format("El E''_k+1 no cumple la ecuación (8)  [productosExistentesDosPrima=%d; productosExistentesPrima=%d; a=%d; b=%d]",
                prodsExistentesDosPrima, prodsExistentesPrima, noPlanifsExistentesA, totalmenteIncancelablesB );
        lanzarExcepcion("eq8", mensaje);
        return false;
    }


    /*
     * Verifica la cantidad de programaciones tipo I en el ctx.estado y en la salida del algoritmo
     */
    public static void cantidadProgramacionesIncancelablesConsistenteTEST(EstadoGlobal estado, SalidaProblemaPlanificacion salida) throws Exception {
        List<Programacion> programacionesI_EnEstado = estado.getProgramaciones().stream()
                .filter(programacion -> programacion.getEstado() == 'I')
                .toList();

        List<Programacion> programacionesI_EnSalida = salida.getProgramaciones().stream()
                .filter(programacion -> programacion.getEstado() == 'I')
                .toList();

        Bitacora.escribir("=== Test PgI real y planificado ===");

        if (programacionesI_EnEstado.size() == programacionesI_EnSalida.size()) {
            for (Programacion pg : programacionesI_EnEstado) {
                Programacion pg_en_salida = buscarProgramacionEnSalida(programacionesI_EnSalida, pg);

                if (pg_en_salida == null) {
                    lanzarExcepcion("Pg_I_real != Pg_I_planif", "No se encontró la programación tipo I en la salida del algoritmo: " + pg.toString());
                }
            }
            return;    
        }

        lanzarExcepcion("Pg_I_real != Pg_I_planif", "No coincide la cantidad de programaciones tipo I entre el estado y la salida del algoritmo");
    }

    private static Programacion buscarProgramacionEnSalida(List<Programacion> programacionesSalida, Programacion programacionBuscada) {
        for (Programacion pg : programacionesSalida) {
            if (pg.equals(programacionBuscada)) {
                return pg;
            }
        }

        return null;
    }

    /*
     * Verifica que, dentro de la lista de rutas válidas, exista al menos una
     * ruta cuyo almacén de origen sea infinito.
     */
    public static void verificarRutasConAlmacenInfinitoComoOrigenTEST(
            EstadoGlobal estado,
            List<Ruta> rutasValidas,
            String mensaje) {
        if (rutasValidas == null || rutasValidas.isEmpty()) {
            Bitacora.escribir("TEST RUTAS ORIGEN INFINITO: lista de rutas vacía o nula");
            return;
        }

        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        boolean hayInfinito = false;
        int rutasConOrigenInfinito = 0;
        TreeSet<Long> idsAlmacenesInfinitos = new TreeSet<>();

        for (Ruta ruta : rutasValidas) {
            if (ruta.obtenerCantidadVuelos() == 0) {
                continue;
            }

            Vuelo primerVuelo = ruta.obtenerPrimerVuelo();
            Almacen origen = primerVuelo.getAlmacenSalida();

//            Bitacora.escribir("Primer vuelo y origen: " + primerVuelo + ", " + origen);

            if (origen != null && origen.isInfinito()) {
                hayInfinito = true;
                rutasConOrigenInfinito++;
                idsAlmacenesInfinitos.add(origen.getId());
            }
        }

        if (!hayInfinito) {
            String _mensaje = "TEST ERROR: No se encontraron rutas con almacenes infinitos como origen - " + mensaje;
            Bitacora.escribir(_mensaje);
            throw new IllegalStateException(_mensaje); 
        }
    }


    public static void verificarConsistenciasEnCambiosTEST(EstadoGlobal estadoGlobal, String mensaje) throws Exception {
        Map<Long, Almacen> almacenes = estadoGlobal.getAlmacenes();
        boolean hayErrores = false;
        List<String> almacenesInconsistentes = new ArrayList<>();
        
       Bitacora.escribir("=== TEST CONSISTENCIA EN CAMBIOS DE ALMACENES === %s", mensaje);

        for (Almacen almacen : almacenes.values()) {
            boolean esConsistente = almacen.verificarConsistenciaEnCambios();
            
            // Solo hacer log cuando hay inconsistencia
            if (!esConsistente) {
                String infoAlmacen = String.format("Almacén ID=%d (%s) - %s", 
                almacen.getId(), 
                almacen.getNombreCiudad(),
                almacen.isInfinito() ? "INFINITO" : "Capacidad: " + almacen.getCapacidad() + " Inventario: " + almacen.getInventario().size());

                hayErrores = true;
                almacenesInconsistentes.add(infoAlmacen);
                Bitacora.escribir("INCONSISTENCIA DETECTADA: %s", infoAlmacen);
                
                if (almacen.getCambios().isEmpty()) {
                    Bitacora.escribir("     (Sin cambios)");
                } else {
                    almacen.getCambios().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            Bitacora.escribir("     %s → %+d productos", entry.getKey(), entry.getValue());
                        });
                }
            }
        }
        
        // Lanzar excepción si se encontraron inconsistencias
        if (hayErrores) {
            String mensajeError = String.format(
                "Se encontraron inconsistencias en %d almacén(es): %s - Contexto: %s",
                almacenesInconsistentes.size(),
                String.join(", ", almacenesInconsistentes),
                mensaje
            );
            lanzarExcepcion("verificarConsistenciasEnCambiosTEST", mensajeError);
        }
    }
//============================================================================================================


































//============================================================================================================
 

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

    public static void probarPersistirProgramacionesEnRuta(Ruta ruta_original, EstadoGlobal estado) throws Exception
    {
        Ruta ruta;
        Vuelo vuelo;
        Almacen almacenOrigen, almacenDestino, almacenOrigen_original, almacenDestino_original;

        LinkedList<Vuelo> vuelosRuta = new LinkedList<>();

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
        almacenOrigen = ruta.obtenerAlmacenOrigen();
        
        List<Producto> productosEnAlmacen = almacenOrigen.obtenerProductos(instanteInicioRuta);

        int capacidadAlmacen = almacenOrigen.isInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();
        int capacidadRuta = estado.obtenerCapacidadRuta(ruta, capacidadAlmacen);

        boolean valido;

        List<Producto> productos = new ArrayList<>();

        for(int i = 0; i != capacidadRuta; i++){
            productos.add(new Producto(almacenOrigen));
        }

        for(Vuelo V : ruta.getVuelos())
        {
            Almacen almacenSalida = V.getAlmacenSalida();
            valido = almacenSalida.registrarSalida(V.getInstanteSalida(), capacidadAlmacen);

            if(!valido && !almacenSalida.isInfinito())
            {
                Bitacora.escribir("MAL! FUCK");
            }

            valido = V.registrarProducto(productos);

            if(!valido)
            {
                Bitacora.escribir("MAL!");
            }

            Almacen almacenEntrada = V.getAlmacenDestino();
            valido = almacenEntrada.registrarEntrada(V.getInstanteLlegada(), capacidadAlmacen);

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
            if (!programacion.getProducto().validarIncancelable_B())
            {
                Bitacora.escribir("TEST ERROR (Programaciones): Existe una programación que no es incancelable. "
                        + "Producto=%s, pedido=%d", programacion.getProducto().getId(), programacion.getPedido().getId()
                );
                
                return false;
            }

            ruta = programacion.getRuta(); //.getVuelosRuta();
            if (ruta.obtenerCantidadVuelos() == 0)
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

    public static void precMeteProdsDePgRutaAlVuelo(
            List<Programacion> programacionesACargar,
            List<Producto> productosACargar,
            Instant instanteActual,
            Vuelo v,
            Almacen a
    ) throws Exception {

        if(  a.isInfinito() ){
            // pgsVuelo→ tipoC con prod tipoC (infinito)
            boolean progsSoloTipoC = programacionesACargar.stream().allMatch(
                    programacion -> programacion.validarCreada_C(instanteActual)
            );

            if(!progsSoloTipoC){
                String msj = String.format("pgVuelo→ tipoC (infinito) ");
                lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
            }

            // pgsVuelo→ tipoC con prod tipoC (infinito)
            boolean prodsSoloTipoC = programacionesACargar.stream().allMatch(
                    programacion ->
                            programacion.getProducto().validarPlanificadoNoExistente_C()
            );

            if(!prodsSoloTipoC){
                String msj = String.format("con prod tipoC (infinito) ");
                lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
            }
        }else{ // almacén no infinito
            // tipoE  con prod tipoD(NOinfinito)
            boolean progsSoloTipoE = programacionesACargar.stream().allMatch(
                    programacion -> programacion.validarExistente_E(instanteActual)
            );

            if(!progsSoloTipoE){
                String msj = String.format("pgsVuelo→ tipoE  (NOinfinito) ");
                lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
            }

            // tipoE  con prod tipoD(NOinfinito)
            boolean prodsSoloTipoD = programacionesACargar.stream().allMatch(
                    programacion ->
                            programacion.getProducto().validarPlanificadoExistente_D()
            );

            if(!prodsSoloTipoD){
                String msj = String.format("con prod tipoD (no infinito) ");
                lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
            }
        }

        // v → inventario = 0
        boolean inventarioCero = v.getInventario().size() == 0;
        if(!inventarioCero){
            String msj = String.format("v → inventario = 0 ");
            lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
        }


    }

    public static void postMeteProdsDePgRutaAlVuelo(
            List<Programacion> programacionesACargar,
            List<Producto> productosACargar,
            Instant instanteActual,
            Vuelo v
    ) throws Exception {
        // pgRuta → not tipoC Y not tipoT
        boolean progsNotTipoCAndNotTipoT = programacionesACargar.stream().allMatch(
                programacion -> !programacion.validarCreada_C(instanteActual)
                        && !programacion.validarTerminada_T(instanteActual)
        );

        if(!progsNotTipoCAndNotTipoT){
            String msj = String.format("pgRuta → not tipoC Y not tipoT");
            lanzarExcepcion("postMeteProdsDePgRutaAlVuelo", msj);
        }

        // prods en pgRuta →not tipoA tipoC
        boolean prodsNotTipoAAndNotTipoC = programacionesACargar.stream().allMatch(
                programacion -> !programacion.getProducto().validarNoPlanificado_A()
                        && !programacion.getProducto().validarPlanificadoNoExistente_C()
        );

        if(!prodsNotTipoAAndNotTipoC){
            String msj = String.format("prods en pgRuta →not tipoA tipoC");
            lanzarExcepcion("postMeteProdsDePgRutaAlVuelo", msj);
        }

        // v → inventario = 0
        boolean inventarioLlenoProgs = v.getInventario().size() == programacionesACargar.size();
        boolean inventarioLlenoProds = v.getInventario().size() == productosACargar.size();
        if(!inventarioLlenoProgs || !inventarioLlenoProds){
            String msj = String.format("v -> inventario = pgRuta.size() = prods en pgRuta.size()\n");
            lanzarExcepcion("precMeteProdsDePgRutaAlVuelo", msj);
        }
    }

}
