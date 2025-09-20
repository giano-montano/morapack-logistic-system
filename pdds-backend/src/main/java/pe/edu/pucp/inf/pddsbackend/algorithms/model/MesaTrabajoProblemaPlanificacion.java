package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.dialect.function.array.ArrayAndElementArgumentTypeResolver;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;

import java.time.Instant;
import java.util.*;


@Getter
public class MesaTrabajoProblemaPlanificacion {
    @NotNull
    private HashMap<Long, AlmacenParaAlgoritmo> almacenes;
    @NotNull
    private HashMap<Long, VueloParaAlgoritmo> vuelos;
    @NotNull
    private HashMap<Long, PedidoParaAlgoritmo> pedidos;
    @NotNull
    private List<RutaProgramadaParaAlgoritmo> rutasSolucionQueGeneraAlgoritmo;

    @Setter
    LoggingReport loggingReport;

    HashMap<Long, List<Long>> idsVuelosPorOrigen;
    HashMap<Long, List<Long>> idsVuelosPorDestino;
    HashMap<Long, List<Long>> idsPedidosPorDestino;
    HashMap<Long, List<Long>> idsRutasPorAlmacen; //??

//    ArrayList<Object> parametrosOpcionalesPersonalizados;

    private final int HORAS_PARA_RECOGER_PEDIDO = 2;
    private final long SEGUNDOS_PARA_RECOGER_PEDIDO = HORAS_PARA_RECOGER_PEDIDO * 3600L;

    public MesaTrabajoProblemaPlanificacion( HashMap<Long, AlmacenParaAlgoritmo> almacenes, HashMap<Long, VueloParaAlgoritmo> vuelos, HashMap<Long, PedidoParaAlgoritmo> pedidos) {
        this.almacenes = almacenes != null?new HashMap<>(almacenes):new HashMap<>();
        this.vuelos = vuelos != null?new HashMap<>(vuelos):new HashMap<>();
        this.pedidos = pedidos != null?new HashMap<>(pedidos):new HashMap<>();
        this.rutasSolucionQueGeneraAlgoritmo = new ArrayList<>();
    }

    public MesaTrabajoProblemaPlanificacion(HashMap<Long, AlmacenParaAlgoritmo> almacenes, HashMap<Long, VueloParaAlgoritmo> vuelos, HashMap<Long, PedidoParaAlgoritmo> pedidos, List<RutaProgramadaParaAlgoritmo> rutasSolucionQueGeneraAlgoritmo, HashMap<Long, List<Long>> idsVuelosPorOrigen, HashMap<Long, List<Long>> idsVuelosPorDestino, HashMap<Long, List<Long>> idsPedidosPorDestino) {
        this.almacenes = almacenes;
        this.vuelos = vuelos;
        this.pedidos = pedidos;
        this.rutasSolucionQueGeneraAlgoritmo = rutasSolucionQueGeneraAlgoritmo;
        this.idsVuelosPorOrigen = idsVuelosPorOrigen;
        this.idsVuelosPorDestino = idsVuelosPorDestino;
        this.idsPedidosPorDestino = idsPedidosPorDestino;
    }

    public static MesaTrabajoProblemaPlanificacion desdeEntradaPlanificacion(EntradaProblemaPlanificacion entradaPlanificacion) {
        return new MesaTrabajoProblemaPlanificacion(
                entradaPlanificacion.almacenes,entradaPlanificacion.vuelos,entradaPlanificacion.pedidos);
    }


