package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

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

    
    public static void escribir(EstadoGlobal estado, String mensaje)
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString();

        sb.append(mensaje + "\n");
        sb.append("=== ESTADO ===\n")
                .append("--- Productos: ").append(estado.getProductos().size()).append("\n")
                .append("--- Almacenes: ").append(estado.getAlmacenes().size()).append("\n")
                .append("--- Vuelos: ").append(estado.getVuelos().size()).append("\n")
                .append("--- Pedidos: ").append(estado.getPedidos().size()).append("\n")
                .append("--- ALmacenes: ").append(estado.getAlmacenes().size())
                .append("\n");
/*
        sb.append("--- DETALLES PRODUCTOS ---\n");
        estado.getProductos().values()
                .forEach(p -> sb.append("  ").append(p).append("\n"));
*/
        sb.append("\n--- DETALLES ALMACENES ---\n");
        estado.getAlmacenes().values().stream()
                .filter(almacen -> !almacen.getIdsProductosExistentes().isEmpty() && almacen.getIdsProductosFuturos().isEmpty())
                .forEach(a -> sb.append("  ").append(a).append("\n"));

        sb.append("\n--- DETALLES VUELOS ---\n");
        estado.getVuelos().values().stream()
                .filter(vuelo -> !vuelo.getIdsProductosContenidos().isEmpty() && !vuelo.getIdsProductosProgramados().isEmpty())
                .forEach(v -> sb.append("  ").append(v).append("\n"));

        sb.append("\n--- DETALLES PEDIDOS ---\n");
        estado.getPedidos().values().stream()
                .filter(pedido -> !pedido.getIdsProductosEntregados().isEmpty() && !pedido.getIdsProductosProgramados().isEmpty())
                .forEach(p -> sb.append("  ").append(p).append("\n"));
        
        escribirProgramaciones(estado.getProgramaciones(), estado, sb);

        escribirProductos(estado.getProductos(), sb);
