package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.val;

import java.util.LinkedList;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

public final class Testeador
{
    private Testeador()
    {
        throw new AssertionError("No se inicializa el Testeador");
    }

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
            Instant instanteSalida  = vuelo.getInicio();
            Instant instanteLlegada = vuelo.getFin();

            // vuelo en tránsito: salida < ahora <= llegada
            if (instanteSalida.isBefore(instanteActual)
                    && !instanteLlegada.isBefore(instanteActual))
            {
                int cantidadEnVuelo = vuelo.getIdsProductosContenidos().size();
                if (cantidadEnVuelo == 0)
                {
                    // Este vuelo no aporta cambios de inventario
                    continue;
                }

                long idAlmacenDestino = vuelo.getIdAlmacenDestino();
                cambiosEsperados
                        .computeIfAbsent(idAlmacenDestino, k -> new HashMap<>())
                        .merge(instanteLlegada, cantidadEnVuelo, Integer::sum);

                // Verificar también productos futuros e instantes de disponibilidad
                Almacen almacenDestino = almacenes.get(idAlmacenDestino);

                for (UUID idProd : vuelo.getIdsProductosContenidos())
                {
                    Producto producto = productos.get(idProd);
                    boolean estaEnFuturos =
                            almacenDestino.getIdsProductosFuturos().contains(producto.getUuid());

                    if (!estaEnFuturos)
                    {
                        Bitacora.escribir(
                                "TEST ERROR (Vuelos en tránsito): Producto %s del vuelo %d "
                                        + "no está en productoFuturo del almacén %d",
                                idProd, vuelo.getId(), almacenDestino.getId());
                        return false;
                    }

                    if (!instanteLlegada.equals(producto.getInstanteDeDisponibilidad()))
                    {
                        Bitacora.escribir(
                                "TEST ERROR (Vuelos en tránsito): Producto %s debería tener "
                                        + "instanteDeDisponibilidad = %s, pero tiene %s",
                                idProd,
                                instanteLlegada,
                                producto.getInstanteDeDisponibilidad());
                        return false;
                    }
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
        LinkedList<Long> ruta;
        long idUltimoVuelo, idAlmacenDestino;
        Vuelo ultimoVuelo;
        Instant llegada, instanteRecojo;
        Almacen almacenDestino;
        Integer deltaRecojo;

        for (Programacion programacion : programaciones)
        {
            if (!programacion.isAPuntoDeCumplirse())
            {
                Bitacora.escribir("TEST ERROR (Programaciones): Existe una programación que no es incancelable. " + "Producto=%s, pedido=%d", programacion.getUuidProducto(), programacion.getIdPedido()
                );
                
                return false;
            }

            ruta = programacion.getIdsVueloRuta();
            if (ruta.isEmpty())
            {
                Bitacora.escribir("TEST ERROR (Programaciones): Programación sin ruta. Producto=%s, pedido=%d", programacion.getUuidProducto(), programacion.getIdPedido());
                
                return false;
            }

            idUltimoVuelo   = ruta.getLast();
            ultimoVuelo     = vuelos.get(idUltimoVuelo);
            llegada         = ultimoVuelo.getFin();
            instanteRecojo  = llegada.plus(Hiperparametros.HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS);
            idAlmacenDestino = ultimoVuelo.getIdAlmacenDestino();
            almacenDestino   = almacenes.get(idAlmacenDestino);

            deltaRecojo = almacenDestino.getCambios().get(instanteRecojo);

            // Cada programación incancelable debería aportar -1 en el instante de recojo
            if (deltaRecojo == null || deltaRecojo >= 0)
            {
                Bitacora.escribir( "TEST ERROR (Programaciones): En almacén %d, para producto=%s (pedido=%d), " + "no se encontró cambio negativo en cambios[%s]", idAlmacenDestino, programacion.getUuidProducto(), programacion.getIdPedido(), instanteRecojo);

                return false;
            }
        }

        return true;
    }

    /*
     * Compara la función calcularRutas_v2 con generarRutasParaPedidosPendientesBFS
     */
    public static void generacionRutasTest(EstadoGlobal estado, Instant instante)
    {
        List<LinkedList<Long>> idsRutasPosibles = estado.generarRutasParaPedidosPendientesBFS(instante);
        List<LinkedList<Vuelo>> rutasPosibles_a = estado.calcularRutas_v2(instante);
        List<LinkedList<Vuelo>> rutasPosibles_B = estado.calcularRutas_v2(instante);

        List<LinkedList<Long>> idsRutasPosibles_a = convertirRutasAVuelosId(rutasPosibles_a);

        compararRutasIds(idsRutasPosibles_a, idsRutasPosibles);
        sonRutasIgualesEntreCorridas(rutasPosibles_a, rutasPosibles_B);
    }

    private static List<LinkedList<Long>> convertirRutasAVuelosId(List<LinkedList<Vuelo>> rutasVuelos)
    {
        List<LinkedList<Long>> rutasIds = new ArrayList<>(rutasVuelos.size());

        for (LinkedList<Vuelo> ruta : rutasVuelos)
        {
            LinkedList<Long> idsRuta = ruta.stream()
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

    private static boolean sonRutasIgualesEntreCorridas(List<LinkedList<Vuelo>> rutasCorridaAnterior, List<LinkedList<Vuelo>> rutasCorridaActual)
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

    private static String crearFirmaRutaVuelo(List<Vuelo> ruta)
    {
        return ruta.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.joining("-"));
    }

    private static Set<String> firmarRutasVuelo(List<LinkedList<Vuelo>> rutas)
    {
        return rutas.stream()
                .map(Testeador::crearFirmaRutaVuelo)
                .collect(Collectors.toSet());
    }

    private static String imprimirRutasVuelos(List<LinkedList<Vuelo>> rutas)
    {
        StringBuilder sb;
        int indiceRuta, cantidadMostrada;

        sb = new StringBuilder();
        cantidadMostrada = Math.min(10, rutas.size());

        sb.append("===== RUTAS (por VUELOS) =====\n");
        sb.append("Total rutas: ").append(rutas.size()).append("\n");
        sb.append("Mostrando primeras ").append(cantidadMostrada).append(" rutas\n\n");

        indiceRuta = 1;

        for (LinkedList<Vuelo> ruta : rutas)
        {
            if (indiceRuta > 10)
            {
                break;
            }

            sb.append("Ruta #").append(indiceRuta++)
              .append(" (tramos = ").append(ruta.size()).append(")\n");

            for (int i = 0; i < ruta.size(); i++)
            {
                Vuelo vuelo = ruta.get(i);
                sb.append("  [")
                  .append(i + 1)
                  .append("] Vuelo ")
                  .append(vuelo.getId())
                  .append(" (")
                  .append(vuelo.getIdAlmacenOrigen())
                  .append(" -> ")
                  .append(vuelo.getIdAlmacenDestino())
                  .append(") ")
                  .append("inicio=")
                  .append(vuelo.getInicio())
                  .append(", fin=")
                  .append(vuelo.getFin())
                  .append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    

}
