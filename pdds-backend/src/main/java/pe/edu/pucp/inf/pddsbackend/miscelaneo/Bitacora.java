package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

/**
 * Logger simplificado para el sistema PDDS.
 * Métodos principales: escribir(String) y escribir(String, Object...)
 */
public final class Bitacora {
    private static volatile Boolean imprimirConsola = true;
    private static volatile Boolean imprimirDisco = true;

    private static final DateTimeFormatter marcaTiempo = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ReentrantLock exclusionMutua = new ReentrantLock();
    private static final StringBuilder reporte = new StringBuilder();
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BRIGHT_BLUE = "\u001B[94m";
    private static final String ANSI_BRIGHT_WHITE = "\u001B[97m";
    private static volatile Path archivo;
    private static volatile Boolean inicializada = false;

    public static boolean workaround = false;

    private Bitacora()
    {
        throw new AssertionError("No se inicializa la Bitacora");
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
            String nombre = "reporte_actual" + ".log";
            archivo = directorioBase.resolve(nombre);
            Files.write(
                archivo,
                new byte[0],
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
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

    public static void guardar(EstadoGlobal estado, String direccion) throws IOException
    {
        Path ruta = Path.of(direccion);
        Path directorio = ruta.getParent();
        if (directorio != null) {
            Files.createDirectories(directorio);
        }

        escribir("[GUARDAR] user.dir = %s", System.getProperty("user.dir"));
        escribir("[GUARDAR] Ruta abs = %s", ruta.toAbsolutePath());

        try (ObjectOutputStream oos =
                     new ObjectOutputStream(Files.newOutputStream(ruta))) {
            oos.writeObject(estado);
        }
        catch (IOException e) {
            escribir(e.toString());
        }
    }

    public static EstadoGlobal cargar(String direccion)
    {
        Path ruta = Path.of(direccion);

        escribir("[CARGAR] user.dir = %s", System.getProperty("user.dir"));
        escribir("[CARGAR] Ruta abs = %s", ruta.toAbsolutePath());

        try (ObjectInputStream ois =
                     new ObjectInputStream(Files.newInputStream(ruta))) {
            return (EstadoGlobal) ois.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            escribir(e.toString());
        }

        return null;
    }

    /**
     * Imprime una cadena de texto
     */
    public static void escribir(String mensaje)
    {
        if (workaround) return;
        asegurarInicializacion();
        String ts = LocalDateTime.now().format(marcaTiempo);
        String linea = "*[" + ts + "] " + mensaje;

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
            System.out.print(ANSI_BRIGHT_BLUE + "[" + ts + "] ");
            System.out.println(ANSI_BRIGHT_WHITE + mensaje);
        }

        if (imprimirDisco)
        {
            guardar();
        }
    }

    /**
     * Emulación de printf - permite formateo estilo printf("%d %s", 10, "ejemplo")
     */
    public static void escribir(String formato, Object... args)
    {
        escribir(String.format(formato, args));
    }

    /**
     * Formatea un Instant para impresión: 2024-01-01T12:00:00Z -> 2024-01-01 12:00:00
     */
    public static String formatearInstante(Instant instante)
    {
        return instante.toString().replace("T", " ").replace("Z", "");
    }

    /**
     * Imprime resumen completo del estado global
     */
    public static void escribir(EstadoGlobal estado, String mensaje) {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        sb.append("=== ESTADO GLOBAL ===\n");
        appendResumenProductos(estado.getProductos(), sb);
        appendResumenProgramaciones(estado.getProgramaciones(), sb);
        appendResumenVuelos(estado, sb);
        appendResumenPedidos(estado, sb);

        escribir(sb.toString());
    }

    /**
     * Imprime resultado del algoritmo
     */
    public static void escribir(ResultadoAlgoritmoDTO resultado, String mensaje)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(mensaje).append("\n");
        sb.append("=== RESULTADO ALGORITMO ===\n");
        sb.append("--- Tiempo de ejecucion: ").append(resultado.tiempoEjecucionMs()).append(" ms\n");
        
        SalidaProblemaPlanificacion salida = resultado.salida();
        
        if (salida == null)
        {
            sb.append("--- Salida: NULL\n");
            return;
        }
        
        sb.append("--- Hubo error: ").append(salida.isHuboErrorEjecucion() ? "SI" : "NO").append("\n");
        sb.append("--- Colapsado: ").append(salida.isColapsado() ? "SI" : "NO").append("\n");
        
        if (salida.isHuboErrorEjecucion() || salida.isColapsado())
        {
            sb.append("--- Error: ").append(salida.getError()).append("\n");
        }
        
        appendResumenProductos(salida.getProductos(), sb);
        appendResumenProgramaciones(salida.getProgramaciones(), sb);
        escribir(sb.toString());
    }

