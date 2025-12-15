package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
     * Esto es una emulación de printf("%d %10", 10, "ejemplo")
     */
    public static void escribir(String formato, Object... args)
    {
        escribir(String.format(formato, args));
    }

    public static void escribir(String mensaje, Instant instante)
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString().replace("T", " ").replace("Z", "");

        sb.append(mensaje);
        sb.append("\t").append(formatInstant.apply(instante));

        escribir(sb.toString());
    }

    public static void escribir(EstadoGlobal estado, String mensaje, boolean incluirCambios)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        appendResumenEstado(estado, sb);
        appendDetalleAlmacenes(estado, incluirCambios, sb);
        appendDetalleVuelos(estado, sb);
        appendResumenVuelos(estado, sb);
        appendDetallePedidos(estado, sb);
        appendResumenProgramaciones(estado.getProgramaciones(), estado, sb);
        appendResumenProductos(estado.getProductos(), sb);

        escribir(sb.toString());
    }

    /*
     * Imprime un solo almacén, opcionalmente incluyendo sus cambios.
     */
    public static void escribir(Almacen almacen, boolean incluirCambios, String mensaje)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        sb.append("=== ALMACEN ===\n");
        sb.append("  ")
          .append(almacen.toString(incluirCambios))
          .append("\n");

        escribir(sb.toString());
    }

    /**
     * Imprime una sola ruta (lista de vuelos), en orden.
     */
    public static void escribir(LinkedList<Vuelo> ruta, String mensaje)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        sb.append("=== RUTA ===\n");

        if (ruta == null || ruta.isEmpty())
        {
            sb.append("  (Ruta vacía)\n");
        }
        else
        {
            int i = 1;
            for (Vuelo vuelo : ruta)
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


    public static void escribir(ResultadoAlgoritmoDTO respuesta, EstadoGlobal estado, String mensaje)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
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

        appendResumenProgramaciones(salida.getProgramaciones(), estado, sb);
        appendResumenProductos(salida.getProductos(), sb);

        escribir(sb.toString());
    }

    public static void escribir(Programacion programacion,
                                                    EstadoGlobal estado,
                                                    boolean incluirCambiosAlmacen, String mensaje)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(mensaje).append("\n");
        appendProgramacionDetallada(programacion, estado, incluirCambiosAlmacen, sb);
        escribir(sb.toString());
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

    private static void appendResumenEstado(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("=== ESTADO ===\n")
                .append("--- Productos: ").append(estado.getProductos().size()).append("\n")
                .append("--- Almacenes: ").append(estado.getAlmacenes().size()).append("\n")
                .append("--- Vuelos: ").append(estado.getVuelos().size()).append("\n")
                .append("--- Pedidos: ").append(estado.getPedidos().size()).append("\n")
                .append("--- Programaciones: ").append(estado.getProgramaciones().size())
                
                .append("\n");
    }

    private static void appendDetalleAlmacenes(EstadoGlobal estado,
                                            boolean incluirCambios,
                                            StringBuilder sb)
    {
        sb.append("\n--- DETALLES ALMACENES ---\n");
        estado.getAlmacenes().values().stream()
                .filter(Almacen::tieneContenido)
                .forEach(a -> sb.append("  ")
                        .append(a.toString(incluirCambios))
                        .append("\n"));
    }

    private static void appendDetalleVuelos(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("\n--- DETALLES VUELOS ---\n");
        estado.getVuelos().values().stream()
                .filter(Vuelo::tieneContenido)
                .forEach(v -> sb.append("  ").append(v).append("\n"));
    }

    private static void appendDetallePedidos(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("\n--- DETALLES PEDIDOS ---\n");
        estado.getPedidos().values().stream()
                .filter(pedido -> !pedido.getIdsProductosEntregados().isEmpty()
                        && !pedido.getIdsProductosProgramados().isEmpty())
                .forEach(p -> sb.append("  ").append(p).append("\n"));
    }

    private static void appendResumenProgramaciones(List<Programacion> programaciones,
                                                    EstadoGlobal estado,
                                                    StringBuilder sb)
    {
        sb.append("\n--- RESUMEN PROGRAMACIONES ---\n");

        // ===== NUEVO: resumen de pedidos (demanda) =====
        int totalPedidos = 0;
        int pedidosConDemandaPendiente = 0;
        long productosDemandadosEnPedidos = 0L;

        if (estado != null && estado.getPedidos() != null)
        {
            Map<Long, Pedido> pedidos = estado.getPedidos();
            totalPedidos = pedidos.size();

            for (Pedido p : pedidos.values())
            {
                // Demanda "solo en pedidos": aquí uso demanda pendiente
                int faltantes = p.cantidadProductosFaltantes_v2(); // <-- usa tu método existente
                if (faltantes > 0)
                {
                    pedidosConDemandaPendiente++;
                    productosDemandadosEnPedidos += faltantes;
                }
            }
        }

        sb.append("  Total pedidos: ").append(totalPedidos).append("\n");
        sb.append("  Pedidos con demanda pendiente: ").append(pedidosConDemandaPendiente).append("\n");
        sb.append("  Productos demandados en pedidos (pendientes): ").append(productosDemandadosEnPedidos).append("\n");
        // ==============================================

        if (programaciones == null || programaciones.isEmpty())
        {
            sb.append("  No hay programaciones\n");
            return;
        }

        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        int totalProgramaciones = programaciones.size();
        int totalVuelos = 0;
        int maxVuelos = 0;
        int minVuelos = Integer.MAX_VALUE;

        long totalEsperaMinutos = 0L;
        long cantidadTiemposEspera = 0L;

        Map<Long, Vuelo> vuelos = (estado != null) ? estado.getVuelos() : null;
        Instant primerInicio = null;
        Instant ultimoInicio = null;

        for (Programacion programacion : programaciones)
        {
            List<Long> idsRuta = programacion.getIdsVueloRuta();
            int numVuelos = (idsRuta == null) ? 0 : idsRuta.size();

            totalVuelos += numVuelos;
            if (numVuelos > maxVuelos) maxVuelos = numVuelos;
            if (numVuelos < minVuelos) minVuelos = numVuelos;

            if (idsRuta == null || idsRuta.isEmpty() || vuelos == null)
            {
                continue;
            }

            for (Long idVuelo : idsRuta)
            {
                Vuelo v = vuelos.get(idVuelo);
                if (v == null) continue;

                Instant inicio = v.getInicio();
                if (inicio != null)
                {
                    if (primerInicio == null || inicio.isBefore(primerInicio)) primerInicio = inicio;
                    if (ultimoInicio == null || inicio.isAfter(ultimoInicio)) ultimoInicio = inicio;
                }
            }

            if (idsRuta.size() < 2) continue;

            for (int i = 0; i < idsRuta.size() - 1; i++)
            {
                Vuelo vueloActual = vuelos.get(idsRuta.get(i));
                Vuelo vueloSiguiente = vuelos.get(idsRuta.get(i + 1));
                if (vueloActual == null || vueloSiguiente == null) continue;

                Instant finActual = vueloActual.getInstanteLlegada();
                Instant inicioSiguiente = vueloSiguiente.getInicio();
                if (finActual == null || inicioSiguiente == null) continue;

                long minutos = Duration.between(finActual, inicioSiguiente).toMinutes();
                if (minutos >= 0)
                {
                    totalEsperaMinutos += minutos;
                    cantidadTiemposEspera++;
                }
            }
        }

        double promedioVuelosPorProg = (double) totalVuelos / totalProgramaciones;
        double esperaMediaMinutos = cantidadTiemposEspera > 0
                ? (double) totalEsperaMinutos / cantidadTiemposEspera
                : 0.0;

        sb.append("  Total de programaciones: ").append(totalProgramaciones).append("\n");
        sb.append("  Vuelos totales en rutas: ").append(totalVuelos).append("\n");
        sb.append("  Vuelos promedio por programacion: ").append(promedioVuelosPorProg).append("\n");
        sb.append("  Maximo numero de vuelos en una programacion: ").append(maxVuelos).append("\n");
        sb.append("  Minimo numero de vuelos en una programacion: ").append(minVuelos == Integer.MAX_VALUE ? 0 : minVuelos).append("\n");
        sb.append("  Tiempo de espera medio entre vuelos (minutos): ").append(esperaMediaMinutos).append("\n");

        if (primerInicio != null)
        {
            sb.append("  Instante del primer vuelo (inicio mas antiguo): ")
                    .append(formatInstant.apply(primerInicio))
                    .append("\n");
        }
        if (ultimoInicio != null)
        {
            sb.append("  Instante del ultimo vuelo (inicio mas reciente): ")
                    .append(formatInstant.apply(ultimoInicio))
                    .append("\n");
        }
    }

    private static void appendResumenProductos(Map<UUID, Producto> productos,
                                            StringBuilder sb)
    {
        sb.append("\n--- DETALLES PRODUCTOS ---\n");

        if (productos == null || productos.isEmpty())
        {
            return;
        }

        int total = productos.size();
        int countExiste = 0;
        int countPlanificado = 0;
        int countNoPlanificado = 0; // <-- NUEVO
        int countProntoParaEntrega = 0;

        for (Producto p : productos.values())
        {
            if (p.isExistente()) countExiste++;

            if (p.isPlanificado())
            {
                countPlanificado++;
            }
            else
            {
                countNoPlanificado++; // <-- NUEVO
            }

            if (p.isIncancelable()) countProntoParaEntrega++;
        }

        sb.append("  Total productos: ").append(total).append("\n");
        sb.append("  Productos existentes (existe=true): ").append(countExiste).append("\n");
        sb.append("  Productos planificados (planificado=true): ").append(countPlanificado).append("\n");
        sb.append("  Productos sin planificacion (planificado=false): ").append(countNoPlanificado).append("\n"); // <-- NUEVO
        sb.append("  Productos pronto para entrega (prontoParaEntrega=true): ").append(countProntoParaEntrega).append("\n");
    }

    private static void appendResumenVuelos(EstadoGlobal estado, StringBuilder sb)
    {
        sb.append("\n--- RESUMEN VUELOS ---\n");

        if (estado.getVuelos() == null || estado.getVuelos().isEmpty())
        {
            sb.append("  No hay vuelos\n");
            return;
        }

        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

        int totalVuelos = 0;
        int vuelosConProductos = 0;

        long totalDuracionMinutos = 0L;
        long cantidadDuraciones = 0L;

        Instant primerInicio = null;
        Instant ultimoInicio = null;

        for (Vuelo vuelo : estado.getVuelos().values())
        {
            totalVuelos++;

            List<UUID> contenidos = vuelo.getIdsProductosContenidos();
            List<UUID> programados = vuelo.getIdsProductosProgramados();

            if (contenidos != null && !contenidos.isEmpty()
                    && programados != null && !programados.isEmpty())
            {
                vuelosConProductos++;
            }

            Instant inicio = vuelo.getInicio();
            Instant fin = vuelo.getInstanteLlegada();

            if (inicio != null)
            {
                if (primerInicio == null || inicio.isBefore(primerInicio))
                {
                    primerInicio = inicio;
                }
                if (ultimoInicio == null || inicio.isAfter(ultimoInicio))
                {
                    ultimoInicio = inicio;
                }
            }

            if (inicio != null && fin != null)
            {
                long minutos = Duration.between(inicio, fin).toMinutes();
                if (minutos >= 0)
                {
                    totalDuracionMinutos += minutos;
                    cantidadDuraciones++;
                }
            }
        }

        double duracionMediaMinutos = cantidadDuraciones > 0
                ? (double) totalDuracionMinutos / cantidadDuraciones
                : 0.0;

        sb.append("  Total de vuelos: ").append(totalVuelos).append("\n");
        sb.append("  Vuelos con productos en inventario: ").append(vuelosConProductos).append("\n");
        sb.append("  Duracion media de los vuelos (minutos): ").append(duracionMediaMinutos).append("\n");

        if (primerInicio != null)
        {
            sb.append("  Instante del primer vuelo (inicio mas antiguo): ")
                    .append(formatInstant.apply(primerInicio))
                    .append("\n");
        }

        if (ultimoInicio != null)
        {
            sb.append("  Instante del ultimo vuelo (inicio mas reciente): ")
                    .append(formatInstant.apply(ultimoInicio))
                    .append("\n");
        }
    }


    private static void appendProgramacionDetallada(Programacion programacion,
                                                EstadoGlobal estado,
                                                boolean incluirCambiosAlmacen,
                                                StringBuilder sb)
    {
        if (programacion == null)
        {
            return;
        }

        Pedido pedido = estado.getPedidos().get(programacion.getIdPedido());

        sb.append("--- PROGRAMACION DETALLADA ---\n");
        sb.append("Programacion (Pedido ").append(programacion.getIdPedido()).append(")\n");
        if (pedido != null)
        {
            sb.append("  Pedido: ").append(pedido).append("\n");
        }

        List<Long> idsRuta = programacion.getIdsVueloRuta();
        if (idsRuta == null || idsRuta.isEmpty())
        {
            sb.append("  Sin vuelos asociados\n");
            return;
        }

        Map<Long, Vuelo> vuelos = estado.getVuelos();
        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        for (int i = 0; i < idsRuta.size(); i++)
        {
            Long idVuelo = idsRuta.get(i);
            Vuelo vuelo = vuelos.get(idVuelo);

            sb.append("  ").append(i + 1).append(". Vuelo ").append(idVuelo).append(":\n");

            if (vuelo == null)
            {
                sb.append("      (no encontrado)\n");
                continue;
            }

            sb.append("      ").append(vuelo.toString()).append("\n");

            Almacen origen = almacenes.get(vuelo.getAlmacenSalida().getId());
            Almacen destino = almacenes.get(vuelo.getAlmacenDestino());

            if (origen != null)
            {
                sb.append("      Origen:\n");
                sb.append("        ").append(origen.toString(incluirCambiosAlmacen)).append("\n");
            }

            if (destino != null)
            {
                sb.append("      Destino:\n");
                sb.append("        ").append(destino.toString(incluirCambiosAlmacen)).append("\n");
            }
        }
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
}
