package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedList;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

public final class Testeador
{
    private Testeador()
    {
        throw new AssertionError("No se inicializa el Testeador");
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

    private static boolean sonRutasIgualesEntreCorridas(List<LinkedList<Vuelo>> rutasCorridaAnterior,
                                                        List<LinkedList<Vuelo>> rutasCorridaActual)
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
