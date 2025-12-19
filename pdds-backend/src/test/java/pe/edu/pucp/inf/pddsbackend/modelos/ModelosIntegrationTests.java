package pe.edu.pucp.inf.pddsbackend.modelos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

public class ModelosIntegrationTests {

    private Instant instanteInicial;
    
    @BeforeEach
    public void setUp() {
        instanteInicial = Instant.parse("2025-01-01T00:00:00Z");
    }

    /* ==================== TESTS INDIVIDUALES POR CLASE ==================== */

    @Test
    public void testProductoTransiciones() {
        // Crear producto tipo C
        Almacen almacen = crearAlmacen(1L, "A", Continente.AFRICA, false, 10);
        Producto producto = new Producto(almacen);
        
        // Verificar estado inicial C
        assertTrue(producto.validarPlanificadoNoExistente_C(), "Producto debe iniciar como tipo C");
        
        // Transición C → D
        producto.transPlanificadoNoExistente_C_PlanificadoExistente_D();
        assertTrue(producto.validarPlanificadoExistente_D(), "Producto debe ser tipo D");
        
        // Transición D → B
        producto.transPlanificadoExistente_D_Incancelable_B();
        assertTrue(producto.validarIncancelable_B(), "Producto debe ser tipo B");
        
        // Transición D → A (crear nuevo producto tipo D)
        Producto producto2 = new Producto(almacen);
        producto2.transPlanificadoNoExistente_C_PlanificadoExistente_D();
        producto2.transPlanificadoExistente_D_NoPlanificado_A();
        assertTrue(producto2.validarNoPlanificado_A(), "Producto debe ser tipo A");
        
        // Transición C → B directa
        Producto producto3 = new Producto(almacen);
        producto3.transPlanificadoNoExistente_C_Incancelable_B();
        assertTrue(producto3.validarIncancelable_B(), "Producto debe ser tipo B");
    }

    @Test
    public void testVueloRegistroProductos() {
        Almacen origen = crearAlmacen(1L, "Origen", Continente.AFRICA, false, 10);
        Almacen destino = crearAlmacen(2L, "Destino", Continente.AFRICA, false, 10);
        
        Instant salida = instanteInicial.plus(Duration.ofHours(1));
        Instant llegada = salida.plus(Duration.ofMinutes(30));
        
        Vuelo vuelo = new Vuelo(origen, destino, "V001", salida, llegada, 5, false, false);
        
        // Registrar productos
        Producto p1 = new Producto(origen);
        Producto p2 = new Producto(origen);
        
        assertTrue(vuelo.registrarProducto(p1), "Debe registrar producto 1");
        assertTrue(vuelo.registrarProducto(p2), "Debe registrar producto 2");
        assertEquals(2, vuelo.getInventario().size(), "Debe tener 2 productos");
        assertEquals(3, vuelo.obtenerEspacioVacio(), "Debe tener 3 espacios vacíos");
        
        // Verificar verificaciones de tiempo
        assertFalse(vuelo.verificarSalida(instanteInicial), "No debe haber salido");
        assertTrue(vuelo.verificarSalida(salida), "Debe haber salido en su instante de salida");
        assertTrue(vuelo.verificarLlegada(llegada), "Debe haber llegado en su instante de llegada");
    }

    @Test
    public void testRutaCoherencia() {
        Almacen a = crearAlmacen(1L, "A", Continente.AFRICA, false, 10);
        Almacen b = crearAlmacen(2L, "B", Continente.AFRICA, true, 10);
        Almacen c = crearAlmacen(3L, "C", Continente.EUROPA, false, 10);
        
        Instant t1 = instanteInicial.plus(Duration.ofHours(1));
        Instant t2 = t1.plus(Duration.ofMinutes(30));
        Instant t3 = t2.plus(Duration.ofMinutes(30));
        Instant t4 = t3.plus(Duration.ofMinutes(30));
        
        Vuelo v1 = new Vuelo(b, a, "V001", t1, t2, 10, false, false);
        Vuelo v2 = new Vuelo(a, c, "V002", t3, t4, 10, false, false);
        
        LinkedList<Vuelo> vuelos = new LinkedList<>();
        vuelos.add(v1);
        vuelos.add(v2);
        
        // Debe crear ruta sin errores (coherente)
        Ruta ruta = new Ruta(vuelos);
        
        assertEquals(2, ruta.obtenerCantidadVuelos(), "Debe tener 2 vuelos");
        assertEquals(t1, ruta.obtenerInstanteSalida(), "Instante salida debe ser del primer vuelo");
        assertEquals(t3, ruta.obtenerInstanteIncancelable(), "Instante incancelable debe ser salida del último vuelo");
        assertEquals(t4, ruta.obtenerInstanteLlegada(), "Instante llegada debe ser del último vuelo");
        
        // Verificar estados de ruta
        assertTrue(ruta.verificarRutaNoEmpieza(instanteInicial), "Ruta no debe haber empezado");
        assertTrue(ruta.verificarRutaEnIntermedios(t2), "Ruta debe estar en intermedios en t2");
        assertTrue(ruta.verificarRutaEnUltimoTramo(t3.plus(Duration.ofMinutes(1))), "Ruta debe estar en último tramo");
    }

