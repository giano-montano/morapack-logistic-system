package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Esta clase es un logger. Los métodos expuestos son: escribir(string) y
 * escribir(string, args).
 *
 * imprimirConsola: Valor true para imprimir en la consola imprimirDisco: Valor
 * true para guardar al disco inmediatamente
 */
public final class Bitacora
{
    private static volatile Boolean imprimirConsola = true;
    private static volatile Boolean imprimirDisco = true;

    private static final DateTimeFormatter marcaTiempo = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter marcaTiempo2 = DateTimeFormatter.ofPattern("ss_mm_HH");
    private static final ReentrantLock exclusionMutua = new ReentrantLock();
    private static final StringBuilder reporte = new StringBuilder();
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BRIGHT_BLUE = "\u001B[94m";
    private static final String ANSI_BRIGHT_WHITE = "\u001B[97m";
    private static volatile Path archivo;
    private static volatile Boolean inicializada = false;

    private Bitacora()
    {
        throw new AssertionError("No se inicializa la Bitacora");
    }

    /**
     * Imprime una cadena de texto que quieres imprimir
     */
    public static void escribir(String mensaje)
    {
        asegurarInicializacion();
        String ts = LocalDateTime.now().format(marcaTiempo);
        String linea = "[" + ts + "] " + mensaje;

        exclusionMutua.lock();
        try
        {
            reporte.append(linea).append(System.lineSeparator());
        }
        finally
        {
            exclusionMutua.unlock();
        }

        if (imprimirConsola)
        {
            System.out.println(
                    ANSI_BRIGHT_BLUE + "[" + ts + "]" + ANSI_RESET + " " + ANSI_BRIGHT_WHITE
                            + mensaje + ANSI_RESET);
        }

        if (imprimirDisco)
        {
            guardar();
        }
    }

    /**
     * Esto es una emulación de printf("%d %10", 10, "ejemplo")
     */
    public static void escribir(String formato, Object... args)
    {
        escribir(String.format(formato, args));
    }

    /*
     * public static void imprimirEstadoGlobal(EstadoGlobalGlobal estado) { Mapa
     * mapa; StringBuilder sb = new StringBuilder();
     * java.util.function.Function<Instant, String> formatInstant = instant ->
     * instant.toString();
     *
     * sb.append("=== ESTADO ===\n")
     * .append("--- Instante: ").append(estado.getInstanteActual()).append("\n")
     * .append("--- Productos: ").append(estado.getProductos().size()).append("\n")
     * .append("--- Almacenes: ").append(estado.getAlmacenes().size()).append("\n")
     * .append("--- Vuelos: ").append(estado.getVuelos().size()).append("\n")
     * .append("--- Pedidos: ").append(estado.getPedidos().size()).append("\n")
     * .append("--- Demanda total: ").append(estado.getDemandaTotal()).append("\n")
     * .append("--- ALmacenes infnitios: ").append(estado.getAlmacenesInfinitos().
     * size()) .append("\n") .append("--- ALmacenes con inventario: ")
     * .append(estado.getAlmacenesConInventario().size()).append("\n")
     * .append("--- ALmacenes con demanda: ")
     * .append(estado.getAlmacenesConDemanda().size()).append("\n\n");
     *
     * sb.append("--- DETALLES PRODUCTOS ---\n"); estado.getProductos().values()
     * .forEach(p -> sb.append("  ").append(p).append("\n"));
     *
     * sb.append("\n--- DETALLES ALMACENES ---\n"); estado.getAlmacenes().values()
     * .forEach(a -> sb.append("  ").append(a).append("\n"));
     *
     * sb.append("\n--- DETALLES VUELOS ---\n");
     * estado.getVuelos().values().stream() .filter(vuelo ->
     * !vuelo.getInventario().isEmpty()) .forEach(v ->
     * sb.append("  ").append(v).append("\n"));
     *
     * sb.append("\n--- DETALLES PEDIDOS ---\n"); estado.getPedidos().values()
     * .forEach(p -> sb.append("  ").append(p).append("\n"));
     *
     * mapa = estado.getMapa(); if (mapa != null) { Map<UUID, TreeSet<Ruta>> rutas =
     * mapa.getRutas(); Map<UUID, Almacen> almacenes = estado.getAlmacenes();
     *
     * sb.append("\n--- RUTAS POR ALMACEN ---\n");
     *
     * for (Map.Entry<UUID, TreeSet<Ruta>> entry : rutas.entrySet()) { UUID
     * almacenId = entry.getKey(); TreeSet<Ruta> rutasDelAlmacen = entry.getValue();
     * Almacen almacenDestino = almacenes.get(almacenId); String ciudadPaisDestino =
     * "Desconocido";
     *
     * if (almacenDestino != null) { ciudadPaisDestino = almacenDestino.getCiudad()
     * + ", " + almacenDestino.getPais(); }
     *
     * sb.append("\t--- Rutas hacia ").append(ciudadPaisDestino).append("\n");
     *
     * for (Ruta ruta : rutasDelAlmacen) { sb.append("\t\t").append(ruta); }
     * sb.append("\n"); } }
     *
     * escribir(sb.toString()); }
     */

    private static void inicializar()
    {
        inicializar(Paths.get("./pdds-backend/reportes"));
    }

    private static void inicializar(Path directorioBase)
    {
        if (inicializada)
        {
            return;
        }

        exclusionMutua.lock();

        try
        {
            if (inicializada)
            {
                return;
            }
            Files.createDirectories(directorioBase.toAbsolutePath().normalize());
            String nombre = "reporte_" + LocalDateTime.now().format(marcaTiempo2) + ".log";
            archivo = directorioBase.resolve(nombre);
            Files.write(archivo, new byte[0], StandardOpenOption.CREATE_NEW);
            inicializada = true;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        finally
        {
            exclusionMutua.unlock();
        }
    }

    private static void asegurarInicializacion()
    {
        if (!inicializada)
        {
            inicializar();
        }
    }

    private static void guardar()
    {
        asegurarInicializacion();
        exclusionMutua.lock();
        try
        {
            if (reporte.length() == 0)
                return;
            Files.write(archivo, reporte.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
            reporte.setLength(0);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        finally
        {
            exclusionMutua.unlock();
        }
    }
}