/*
        mapa = estado.getMapa();
        if (mapa != null)
        {
            Map<UUID, TreeSet<Ruta>> rutas = mapa.getRutas();
            Map<UUID, Almacen> almacenes = estado.getAlmacenes();

            sb.append("\n--- RUTAS POR ALMACEN ---\n");

            for (Map.Entry<UUID, TreeSet<Ruta>> entry : rutas.entrySet())
            {
                UUID almacenId = entry.getKey();
                TreeSet<Ruta> rutasDelAlmacen = entry.getValue();
                Almacen almacenDestino = almacenes.get(almacenId);
                String ciudadPaisDestino = "Desconocido";

                if (almacenDestino != null)
                {
                    ciudadPaisDestino = almacenDestino.getCiudad() + ", "
                            + almacenDestino.getPais();
                }

                sb.append("\t--- Rutas hacia ").append(ciudadPaisDestino).append("\n");

                for (Ruta ruta : rutasDelAlmacen)
                {
                    sb.append("\t\t").append(ruta);
                }
                sb.append("\n");
            }
        }
*/
        escribir(sb.toString());
    }

    public static void escribir(ResultadoAlgoritmoDTO respuesta, EstadoGlobal estado, String mensaje) 
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append(mensaje + "\n");
        sb.append("=== RESULTADO ALGORITMO ===\n");

        if (respuesta == null)
        {
            sb.append("--- Respuesta nula\n");
            escribir(sb.toString());
            return;
        }

        sb.append("--- Tiempo de ejecucion: ")
                .append(respuesta.tiempoEjecucionMs())
                .append(" ms\n");

        SalidaProblemaPlanificacion salida = respuesta.salida();
        if (salida == null)
        {
            sb.append("--- Salida nula\n");
            escribir(sb.toString());
            return;
        }

        sb.append("--- Hubo error: ").append(salida.isHuboErrorEjecucion()).append("\n");
        sb.append("--- Colapsado: ").append(salida.isColapsado()).append("\n");
        if (salida.isHuboErrorEjecucion() || salida.isColapsado())
        {
            sb.append("--- Error: ").append(salida.getError()).append("\n");
        }

        escribirProgramaciones(salida.getProgramaciones(), estado, sb);

        escribirProductos(salida.getProductos(), sb);   

        escribir(sb.toString());
    }

    private static void escribirProgramaciones(
            List<Programacion> programaciones,
            EstadoGlobal estado, StringBuilder sb)
    {
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        sb.append("\n--- DETALLES PROGRAMACIONES ---\n");

        if (programaciones == null || programaciones.isEmpty())
        {
            return;
        }

        for (Programacion programacion : programaciones)
        {
            Pedido pedido = estado.getPedidos().get(programacion.getIdPedido());
            String destinoPedido = "Destino desconocido";

            if (pedido != null)
            {
                Almacen almacenDestino = estado.getAlmacenes().get(pedido.getIdAlmacenDestino());
                if (almacenDestino != null)
                {
                    destinoPedido = "Destino: " + almacenDestino.getNombreCiudad() + ", "
                            + almacenDestino.getNombrePais();
                }
                else if (pedido.getContinenteDestino() != null)
                {
                    destinoPedido = "Destino: continente " + pedido.getContinenteDestino();
                }
            }

            sb.append("  Programacion (Pedido ")
                    .append(programacion.getIdPedido())
                    .append(" - ")
                    .append(destinoPedido)
                    .append(")\n");

            if (programacion.getIdsVueloRuta() == null
                    || programacion.getIdsVueloRuta().isEmpty())
            {
                sb.append("\tSin vuelos asociados\n");
            }
            else
            {
                int i = 0;
                for (Long idVuelo : programacion.getIdsVueloRuta())
                {
                    i++;

                    Vuelo vuelo = estado.getVuelos().get(idVuelo);
                    if (vuelo == null)
                    {
                        sb.append("\t\t\t").append(i).append(". Vuelo ")
                                .append(idVuelo).append(" (no encontrado)\n");
                        continue;
                    }

                    Almacen origen = estado.getAlmacenes().get(vuelo.getIdAlmacenOrigen());
                    Almacen destino = estado.getAlmacenes().get(vuelo.getIdAlmacenDestino());

                    String ciudadOrigen = origen != null ? origen.getNombreCiudad() : "Desconocido";
                    String paisOrigen = origen != null ? origen.getNombrePais() : "Desconocido";
                    String ciudadDestino = destino != null ? destino.getNombreCiudad() : "Desconocido";
                    String paisDestino = destino != null ? destino.getNombrePais() : "Desconocido";

                    sb.append("\t\t\t").append(i).append(". (")
                            .append(formatInstant.apply(vuelo.getInicio()))
                            .append(") ")
                            .append(ciudadOrigen).append(",").append(paisOrigen)
                            .append(" -> (")
                            .append(formatInstant.apply(vuelo.getFin()))
                            .append(") ")
                            .append(ciudadDestino).append(",").append(paisDestino)
                            .append("\n");
                }
            }
        }
    }

    private static void escribirProductos(Map<UUID, Producto> productos, StringBuilder sb)
    {
        sb.append("\n--- DETALLES PRODUCTOS ---\n");

        if (productos == null || productos.isEmpty())
        {
            return;
        }

        int total = productos.size();
        int countExiste = 0;
        int countPlanificado = 0;
        int countProntoParaEntrega = 0;

        for (Producto p : productos.values())
        {
            if (p.isExiste())
            {
                countExiste++;
            }
            if (p.isPlanificado())
            {
                countPlanificado++;
            }
            if (p.isProntoParaEntrega())
            {
                countProntoParaEntrega++;
            }
        }

        sb.append("  Total productos: ").append(total).append("\n");
        sb.append("  Productos existentes (existe=true): ")
                .append(countExiste).append("\n");
        sb.append("  Productos planificados (planificado=true): ")
                .append(countPlanificado).append("\n");
        sb.append("  Productos pronto para entrega (prontoParaEntrega=true): ")
                .append(countProntoParaEntrega).append("\n");

        return;
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
            String nombre = "reporte_XD" + ".log";
            archivo = directorioBase.resolve(nombre);
            Files.write(archivo, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
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