    @Test
    public void testRutaIncoherenciaTemporal() {
        Almacen a = crearAlmacen(1L, "A", Continente.AFRICA, false, 10);
        Almacen b = crearAlmacen(2L, "B", Continente.AFRICA, false, 10);
        
        Instant t1 = instanteInicial.plus(Duration.ofHours(1));
        Instant t2 = t1.plus(Duration.ofMinutes(30));
        Instant t3 = t2.minus(Duration.ofMinutes(10)); // Incoherencia: sale antes de que llegue anterior
        Instant t4 = t3.plus(Duration.ofMinutes(30));
        
        Vuelo v1 = new Vuelo(a, b, "V001", t1, t2, 10, false, false);
        Vuelo v2 = new Vuelo(b, a, "V002", t3, t4, 10, false, false);
        
        LinkedList<Vuelo> vuelos = new LinkedList<>();
        vuelos.add(v1);
        vuelos.add(v2);
        
        // Debe lanzar excepción por incoherencia temporal
        assertThrows(IllegalStateException.class, () -> new Ruta(vuelos));
    }

    @Test
    public void testPedidoRegistroProductos() {
        Almacen destino = crearAlmacen(1L, "Destino", Continente.AFRICA, false, 10);
        Pedido pedido = new Pedido(1L, destino, 5, 0, instanteInicial, null, false, Continente.AFRICA);
        
        assertEquals(5, pedido.obtenerCantidadProductosFaltantes(), "Deben faltar 5 productos");
        assertEquals(5, pedido.obtenerCantidadProgramacionesFaltantes(), "Deben faltar 5 programaciones");
        
        // Programar producto
        Producto p1 = new Producto(destino);
        assertTrue(pedido.registrarProductoProgramado(p1), "Debe registrar producto programado");
        assertEquals(4, pedido.obtenerCantidadProgramacionesFaltantes(), "Deben faltar 4 programaciones");
        
        // Entregar producto
        p1.transPlanificadoNoExistente_C_Incancelable_B();
        assertTrue(pedido.registrarProductoEntregado(p1), "Debe registrar producto entregado");
        assertEquals(4, pedido.obtenerCantidadProductosFaltantes(), "Deben faltar 4 productos");
    }

    @Test
    public void testPedidoInstanteLimiteIntercontinental() {
        Almacen origenContinental = crearAlmacen(1L, "Continental", Continente.AFRICA, false, 10);
        Almacen origenIntercontinental = crearAlmacen(2L, "Intercont", Continente.EUROPA, false, 10);
        Almacen destino = crearAlmacen(3L, "Destino", Continente.AFRICA, false, 10);
        
        Pedido pedido = new Pedido(1L, destino, 2, 0, instanteInicial, null, false, Continente.AFRICA);
        
        // Inicialmente debe ser continental (2 días)
        Instant limiteEsperado = instanteInicial.plus(Duration.ofDays(2));
        assertEquals(limiteEsperado, pedido.getInstanteLimite(), "Límite inicial debe ser 2 días");
        
        // Programar producto continental
        Producto p1 = new Producto(origenContinental);
        pedido.registrarProductoProgramado(p1);
        assertEquals(limiteEsperado, pedido.getInstanteLimite(), "Límite debe seguir siendo 2 días");
        
        // Programar producto intercontinental
        Producto p2 = new Producto(origenIntercontinental);
        pedido.registrarProductoProgramado(p2);
        Instant limiteIntercont = instanteInicial.plus(Duration.ofDays(3));
        assertEquals(limiteIntercont, pedido.getInstanteLimite(), "Límite debe cambiar a 3 días");
    }

