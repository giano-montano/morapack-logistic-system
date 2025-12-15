package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Estrategia de planificación MOCK para testing de simulación. Genera
 * soluciones dummy que siempre son válidas, sin ejecutar el algoritmo GRASP
 * real.
 *
 * ⚠️ SOLO PARA TESTING - No usar en producción
 */
@Slf4j
@Component
public class EstrategiaPlanificacionMock extends EstrategiaPlanificacion
{

    private Random random = new Random();
    private static final int MAX_PROGRAMACIONES_POR_ITERACION = 5; // Limitar para no saturar
    private static final double PROBABILIDAD_PROGRAMAR_PEDIDO = 0.7; // 70% chance de marcarComoProgramado un
                                                                     // pedido

    @Override
    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion entrada)
    {
        log.info("🎭 MOCK: Iniciando planificación ficticia (para testing)");

        EstadoGlobal estadoGlobal = entrada.getEstadoGlobalCopia();
        setSemilla(entrada.getSemilla());

        List<Programacion> programacionesMock = new ArrayList<>();

        try
        {
            // Obtener pedidos pendientes
            List<Pedido> pedidosPendientes = estadoGlobal
                    .obtenerPedidosPendientesDeEntregaYProgram();

            if (pedidosPendientes.isEmpty())
            {
                log.info("🎭 MOCK: No hay pedidos pendientes");
                return SalidaProblemaPlanificacion.builder()
                        .programaciones(programacionesMock)
                        .colapsado(false)
                        .huboErrorEjecucion(false)
                        .build();
            }

            log.info("🎭 MOCK: Encontrados {} pedidos pendientes", pedidosPendientes.size());

            // Obtener almacenes infinitos (orígenes)
            List<Almacen> almacenesInfinitos = estadoGlobal
                    .devolverAlmacenesInfinitosOConStockDisponible(entrada.getInstanteActual()); // RAAAA

            if (almacenesInfinitos.isEmpty())
            {
                log.warn("🎭 MOCK: No hay almacenes infinitos disponibles");
                return SalidaProblemaPlanificacion.builder()
                        .programaciones(programacionesMock)
                        .colapsado(true)
                        .huboErrorEjecucion(false)
                        .build();
            }

            // Generar programaciones mock para algunos pedidos
            int programacionesCreadas = 0;
            for (Pedido pedido : pedidosPendientes)
            {
                // Limitar número de programaciones por iteración
                if (programacionesCreadas >= MAX_PROGRAMACIONES_POR_ITERACION)
                {
                    break;
                }

                // Probabilidad de marcarComoProgramado este pedido
                if (random.nextDouble() > PROBABILIDAD_PROGRAMAR_PEDIDO)
                {
                    continue;
                }

                int productosAProgramar = Math.min(
                        pedido.getCantidadProductosPendientes(),
                        random.nextInt(3) + 1 // 1-3 productos por vez
                );

                for (int i = 0; i < productosAProgramar; i++)
                {
                    Programacion programacionMock = generarProgramacionMock(
                            estadoGlobal, pedido, almacenesInfinitos);

                    if (programacionMock != null)
                    {
                        programacionesMock.add(programacionMock);
                        // Actualizar el estado global (simular que se programó)
                        estadoGlobal.anadirProgramacionSolucion(programacionMock, Instant.now());
                        programacionesCreadas++;
                    }
                }
            }

            log.info("🎭 MOCK: Generadas {} programaciones ficticias", programacionesMock.size());

            return SalidaProblemaPlanificacion.builder()
                    .programaciones(programacionesMock)
                    .colapsado(false)
                    .huboErrorEjecucion(false)
                    .build();

        }
        catch (Exception ex)
        {
            log.error("🎭 MOCK: Error generando planificación ficticia", ex);
            return SalidaProblemaPlanificacion.builder()
                    .programaciones(new ArrayList<>())
                    .colapsado(false)
                    .huboErrorEjecucion(true)
                    .error("Error en mock: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * Genera una programación mock válida para un pedido
     */
    private Programacion generarProgramacionMock(
            EstadoGlobal estadoGlobal,
            Pedido pedido,
            List<Almacen> almacenesInfinitos)
    {

        try
        {
            // Seleccionar almacén origen aleatorio
            Almacen almacenOrigen = almacenesInfinitos
                    .get(random.nextInt(almacenesInfinitos.size()));

            // Buscar vuelos que conecten origen -> destino (directos o con escalas)
            LinkedList<Long> rutaMock = buscarRutaSimpleMock(
                    estadoGlobal,
                    almacenOrigen.getId(),
                    pedido.getAlmacenDestino());

            if (rutaMock == null || rutaMock.isEmpty())
            {
                log.debug("🎭 MOCK: No se encontró ruta para pedido {}", pedido.getId());
                return null;
            }

            // Crear o reutilizar producto
            Producto producto = crearProductoMock(estadoGlobal, almacenOrigen, rutaMock);

            if (producto == null)
            {
                log.debug("🎭 MOCK: No se pudo crear producto para pedido {}", pedido.getId());
                return null;
            }

            // Crear programación
            Programacion programacion = new Programacion(
                    pedido.getId(),
                    producto.getId(),
                    rutaMock);

            log.debug("🎭 MOCK: Creada programación {} -> {} (ruta: {} vuelos)",
                    pedido.getId(), producto.getId(), rutaMock.size());

            return programacion;

        }
        catch (Exception ex)
        {
            log.error("🎭 MOCK: Error generando programación para pedido " + pedido.getId(), ex);
            return null;
        }
    }

    /**
     * Busca una ruta simple (1-2 vuelos) entre origen y destino
     */
    private LinkedList<Long> buscarRutaSimpleMock(
            EstadoGlobal estadoGlobal,
            Long idAlmacenOrigen,
            Long idAlmacenDestino)
    {

        LinkedList<Long> ruta = new LinkedList<>();

        // Buscar vuelo directo
        List<Vuelo> vuelosDirectos = estadoGlobal.getVuelos().values().stream()
                .filter(v -> v.getAlmacenSalida().getId() == idAlmacenOrigen)
                .filter(v -> v.getAlmacenDestino() == idAlmacenDestino)
                .filter(v -> v.getCapacidadDisponibleParaReserva() > 0)
                .limit(5)
                .collect(Collectors.toList());

        if (!vuelosDirectos.isEmpty())
        {
            // Seleccionar vuelo directo aleatorio
            Vuelo vueloElegido = vuelosDirectos.get(random.nextInt(vuelosDirectos.size()));
            ruta.add(vueloElegido.getId());
            return ruta;
        }

        // Si no hay directo, buscar con 1 escala
        List<Vuelo> vuelosSalida = estadoGlobal.getVuelos().values().stream()
                .filter(v -> v.getAlmacenSalida().getId() == idAlmacenOrigen)
                .filter(v -> v.getCapacidadDisponibleParaReserva() > 0)
                .limit(10)
                .collect(Collectors.toList());

        for (Vuelo v1 : vuelosSalida)
        {
            // Buscar vuelo que conecte
            List<Vuelo> vuelosConexion = estadoGlobal.getVuelos().values().stream()
                    .filter(v -> v.getAlmacenSalida().getId() == v1.getAlmacenDestino())
                    .filter(v -> v.getAlmacenDestino() == idAlmacenDestino)
                    .filter(v -> v.getCapacidadDisponibleParaReserva() > 0)
                    .filter(v -> v.getInicio().isAfter(v1.getInstanteLlegada())) // Temporalmente válido
                    .limit(3)
                    .collect(Collectors.toList());

            if (!vuelosConexion.isEmpty())
            {
                Vuelo v2 = vuelosConexion.get(random.nextInt(vuelosConexion.size()));
                ruta.add(v1.getId());
                ruta.add(v2.getId());
                return ruta;
            }
        }

        return null; // No se encontró ruta
    }

    /**
     * Crea un producto mock para la ruta
     */
    private Producto crearProductoMock(
            EstadoGlobal estadoGlobal,
            Almacen almacenOrigen,
            LinkedList<Long> ruta)
    {

        // Crear nuevo producto con UUID aleatorio
        Producto producto = new Producto(almacenOrigen.getId(), ruta, Instant.now());

        // Añadir al estado global si no existe
        if (!producto.isExistente())
        {
            estadoGlobal.anadirProducto(producto);
        }

        return producto;
    }
}
