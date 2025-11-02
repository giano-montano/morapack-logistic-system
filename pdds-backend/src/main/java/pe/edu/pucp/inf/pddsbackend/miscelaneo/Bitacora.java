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
 * imprimirConsola: Valor true para imprimir en la consola
 * imprimirDisco: Valor true para guardar al disco inmediatamente
 */
public final class Bitacora
{
    private static volatile boolean imprimirConsola = false; // momentaneo
    private static volatile boolean imprimirDisco = false; // momentaneo

    private static final DateTimeFormatter marcaTiempo = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter marcaTiempo2 = DateTimeFormatter.ofPattern("ss_mm_HH");
    private static final ReentrantLock exclusionMutua = new ReentrantLock();
    private static final StringBuilder reporte = new StringBuilder();
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BRIGHT_BLUE = "\u001B[94m";
    private static final String ANSI_BRIGHT_WHITE = "\u001B[97m";
    private static volatile Path archivo;
    private static volatile boolean inicializada = false;

    private Bitacora()
    {
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