    @Test
    public void testProgramacionTransiciones() {
        Almacen a = crearAlmacen(1L, "A", Continente.AFRICA, false, 10);
        Almacen b = crearAlmacen(2L, "B", Continente.AFRICA, false, 10);
        
        Instant salida = instanteInicial.plus(Duration.ofHours(1));
        Instant llegada = salida.plus(Duration.ofMinutes(30));
        
        Vuelo vuelo = new Vuelo(a, b, "V001", salida, llegada, 10, false, false);
        LinkedList<Vuelo> vuelos = new LinkedList<>();
        vuelos.add(vuelo);
        Ruta ruta = new Ruta(vuelos);
        
        Pedido pedido = new Pedido(1L, b, 1, 0, instanteInicial, null, false, Continente.AFRICA);
        Producto producto = new Producto(a);
        
        Programacion prog = new Programacion(pedido, producto, ruta);
        
        // Verificar estado inicial C
        assertTrue(prog.validarCreada_C(instanteInicial), "Programación debe estar creada");
        
        // Transición C → E
        prog.transCreada_C_Existente_E();
        assertTrue(prog.validarExistente_E(instanteInicial.plus(Duration.ofMinutes(30))), "Programación debe ser existente");
        
        // Transición E → I
        prog.transExistente_E_Incancelable_I();
        Instant enVuelo = salida.plus(Duration.ofMinutes(15));
        assertTrue(prog.validarIncancelable_I(enVuelo), "Programación debe ser incancelable");
        
        // Transición I → T
        prog.transIncancelable_I_Terminada_T();
        Instant despuesRecojo = llegada.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO + 1));
        assertTrue(prog.validarTerminada_T(despuesRecojo), "Programación debe estar terminada");
    }

    @Test
    public void testAlmacenRegistroYCapacidad() {
        Almacen almacen = crearAlmacen(1L, "Test", Continente.AFRICA, false, 5);
        
        Producto p1 = new Producto(almacen);
        Producto p2 = new Producto(almacen);
        
        // Registrar productos
        assertTrue(almacen.registrarProducto(p1), "Debe registrar p1");
        assertTrue(almacen.registrarProducto(p2), "Debe registrar p2");
        assertEquals(2, almacen.getInventario().size(), "Debe tener 2 productos");
        
        // Verificar espacios
        Instant t1 = instanteInicial.plus(Duration.ofHours(1));
        assertEquals(3, almacen.calcularEspacioVacioMaximoEnInstante(t1), "Debe tener 3 espacios disponibles");
        
        // Registrar salida futura
        assertTrue(almacen.registrarSalida(t1, 2), "Debe registrar salida de 2 productos");
        assertEquals(5, almacen.calcularEspacioVacioMaximoEnInstante(t1), "Después de salida debe tener 5 espacios");
    }

    /* ==================== TEST DE INTEGRACIÓN COMPLETO ==================== */

    @Test
    public void testIntegracionCompletaConProgramaciones() {
        System.out.println("\n========== INICIANDO TEST DE INTEGRACIÓN ==========\n");
        
        // 1. Crear almacenes
        Almacen almacenA = crearAlmacen(1L, "A", Continente.AFRICA, false, 10);
        Almacen almacenB = crearAlmacen(2L, "B", Continente.AFRICA, true, 10); // Infinito
        Almacen almacenC = crearAlmacen(3L, "C", Continente.EUROPA, false, 10);
        
        System.out.println("Almacenes creados:");
        System.out.println("  A: Continental, capacidad 10");
        System.out.println("  B: Continental, infinito");
        System.out.println("  C: Intercontinental, capacidad 10\n");
        
        // 2. Crear vuelos (30 minutos cada uno)
        Instant t01_00 = instanteInicial.plus(Duration.ofHours(1));      // 01:00
        Instant t01_30 = t01_00.plus(Duration.ofMinutes(30));            // 01:30
        Instant t02_00 = instanteInicial.plus(Duration.ofHours(2));      // 02:00
        Instant t02_30 = t02_00.plus(Duration.ofMinutes(30));            // 02:30
        Instant t03_00 = instanteInicial.plus(Duration.ofHours(3));      // 03:00
        Instant t03_30 = t03_00.plus(Duration.ofMinutes(30));            // 03:30
        
        Vuelo vueloBA_01 = new Vuelo(almacenB, almacenA, "V001", t01_00, t01_30, 10, false, false);
        Vuelo vueloBA_02 = new Vuelo(almacenB, almacenA, "V002", t02_00, t02_30, 10, false, false);
        Vuelo vueloAC = new Vuelo(almacenA, almacenC, "V003", t03_00, t03_30, 10, false, false);
        
        System.out.println("Vuelos creados:");
        System.out.println("  V001: B→A 01:00-01:30");
        System.out.println("  V002: B→A 02:00-02:30");
        System.out.println("  V003: A→C 03:00-03:30\n");
        
        // 3. Crear rutas
        LinkedList<Vuelo> rutaBAC_01 = new LinkedList<>();
        rutaBAC_01.add(vueloBA_01);
        rutaBAC_01.add(vueloAC);
        Ruta ruta1 = new Ruta(rutaBAC_01);
        
        LinkedList<Vuelo> rutaBAC_02 = new LinkedList<>();
        rutaBAC_02.add(vueloBA_02);
        rutaBAC_02.add(vueloAC);
        Ruta ruta2 = new Ruta(rutaBAC_02);
        
        LinkedList<Vuelo> rutaAC = new LinkedList<>();
        rutaAC.add(vueloAC);
        Ruta ruta3 = new Ruta(rutaAC);
        
        System.out.println("Rutas creadas:");
        System.out.println("  Ruta 1: B→A→C (vuelo 01:00)");
        System.out.println("  Ruta 2: B→A→C (vuelo 02:00)");
        System.out.println("  Ruta 3: A→C\n");
        
        // 4. Crear pedido
        Pedido pedido = new Pedido(1L, almacenC, 10, 0, instanteInicial, null, false, Continente.EUROPA);
        System.out.println("Pedido creado: 10 productos, destino C\n");
        
        // 5. Crear productos tipo A (5 productos en almacén A)
        List<Producto> productosA = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Producto p = new Producto(almacenA);
            p.transPlanificadoNoExistente_C_PlanificadoExistente_D();
            p.transPlanificadoExistente_D_NoPlanificado_A();
            productosA.add(p);
            almacenA.registrarProducto(p);
        }
        System.out.println("5 productos tipo A creados y registrados en almacén A\n");
        
        // 6. Crear programaciones y productos
        List<Programacion> programaciones = new ArrayList<>();
        
        // 3 programaciones con ruta B-A-C (vuelo 01:00)
        List<Programacion> programaciones_1 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Producto p = new Producto(almacenB);
            Programacion prog = new Programacion(pedido, p, ruta1);
            programaciones_1.add(prog);
        }

        persistirProgramaciones(programaciones_1);
        System.out.println("3 programaciones creadas con ruta B→A→C (vuelo 01:00)");
        
        // 2 programaciones con ruta B-A-C (vuelo 02:00)
        List<Programacion> programaciones_2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Producto p = new Producto(almacenB);
            Programacion prog = new Programacion(pedido, p, ruta2);
            programaciones_2.add(prog);
        }
        persistirProgramaciones(programaciones_2);
        System.out.println("2 programaciones creadas con ruta B→A→C (vuelo 02:00)");
        
        // 5 programaciones con ruta A-C (productos tipo A)
        List<Programacion> programaciones_3 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Producto p = productosA.get(i);
            p.transNoPlanificado_A_PlanificadoExistente_D();
            Programacion prog = new Programacion(pedido, p, ruta3);
        }
        persistirProgramaciones(programaciones_3);
        System.out.println("5 programaciones creadas con ruta A→C (productos tipo A)\n");
        
        programaciones.addAll(programaciones_1);
        programaciones.addAll(programaciones_2);    
        programaciones.addAll(programaciones_3);

        assertEquals(10, programaciones_1.size() + programaciones_2.size() + programaciones_3.size(), "Debe haber 10 programaciones");
        assertEquals(10, pedido.getProductosProgramados().size(), "Pedido debe tener 10 productos programados");
        
        System.out.println("========== INICIANDO SIMULACIÓN DE EVENTOS ==========\n");
        
        // 7. Loop de eventos (salidas y llegadas en orden)
        
        // Evento 1: Salida vuelo V001 (B→A) a las 01:00
        System.out.println("--- Evento 1: Salida V001 (B→A) a las 01:00 ---");
        procesarSalidaVuelo(vueloBA_01, t01_00, almacenB, almacenA, programaciones);
        
        // Evento 2: Llegada vuelo V001 (B→A) a las 01:30
        System.out.println("\n--- Evento 2: Llegada V001 (B→A) a las 01:30 ---");
        procesarLlegadaVuelo(vueloBA_01, t01_30, almacenA, programaciones, false);
        
        // Evento 3: Salida vuelo V002 (B→A) a las 02:00
        System.out.println("\n--- Evento 3: Salida V002 (B→A) a las 02:00 ---");
        procesarSalidaVuelo(vueloBA_02, t02_00, almacenB, almacenA, programaciones);
        
        // Evento 4: Llegada vuelo V002 (B→A) a las 02:30
        System.out.println("\n--- Evento 4: Llegada V002 (B→A) a las 02:30 ---");
        procesarLlegadaVuelo(vueloBA_02, t02_30, almacenA, programaciones, false);
        
        // Evento 5: Salida vuelo V003 (A→C) a las 03:00
        System.out.println("\n--- Evento 5: Salida V003 (A→C) a las 03:00 ---");
        procesarSalidaVuelo(vueloAC, t03_00, almacenA, almacenC, programaciones);
        
        // Evento 6: Llegada vuelo V003 (A→C) a las 03:30 (vuelo final)
        System.out.println("\n--- Evento 6: Llegada V003 (A→C) a las 03:30 ---");
        procesarLlegadaVuelo(vueloAC, t03_30, almacenC, programaciones, true);
        
        // Evento 7: Recojo de productos (HORAS_ESPERA_PARA_RECOJO después de llegada)
        Instant instanteRecojo = t03_30.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));
        System.out.println("\n--- Evento 7: Recojo de productos a las " + instanteRecojo + " ---");
        procesarRecojo(almacenC, instanteRecojo, programaciones, pedido);
        
        // Verificaciones finales
        System.out.println("\n========== VERIFICACIONES FINALES ==========");
        assertEquals(10, pedido.getProductosEntregados().size(), "Deben haberse entregado 10 productos");
        assertEquals(0, pedido.getProductosProgramados().size(), "No deben quedar productos programados");
        assertEquals(0, pedido.obtenerCantidadProductosFaltantes(), "No deben faltar productos");
        System.out.println("✓ 10 productos entregados");
        System.out.println("✓ 0 productos programados restantes");
        System.out.println("✓ Pedido completado\n");
        
        // Verificar todas las programaciones están terminadas
        for (Programacion prog : programaciones) {
            assertTrue(prog.validarTerminada_T(instanteRecojo), "Todas las programaciones deben estar terminadas");
        }
        System.out.println("✓ Todas las programaciones terminadas\n");
        
        System.out.println("========== TEST COMPLETADO EXITOSAMENTE ==========\n");
    }

    /* ==================== MÉTODOS AUXILIARES ==================== */

    private Almacen crearAlmacen(long id, String nombre, Continente continente, boolean infinito, int capacidad) {
        return new Almacen(id, infinito, capacidad, 0, "País" + nombre, "Ciudad" + nombre,
                nombre + "AP", nombre + "CD", new ArrayList<>(), continente);
    }

    private void procesarSalidaVuelo(Vuelo vuelo, Instant instanteSalida, Almacen origen, 
                                     Almacen destino, List<Programacion> programaciones) {
        System.out.println("Procesando salida de vuelo " + vuelo.getCodigo());
        System.out.println("  Productos en vuelo: " + vuelo.getInventario().size());
        
        // Solo registrar en almacenes no infinitos
        if (!origen.isInfinito()) {
            // Borrar productos del almacén origen
            for (Producto p : vuelo.getInventario()) {
                if (origen.getInventario().contains(p)) {
                    origen.borrarProductoSincronizado(p);
                    System.out.println("  - Producto borrado del almacén origen");
                }
            }
            
            // Registrar productos futuros en destino
            for (Producto p : vuelo.getInventario()) {
                if (!destino.isInfinito()) {
                    destino.registrarProductoFuturo(p, vuelo.getInstanteLlegada());
                }
            }
        }
        
        // Transicionar programaciones C → E si el vuelo es el primero de su ruta
        for (Programacion prog : programaciones) {
            if (prog.getRuta().obtenerPrimerVuelo().equals(vuelo) && 
                prog.validarCreada_C(instanteSalida)) {
                prog.transCreada_C_Existente_E();
                System.out.println("  - Programación " + prog.getProducto().getId() + " transicionada C → E");
            }
        }
    }

    private void procesarLlegadaVuelo(Vuelo vuelo, Instant instanteLlegada, Almacen destino,
                                     List<Programacion> programaciones, boolean esVueloFinal) {
        System.out.println("Procesando llegada de vuelo " + vuelo.getCodigo());
        System.out.println("  Es vuelo final: " + esVueloFinal);
        
        // Solo registrar en almacenes no infinitos
        if (!destino.isInfinito()) {
            // Registrar productos en almacén destino
            for (Producto p : vuelo.getInventario()) {
                destino.registrarProductoSincronizado(p);
                System.out.println("  - Producto registrado en almacén destino");
            }
        }
        
        // Transicionar programaciones según sea vuelo final o no
        for (Programacion prog : programaciones) {
            if (prog.getRuta().verificarVueloEnRuta(vuelo)) {
                if (esVueloFinal && prog.getRuta().obtenerUltimoVuelo().equals(vuelo)) {
                    // Transición a incancelable (E → I o C → I)
                    if (prog.validarExistente_E(instanteLlegada)) {
                        prog.transExistente_E_Incancelable_I();
                        prog.getProducto().transPlanificadoExistente_D_Incancelable_B();
                        System.out.println("  - Programación " + prog.getProducto().getId() + " transicionada E → I");
                    } else if (prog.validarCreada_C(instanteLlegada)) {
                        prog.transCreada_C_Incancelable_I();
                        prog.getProducto().transPlanificadoNoExistente_C_Incancelable_B();
                        System.out.println("  - Programación " + prog.getProducto().getId() + " transicionada C → I");
                    }
                }
            }
        }
    }

    private void procesarRecojo(Almacen almacen, Instant instanteRecojo, 
                               List<Programacion> programaciones, Pedido pedido) {
        System.out.println("Procesando recojo de productos");
        
        int productosEntregados = 0;
        for (Programacion prog : programaciones) {
            if (prog.validarIncancelable_I(instanteRecojo)) {
                // Registrar recojo en almacén
                if (!almacen.isInfinito()) {
                    almacen.registrarRecojoDeProductos(prog.getProducto(), instanteRecojo);
                    almacen.borrarProductoSincronizado(prog.getProducto());
                }
                
                // Registrar como entregado en pedido
                pedido.registrarProductoEntregado(prog.getProducto());
                
                // Transicionar programación I → T
                prog.transIncancelable_I_Terminada_T();
                productosEntregados++;
                
                System.out.println("  - Producto " + prog.getProducto().getId() + " entregado y programación terminada");
            }
        }
        
        System.out.println("  Total productos entregados: " + productosEntregados);
    }

    
    /*
     * Guardar en el estadoGlobal las programaciones que ha construido en  construirProgramaciones_v2. Esta operacion es delicada. Las programaciones que llegna a esta función tienen la caracteristica de que comparten la ruta. Cada programación corresponde a un producto.
     *
     * Un vuelo tiene un almacenSalida (origen) y un almacenEntrada (llegada).
     * El pedido tiene un almacenDestino, que es el almacenEntrada del ultimo vuelo
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
        Almacen almacenDestino = ruta.obtenerAlmacenDestino();
        Instant instanteLlegadUltimoVuelo = ruta.obtenerUltimoVuelo().getInstanteLlegada();

        for(Producto producto : productos)
        {
            valido = almacenDestino.registrarRecojoDeProductos(producto, instanteLlegadUltimoVuelo);
            if(!valido) {
                String mensaje = "ERROR (Persitir programaciones): No se puede marcar el recojo de los productos";
                Bitacora.escribir(mensaje);
                throw new IllegalStateException(mensaje);
            }
        }

        // registro de los productos al pedido
        valido = pedido.registrarProductoProgramado(productos);

        if(!valido) {
            String mensaje = "ERROR (Persitir programaciones): Registro ilegal de productos en el pedido";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
    }
}
