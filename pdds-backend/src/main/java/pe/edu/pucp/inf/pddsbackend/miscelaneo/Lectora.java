package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Continente;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@NoArgsConstructor
public class Lectora
{

    public Map<UUID, Almacen> leerArchivoAlmacenes()
            throws IOException
    {
        String linea;
        BufferedReader bufferedReader;
        Continente continente = null;
        Almacen almacen;
        Map<UUID, Almacen> almacenes;

        almacenes = new HashMap<>();
        bufferedReader = this.abrirArchivo("datos/almacenes.txt");

        try
        {
            while ((linea = bufferedReader.readLine()) != null)
            {
                linea = linea.trim();

                if (!linea.isEmpty())
                {
                    if (!Character.isDigit(linea.charAt(0)))
                    {
                        continente = Continente.valueOf(
                                linea.trim().replaceAll("\s+", "_").toUpperCase());
                        continue;
                    }

                    String[] partes = linea.split("\s+");
                    String id = partes[1];
                    String ciudad = partes[2];
                    String pais = partes[3];
                    Integer utc = (int) Long.parseLong(partes[5]);
                    Integer capacidad = (int) Long.parseLong(partes[6]);

                    almacen = new Almacen(id,
                            capacidad,
                            0,
                            utc,
                            ciudad,
                            pais,
                            continente);
                    almacenes.put(almacen.getId(), almacen);
                }
            }

            return almacenes;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

    }

    public Map<UUID, Vuelo> leerArchivoVuelos(Instant instanteActual, Map<UUID, Almacen> almacenes)
    {
        String linea;
        BufferedReader bufferedReader;
        Vuelo vuelo;
        Map<UUID, Vuelo> vuelos;

        vuelos = new HashMap<>();
        bufferedReader = this.abrirArchivo("datos/vuelos.txt");

        try
        {
            while ((linea = bufferedReader.readLine()) != null)
            {
                linea = linea.trim();

                if (!linea.isEmpty())
                {
                    String[] partes = linea.split("-");
                    String idOrigen = partes[0];
                    String idDestino = partes[1];
                    String horaSalida = partes[2];
                    String horaLlegada = partes[3];
                    Integer capacidad = (int) Long.parseLong(partes[4]);
                    Instant salida = calcularInstantVuelo(instanteActual, horaSalida);
                    Instant llegada = calcularInstantVueloConDia(instanteActual, horaSalida,
                            horaLlegada);

                    vuelo = new Vuelo(UUID.randomUUID(),
                            capacidad,
                            0,
                            almacenes.get(UUID.nameUUIDFromBytes(idOrigen.getBytes())),
                            almacenes.get(UUID.nameUUIDFromBytes(idDestino.getBytes())),
                            salida,
                            llegada);
                    vuelos.put(vuelo.getId(), vuelo);
                }
            }

            return vuelos;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

    }

    public Map<UUID, Pedido> leerArchivoPedidos(Instant inicioOperaciones,
            Map<UUID, Almacen> almacenes)
    {
        String linea;
        BufferedReader bufferedReader;
        Pedido pedido;
        Map<UUID, Pedido> pedidos;

        pedidos = new LinkedHashMap<>();
        bufferedReader = this.abrirArchivo("datos/pedidos.txt");

        try
        {
            while ((linea = bufferedReader.readLine()) != null)
            {
                linea = linea.trim();

                if (!linea.isEmpty())
                {
                    String[] partes = linea.split("-", 5);
                    Integer dia = Integer.parseInt(partes[0]);
                    Instant instanteRegistro = this.calcularInstantDesdeDiaYHora(dia, partes[1],
                            inicioOperaciones);
                    String idAlmacen = partes[2];
                    Integer cantidad = (int) Long.parseLong(partes[3]);
                    UUID id = UUID.fromString(partes[4]);
                    List<Producto> productosExistentes = new ArrayList<Producto>();

                    pedido = new Pedido(id,
                            cantidad,
                            0,
                            instanteRegistro,
                            almacenes.get(UUID.nameUUIDFromBytes(idAlmacen.getBytes())),
                            productosExistentes);
                    pedidos.put(pedido.getId(), pedido);
                }
            }

            return pedidos;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

    }

    private BufferedReader abrirArchivo(String rutaArchivo)
    {
        InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(rutaArchivo);

        if (inputStream == null)
        {
            throw new UncheckedIOException(
                    new FileNotFoundException("Resource not found: datos/almacenes.txt"));
        }

        return new BufferedReader(new InputStreamReader(inputStream));
    }

    private Instant calcularInstantVuelo(Instant instanteBase, String horaHHmm)
    {
        LocalDate diaBase = LocalDateTime.ofInstant(instanteBase, ZoneOffset.UTC).toLocalDate();
        LocalTime hora = LocalTime.parse(horaHHmm);

        return LocalDateTime.of(diaBase, hora).toInstant(ZoneOffset.UTC);
    }

    private Instant calcularInstantVueloConDia(Instant instanteBase, String horaSalida,
            String horaLlegada)
    {
        Instant salida = calcularInstantVuelo(instanteBase, horaSalida);

        LocalDateTime salidaLocal = LocalDateTime.ofInstant(salida, ZoneOffset.UTC);
        LocalTime llegadaHora = LocalTime.parse(horaLlegada);
        LocalDate llegadaFecha = salidaLocal.toLocalDate();

        if (llegadaHora.isBefore(salidaLocal.toLocalTime()))
        {
            llegadaFecha = llegadaFecha.plusDays(1);
        }

        return LocalDateTime.of(llegadaFecha, llegadaHora).toInstant(ZoneOffset.UTC);
    }

    public Instant calcularInstantDesdeDiaYHora(Integer dia, String hora, Instant inicioOperaciones)
    {
        ZonedDateTime inicio = inicioOperaciones.atZone(ZoneOffset.UTC);
        ZonedDateTime fechaObjetivo = inicio.plusDays(dia - 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime tiempo = LocalTime.parse(hora, formatter);

        fechaObjetivo = fechaObjetivo.withHour(tiempo.getHour()).withMinute(tiempo.getMinute())
                .withSecond(0).withNano(0);

        return fechaObjetivo.toInstant();
    }

}
