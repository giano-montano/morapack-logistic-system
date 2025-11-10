package pe.edu.pucp.inf.pddsbackend.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Continente;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.AlmacenService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.Utils;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;


import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InicializadorDeDatos implements CommandLineRunner {

    private final PedidoRepository pedidoRepository;
    private final AlmacenRepository almacenRepository;
    private final VueloRepository vueloRepository;
    private final PedidoService pedidoService;
    private final AlmacenService almacenService;
    private final VueloService vueloService;



    private static final int DIAS_ANADIR_A_VUELOS = 30; // cuántos días instanciar a partir de startDate; mínimo pon 1 para que cuente hoy
    private static final int SEGUNDOS_ANADIR_A_VUELOS = DIAS_ANADIR_A_VUELOS*24*3600;
    private static int CONTADOR_GLOBAL_PEDIDOS = 0;
    private static int TOPE_PEDIDOS = 50; // top limit si quieres limitar (no usado actualmente)

    // Nombres por defecto (en classpath:resources/archivos-inicializador/)
    private static final String DEFAULT_ALMACENES_FILE = "archivos-inicializador/c.1inf54.25.2.Aeropuerto.husos.v1.20250818__estudiantes.txt";
    private static final String DEFAULT_VUELOS_FILE = "archivos-inicializador/c.1inf54.25.2.planes_vuelo.v4.20250818.txt";
    private static final String DEFAULT_PEDIDOS_FILE = "archivos-inicializador/seed-1759184602_days-1_storages-30.txt"; // seed-1759184602_days-1_storages-30.txt

    @Override
    public void run(String... args) throws Exception {
        // cambié hibernate a UPDATE no CREATE, para más practicidad y rapidez.
        //Poner en false si no deseas I/O
        LoggingReport.imprimir=true;
        //Comentar según dataset deseado
        TOPE_PEDIDOS=1;
//        cargarTropecientosPedidosConAlmacenesVuelosFijos();
        boolean ejecutarConArchivos=false; // poner en true cuando quieras meter todos los datos e inmediatamente en false tras ejecución
        boolean cargarAlmacenes = false;
        boolean cargarPedidos = false;
        boolean cargarVuelos = false;
        //  me parece que no detecta duplicados alguno de los métodos, por eso.
        if(!ejecutarConArchivos) return;
        System.out.println("Inicializando datos de prueba para generador de rutas...");

        // Determinar nombres/paths (args opcionales)
        String almacenesPath = args.length > 0 ? args[0] : DEFAULT_ALMACENES_FILE;
        String vuelosPath = args.length > 1 ? args[1] : DEFAULT_VUELOS_FILE;
        String pedidosPath = args.length > 2 ? args[2] : DEFAULT_PEDIDOS_FILE;

        // Fecha/mes/año para pedidos: por defecto hoy
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        try {
            // 1) Cargar almacenes
            if(cargarAlmacenes){
                System.out.println("InicializadorDeDatos: cargando almacenes desde '" + almacenesPath + "'...");
                try (InputStream is = Utils.openResourceAsStream(almacenesPath)) {
                    if (is == null) {
                        System.out.println("Archivo de almacenes no encontrado: " + almacenesPath);
                    } else {
                        ProcessResult resAlm = almacenService.cargarAlmacenesEnBDDesdeArchivoDelProfe(is);
                        System.out.println("Almacenes -> saved: " + resAlm.getSavedCount() + ", skipped: " + resAlm.getSkippedCount());
                        if (!resAlm.getErrors().isEmpty()) {
                            System.out.println("Almacenes - errores: " + resAlm.getErrors().size() + " (ver logs)");
                            resAlm.getErrors().forEach(e -> System.out.println("  " + e));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error procesando archivo almacenes: " + e.getMessage());
                    e.printStackTrace();
                }

                // 2) Cargar vuelos programados
                System.out.println("InicializadorDeDatos: cargando vuelos programados desde '" + vuelosPath + "'...");
                try (InputStream is = Utils.openResourceAsStream(vuelosPath)) {
                    if (is == null) {
                        System.out.println("Archivo de vuelos programados no encontrado: " + vuelosPath);
                    } else {
                        ProcessResult resVProg = vueloService.procesarArchivoPlanesVueloDelProfe(is);
                        System.out.println("VuelosProgramados -> saved: " + resVProg.getSavedCount() + ", skipped: " + resVProg.getSkippedCount());
                        if (!resVProg.getErrors().isEmpty()) {
                            System.out.println("VuelosProgramados - errores: " + resVProg.getErrors().size());
                            resVProg.getErrors().forEach(e -> System.out.println("  " + e));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error procesando archivo vuelos programados: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if(cargarVuelos) {
                // 3) Crear vuelos concretos (planchar para DIAS_ANADIR_A_VUELOS)
                System.out.println("InicializadorDeDatos: creando vuelos concretos para " + DIAS_ANADIR_A_VUELOS + " día(s) empezando hoy...");
                try {
                    LocalDate startDate = LocalDate.now(ZoneOffset.UTC);
                    LocalDate firstDayOfMonth = startDate.with(TemporalAdjusters.firstDayOfMonth());
                    ProcessResult resCreate = vueloService.createConcreteFlights(firstDayOfMonth, DIAS_ANADIR_A_VUELOS, false);
                    System.out.println("Vuelos concretos -> saved: " + resCreate.getSavedCount() + ", skipped: " + resCreate.getSkippedCount());
                    if (!resCreate.getErrors().isEmpty()) {
                        System.out.println("Vuelos concretos - errores: " + resCreate.getErrors().size());
                        resCreate.getErrors().forEach(e -> System.out.println("  " + e));
                    }
                } catch (Exception e) {
                    System.err.println("Error creando vuelos concretos: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            if((cargarPedidos)){

                // 4) Cargar pedidos
                System.out.println("InicializadorDeDatos: cargando pedidos desde '" + pedidosPath + "' (mes=" + month + ", year=" + year + ")...");
                try (InputStream is = Utils.openResourceAsStream(pedidosPath)) {
                    if (is == null) {
                        System.out.println("Archivo de pedidos no encontrado: " + pedidosPath);
                    } else {
                        ProcessResult resPedidos = pedidoService.processOrders(is, month, year);
                        System.out.println("Pedidos -> saved: " + resPedidos.getSavedCount() + ", skipped: " + resPedidos.getSkippedCount());
                        if (!resPedidos.getErrors().isEmpty()) {
                            System.out.println("Pedidos - errores: " + resPedidos.getErrors().size());
                            resPedidos.getErrors().forEach(e -> System.out.println("  " + e));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error procesando archivo pedidos: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("InicializadorDeDatos: inicialización terminada.");
        } catch (Exception e) {
            System.err.println("InicializadorDeDatos: error inesperado: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Data insertion complete during application startup.");
    }



    private void cargarDesdeArchivoCsv(String nombreArchivo) {
        return;
    }

    public void cargarTropecientosPedidosConAlmacenesVuelosFijos(){

// base time to make schedules reproducibles
        Instant base = Instant.now().plusSeconds(SEGUNDOS_ANADIR_A_VUELOS).truncatedTo(ChronoUnit.HOURS);

// // 1) ALMACENES (algunos infinitos, otros no)
//         AlmacenEntidad globalHub = createAlmacen("GLBH", "Global Hub City", "Global", "GLOBAL", true, 1_000_000, 0, -5, Continente.NORTEAMERICA);
//         AlmacenEntidad lima = createAlmacen("SPIM", "Lima", "Perú", "LIMA", false, 50_000, 0, -5, Continente.SUDAMERICA);
//         AlmacenEntidad bogota = createAlmacen("SPZO", "Bogotá", "Colombia", "BOGO", false, 5_000, 0, -5, Continente.SUDAMERICA);
//         AlmacenEntidad caracas = createAlmacen("SPQU", "Caracas", "Venezuela", "CARA", false, 6_000, 0, -5, Continente.SUDAMERICA);
//         AlmacenEntidad santiago = createAlmacen("SPRU", "Santiago", "Chile", "SANT", false, 4_000, 0, -5, Continente.SUDAMERICA);
//         AlmacenEntidad bruselas = createAlmacen("SPQT", "Bruselas", "Bélgica", "BRUS", false, 3_000, 0, -5, Continente.EUROPA);

// // another regional hub (infinite) for multi-continent tests
//         AlmacenEntidad regionalHub = createAlmacen("RGNH", "Regional Hub", "CountryX", "RREG", true, 500_000, 0, 0, Continente.EUROPA);


        // === Hubs ===
        AlmacenEntidad globalHub = createAlmacen("GLBH", "Global Hub City", "Global", "GLOBAL", true, 1_000_000, 0, -5, Continente.NORTEAMERICA);
        AlmacenEntidad megaHub   = createAlmacen("HINF", "Mega Global Hub", "Universal", "MEGA", true, 1_000_000, 0, 0, Continente.NORTEAMERICA);
        AlmacenEntidad euroHub   = createAlmacen("EHUB", "Euro Hub", "Europa", "EURO", true, 1_000_000, 0, +1, Continente.EUROPA);
        AlmacenEntidad asiaHub   = createAlmacen("AHUB", "Asia Hub", "Asia", "ASIA", true, 1_000_000, 0, +5, Continente.ASIA);

        // === 20 almacenes (20 países distintos) ===
        // América del Sur
        AlmacenEntidad bogota        = createAlmacen("SKBO", "Bogotá", "Colombia", "BOGO", false, 430, 0, -5, Continente.SUDAMERICA);
        AlmacenEntidad quito         = createAlmacen("SEQM", "Quito", "Ecuador", "QUIT", false, 410, 0, -5, Continente.SUDAMERICA);
        AlmacenEntidad lima          = createAlmacen("SPIM", "Lima", "Perú", "LIMA", false, 440, 0, -5, Continente.SUDAMERICA);
        AlmacenEntidad caracas       = createAlmacen("SVMI", "Caracas", "Venezuela", "CARA", false, 400, 0, -4, Continente.SUDAMERICA);
        AlmacenEntidad brasilia      = createAlmacen("SBBR", "Brasilia", "Brasil", "BRAS", false, 480, 0, -3, Continente.SUDAMERICA);
        AlmacenEntidad laPaz         = createAlmacen("SLLP", "La Paz", "Bolivia", "LAPA", false, 420, 0, -4, Continente.SUDAMERICA);
        AlmacenEntidad santiago      = createAlmacen("SCEL", "Santiago", "Chile", "SANT", false, 460, 0, -3, Continente.SUDAMERICA);
        AlmacenEntidad buenosAires   = createAlmacen("SAEZ", "Buenos Aires", "Argentina", "BUEN", false, 460, 0, -3, Continente.SUDAMERICA);
        AlmacenEntidad asuncion      = createAlmacen("SGAS", "Asunción", "Paraguay", "ASUN", false, 400, 0, -4, Continente.SUDAMERICA);
        AlmacenEntidad montevideo    = createAlmacen("SUMU", "Montevideo", "Uruguay", "MONT", false, 400, 0, -3, Continente.SUDAMERICA);

        // Europa
        AlmacenEntidad berlin        = createAlmacen("EDDB", "Berlín", "Alemania", "BERL", false, 480, 0, +2, Continente.EUROPA);
        AlmacenEntidad viena         = createAlmacen("LOWW", "Viena", "Austria", "VIEN", false, 430, 0, +2, Continente.EUROPA);
        AlmacenEntidad bruselas      = createAlmacen("EBBR", "Bruselas", "Bélgica", "BRUS", false, 440, 0, +2, Continente.EUROPA);
        AlmacenEntidad praga         = createAlmacen("LKPR", "Praga", "Chequia", "PRAG", false, 400, 0, +2, Continente.EUROPA);
        AlmacenEntidad amsterdam     = createAlmacen("EHAM", "Ámsterdam", "Países Bajos", "AMST", false, 480, 0, +2, Continente.EUROPA);

        // Asia
        AlmacenEntidad delhi         = createAlmacen("VIDP", "Delhi", "India", "DELH", false, 480, 0, +5, Continente.ASIA);
        AlmacenEntidad dubai         = createAlmacen("OMDB", "Dubái", "Emiratos Árabes Unidos", "DUBA", false, 420, 0, +4, Continente.ASIA);
        AlmacenEntidad karachi       = createAlmacen("OPKC", "Karachi", "Pakistán", "KARA", false, 410, 0, +5, Continente.ASIA);
        AlmacenEntidad baku          = createAlmacen("UBBB", "Bakú", "Azerbaiyán", "BAKU", false, 400, 0, +4, Continente.ASIA);
        AlmacenEntidad seoul         = createAlmacen("RKSI", "Seúl", "Corea del Sur", "SEUL", false, 470, 0, +9, Continente.ASIA);


// save all almacenes
        List<AlmacenEntidad> almacenes = List.of(
                // Hubs
                globalHub, megaHub, euroHub, asiaHub,

                // América del Sur
                bogota, quito, lima, caracas, brasilia,
                laPaz, santiago, buenosAires, asuncion, montevideo,

                // Europa
                berlin, viena, bruselas, praga, amsterdam,

                // Asia
                delhi, dubai, karachi, baku, seoul
        );
        almacenRepository.saveAll(almacenes);

// 2) VUELOS (conexiones). Diseñados para producir:
// - rutas obvias: directas desde hubs a destinos (ej. GLBH -> SPZO (Cusco) directo)
// - rutas alternativas: GLBH -> SPIM (Lima) -> SPZO (Cusco) o GLBH -> SPRU -> SPQU -> SPZO
// - rutas buenas pero no obvias: conexiones entre hubs/regional + salto local



// // From Global Hub -> Lima (big capacity, frequent)

//         vuelos.add(createVuelo("GH-LIM-02", globalHub, lima, 1800, base.plusSeconds(12*3600), base.plusSeconds(14*3600)));


// // From Lima -> Cusco (direct, low capacity — "ruta obvia" pero limitada)
//         vuelos.add(createVuelo("LIM-CUZ-01", lima, bogota, 120, base.plusSeconds(9*3600), base.plusSeconds(10*3600).plusSeconds(30*60)));
//         vuelos.add(createVuelo("LIM-CUZ-02", lima, bogota, 80, base.plusSeconds(15*3600), base.plusSeconds(16*3600).plusSeconds(30*60)));


// // From Global Hub -> Cusco (direct but rarer, medium capacity) -> 'ruta obvia'
//         vuelos.add(createVuelo("GH-CUZ-01", globalHub, bogota, 400, base.plusSeconds(5*3600), base.plusSeconds(7*3600).plusSeconds(30*60)));


// // // Global Hub -> Arequipa -> Cusco chain (non-obvious option)
//          vuelos.add(createVuelo("GH-ARE-01", globalHub, caracas, 600, base.plusSeconds(7*3600), base.plusSeconds(9*3600)));
//          vuelos.add(createVuelo("ARE-CUZ-01", caracas, bogota, 150, base.plusSeconds(11*3600), base.plusSeconds(12*3600).plusSeconds(30*60)));


// // // Global Hub -> Trujillo -> Arequipa -> Cusco (longer path, useful if others full)
//          vuelos.add(createVuelo("GH-TRU-01", globalHub, santiago, 500, base.plusSeconds(8*3600), base.plusSeconds(10*3600)));
//          vuelos.add(createVuelo("GH-TRU-02", globalHub, santiago, 1000, base.plusSeconds(8*3600), base.plusSeconds(10*3600)));
//          vuelos.add(createVuelo("TRU-ARE-01", santiago, caracas, 100, base.plusSeconds(11*3600), base.plusSeconds(12*3600)));
//          vuelos.add(createVuelo("TRU-ARE-02", santiago, caracas, 60, base.plusSeconds(17*3600), base.plusSeconds(18*3600)));


// // // Lima -> Arequipa (alternative connector)
//          vuelos.add(createVuelo("LIM-ARE-01", lima, caracas, 200, base.plusSeconds(13*3600), base.plusSeconds(14*3600).plusSeconds(30*60)));


// // // Some local flights with very small capacity to force splitting
//          vuelos.add(createVuelo("ARE-CUZ-02", caracas, bogota, 30, base.plusSeconds(19*3600), base.plusSeconds(20*3600).plusSeconds(30*60)));


// // // Iquitos isolated: only via Lima or Global hub (test longer routing)
//          vuelos.add(createVuelo("GH-IQT-01", globalHub, bruselas, 300, base.plusSeconds(6*3600), base.plusSeconds(10*3600)));
//          vuelos.add(createVuelo("LIM-IQT-01", lima, bruselas, 80, base.plusSeconds(10*3600), base.plusSeconds(14*3600)));


// // // Some flights that are full (capacidad = 0 available) to force algorithm to avoid
//          VueloEntidad fullFlight = createVuelo("FULL-1", globalHub, bogota, 0, base.plusSeconds(3*3600), base.plusSeconds(5*3600));
//          vuelos.add(fullFlight);


// // // Inter-hub flight (regional)
//          vuelos.add(createVuelo("GH-RG-01", globalHub, regionalHub, 1000, base.plusSeconds(2*3600), base.plusSeconds(8*3600)));
//          vuelos.add(createVuelo("RG-GH-01", regionalHub, globalHub, 1000, base.plusSeconds(22*3600), base.plusSeconds(4*3600).plusSeconds(1*3600*24)));

// // // vuelo extra a bogota
//          vuelos.add(createVuelo("RG-BOG-01", regionalHub, bogota, 500, base.plusSeconds(9*3600), base.plusSeconds(15*3600)));


//         // VueloEntidad 1: HUB -> Bogotá
//         vuelos.add(createVuelo(
//                 "GH-BOG-01",         // código del vuelo
//                 globalHub,                 // origen
//                 santiago,               // destino
//                 1200,                  // capacidad
//                 base.plusSeconds(8*3600),                    // salida (8 horas desde base)
//                 base.plusSeconds(8*3600).plusSeconds(2*3600) // llegada (2h de vuelo)
//         ));

//         // VueloEntidad 2: Bogotá -> Santiago
//         vuelos.add(createVuelo(
//                 "BOG-SCL-01",
//                 santiago,
//                 bogota,
//                 1000,
//                 base.plusSeconds(11*3600),                   // salida (deja ~1h de conexión)
//                 base.plusSeconds(11*3600).plusSeconds(5*3600) // llegada (5h de vuelo)
//         ));

        List<VueloEntidad> vuelos = new ArrayList<>();

        // === 200 vuelos (capacidad ≤ 600) ===
        vuelos.add(createVuelo("GL-LIM-001", globalHub, lima, 401, base.plusSeconds(167621), base.plusSeconds(185813)));
        vuelos.add(createVuelo("GL-BOG-002", globalHub, bogota, 402, base.plusSeconds(6556), base.plusSeconds(82253)));
        vuelos.add(createVuelo("GL-QUI-003", globalHub, quito, 403, base.plusSeconds(64196), base.plusSeconds(126309)));
        vuelos.add(createVuelo("GL-CAR-004", globalHub, caracas, 404, base.plusSeconds(36579), base.plusSeconds(67047)));
        vuelos.add(createVuelo("GL-BRA-005", globalHub, brasilia, 405, base.plusSeconds(177392), base.plusSeconds(252474)));
        vuelos.add(createVuelo("GL-LAP-006", globalHub, laPaz, 406, base.plusSeconds(22790), base.plusSeconds(181184)));
        vuelos.add(createVuelo("GL-SAN-007", globalHub, santiago, 407, base.plusSeconds(110604), base.plusSeconds(122535)));
        vuelos.add(createVuelo("GL-BUE-008", globalHub, buenosAires, 408, base.plusSeconds(7811), base.plusSeconds(35972)));
        vuelos.add(createVuelo("GL-ASU-009", globalHub, asuncion, 409, base.plusSeconds(57314), base.plusSeconds(121904)));
        vuelos.add(createVuelo("GL-MON-010", globalHub, montevideo, 410, base.plusSeconds(132475), base.plusSeconds(214982)));
        vuelos.add(createVuelo("GL-BER-011", globalHub, berlin, 411, base.plusSeconds(6956), base.plusSeconds(157683)));
        vuelos.add(createVuelo("GL-VIE-012", globalHub, viena, 412, base.plusSeconds(52124), base.plusSeconds(198577)));
        vuelos.add(createVuelo("GL-BRU-013", globalHub, bruselas, 413, base.plusSeconds(3600*2), base.plusSeconds(3600*3)));
        vuelos.add(createVuelo("GL-BRU-a14", globalHub, bruselas, 413, base.plusSeconds(3600*1), base.plusSeconds(3600*2)));
        vuelos.add(createVuelo("GL-BRU-b15", globalHub, bruselas, 413, base.plusSeconds((long) (3600*1.2)), base.plusSeconds((long) (3600*1.5))));
        vuelos.add(createVuelo("GL-PRA-014", globalHub, praga, 414, base.plusSeconds(117757), base.plusSeconds(194283)));
        vuelos.add(createVuelo("GL-AMS-015", globalHub, amsterdam, 415, base.plusSeconds(1703), base.plusSeconds(47156)));
        vuelos.add(createVuelo("GL-DEL-016", globalHub, delhi, 416, base.plusSeconds(183013), base.plusSeconds(242005)));
        vuelos.add(createVuelo("GL-DUB-017", globalHub, dubai, 417, base.plusSeconds(62104), base.plusSeconds(195405)));
        vuelos.add(createVuelo("GL-KAR-018", globalHub, karachi, 418, base.plusSeconds(122421), base.plusSeconds(193093)));
        vuelos.add(createVuelo("GL-BAK-019", globalHub, baku, 419, base.plusSeconds(24602), base.plusSeconds(125949)));
        vuelos.add(createVuelo("GL-SEO-020", globalHub, seoul, 420, base.plusSeconds(126876), base.plusSeconds(189615)));
        vuelos.add(createVuelo("ME-LIM-021", megaHub, lima, 421, base.plusSeconds(16709), base.plusSeconds(34408)));
        vuelos.add(createVuelo("ME-BOG-022", megaHub, bogota, 422, base.plusSeconds(193880), base.plusSeconds(241026)));
        vuelos.add(createVuelo("ME-QUI-023", megaHub, quito, 423, base.plusSeconds(109313), base.plusSeconds(187091)));
        vuelos.add(createVuelo("ME-CAR-024", megaHub, caracas, 424, base.plusSeconds(44973), base.plusSeconds(96807)));
        vuelos.add(createVuelo("ME-BRA-025", megaHub, brasilia, 425, base.plusSeconds(78973), base.plusSeconds(158545)));
        vuelos.add(createVuelo("ME-LAP-026", megaHub, laPaz, 426, base.plusSeconds(62945), base.plusSeconds(147740)));
        vuelos.add(createVuelo("ME-SAN-027", megaHub, santiago, 427, base.plusSeconds(178119), base.plusSeconds(253957)));
        vuelos.add(createVuelo("ME-BUE-028", megaHub, buenosAires, 428, base.plusSeconds(1877), base.plusSeconds(56696)));
        vuelos.add(createVuelo("ME-ASU-029", megaHub, asuncion, 429, base.plusSeconds(17536), base.plusSeconds(63877)));
        vuelos.add(createVuelo("ME-MON-030", megaHub, montevideo, 430, base.plusSeconds(77729), base.plusSeconds(122581)));
        vuelos.add(createVuelo("ME-BER-031", megaHub, berlin, 431, base.plusSeconds(43378), base.plusSeconds(112824)));
        vuelos.add(createVuelo("ME-VIE-032", megaHub, viena, 432, base.plusSeconds(142772), base.plusSeconds(189129)));
        vuelos.add(createVuelo("ME-BRU-033", megaHub, bruselas, 433, base.plusSeconds(189125), base.plusSeconds(341881)));
        vuelos.add(createVuelo("ME-PRA-034", megaHub, praga, 434, base.plusSeconds(149891), base.plusSeconds(210957)));
        vuelos.add(createVuelo("ME-AMS-035", megaHub, amsterdam, 435, base.plusSeconds(102591), base.plusSeconds(147719)));
        vuelos.add(createVuelo("ME-DEL-036", megaHub, delhi, 436, base.plusSeconds(48122), base.plusSeconds(121222)));
        vuelos.add(createVuelo("ME-DUB-037", megaHub, dubai, 437, base.plusSeconds(93132), base.plusSeconds(151653)));
        vuelos.add(createVuelo("ME-KAR-038", megaHub, karachi, 438, base.plusSeconds(175682), base.plusSeconds(214275)));
        vuelos.add(createVuelo("ME-BAK-039", megaHub, baku, 439, base.plusSeconds(183977), base.plusSeconds(196935)));
        vuelos.add(createVuelo("ME-SEO-040", megaHub, seoul, 440, base.plusSeconds(159680), base.plusSeconds(246507)));
        vuelos.add(createVuelo("EU-LIM-041", euroHub, lima, 441, base.plusSeconds(44862), base.plusSeconds(188483)));
        vuelos.add(createVuelo("EU-BOG-042", euroHub, bogota, 442, base.plusSeconds(191136), base.plusSeconds(210779)));
        vuelos.add(createVuelo("EU-QUI-043", euroHub, quito, 443, base.plusSeconds(42834), base.plusSeconds(167612)));
        vuelos.add(createVuelo("EU-CAR-044", euroHub, caracas, 444, base.plusSeconds(99471), base.plusSeconds(173836)));
        vuelos.add(createVuelo("EU-BRA-045", euroHub, brasilia, 445, base.plusSeconds(167772), base.plusSeconds(244372)));
        vuelos.add(createVuelo("EU-LAP-046", euroHub, laPaz, 446, base.plusSeconds(57570), base.plusSeconds(146178)));
        vuelos.add(createVuelo("EU-SAN-047", euroHub, santiago, 447, base.plusSeconds(14663), base.plusSeconds(78306)));
        vuelos.add(createVuelo("EU-BUE-048", euroHub, buenosAires, 448, base.plusSeconds(8414), base.plusSeconds(94708)));
        vuelos.add(createVuelo("EU-ASU-049", euroHub, asuncion, 449, base.plusSeconds(105162), base.plusSeconds(178948)));
        vuelos.add(createVuelo("EU-MON-050", euroHub, montevideo, 450, base.plusSeconds(17350), base.plusSeconds(76257)));
        vuelos.add(createVuelo("EU-BER-051", euroHub, berlin, 451, base.plusSeconds(148682), base.plusSeconds(246380)));
        // 50 aprox VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
        vuelos.add(createVuelo("EU-VIE-052", euroHub, viena, 452, base.plusSeconds(194400), base.plusSeconds(216000)));
        vuelos.add(createVuelo("EU-BRU-053", euroHub, bruselas, 453, base.plusSeconds(198000), base.plusSeconds(223200)));
        vuelos.add(createVuelo("EU-PRA-054", euroHub, praga, 454, base.plusSeconds(201600), base.plusSeconds(208800)));
        vuelos.add(createVuelo("EU-AMS-055", euroHub, amsterdam, 455, base.plusSeconds(205200), base.plusSeconds(216000)));
        vuelos.add(createVuelo("EU-DEL-056", euroHub, delhi, 456, base.plusSeconds(208800), base.plusSeconds(223200)));
        vuelos.add(createVuelo("EU-DUB-057", euroHub, dubai, 457, base.plusSeconds(212400), base.plusSeconds(230400)));
        vuelos.add(createVuelo("EU-KAR-058", euroHub, karachi, 458, base.plusSeconds(216000), base.plusSeconds(237600)));
        vuelos.add(createVuelo("EU-BAK-059", euroHub, baku, 459, base.plusSeconds(219600), base.plusSeconds(244800)));
        vuelos.add(createVuelo("EU-SEO-060", euroHub, seoul, 460, base.plusSeconds(223200), base.plusSeconds(230400)));
        vuelos.add(createVuelo("AS-LIM-061", asiaHub, lima, 461, base.plusSeconds(226800), base.plusSeconds(237600)));
        vuelos.add(createVuelo("AS-BOG-062", asiaHub, bogota, 462, base.plusSeconds(230400), base.plusSeconds(244800)));
        vuelos.add(createVuelo("AS-QUI-063", asiaHub, quito, 463, base.plusSeconds(234000), base.plusSeconds(252000)));
        vuelos.add(createVuelo("AS-CAR-064", asiaHub, caracas, 464, base.plusSeconds(237600), base.plusSeconds(259200)));
        vuelos.add(createVuelo("AS-BRA-065", asiaHub, brasilia, 465, base.plusSeconds(241200), base.plusSeconds(266400)));
        vuelos.add(createVuelo("AS-LAP-066", asiaHub, laPaz, 466, base.plusSeconds(244800), base.plusSeconds(252000)));
        vuelos.add(createVuelo("AS-SAN-067", asiaHub, santiago, 467, base.plusSeconds(248400), base.plusSeconds(259200)));
        vuelos.add(createVuelo("AS-BUE-068", asiaHub, buenosAires, 468, base.plusSeconds(252000), base.plusSeconds(266400)));
        vuelos.add(createVuelo("AS-ASU-069", asiaHub, asuncion, 469, base.plusSeconds(255600), base.plusSeconds(273600)));
        vuelos.add(createVuelo("AS-MON-070", asiaHub, montevideo, 470, base.plusSeconds(259200), base.plusSeconds(280800)));
        vuelos.add(createVuelo("AS-BER-071", asiaHub, berlin, 471, base.plusSeconds(262800), base.plusSeconds(288000)));
        vuelos.add(createVuelo("AS-VIE-072", asiaHub, viena, 472, base.plusSeconds(266400), base.plusSeconds(273600)));
        vuelos.add(createVuelo("AS-BRU-073", asiaHub, bruselas, 473, base.plusSeconds(270000), base.plusSeconds(280800)));
        vuelos.add(createVuelo("AS-PRA-074", asiaHub, praga, 474, base.plusSeconds(273600), base.plusSeconds(288000)));
        vuelos.add(createVuelo("AS-AMS-075", asiaHub, amsterdam, 475, base.plusSeconds(277200), base.plusSeconds(295200)));
        vuelos.add(createVuelo("AS-DEL-076", asiaHub, delhi, 476, base.plusSeconds(280800), base.plusSeconds(302400)));
        vuelos.add(createVuelo("AS-DUB-077", asiaHub, dubai, 477, base.plusSeconds(284400), base.plusSeconds(309600)));
        vuelos.add(createVuelo("AS-KAR-078", asiaHub, karachi, 478, base.plusSeconds(288000), base.plusSeconds(295200)));
        vuelos.add(createVuelo("AS-BAK-079", asiaHub, baku, 479, base.plusSeconds(291600), base.plusSeconds(302400)));
        vuelos.add(createVuelo("AS-SEO-080", asiaHub, seoul, 480, base.plusSeconds(295200), base.plusSeconds(309600)));
        vuelos.add(createVuelo("LIM-BOG-081", lima, bogota, 461, base.plusSeconds(115200), base.plusSeconds(126000)));
        vuelos.add(createVuelo("BOG-QUI-082", bogota, quito, 462, base.plusSeconds(118800), base.plusSeconds(133200)));
        vuelos.add(createVuelo("QUI-CAR-083", quito, caracas, 463, base.plusSeconds(122400), base.plusSeconds(140400)));
        vuelos.add(createVuelo("CAR-BRA-084", caracas, brasilia, 464, base.plusSeconds(126000), base.plusSeconds(147600)));
        vuelos.add(createVuelo("BRA-LAP-085", brasilia, laPaz, 465, base.plusSeconds(129600), base.plusSeconds(136800)));
        vuelos.add(createVuelo("LAP-SAN-086", laPaz, santiago, 466, base.plusSeconds(133200), base.plusSeconds(144000)));
        vuelos.add(createVuelo("SAN-BUE-087", santiago, buenosAires, 467, base.plusSeconds(136800), base.plusSeconds(151200)));
        vuelos.add(createVuelo("BUE-ASU-088", buenosAires, asuncion, 468, base.plusSeconds(140400), base.plusSeconds(158400)));
        vuelos.add(createVuelo("ASU-MON-089", asuncion, montevideo, 469, base.plusSeconds(144000), base.plusSeconds(165600)));
        vuelos.add(createVuelo("MON-LIM-090", montevideo, lima, 470, base.plusSeconds(147600), base.plusSeconds(154800)));
        vuelos.add(createVuelo("BER-VIE-091", berlin, viena, 471, base.plusSeconds(151200), base.plusSeconds(162000)));
        vuelos.add(createVuelo("VIE-PRA-092", viena, praga, 472, base.plusSeconds(154800), base.plusSeconds(169200)));
        vuelos.add(createVuelo("PRA-AMS-093", praga, amsterdam, 473, base.plusSeconds(158400), base.plusSeconds(176400)));
        vuelos.add(createVuelo("AMS-BRU-094", amsterdam, bruselas, 474, base.plusSeconds(162000), base.plusSeconds(183600)));
        vuelos.add(createVuelo("BRU-BER-095", bruselas, berlin, 475, base.plusSeconds(165600), base.plusSeconds(172800)));
        vuelos.add(createVuelo("DEL-DUB-096", delhi, dubai, 476, base.plusSeconds(169200), base.plusSeconds(180000)));
        vuelos.add(createVuelo("DUB-KAR-097", dubai, karachi, 477, base.plusSeconds(172800), base.plusSeconds(187200)));
        vuelos.add(createVuelo("KAR-BAK-098", karachi, baku, 478, base.plusSeconds(176400), base.plusSeconds(194400)));
        vuelos.add(createVuelo("BAK-SEO-099", baku, seoul, 479, base.plusSeconds(180000), base.plusSeconds(201600)));
        vuelos.add(createVuelo("SEO-DEL-100", seoul, delhi, 480, base.plusSeconds(3600), base.plusSeconds(10800)));
        vuelos.add(createVuelo("LIM-BOG-101", lima, bogota, 481, base.plusSeconds(7200), base.plusSeconds(18000)));
        //100 aprox
        vuelos.add(createVuelo("BOG-QUI-102", bogota, quito, 482, base.plusSeconds(10800), base.plusSeconds(25200)));
        vuelos.add(createVuelo("QUI-CAR-103", quito, caracas, 483, base.plusSeconds(14400), base.plusSeconds(32400)));
        vuelos.add(createVuelo("CAR-BRA-104", caracas, brasilia, 484, base.plusSeconds(18000), base.plusSeconds(39600)));
        vuelos.add(createVuelo("BRA-LAP-105", brasilia, laPaz, 485, base.plusSeconds(21600), base.plusSeconds(28800)));
        vuelos.add(createVuelo("LAP-SAN-106", laPaz, santiago, 486, base.plusSeconds(25200), base.plusSeconds(36000)));
        vuelos.add(createVuelo("SAN-BUE-107", santiago, buenosAires, 487, base.plusSeconds(28800), base.plusSeconds(43200)));
        vuelos.add(createVuelo("BUE-ASU-108", buenosAires, asuncion, 488, base.plusSeconds(32400), base.plusSeconds(50400)));
        vuelos.add(createVuelo("ASU-MON-109", asuncion, montevideo, 489, base.plusSeconds(36000), base.plusSeconds(57600)));
        vuelos.add(createVuelo("MON-LIM-110", montevideo, lima, 490, base.plusSeconds(39600), base.plusSeconds(46800)));
        vuelos.add(createVuelo("BER-VIE-111", berlin, viena, 491, base.plusSeconds(43200), base.plusSeconds(54000)));
        vuelos.add(createVuelo("VIE-PRA-112", viena, praga, 492, base.plusSeconds(46800), base.plusSeconds(61200)));
        vuelos.add(createVuelo("PRA-AMS-113", praga, amsterdam, 493, base.plusSeconds(50400), base.plusSeconds(68400)));
        vuelos.add(createVuelo("AMS-BRU-114", amsterdam, bruselas, 494, base.plusSeconds(54000), base.plusSeconds(75600)));
        vuelos.add(createVuelo("BRU-BER-115", bruselas, berlin, 495, base.plusSeconds(57600), base.plusSeconds(64800)));
        vuelos.add(createVuelo("DEL-DUB-116", delhi, dubai, 496, base.plusSeconds(61200), base.plusSeconds(72000)));
        vuelos.add(createVuelo("DUB-KAR-117", dubai, karachi, 497, base.plusSeconds(64800), base.plusSeconds(79200)));
        vuelos.add(createVuelo("KAR-BAK-118", karachi, baku, 498, base.plusSeconds(68400), base.plusSeconds(86400)));
        vuelos.add(createVuelo("BAK-SEO-119", baku, seoul, 499, base.plusSeconds(72000), base.plusSeconds(93600)));
        vuelos.add(createVuelo("SEO-DEL-120", seoul, delhi, 500, base.plusSeconds(75600), base.plusSeconds(82800)));
        vuelos.add(createVuelo("LIM-BOG-121", lima, bogota, 501, base.plusSeconds(79200), base.plusSeconds(90000)));
        vuelos.add(createVuelo("BOG-QUI-122", bogota, quito, 502, base.plusSeconds(82800), base.plusSeconds(97200)));
        vuelos.add(createVuelo("QUI-CAR-123", quito, caracas, 503, base.plusSeconds(86400), base.plusSeconds(104400)));
        vuelos.add(createVuelo("CAR-BRA-124", caracas, brasilia, 504, base.plusSeconds(90000), base.plusSeconds(111600)));
        vuelos.add(createVuelo("BRA-LAP-125", brasilia, laPaz, 505, base.plusSeconds(93600), base.plusSeconds(100800)));
        vuelos.add(createVuelo("LAP-SAN-126", laPaz, santiago, 506, base.plusSeconds(97200), base.plusSeconds(108000)));
        vuelos.add(createVuelo("SAN-BUE-127", santiago, buenosAires, 507, base.plusSeconds(100800), base.plusSeconds(115200)));
        vuelos.add(createVuelo("BUE-ASU-128", buenosAires, asuncion, 508, base.plusSeconds(104400), base.plusSeconds(122400)));
        vuelos.add(createVuelo("ASU-MON-129", asuncion, montevideo, 509, base.plusSeconds(108000), base.plusSeconds(129600)));
        vuelos.add(createVuelo("MON-LIM-130", montevideo, lima, 510, base.plusSeconds(111600), base.plusSeconds(118800)));
        vuelos.add(createVuelo("BER-VIE-131", berlin, viena, 511, base.plusSeconds(115200), base.plusSeconds(126000)));
        vuelos.add(createVuelo("VIE-PRA-132", viena, praga, 512, base.plusSeconds(118800), base.plusSeconds(133200)));
        vuelos.add(createVuelo("PRA-AMS-133", praga, amsterdam, 513, base.plusSeconds(122400), base.plusSeconds(140400)));
        vuelos.add(createVuelo("AMS-BRU-134", amsterdam, bruselas, 514, base.plusSeconds(126000), base.plusSeconds(147600)));
        vuelos.add(createVuelo("BRU-BER-135", bruselas, berlin, 515, base.plusSeconds(129600), base.plusSeconds(136800)));
        vuelos.add(createVuelo("DEL-DUB-136", delhi, dubai, 516, base.plusSeconds(133200), base.plusSeconds(144000)));
        vuelos.add(createVuelo("DUB-KAR-137", dubai, karachi, 517, base.plusSeconds(136800), base.plusSeconds(151200)));
        vuelos.add(createVuelo("KAR-BAK-138", karachi, baku, 518, base.plusSeconds(140400), base.plusSeconds(158400)));
        vuelos.add(createVuelo("BAK-SEO-139", baku, seoul, 519, base.plusSeconds(144000), base.plusSeconds(165600)));
        vuelos.add(createVuelo("SEO-DEL-140", seoul, delhi, 520, base.plusSeconds(147600), base.plusSeconds(154800)));
        vuelos.add(createVuelo("LIM-BOG-141", lima, bogota, 521, base.plusSeconds(151200), base.plusSeconds(162000)));
        vuelos.add(createVuelo("BOG-QUI-142", bogota, quito, 522, base.plusSeconds(154800), base.plusSeconds(169200)));
        vuelos.add(createVuelo("QUI-CAR-143", quito, caracas, 523, base.plusSeconds(158400), base.plusSeconds(176400)));
        vuelos.add(createVuelo("CAR-BRA-144", caracas, brasilia, 524, base.plusSeconds(162000), base.plusSeconds(183600)));
        vuelos.add(createVuelo("BRA-LAP-145", brasilia, laPaz, 525, base.plusSeconds(165600), base.plusSeconds(172800)));
        vuelos.add(createVuelo("LAP-SAN-146", laPaz, santiago, 526, base.plusSeconds(169200), base.plusSeconds(180000)));
        vuelos.add(createVuelo("SAN-BUE-147", santiago, buenosAires, 527, base.plusSeconds(172800), base.plusSeconds(187200)));
        vuelos.add(createVuelo("BUE-ASU-148", buenosAires, asuncion, 528, base.plusSeconds(176400), base.plusSeconds(194400)));
        vuelos.add(createVuelo("ASU-MON-149", asuncion, montevideo, 529, base.plusSeconds(180000), base.plusSeconds(201600)));
        vuelos.add(createVuelo("MON-LIM-150", montevideo, lima, 530, base.plusSeconds(3600), base.plusSeconds(10800)));
        vuelos.add(createVuelo("BER-VIE-151", berlin, viena, 531, base.plusSeconds(7200), base.plusSeconds(18000)));
        vuelos.add(createVuelo("VIE-PRA-152", viena, praga, 532, base.plusSeconds(10800), base.plusSeconds(25200)));
        vuelos.add(createVuelo("PRA-AMS-153", praga, amsterdam, 533, base.plusSeconds(14400), base.plusSeconds(32400)));
        vuelos.add(createVuelo("AMS-BRU-154", amsterdam, bruselas, 534, base.plusSeconds(18000), base.plusSeconds(3600*42)));
        vuelos.add(createVuelo("BRU-BER-155", bruselas, berlin, 535, base.plusSeconds(21600), base.plusSeconds(28800)));
        vuelos.add(createVuelo("DEL-DUB-156", delhi, dubai, 536, base.plusSeconds(25200), base.plusSeconds(36000)));
        vuelos.add(createVuelo("DUB-KAR-157", dubai, karachi, 537, base.plusSeconds(28800), base.plusSeconds(43200)));
        vuelos.add(createVuelo("KAR-BAK-158", karachi, baku, 538, base.plusSeconds(32400), base.plusSeconds(50400)));
        vuelos.add(createVuelo("BAK-SEO-159", baku, seoul, 539, base.plusSeconds(36000), base.plusSeconds(57600)));
        vuelos.add(createVuelo("SEO-DEL-160", seoul, delhi, 540, base.plusSeconds(39600), base.plusSeconds(46800)));
        vuelos.add(createVuelo("GH-LIM-201", globalHub, lima, 480, base.plusSeconds(2*3600), base.plusSeconds(4*3600)));
        vuelos.add(createVuelo("GH-BOG-202", globalHub, bogota, 520, base.plusSeconds(3*3600), base.plusSeconds(5*3600)));
        vuelos.add(createVuelo("GH-QUI-203", globalHub, quito, 500, base.plusSeconds(4*3600), base.plusSeconds(6*3600)));
        vuelos.add(createVuelo("GH-CAR-204", globalHub, caracas, 560, base.plusSeconds(5*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("GH-BRA-205", globalHub, brasilia, 540, base.plusSeconds(6*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("GH-SCL-206", globalHub, santiago, 600, base.plusSeconds(7*3600), base.plusSeconds(10*3600)));
        vuelos.add(createVuelo("GH-BUE-207", globalHub, buenosAires, 580, base.plusSeconds(8*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("GH-ASU-208", globalHub, asuncion, 500, base.plusSeconds(9*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("GH-MVD-209", globalHub, montevideo, 520, base.plusSeconds(10*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("GH-BER-210", globalHub, berlin, 560, base.plusSeconds(12*3600), base.plusSeconds(18*3600)));
        //  kkkkkkkkkkkkk 50 50 50 50 aAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        vuelos.add(createVuelo("MH-LIM-211", megaHub, lima, 580, base.plusSeconds(2*3600), base.plusSeconds(6*3600)));
        vuelos.add(createVuelo("MH-BOG-212", megaHub, bogota, 520, base.plusSeconds(3*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("MH-QUI-213", megaHub, quito, 500, base.plusSeconds(4*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("MH-CAR-214", megaHub, caracas, 560, base.plusSeconds(5*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("MH-BRA-215", megaHub, brasilia, 540, base.plusSeconds(6*3600), base.plusSeconds(10*3600)));
        vuelos.add(createVuelo("MH-SCL-216", megaHub, santiago, 600, base.plusSeconds(7*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("MH-BUE-217", megaHub, buenosAires, 580, base.plusSeconds(8*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("MH-ASU-218", megaHub, asuncion, 500, base.plusSeconds(9*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("MH-MVD-219", megaHub, montevideo, 520, base.plusSeconds(10*3600), base.plusSeconds(14*3600)));
        vuelos.add(createVuelo("MH-BRU-220", megaHub, bruselas, 560, base.plusSeconds(11*3600), base.plusSeconds(38*3600)));

        vuelos.add(createVuelo("EH-BER-221", euroHub, berlin, 420, base.plusSeconds(2*3600), base.plusSeconds(4*3600)));
        vuelos.add(createVuelo("EH-VIE-222", euroHub, viena, 460, base.plusSeconds(3*3600), base.plusSeconds(5*3600)));
        vuelos.add(createVuelo("EH-BRU-223", euroHub, bruselas, 440, base.plusSeconds(4*3600), base.plusSeconds(24*3600)));
        vuelos.add(createVuelo("EH-PRG-224", euroHub, praga, 480, base.plusSeconds(5*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("EH-AMS-225", euroHub, amsterdam, 460, base.plusSeconds(6*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("EH-LIM-226", euroHub, lima, 580, base.plusSeconds(8*3600), base.plusSeconds(16*3600)));
        vuelos.add(createVuelo("EH-BOG-227", euroHub, bogota, 560, base.plusSeconds(9*3600), base.plusSeconds(17*3600)));
        vuelos.add(createVuelo("EH-SCL-228", euroHub, santiago, 600, base.plusSeconds(10*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("EH-BUE-229", euroHub, buenosAires, 580, base.plusSeconds(11*3600), base.plusSeconds(19*3600)));
        vuelos.add(createVuelo("EH-DEL-230", euroHub, delhi, 600, base.plusSeconds(12*3600), base.plusSeconds(20*3600)));

        vuelos.add(createVuelo("AH-DEL-231", asiaHub, delhi, 560, base.plusSeconds(2*3600), base.plusSeconds(6*3600)));
        vuelos.add(createVuelo("AH-DXB-232", asiaHub, dubai, 540, base.plusSeconds(3*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("AH-KHI-233", asiaHub, karachi, 520, base.plusSeconds(4*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("AH-BAK-234", asiaHub, baku, 500, base.plusSeconds(5*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("AH-SEL-235", asiaHub, seoul, 580, base.plusSeconds(6*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("AH-LIM-236", asiaHub, lima, 600, base.plusSeconds(8*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("AH-BOG-237", asiaHub, bogota, 580, base.plusSeconds(9*3600), base.plusSeconds(19*3600)));
        vuelos.add(createVuelo("AH-SCL-238", asiaHub, santiago, 560, base.plusSeconds(10*3600), base.plusSeconds(20*3600)));
        vuelos.add(createVuelo("AH-BUE-239", asiaHub, buenosAires, 540, base.plusSeconds(11*3600), base.plusSeconds(21*3600)));
        vuelos.add(createVuelo("AH-BER-240", asiaHub, berlin, 600, base.plusSeconds(12*3600), base.plusSeconds(22*3600)));

        vuelos.add(createVuelo("GH-DEL-241", globalHub, delhi, 600, base.plusSeconds(13*3600), base.plusSeconds(21*3600)));
        vuelos.add(createVuelo("GH-DXB-242", globalHub, dubai, 580, base.plusSeconds(14*3600), base.plusSeconds(22*3600)));
        vuelos.add(createVuelo("GH-KHI-243", globalHub, karachi, 560, base.plusSeconds(15*3600), base.plusSeconds(23*3600)));
        vuelos.add(createVuelo("GH-BAK-244", globalHub, baku, 540, base.plusSeconds(16*3600), base.plusSeconds(24*3600)));
        vuelos.add(createVuelo("GH-SEL-245", globalHub, seoul, 520, base.plusSeconds(17*3600), base.plusSeconds(27*3600)));

        vuelos.add(createVuelo("LIM-BOG-296", lima, bogota, 420, base.plusSeconds(2*3600), base.plusSeconds(4*3600)));
        vuelos.add(createVuelo("BOG-LIM-297", bogota, lima, 420, base.plusSeconds(5*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("BOG-QUI-298", bogota, quito, 380, base.plusSeconds(3*3600), base.plusSeconds(5*3600)));
        vuelos.add(createVuelo("QUI-BOG-299", quito, bogota, 380, base.plusSeconds(6*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("QUI-CAR-300", quito, caracas, 360, base.plusSeconds(4*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("CAR-QUI-301", caracas, quito, 360, base.plusSeconds(8*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("CAR-BRA-302", caracas, brasilia, 400, base.plusSeconds(5*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("BRA-CAR-303", brasilia, caracas, 400, base.plusSeconds(9*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("BRA-LAP-304", brasilia, laPaz, 420, base.plusSeconds(6*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("LAP-BRA-305", laPaz, brasilia, 420, base.plusSeconds(10*3600), base.plusSeconds(13*3600)));

        vuelos.add(createVuelo("LAP-SCL-306", laPaz, santiago, 380, base.plusSeconds(7*3600), base.plusSeconds(10*3600)));
        vuelos.add(createVuelo("SCL-LAP-307", santiago, laPaz, 380, base.plusSeconds(11*3600), base.plusSeconds(14*3600)));
        vuelos.add(createVuelo("SCL-BUE-308", santiago, buenosAires, 400, base.plusSeconds(8*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("BUE-SCL-309", buenosAires, santiago, 400, base.plusSeconds(12*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("BUE-ASU-310", buenosAires, asuncion, 360, base.plusSeconds(9*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("ASU-BUE-311", asuncion, buenosAires, 360, base.plusSeconds(13*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("ASU-MVD-312", asuncion, montevideo, 340, base.plusSeconds(10*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("MVD-ASU-313", montevideo, asuncion, 340, base.plusSeconds(14*3600), base.plusSeconds(16*3600)));
        vuelos.add(createVuelo("MVD-LIM-314", montevideo, lima, 420, base.plusSeconds(11*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("LIM-MVD-315", lima, montevideo, 420, base.plusSeconds(16*3600), base.plusSeconds(20*3600)));

        vuelos.add(createVuelo("LIM-SCL-316", lima, santiago, 440, base.plusSeconds(3*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("SCL-LIM-317", santiago, lima, 440, base.plusSeconds(8*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("SCL-BOG-318", santiago, bogota, 420, base.plusSeconds(4*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("BOG-SCL-319", bogota, santiago, 420, base.plusSeconds(10*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("BOG-BUE-320", bogota, buenosAires, 420, base.plusSeconds(5*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("BUE-BOG-321", buenosAires, bogota, 420, base.plusSeconds(12*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("BUE-QUI-322", buenosAires, quito, 400, base.plusSeconds(6*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("QUI-BUE-323", quito, buenosAires, 400, base.plusSeconds(13*3600), base.plusSeconds(19*3600)));
        vuelos.add(createVuelo("QUI-ASU-324", quito, asuncion, 380, base.plusSeconds(7*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("ASU-QUI-325", asuncion, quito, 380, base.plusSeconds(14*3600), base.plusSeconds(20*3600)));

        vuelos.add(createVuelo("ASU-CAR-326", asuncion, caracas, 360, base.plusSeconds(2*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("CAR-ASU-327", caracas, asuncion, 360, base.plusSeconds(9*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("CAR-MVD-328", caracas, montevideo, 400, base.plusSeconds(3*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("MVD-CAR-329", montevideo, caracas, 400, base.plusSeconds(10*3600), base.plusSeconds(16*3600)));
        vuelos.add(createVuelo("MVD-BRA-330", montevideo, brasilia, 420, base.plusSeconds(4*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("BRA-MVD-331", brasilia, montevideo, 420, base.plusSeconds(9*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("BRA-BOG-332", brasilia, bogota, 420, base.plusSeconds(5*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("BOG-BRA-333", bogota, brasilia, 420, base.plusSeconds(10*3600), base.plusSeconds(14*3600)));
        vuelos.add(createVuelo("BOG-LIM-334", bogota, lima, 420, base.plusSeconds(6*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("LIM-BOG-335", lima, bogota, 420, base.plusSeconds(11*3600), base.plusSeconds(13*3600)));

        vuelos.add(createVuelo("BER-VIE-336", berlin, viena, 300, base.plusSeconds(2*3600), base.plusSeconds(4*3600)));
        vuelos.add(createVuelo("VIE-PRG-337", viena, praga, 300, base.plusSeconds(5*3600), base.plusSeconds(7*3600)));
        vuelos.add(createVuelo("PRG-AMS-338", praga, amsterdam, 320, base.plusSeconds(3*3600), base.plusSeconds(5*3600)));
        vuelos.add(createVuelo("AMS-BRU-339", amsterdam, bruselas, 320, base.plusSeconds(6*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("BRU-BER-340", bruselas, berlin, 320, base.plusSeconds(8*3600), base.plusSeconds(10*3600)));
        vuelos.add(createVuelo("BER-AMS-341", berlin, amsterdam, 320, base.plusSeconds(9*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("AMS-VIE-342", amsterdam, viena, 320, base.plusSeconds(10*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("VIE-BRU-343", viena, bruselas, 320, base.plusSeconds(11*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("BRU-PRG-344", bruselas, praga, 320, base.plusSeconds(12*3600), base.plusSeconds(14*3600)));
        vuelos.add(createVuelo("PRG-BER-345", praga, berlin, 320, base.plusSeconds(13*3600), base.plusSeconds(15*3600)));

        vuelos.add(createVuelo("DEL-DXB-346", delhi, dubai, 420, base.plusSeconds(2*3600), base.plusSeconds(6*3600)));
        vuelos.add(createVuelo("DXB-KHI-347", dubai, karachi, 380, base.plusSeconds(5*3600), base.plusSeconds(9*3600)));
        vuelos.add(createVuelo("KHI-BAK-348", karachi, baku, 380, base.plusSeconds(6*3600), base.plusSeconds(11*3600)));
        vuelos.add(createVuelo("BAK-SEL-349", baku, seoul, 420, base.plusSeconds(7*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("SEL-DEL-350", seoul, delhi, 420, base.plusSeconds(8*3600), base.plusSeconds(14*3600)));
        vuelos.add(createVuelo("DEL-KHI-351", delhi, karachi, 380, base.plusSeconds(9*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("KHI-SEL-352", karachi, seoul, 420, base.plusSeconds(10*3600), base.plusSeconds(16*3600)));
        vuelos.add(createVuelo("SEL-DXB-353", seoul, dubai, 420, base.plusSeconds(11*3600), base.plusSeconds(17*3600)));
        vuelos.add(createVuelo("DXB-BAK-354", dubai, baku, 380, base.plusSeconds(12*3600), base.plusSeconds(15*3600)));
        vuelos.add(createVuelo("BAK-DEL-355", baku, delhi, 380, base.plusSeconds(13*3600), base.plusSeconds(16*3600)));

        vuelos.add(createVuelo("BER-LIM-356", berlin, lima, 520, base.plusSeconds(4*3600), base.plusSeconds(12*3600)));
        vuelos.add(createVuelo("LIM-BER-357", lima, berlin, 520, base.plusSeconds(13*3600), base.plusSeconds(21*3600)));
        vuelos.add(createVuelo("VIE-BOG-358", viena, bogota, 520, base.plusSeconds(5*3600), base.plusSeconds(13*3600)));
        vuelos.add(createVuelo("BOG-VIE-359", bogota, viena, 520, base.plusSeconds(14*3600), base.plusSeconds(22*3600)));
        vuelos.add(createVuelo("BRU-SCL-360", bruselas, santiago, 520, base.plusSeconds(6*3600), base.plusSeconds(16*3600)));
        vuelos.add(createVuelo("SCL-BRU-361", santiago, bruselas, 520, base.plusSeconds(17*3600), base.plusSeconds(27*3600)));
        vuelos.add(createVuelo("PRG-BUE-362", praga, buenosAires, 520, base.plusSeconds(7*3600), base.plusSeconds(17*3600)));
        vuelos.add(createVuelo("BUE-PRG-363", buenosAires, praga, 520, base.plusSeconds(4*3600), base.plusSeconds(18*3600))); // ESTABA AL REVÉS XDDDDDDDDD
        vuelos.add(createVuelo("AMS-BRA-364", amsterdam, brasilia, 520, base.plusSeconds(8*3600), base.plusSeconds(18*3600)));
        vuelos.add(createVuelo("BRA-AMS-365", brasilia, amsterdam, 520, base.plusSeconds(5*3600), base.plusSeconds(19*3600))); // Y ACABA TMB XDDDD


        vueloRepository.saveAll(vuelos);

        // 3) PEDIDOS (todos a almacenes NO infinitos):
// - pedidos grandes que requieren dividir la cantidad entre varias rutas/flights
// - pedidos pequeños que caben en una sola ruta
// - pedidos a destinos con sólo rutas indirectas

        List<PedidoEntidad> pedidos = new ArrayList<>();
        pedidos.add(createPedido(bruselas, 800));
        pedidos.add(createPedido(amsterdam, 600));
        pedidos.add(createPedido(praga, 400));
        pedidos.add(createPedido(laPaz, 150));
        pedidos.add(createPedido(caracas, 900));
        pedidos.add(createPedido(asuncion, 50));
        pedidos.add(createPedido(santiago, 800));
        pedidos.add(createPedido(berlin, 5));
        pedidos.add(createPedido(karachi, 700));
        pedidos.add(createPedido(lima, 700));
        pedidos.add(createPedido(bruselas, 900));
        pedidos.add(createPedido(quito, 600));
        pedidos.add(createPedido(buenosAires, 100));
        pedidos.add(createPedido(dubai, 10));
        pedidos.add(createPedido(montevideo, 500));
        pedidos.add(createPedido(viena, 5));
        pedidos.add(createPedido(delhi, 100));
        pedidos.add(createPedido(quito, 500));
        pedidos.add(createPedido(laPaz, 75));
        pedidos.add(createPedido(seoul, 500));
        pedidos.add(createPedido(laPaz, 400));
        pedidos.add(createPedido(bogota, 75));
        pedidos.add(createPedido(laPaz, 5));
        pedidos.add(createPedido(seoul, 20));
        pedidos.add(createPedido(caracas, 150));
        pedidos.add(createPedido(quito, 75));
        pedidos.add(createPedido(montevideo, 800));
        pedidos.add(createPedido(dubai, 900));
        pedidos.add(createPedido(asuncion, 900));
        pedidos.add(createPedido(viena, 700));
        pedidos.add(createPedido(dubai, 400));
        pedidos.add(createPedido(dubai, 200));
        pedidos.add(createPedido(berlin, 1000));
        pedidos.add(createPedido(karachi, 10));
        pedidos.add(createPedido(seoul, 50));
        pedidos.add(createPedido(viena, 800));
        pedidos.add(createPedido(praga, 300));
        pedidos.add(createPedido(karachi, 150));
        pedidos.add(createPedido(brasilia, 100));
        pedidos.add(createPedido(santiago, 200));
        pedidos.add(createPedido(delhi, 10));
        pedidos.add(createPedido(santiago, 900));
        pedidos.add(createPedido(delhi, 1000));
        pedidos.add(createPedido(delhi, 150));
        pedidos.add(createPedido(bruselas, 300));
        pedidos.add(createPedido(viena, 500));
        pedidos.add(createPedido(asuncion, 5));
        pedidos.add(createPedido(bruselas, 900));
        pedidos.add(createPedido(quito, 800));
        pedidos.add(createPedido(brasilia, 10));
        //hasta aquí 50 ^^^^^^^^^^^^^^^^^^^^^^
        pedidos.add(createPedido(berlin, 300));
        pedidos.add(createPedido(santiago, 150));
        pedidos.add(createPedido(brasilia, 500));
        pedidos.add(createPedido(bruselas, 800));
        pedidos.add(createPedido(caracas, 800));
        pedidos.add(createPedido(brasilia, 700));
        pedidos.add(createPedido(karachi, 200));
        pedidos.add(createPedido(asuncion, 5));
        pedidos.add(createPedido(buenosAires, 5));
        pedidos.add(createPedido(santiago, 600));
        pedidos.add(createPedido(laPaz, 800));
        pedidos.add(createPedido(santiago, 75));
        pedidos.add(createPedido(dubai, 1000));
        pedidos.add(createPedido(baku, 500));
        pedidos.add(createPedido(caracas, 75));
        pedidos.add(createPedido(viena, 50));
        pedidos.add(createPedido(lima, 600));
        pedidos.add(createPedido(laPaz, 10));
        pedidos.add(createPedido(montevideo, 400));
        pedidos.add(createPedido(buenosAires, 500));
        pedidos.add(createPedido(amsterdam, 200));
        pedidos.add(createPedido(praga, 300));
        pedidos.add(createPedido(quito, 400));
        pedidos.add(createPedido(seoul, 900));
        pedidos.add(createPedido(montevideo, 300));
        pedidos.add(createPedido(bruselas, 900));
        pedidos.add(createPedido(karachi, 500));
        pedidos.add(createPedido(dubai, 900));
        pedidos.add(createPedido(viena, 500));
        pedidos.add(createPedido(quito, 500));
        pedidos.add(createPedido(brasilia, 50));
        pedidos.add(createPedido(dubai, 60));
        pedidos.add(createPedido(baku, 300));
        pedidos.add(createPedido(quito, 10));
        pedidos.add(createPedido(karachi, 150));
        pedidos.add(createPedido(bogota, 500));
        pedidos.add(createPedido(lima, 300));
        pedidos.add(createPedido(caracas, 400));
        pedidos.add(createPedido(montevideo, 1000));
        pedidos.add(createPedido(lima, 800));
        pedidos.add(createPedido(buenosAires, 400));
        pedidos.add(createPedido(buenosAires, 800));
        pedidos.add(createPedido(berlin, 75));
        pedidos.add(createPedido(praga, 90));
        pedidos.add(createPedido(baku, 20));
        pedidos.add(createPedido(viena, 20));
        pedidos.add(createPedido(montevideo, 50));
        pedidos.add(createPedido(asuncion, 50));
        pedidos.add(createPedido(praga, 70));
        pedidos.add(createPedido(karachi, 150));

//        cargarPedidosSinGuardarAun();
        List<PedidoEntidad> pedidosAGuardar = new ArrayList<>();
        for (int i = 0; i < TOPE_PEDIDOS; i++) {
            pedidosAGuardar.add(pedidos.get(i));
        }
        pedidoRepository.saveAll(pedidosAGuardar);
// Large pedido to Cusco that exceeds single-flight capacity -> should split
//         pedidos.add(createPedido(bogota, 900)); // expect multiple rutas: GH-CUZ, GH-ARE+ARE-CUZ, LIM-CUZ etc.

// // Medium pedido to Cusco
//          pedidos.add(createPedido(bogota, 200));

// // // Small pedido to Arequipa (fits in LIM-ARE or GH-ARE)
//          pedidos.add(createPedido(caracas, 150));

// // // Very large pedido to Trujillo (forces multi-hop through GH or direct GH-TRU)
//          pedidos.add(createPedido(santiago, 700));

// // // PedidoEntidad to Iquitos (route options via GH->IQT or LIM->IQT)
//          pedidos.add(createPedido(bruselas, 250));

// // // Another pedido to Cusco but arrives later (created timestamp differs) to test scheduling priority
//          PedidoEntidad latePedido = createPedido(bogota, 60);
//          latePedido.setInstanteRegistro(base.plusSeconds(1*3600*24));
//          pedidos.add(latePedido);

    }

    // ---------- helpers ----------


    private AlmacenEntidad createAlmacen(String codigoAeropuerto, String nombreCiudad, String nombrePais, String codigoCiudad4, boolean esInfinito,
                                         int capacidadMaxima, int capacidadOcupada, int gmt, Continente continente) {
        AlmacenEntidad a = AlmacenEntidad.builder()
                .codigoAeropuertoEn4Letras(codigoAeropuerto)
                .nombreCiudad(nombreCiudad)
                .nombrePais(nombrePais)
                .codigoCiudadEn4Letras(codigoCiudad4)
                .esInfinito(esInfinito)
                .capacidadMaxima(capacidadMaxima)
                .capacidadOcupada(capacidadOcupada)
                .gmt(gmt)
                .latitud(0.0)
                .longitud(0.0)
                .continente(continente)
                .activo(true)
                .build();
        return a;
    }


    private VueloEntidad createVuelo(String codigo, AlmacenEntidad origen, AlmacenEntidad destino, int capacidad, Instant salida, Instant llegada) {
        VueloEntidad v = VueloEntidad.builder()
                .codigo4Letras(codigo)
                .almacenOrigen(origen)
                .almacenDestino(destino)
                .esIntercontinental(! origen.getContinente().equals(destino.getContinente()))
                .capacidadMaxima(capacidad)
                .capacidadOcupada(0)
                .fechaHoraInicioUtc(salida)
                .fechaHoraFinUtc(llegada)
                .activo(true)
                .build();
        return v;
    }


    private PedidoEntidad createPedido(AlmacenEntidad destino, int cantidad) {
        PedidoEntidad p = PedidoEntidad.builder()
                .almacenDestino(destino)
                .cantidadProductosPedidos(cantidad)
                .cantidadProductosEntregados(0)
//                .estado(EstadoPedido.PENDIENTE)
                .instanteRegistro(Instant.now())
                .build();
        CONTADOR_GLOBAL_PEDIDOS++;
        return p;

    }

}
