package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Verifica que, dentro de la lista de rutas válidas, exista al menos una
     * ruta cuyo almacén de origen sea infinito.
     */
    public static void verificarRutasConAlmacenInfinitoComoOrigen(EstadoGlobal estado, List<LinkedList<Vuelo>> rutasValidas)
    {
        if (rutasValidas == null || rutasValidas.isEmpty())
        {
            Bitacora.escribir("TEST RUTAS ORIGEN INFINITO: lista de rutas vacía o nula");
            return;
        }

        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        boolean hayInfinito = false;
        int rutasConOrigenInfinito = 0;
        TreeSet<Long> idsAlmacenesInfinitos = new TreeSet<>();

        for (LinkedList<Vuelo> ruta : rutasValidas)
        {
            if (ruta == null || ruta.isEmpty())
            {
                continue;
            }

            Vuelo primerVuelo = ruta.getFirst();
            Almacen origen = almacenes.get(primerVuelo.getIdAlmacenOrigen());

            if (origen != null && origen.isInfinito())
            {
                hayInfinito = true;
                rutasConOrigenInfinito++;
                idsAlmacenesInfinitos.add(origen.getId());
            }
        }

        if (!hayInfinito)
        {
            String mensaje = "TEST ERROR: No se encontraron rutas con almacenes infinitos como origen";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje); 
        }
    }

    public static void cantidadProductosConsistenteTest(EstadoGlobal estado)
    {
        int productosEnAlmacenes = 0;
        int productosEnVuelos = 0;
        int productosExistentes = 0;

        // ====== 1) Productos en ALMACENES (solo EXISTENTES) ======
        Map<Long, Almacen> almacenes = estado.getAlmacenes();

        for (Almacen a : almacenes.values())
        {
            productosEnAlmacenes += a.getIdsProductosExistentes().size();
        }


        // ====== 2) Productos en VUELOS ======
        Map<Long, Vuelo> vuelos = estado.getVuelos();
        
        for (Vuelo v : vuelos.values())
        {
            productosEnVuelos += v.getIdsProductosContenidos().size();
        }

        // ====== 3) Total existentes (existe=true) ======
        Map<UUID, Producto> productos = estado.getProductos();
        for (Producto p : productos.values())
        {
            if (p.isExistente())
            {
                productosExistentes++;
            }
        }
        int totalUbicados = productosEnAlmacenes + productosEnVuelos;

        // ====== 4) Log + assert ======
        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST CONSISTENCIA PRODUCTOS ===\n");
        sb.append("Productos en almacenes (existentes): ").append(productosEnAlmacenes).append("\n");
        sb.append("Productos en vuelos: ").append(productosEnVuelos).append("\n");
        sb.append("Total ubicados (almacenes+vuelos): ").append(totalUbicados).append("\n");
        sb.append("Total productos existentes (existe=true): ").append(productosExistentes).append("\n");
        sb.append("Consistente: ").append(totalUbicados == productosExistentes).append("\n");

        if (totalUbicados != productosExistentes)
        {
            sb.append("ERROR: No coincide.\n");
        }

        Bitacora.escribir(sb.toString());
/* 
        if (totalUbicados != productosExistentes)
        {
            throw new IllegalStateException(
                    "Inconsistencia productos: ubicados=" + totalUbicados + " vs existentes=" + productosExistentes);
        }
        */
    }
    public static void cantidadDeProgramacionesPlanificadasTest(EstadoGlobal estado)
    {
        // ====== Programaciones ======
        int nProgIncancelables = 0;   // aPuntoDeCumplirse = true
        int nProgCancelables = 0;     // aPuntoDeCumplirse = false

        List<Programacion> programaciones = estado.getProgramaciones();
        if (programaciones != null)
        {
            for (Programacion p : programaciones)
            {
                if (p != null && p.isAPuntoDeCumplirse())
                {
                    nProgIncancelables++;
                }
                else
                {
                    nProgCancelables++;
                }
            }
        }

        // ====== Productos ======
        int nProdExistentes = 0;          // existe = true
        int nProdInexistentes = 0;        // existe = false
        int nProdIncancelables = 0;       // prontoParaEntrega = true
        int nProdPlanificados = 0;        // planificado = true

        HashMap<UUID, Producto> productos = estado.getProductos();
        if (productos != null)
        {
            for (Producto prod : productos.values())
            {
                if (prod == null) continue;

                if (prod.isExistente()) nProdExistentes++;
                else nProdInexistentes++;

                if (prod.isIncancelable()) nProdIncancelables++;

                if (prod.isPlanificado()) nProdPlanificados++;
            }
        }

        // ====== Log ======
        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST: Conteos de Programaciones y Productos ===\n");

        sb.append("--- PROGRAMACIONES ---\n");
        sb.append("  Total: ").append(programaciones == null ? 0 : programaciones.size()).append("\n");
        sb.append("  Incancelables (aPuntoDeCumplirse=true): ").append(nProgIncancelables).append("\n");
        sb.append("  Cancelables (aPuntoDeCumplirse=false): ").append(nProgCancelables).append("\n");

        sb.append("--- PRODUCTOS ---\n");
        sb.append("  Total: ").append(productos == null ? 0 : productos.size()).append("\n");
        sb.append("  Existentes (existe=true): ").append(nProdExistentes).append("\n");
        sb.append("  Inexistentes (existe=false): ").append(nProdInexistentes).append("\n");
        sb.append("  Incancelables (prontoParaEntrega=true): ").append(nProdIncancelables).append("\n");
        sb.append("  Planificados (planificado=true): ").append(nProdPlanificados).append("\n");

        Bitacora.escribir(sb.toString());
    }


    public static void probarPersistirProgramacionesEnRuta(LinkedList<Vuelo> ruta_original, EstadoGlobal estado)
    {
        LinkedList<Vuelo> ruta;
        Vuelo vuelo;
        Almacen almacenOrigen, almacenDestino, almacenOrigen_original, almacenDestino_original;

        ruta = new LinkedList<>();

        for(Vuelo vuelo_original : ruta_original)
        {
            almacenDestino_original = estado.destinoVuelo_v2(vuelo_original);
            almacenOrigen_original = estado.origenVuelo_v2(vuelo_original);
            
            almacenDestino = deepCopy(almacenDestino_original);
            almacenOrigen = deepCopy(almacenOrigen_original);
            vuelo = deepCopy(vuelo_original);
            ruta.add(vuelo);
        }

        Instant instanteInicioRuta = ruta.getFirst().getInicio();
        almacenOrigen = estado.destinoRuta(ruta);
        
        List<Producto> productosEnAlmacen = estado.obtenerProductosDisponibles_v2(almacenOrigen, instanteInicioRuta);

        int capacidadAlmacen = almacenOrigen.isInfinito()? Integer.MAX_VALUE : productosEnAlmacen.size();
        int capacidadRuta = estado.obtenerCapacidadRuta_v2(ruta, capacidadAlmacen);

        boolean valido;

        List<Producto> productos = new ArrayList<>();

        for(int i = 0; i != capacidadRuta; i++){
            productos.add(new Producto(almacenOrigen, instanteInicioRuta));
        }

        for(Vuelo V : ruta)
        {
            Almacen almacenSalida = estado.origenVuelo_v2(V);
            valido = almacenSalida.registrarSalida_v2(V.getInicio(), capacidadAlmacen);

            if(!valido && !almacenSalida.isInfinito())
            {
                Bitacora.escribir("MAL! FUCK");
            }

            valido = V.registrarInventario_v2(productos);

            if(!valido)
            {
                Bitacora.escribir("MAL!");
            }

            Almacen almacenEntrada = estado.destinoVuelo_v2(V);
            valido = almacenEntrada.registrarEntrada_v2(V.getFin(), capacidadAlmacen);

            if(!valido)
            {
                Bitacora.escribir("MAL! ");
            }
        }

    }

    /**
     * Helper para clonar profundamente un objeto Serializable usando serialización.
     */
    @SuppressWarnings("unchecked")
    private static <T extends java.io.Serializable> T deepCopy(T objeto)
    {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos))
        {
            oos.writeObject(objeto);
            oos.flush();
            byte[] bytes = bos.toByteArray();

            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
                 java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis))
            {
                return (T) ois.readObject();
            }
        }
        catch (java.io.IOException | ClassNotFoundException e)
        {
            throw new RuntimeException("Error en deepCopy de EstadoGlobal", e);
        }
    }
    /*
     * Verifica que los cambios esten bien realizados
     */
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
        }else{
            Bitacora.escribir("===TEST CAMBIOS===\n NO COINCIDE");
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

                long idAlmacenDestino = vuelo.getAlmacenDestino();
                cambiosEsperados
                        .computeIfAbsent(idAlmacenDestino, k -> new HashMap<>())
                        .merge(instanteLlegada, cantidadEnVuelo, Integer::sum);

                // Verificar también productos futuros e instantes de disponibilidad
                Almacen almacenDestino = almacenes.get(idAlmacenDestino);

                for (UUID idProd : vuelo.getIdsProductosContenidos())
                {
                    Producto producto = productos.get(idProd);
                    boolean estaEnFuturos =
                            almacenDestino.getIdsProductosFuturos().contains(producto.getId());

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
            instanteRecojo  = llegada.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));
            idAlmacenDestino = ultimoVuelo.getAlmacenDestino();
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
                  .append(vuelo.getAlmacenDestino())
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
