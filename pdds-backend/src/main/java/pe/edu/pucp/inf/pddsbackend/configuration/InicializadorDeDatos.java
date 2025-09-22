package pe.edu.pucp.inf.pddsbackend.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;
import pe.edu.pucp.inf.pddsbackend.models.entities.Continente;
import pe.edu.pucp.inf.pddsbackend.models.entities.Vuelo;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InicializadorDeDatos implements CommandLineRunner {

    private final PedidoRepository pedidoRepository; // Assuming you have a repository for your entity
    private final AlmacenRepository almacenRepository;
    private final VueloRepository vueloRepository;

    private static final int DIAS_ANADIR_A_VUELOS = 1;
    private static final int SEGUNDOS_ANADIR_A_VUELOS = DIAS_ANADIR_A_VUELOS*24*3600;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Inicializando datos de prueba para generador de rutas...");


// base time to make schedules reproducibles
        Instant base = Instant.now().plusSeconds(SEGUNDOS_ANADIR_A_VUELOS).truncatedTo(ChronoUnit.HOURS);

// 1) ALMACENES (algunos infinitos, otros no)
        Almacen globalHub = createAlmacen("GLBH", "Global Hub City", "Global", "GLOBAL", true, 1_000_000, 0, -5, Continente.NORTEAMERICA);
        Almacen lima = createAlmacen("SPIM", "Lima", "Perú", "LIMA", false, 50_000, 0, -5, Continente.SUDAMERICA);
        Almacen bogota = createAlmacen("SPZO", "Bogotá", "Colombia", "BOGO", false, 5_000, 0, -5, Continente.SUDAMERICA);
        Almacen caracas = createAlmacen("SPQU", "Caracas", "Venezuela", "CARA", false, 6_000, 0, -5, Continente.SUDAMERICA);
        Almacen santiago = createAlmacen("SPRU", "Santiago", "Chile", "SANT", false, 4_000, 0, -5, Continente.SUDAMERICA);
        Almacen bruselas = createAlmacen("SPQT", "Bruselas", "Bélgica", "BRUS", false, 3_000, 0, -5, Continente.EUROPA);

// another regional hub (infinite) for multi-continent tests
        Almacen regionalHub = createAlmacen("RGNH", "Regional Hub", "CountryX", "RREG", true, 500_000, 0, 0, Continente.EUROPA);

// save all almacenes
        List<Almacen> almacenes = List.of(globalHub, regionalHub, lima, bogota, caracas, santiago, bruselas);
        almacenRepository.saveAll(almacenes);

// 2) VUELOS (conexiones). Diseñados para producir:
// - rutas obvias: directas desde hubs a destinos (ej. GLBH -> SPZO (Cusco) directo)
// - rutas alternativas: GLBH -> SPIM (Lima) -> SPZO (Cusco) o GLBH -> SPRU -> SPQU -> SPZO
// - rutas buenas pero no obvias: conexiones entre hubs/regional + salto local
        List<Vuelo> vuelos = new ArrayList<>();


// From Global Hub -> Lima (big capacity, frequent)
        vuelos.add(createVuelo("GH-LIM-01", globalHub, lima, 2000, base.plusSeconds(6*3600), base.plusSeconds(8*3600)));
        vuelos.add(createVuelo("GH-LIM-02", globalHub, lima, 1800, base.plusSeconds(12*3600), base.plusSeconds(14*3600)));


// From Lima -> Cusco (direct, low capacity — "ruta obvia" pero limitada)
        vuelos.add(createVuelo("LIM-CUZ-01", lima, bogota, 120, base.plusSeconds(9*3600), base.plusSeconds(10*3600).plusSeconds(30*60)));
        vuelos.add(createVuelo("LIM-CUZ-02", lima, bogota, 80, base.plusSeconds(15*3600), base.plusSeconds(16*3600).plusSeconds(30*60)));


// From Global Hub -> Cusco (direct but rarer, medium capacity) -> 'ruta obvia'
        vuelos.add(createVuelo("GH-CUZ-01", globalHub, bogota, 400, base.plusSeconds(5*3600), base.plusSeconds(7*3600).plusSeconds(30*60)));


// // Global Hub -> Arequipa -> Cusco chain (non-obvious option)
         vuelos.add(createVuelo("GH-ARE-01", globalHub, caracas, 600, base.plusSeconds(7*3600), base.plusSeconds(9*3600)));
         vuelos.add(createVuelo("ARE-CUZ-01", caracas, bogota, 150, base.plusSeconds(11*3600), base.plusSeconds(12*3600).plusSeconds(30*60)));


// // Global Hub -> Trujillo -> Arequipa -> Cusco (longer path, useful if others full)
         vuelos.add(createVuelo("GH-TRU-01", globalHub, santiago, 500, base.plusSeconds(8*3600), base.plusSeconds(10*3600)));
         vuelos.add(createVuelo("GH-TRU-02", globalHub, santiago, 1000, base.plusSeconds(8*3600), base.plusSeconds(10*3600)));
         vuelos.add(createVuelo("TRU-ARE-01", santiago, caracas, 100, base.plusSeconds(11*3600), base.plusSeconds(12*3600)));
         vuelos.add(createVuelo("TRU-ARE-02", santiago, caracas, 60, base.plusSeconds(17*3600), base.plusSeconds(18*3600)));


// // Lima -> Arequipa (alternative connector)
         vuelos.add(createVuelo("LIM-ARE-01", lima, caracas, 200, base.plusSeconds(13*3600), base.plusSeconds(14*3600).plusSeconds(30*60)));


// // Some local flights with very small capacity to force splitting
         vuelos.add(createVuelo("ARE-CUZ-02", caracas, bogota, 30, base.plusSeconds(19*3600), base.plusSeconds(20*3600).plusSeconds(30*60)));


// // Iquitos isolated: only via Lima or Global hub (test longer routing)
         vuelos.add(createVuelo("GH-IQT-01", globalHub, bruselas, 300, base.plusSeconds(6*3600), base.plusSeconds(10*3600)));
         vuelos.add(createVuelo("LIM-IQT-01", lima, bruselas, 80, base.plusSeconds(10*3600), base.plusSeconds(14*3600)));


// // Some flights that are full (capacidad = 0 available) to force algorithm to avoid
         Vuelo fullFlight = createVuelo("FULL-1", globalHub, bogota, 0, base.plusSeconds(3*3600), base.plusSeconds(5*3600));
         vuelos.add(fullFlight);


// // Inter-hub flight (regional)
         vuelos.add(createVuelo("GH-RG-01", globalHub, regionalHub, 1000, base.plusSeconds(2*3600), base.plusSeconds(8*3600)));
         vuelos.add(createVuelo("RG-GH-01", regionalHub, globalHub, 1000, base.plusSeconds(22*3600), base.plusSeconds(4*3600).plusSeconds(1*3600*24)));

// // vuelo extra a bogota
         vuelos.add(createVuelo("RG-BOG-01", regionalHub, bogota, 500, base.plusSeconds(9*3600), base.plusSeconds(15*3600)));


        // Vuelo 1: HUB -> Bogotá
        vuelos.add(createVuelo(
                "GH-BOG-01",         // código del vuelo
                globalHub,                 // origen
                santiago,               // destino
                1200,                  // capacidad
                base.plusSeconds(8*3600),                    // salida (8 horas desde base)
                base.plusSeconds(8*3600).plusSeconds(2*3600) // llegada (2h de vuelo)
        ));

        // Vuelo 2: Bogotá -> Santiago
        vuelos.add(createVuelo(
                "BOG-SCL-01", 
                santiago, 
                bogota, 
                1000, 
                base.plusSeconds(11*3600),                   // salida (deja ~1h de conexión)
                base.plusSeconds(11*3600).plusSeconds(5*3600) // llegada (5h de vuelo)
        ));
        vueloRepository.saveAll(vuelos);

        // 3) PEDIDOS (todos a almacenes NO infinitos):
// - pedidos grandes que requieren dividir la cantidad entre varias rutas/flights
// - pedidos pequeños que caben en una sola ruta
// - pedidos a destinos con sólo rutas indirectas

        List<Pedido> pedidos = new ArrayList<>();

// Large pedido to Cusco that exceeds single-flight capacity -> should split
        pedidos.add(createPedido(bogota, 900)); // expect multiple rutas: GH-CUZ, GH-ARE+ARE-CUZ, LIM-CUZ etc.

// Medium pedido to Cusco
         pedidos.add(createPedido(bogota, 200));

// // Small pedido to Arequipa (fits in LIM-ARE or GH-ARE)
         pedidos.add(createPedido(caracas, 150));

// // Very large pedido to Trujillo (forces multi-hop through GH or direct GH-TRU)
         pedidos.add(createPedido(santiago, 700));

// // Pedido to Iquitos (route options via GH->IQT or LIM->IQT)
         pedidos.add(createPedido(bruselas, 250));

// // Another pedido to Cusco but arrives later (created timestamp differs) to test scheduling priority
         Pedido latePedido = createPedido(bogota, 60);
         latePedido.setInstanteRegistro(base.plusSeconds(1*3600*24));
         pedidos.add(latePedido);

        pedidoRepository.saveAll(pedidos);

        System.out.println("Data insertion complete during application startup.");
    }

    // ---------- helpers ----------


    private Almacen createAlmacen(String codigoAeropuerto, String nombreCiudad, String nombrePais, String codigoCiudad4, boolean esInfinito,
                                  int capacidadMaxima, int capacidadOcupada, int gmt, Continente continente) {
        Almacen a = Almacen.builder()
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


    private Vuelo createVuelo(String codigo, Almacen origen, Almacen destino, int capacidad, Instant salida, Instant llegada) {
        Vuelo v = Vuelo.builder()
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


    private Pedido createPedido(Almacen destino, int cantidad) {
        Pedido p = Pedido.builder()
                .almacenDestino(destino)
                .cantidadProductosPedidos(cantidad)
                .cantidadProductosEntregados(0)
//                .estado(EstadoPedido.PENDIENTE)
                .instanteRegistro(Instant.now())
                .build();
        return p;
    }

}