    private static void appendResumenProductos(Map<UUID, Producto> productos, StringBuilder sb) {
        sb.append("\n--- RESUMEN PRODUCTOS ---\n");
        
        if (productos == null || productos.isEmpty()) {
            sb.append("  Total productos: 0\n");
            return;
        }
        
        int total = productos.size();
        int existentes = 0;
        int tipoA = 0;
        int tipoB = 0;
        int tipoC = 0;
        int tipoD = 0;
        
        for (Producto p : productos.values()) {
            if (p.isExistente()) existentes++;
            
            if (p.validarNoPlanificado_A()) tipoA++;
            else if (p.validarIncancelable_B()) tipoB++;
            else if (p.validarPlanificadoNoExistente_C()) tipoC++;
            else if (p.validarPlanificadoExistente_D()) tipoD++;
        }
        
        sb.append("  Total productos: ").append(total).append("\n");
        sb.append("  Productos existentes: ").append(existentes).append("\n");
        sb.append("  Productos tipo A (no planificado): ").append(tipoA).append("\n");
        sb.append("  Productos tipo B (incancelable): ").append(tipoB).append("\n");
        sb.append("  Productos tipo C (planificado no existente): ").append(tipoC).append("\n");
        sb.append("  Productos tipo D (planificado existente): ").append(tipoD).append("\n");
    }

    private static void appendResumenProgramaciones(List<Programacion> programaciones, StringBuilder sb)
    {
        sb.append("\n--- RESUMEN PROGRAMACIONES ---\n");
        
        if (programaciones == null || programaciones.isEmpty())
        {
            sb.append("  Total programaciones: 0\n");
            return;
        }
        
        int total = programaciones.size();
        int tipoI = 0;
        int tipoC = 0;
        int tipoE = 0;
        int tipoT = 0;
        
        for (Programacion prog : programaciones)
        {
            char estado = prog.getEstado();
            if (estado == 'I') tipoI++;
            else if (estado == 'C') tipoC++;
            else if (estado == 'E') tipoE++;
            else if (estado == 'T') tipoT++;
        }
        
        sb.append("  Total programaciones: ").append(total).append("\n");
        sb.append("  Programaciones tipo I (incancelable): ").append(tipoI).append("\n");
        sb.append("  Programaciones tipo C (creacion): ").append(tipoC).append("\n");
        sb.append("  Programaciones tipo E (existente): ").append(tipoE).append("\n");
        sb.append("  Programaciones tipo T (terminada): ").append(tipoT).append("\n");
    }

    private static void appendResumenVuelos(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("\n--- RESUMEN VUELOS ---\n");
        
        Map<Long, Vuelo> vuelos = estado.getVuelos();
        
        if (vuelos == null || vuelos.isEmpty())
        {
            sb.append("  Cantidad de vuelos: 0\n");
            return;
        }
        
        int total = vuelos.size();
        Instant primerSalida = null;
        Instant ultimaSalida = null;
        
        for (Vuelo v : vuelos.values())
        {
            Instant salida = v.getInstanteSalida();
            if (salida != null)
            {
                if (primerSalida == null || salida.isBefore(primerSalida))
                {
                    primerSalida = salida;
                }
                if (ultimaSalida == null || salida.isAfter(ultimaSalida))
                {
                    ultimaSalida = salida;
                }
            }
        }
        
        sb.append("  Cantidad de vuelos: ").append(total).append("\n");
        
        if (primerSalida != null)
        {
            sb.append("  Instante de salida del primer vuelo: ").append(formatearInstante(primerSalida)).append("\n");
        }
        
        if (ultimaSalida != null)
        {
            sb.append("  Instante de salida del ultimo vuelo: ").append(formatearInstante(ultimaSalida)).append("\n");
        }
    }

    private static void appendResumenPedidos(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("\n--- RESUMEN PEDIDOS ---\n");
        
        Map<Long, Pedido> pedidos = estado.getPedidos();
        
        if (pedidos == null || pedidos.isEmpty())
        {
            sb.append("  Cantidad de pedidos: 0\n");
            return;
        }
        
        int totalPedidos = pedidos.size();
        int totalProductosPedidos = 0;
        int totalProductosFaltantes = 0;
        boolean hayProgramacionesFaltantes = false;
        
        for (Pedido p : pedidos.values())
        {
            totalProductosPedidos += p.getCantidadProductos();
            totalProductosFaltantes += p.obtenerCantidadProgramacionesFaltantes();
            
            if (p.obtenerCantidadProgramacionesFaltantes() != 0)
            {
                hayProgramacionesFaltantes = true;
            }
        }
        
        sb.append("  Cantidad de pedidos: ").append(totalPedidos).append("\n");
        sb.append("  Cantidad de productos pedidos: ").append(totalProductosPedidos).append("\n");
        sb.append("  Cantidad de productos faltantes: ").append(totalProductosFaltantes).append("\n");
        sb.append("  Hay pedidos con programaciones faltantes: ").append(hayProgramacionesFaltantes ? "SI" : "NO").append("\n");
    }

    /**
     * Imprime una sola ruta (lista de vuelos), en orden.
     */
    public static void escribir(Ruta ruta, String mensaje)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        sb.append("=== RUTA ===\n");

        if (ruta.obtenerCantidadVuelos() == 0)
        {
            sb.append("  (Ruta vacía)\n");
        }
        else
        {
            int i = 1;
            for (Vuelo vuelo : ruta.getVuelos())
            {
                sb.append("  ")
                  .append(i++)
                  .append(". ")
                  .append(vuelo.toString())
                  .append("\n");
            }
        }

        escribir(sb.toString());
    }
}