    public boolean rutaEsFactibleEnEstadoActual(RutaProgramadaParaAlgoritmo rutaPlanificacion) { // RutaProgramadaParaAlgoritmo podría ser interfaz!
        PedidoParaAlgoritmo pedidoAsociado = pedidos.get(rutaPlanificacion.getIdPedidoAsociado());
        int cantidadDelPedido = rutaPlanificacion.getCantidadTotalOParcial();
        List<VueloParaAlgoritmo> vuelosAsociados = rutaPlanificacion.getIdsVuelosEnOrden()
                .stream()
                .map(id -> vuelos.get(id))
                .toList();

        if (cantidadDelPedido <= 0) return false;
        int pendientePedido = pedidoAsociado.getCantidadProductosPedidos()
                - pedidoAsociado.getCantidadProductosProgramados();
        if (cantidadDelPedido > pendientePedido) return false;

        VueloParaAlgoritmo ultimoVuelo = vuelosAsociados.get(vuelosAsociados.size() - 1);
        if (ultimoVuelo.getIdAlmacenDestino() != pedidoAsociado.getIdAlmacenDestino()) {
            return false;
        }

        VueloParaAlgoritmo prev = null;
        // opcional: cache para evitar recalcular mismo almacen+instante muchas veces
        Map<String, AlmacenParaAlgoritmo> cacheSimulAlmacenes = new HashMap<>();

        for (VueloParaAlgoritmo vuelo : vuelosAsociados) {
            // conectividad entre tramos: prev.dest == current.origin
            if (prev != null) {
                if (prev.getIdAlmacenDestino() != vuelo.getIdAlmacenOrigen()) {
                    return false; // ruta desconectada
                }
                // orden temporal: inicio actual >= fin prev
                if (vuelo.getInicio().isBefore(prev.getFin())) {
                    return false; // solapamiento temporal inválido
                }
            }

            // capacidad del vuelo (usando capacidadSinOcupar actual en el objeto)
            if (vuelo.getCapacidadSinOcupar() < cantidadDelPedido) {
                return false; // vuelo sin espacio suficiente
            }

            // 3.d capacidad en almacén origen al inicio del vuelo
            AlmacenParaAlgoritmo almOrigen = almacenes.get(vuelo.getIdAlmacenOrigen());
            String keyOrigen = almOrigen.getId() + "|" + vuelo.getInicio().toString();
            AlmacenParaAlgoritmo simulOrigen = cacheSimulAlmacenes.computeIfAbsent(keyOrigen,
                    k -> obtenerAlmacenEnInstante(almOrigen, vuelo.getInicio()));
            if (simulOrigen.getCapacidadSinOcupar() < cantidadDelPedido) {
                return false; // origen no puede suministrar en ese instante
            }

            // 3.e capacidad en almacén destino al fin del vuelo
            AlmacenParaAlgoritmo almDestino = almacenes.get(vuelo.getIdAlmacenDestino());
            String keyDestino = almDestino.getId() + "|" + vuelo.getFin().toString();
            AlmacenParaAlgoritmo simulDestino = cacheSimulAlmacenes.computeIfAbsent(keyDestino,
                    k -> obtenerAlmacenEnInstante(almDestino, vuelo.getFin()));
            if (simulDestino.getCapacidadSinOcupar() < cantidadDelPedido) {
                return false; // destino no tiene espacio al llegar
            }

            prev = vuelo;
        }

        // todas las comprobaciones pasaron
        return true;
    }

    /**
     * Añade a la mesa (estado global) una ruta ya validada.
     * - Actualiza: rutasQueGeneraAlgoritmo, vuelos.capacidadOcupadaProductos, pedido.cantidadProductosProgramados.
     *
     * IMPORTANTE: se asume que la ruta fue validada previamente contra el estado actual
     * (capacidad de vuelos/almacenes, conectividad temporal, etc.). Si hay una inconsistencia
     * (por ejemplo, falta de capacidad en un vuelo) lanzamos IllegalStateException para detectar
     * condiciones de carrera o errores lógicos.
     */
    public void anadirRutaSolucion(RutaProgramadaParaAlgoritmo r) {
        if (r == null) return;

        // Protección simple: si ya existe la misma instancia no la volvemos a añadir
        if (this.rutasSolucionQueGeneraAlgoritmo.contains(r)) {
            // ya añadida, nada que hacer
            return;
        }

        final int cantidad = r.getCantidadTotalOParcial();
        final long idPedido = r.getIdPedidoAsociado();

        // 1) Añadir la ruta al conjunto de rutas actuales (esto permite que las simulaciones vean la nueva ruta)
        this.rutasSolucionQueGeneraAlgoritmo.add(r);

        // 2) Actualizar el pedido: incrementar cantidadProgramada
        PedidoParaAlgoritmo pedido = this.pedidos.get(idPedido);
        if (pedido != null) {
            // suponemos getters/setters en PedidoParaAlgoritmo
//            int yaProgramados = pedido.getCantidadProductosProgramados();
            pedido.agregarCantidadProgramada(cantidad);
        } else {
            // si no existe el pedido algo anda mal en la lógica previa — lo dejamos claro lanzando excepción
            throw new IllegalStateException("Pedido inexistente al añadir ruta: idPedido=" + idPedido);
        }

        // 3) Ocupar capacidad en cada vuelo de la ruta (opera de forma sincronizada en VueloParaAlgoritmo)
        //    Si alguno falla, lanzamos excepción (y no intentamos rollback interno aquí, porque se asumió validación).
        for (Long idVuelo : r.getIdsVuelosEnOrden()) {
            VueloParaAlgoritmo vuelo = this.vuelos.get(idVuelo);
            if (vuelo == null) {
                throw new IllegalStateException("Vuelo inexistente al añadir ruta: idVuelo=" + idVuelo);
            }
            boolean pudo = vuelo.ocuparCapacidad(cantidad); // método synchronized en VueloParaAlgoritmo
            if (!pudo) {
                // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
                // Lanzamos excepción para que el llamador decida rollback/handling.
                throw new IllegalStateException("Vuelo sin capacidad al añadir ruta (inconsistencia). idVuelo=" + idVuelo +
                        " cantidad=" + cantidad + " capacidadSinOcupar=" + vuelo.getCapacidadSinOcupar());
            }
            // Opcional: actualizar índice local si usas idsRutasProgramadasDePlanifNoColapsNiReprog
            // vuelo.getIdsRutasProgramadasDePlanifNoColapsNiReprog().add(someRouteIdentifier);
        }

        // 4) (Opcional) actualizar índices auxiliares en la mesa
        //    Por ejemplo, si mantienes un contador global de rutas, o un mapa pedido->rutas,
        //    puedes registrar la ruta para consultas rápidas:
        //    rutasPorPedido.computeIfAbsent(idPedido, k->new ArrayList<>()).add(r);

        // Nota: no tocamos aquí capacidades de almacén directamente porque las simulaciones
        // (obtenerAlmacenEnInstante) ya consideran rutas en rutasQueGeneraAlgoritmo para calcular
        // ocupaciones/entradas/salidas en instantes futuros. Si deseas reservar espacio en los
        // almacenes ahora mismo (p. ej. capacidadReservada), hazlo explícito aquí.
    }




    /**
     * Simula el estado del almacén `alm` en el instante `instante` teniendo en cuenta
     * todas las rutas que genera el algoritmo (rutasSolucionQueGeneraAlgoritmo).
     *
     * Regla: cada vuelo provoca:
     *  - en su origen: desocupación desde la hora de inicio (la carga sale del almacén);
     *  - en su destino: ocupación desde la hora de fin (la carga llega y ocupa espacio);
     *  - si el vuelo es el último tramo de la ruta: después de fin + ventana (2h) se libera (pickup).
     *
     * Esto hace que las escalas depositen en almacenes intermedios (llegada->ocupar, posterior salida->desocupar).
     */
    public AlmacenParaAlgoritmo obtenerAlmacenEnInstante(AlmacenParaAlgoritmo alm, Instant instante){ //usamos las rutas programadas hasta ahora
        AlmacenParaAlgoritmo almacenSimuladoHastaInstante = alm.clone();
        long idAlmacenSimulado = alm.getId();

        for(RutaProgramadaParaAlgoritmo rutita : rutasSolucionQueGeneraAlgoritmo){
            List<VueloParaAlgoritmo> vuelitos = obtenerVariosVuelosPorIds(rutita.getIdsVuelosEnOrden());
            int cantProdsRuta = rutita.getCantidadTotalOParcial();
            // procesar cada vuelo: salida en origen, llegada en destino
            for (int i = 0; i < vuelitos.size(); i++) {
                VueloParaAlgoritmo vuelo = vuelitos.get(i);
                if (vuelo == null) continue;

                Instant inicio = vuelo.getInicio();
                Instant fin = vuelo.getFin();

                // 1) salida: si este almacén es origen y la salida ya ocurrió (instante >= inicio)
                if (vuelo.getIdAlmacenOrigen() == idAlmacenSimulado) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                    }
                }

                // 2) llegada: si este almacén es destino y la llegada ya ocurrió (instante >= fin)
                if (vuelo.getIdAlmacenDestino() == idAlmacenSimulado) {
                    if (!instante.isBefore(fin)) { // instante >= fin
                        almacenSimuladoHastaInstante.ocuparCapacidad(cantProdsRuta);
                    }

                    // 3) si es el último vuelo de la ruta, aplicar pickup (liberación tras ventana)
                    if (i == vuelitos.size() - 1) {
                        Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);
                        if (!instante.isBefore(instantePickup)) { // instante >= fin + ventana
                            almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                        }
                    }
                }
            }
        }
        return almacenSimuladoHastaInstante;
    }

    // Creo que lo reservado no sirve, debido a la variable temporal, lo que ahora está reservado puede ya  haber desaparecido en unos días y estar libre.
    public boolean ocuparEnAlmacenEsFactibleEnInstante(int cantidad, AlmacenParaAlgoritmo alm, Instant instante){ //usamos las rutas programadas hasta ahora
        AlmacenParaAlgoritmo almacenSimuladoHastaInstante = obtenerAlmacenEnInstante(alm, instante);
        return almacenSimuladoHastaInstante.obtenerCapacidadSinOcupar() >= cantidad;
    }

    List<VueloParaAlgoritmo> obtenerVariosVuelosPorIds(List<Long> idsVuelosEnOrden){
        List<VueloParaAlgoritmo> vuelosAObtener = new ArrayList<>();
        for(Long id: idsVuelosEnOrden){
            vuelosAObtener.add(vuelos.get(id));
        }
        return vuelosAObtener;
    }

}
