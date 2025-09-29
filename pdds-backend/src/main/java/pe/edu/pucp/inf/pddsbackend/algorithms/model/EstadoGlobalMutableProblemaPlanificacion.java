package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.SerializationUtils;
import pe.edu.pucp.inf.pddsbackend.utils.Formateador;
import pe.edu.pucp.inf.pddsbackend.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Getter
public class EstadoGlobalMutableProblemaPlanificacion implements Serializable {
    @NotNull
    private HashMap<Long, AlmacenParaAlgoritmo> almacenes;
    @NotNull
    private HashMap<Long, VueloParaAlgoritmo> vuelos;
    @NotNull
    private HashMap<Long, PedidoParaAlgoritmo> pedidos;

    @NotNull
    @Setter
    private List<RutaProgramadaParaAlgoritmo> rutasSolucionQueGeneraAlgoritmo; // EMPIEZA VACÍO !!!!!!!!!!!!!!!!!!!!!!!

    @Setter
    LoggingReport loggingReport = new LoggingReport();
    // índices
    HashMap<Long, List<Long>> idsVuelosPorOrigen;
    HashMap<Long, List<Long>> idsVuelosPorDestino;
    HashMap<Long, List<Long>> idsPedidosPorDestino;
    HashMap<Long, List<Long>> idsRutasPorAlmacen = new HashMap<>(); //??

    Set<Long> idsAlmacenesNoInfinitos = new HashSet<>();
    Set<Long> idsAlmacenesInfinitosOConStock = new HashSet<>();
    Set<AlmacenParaAlgoritmo> almacenesInfinitosOConStock = new HashSet<>();
//    ArrayList<Object> parametrosOpcionalesPersonalizados;


    private final int HORAS_PARA_RECOGER_PEDIDO = 2;
    private final long SEGUNDOS_PARA_RECOGER_PEDIDO = HORAS_PARA_RECOGER_PEDIDO * 3600L;
    private static final int MAX_LEGS = 10; // número máximo de tramos por ruta (incluye primer vuelo)
    private static final int MAX_RUTAS_POR_DESTINO = 25;
    private static final int MAX_RUTAS_POR_ORIGEN = 15;

    public static EstadoGlobalMutableProblemaPlanificacion desdeEntradaPlanificacion(EntradaProblemaPlanificacion entradaPlanificacion) {
        return new EstadoGlobalMutableProblemaPlanificacion(
                entradaPlanificacion.almacenes,entradaPlanificacion.vuelos,entradaPlanificacion.pedidos);
    }

    public EstadoGlobalMutableProblemaPlanificacion(HashMap<Long, AlmacenParaAlgoritmo> almacenes, HashMap<Long, VueloParaAlgoritmo> vuelos, HashMap<Long, PedidoParaAlgoritmo> pedidos) {
        this.almacenes = almacenes != null?new HashMap<>(almacenes):new HashMap<>();
        this.vuelos = vuelos != null?new HashMap<>(vuelos):new HashMap<>();
        this.pedidos = pedidos != null?new HashMap<>(pedidos):new HashMap<>();
        this.rutasSolucionQueGeneraAlgoritmo = new ArrayList<>();

        // 1) Agrupar vuelos por almacen destino: Map<idAlmacenDestino, List<idVuelo>>
        Map<Long, List<Long>> tmpByDestino = this.vuelos.values().stream()
                .collect(Collectors.groupingBy(
                        VueloParaAlgoritmo::getIdAlmacenDestino,
                        Collectors.mapping(VueloParaAlgoritmo::getId, Collectors.toList())
                ));

        // 2) Agrupar vuelos por almacen origen: Map<idAlmacenOrigen, List<idVuelo>>
        Map<Long, List<Long>> tmpByOrigen = this.vuelos.values().stream()
                .collect(Collectors.groupingBy(
                        VueloParaAlgoritmo::getIdAlmacenOrigen,
                        Collectors.mapping(VueloParaAlgoritmo::getId, Collectors.toList())
                ));

        // 3) Agrupar pedidos por almacen destino: Map<idAlmacenDestino, List<idPedido>>
        Map<Long, List<Long>> tmpPedidosPorDestino = this.pedidos.values().stream()
                .collect(Collectors.groupingBy(
                        PedidoParaAlgoritmo::getIdAlmacenDestino,
                        Collectors.mapping(PedidoParaAlgoritmo::getId, Collectors.toList())
                ));

        // 4) Inicializar los HashMap finales garantizando una entrada para cada almacén conocido
        this.idsVuelosPorDestino = new HashMap<>();
        this.idsVuelosPorOrigen = new HashMap<>();
        this.idsPedidosPorDestino = new HashMap<>();

        // Primero, asegura que cada almacén tenga una lista (vacía si no hay vuelos/pedidos)
        for (Long idAlm : this.almacenes.keySet()) {
            this.idsVuelosPorDestino.put(idAlm, new ArrayList<>());
            this.idsVuelosPorOrigen.put(idAlm, new ArrayList<>());
            this.idsPedidosPorDestino.put(idAlm, new ArrayList<>());
        }

        // 5) Rellenar con los resultados de los groupings (si hay claves que no están en almacenes, también las añadimos)
        tmpByDestino.forEach((idAlm, vuelosList) ->
                this.idsVuelosPorDestino.merge(idAlm, new ArrayList<>(vuelosList), (oldList, newList) -> { oldList.addAll(newList); return oldList; })
        );

        tmpByOrigen.forEach((idAlm, vuelosList) ->
                this.idsVuelosPorOrigen.merge(idAlm, new ArrayList<>(vuelosList), (oldList, newList) -> { oldList.addAll(newList); return oldList; })
        );

        tmpPedidosPorDestino.forEach((idAlm, pedidosList) ->
                this.idsPedidosPorDestino.merge(idAlm, new ArrayList<>(pedidosList), (oldList, newList) -> { oldList.addAll(newList); return oldList; })
        );

        idsAlmacenesNoInfinitos = devolverIdsAlmacenesNoInfinitos();
        almacenesInfinitosOConStock = new HashSet<>(devolverAlmacenesInfinitosOConStockDisponible());
        idsAlmacenesInfinitosOConStock = almacenesInfinitosOConStock.stream()
                .map(AlmacenParaAlgoritmo::getId).collect(Collectors.toSet());
    }

    public boolean hayPedidosPendientesPorProgramar() {
        if (pedidos == null || pedidos.isEmpty()) return false;
        for (PedidoParaAlgoritmo p : pedidos.values()) {
            if (p == null) continue;
//            int total = p.getCantidadProductosPedidos() ;
//            int entregados = p.getCantidadProductosEntregados();
//            int programados = p.getCantidadProductosProgramados();
            int remaining = p.getCantidadRestanteDeEntregaYProgram();
            if (remaining > 0) return true;
        }
        return false;
    }

    public int contarPedidosPendientes() {
        if (pedidos == null || pedidos.isEmpty()) return 0;
        int c = 0;
        for (PedidoParaAlgoritmo p : pedidos.values()) {
            if (p == null) continue;
//            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
//            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
//            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = p.getCantidadRestanteDeEntregaYProgram();
            if (remaining > 0) c++;
        }
        return c;
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

            // capacidad del vuelo (usar cálculo actualizado)
            if (vuelo.obtenerCapacidadSinOcupar() < cantidadDelPedido) {
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
     * MÁS IMPORTANTE: no usar esta función en cualquier contexto fuera del algoritmo, ya que el algoritmo
     * usa como artificios el mutar estados como la capacidad ocupada de vuelos = reservados
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
            int remaining = pedido.getCantidadRestanteDeEntregaYProgram();
            if (remaining <= 0)
                loggingReport.appendReport("Pedido id=" + pedido.getId() + " está satisfecho (remaining=0) y se elimina de pendientes.");
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
//            loggingReport.appendReport("Vuelo recuperado con el idVuelo=" + idVuelo + ": " + vuelo);
//            System.out.println("Vuelo recuperado con el idVuelo=" + idVuelo + ": " + vuelo);
            boolean pudo = vuelo.ocuparCapacidad(cantidad); // método synchronized en VueloParaAlgoritmo
            if(loggingReport!=null)
                loggingReport.appendReport("anadirRutaSolucion: Ocupar cantidad "+cantidad+" en vuelo: "
                        +vuelo+" Pudo? "+pudo);
            if (!pudo) {
                // inconsistencia grave: la ruta fue validada pero ahora el vuelo no tiene espacio.
                // Lanzamos excepción para que el llamador decida rollback/handling.
                throw new IllegalStateException("Vuelo sin capacidad al añadir ruta (inconsistencia). idVuelo=" + idVuelo +
                    " cantidad a poner deseada=" + cantidad + " capacidadSinOcuparActual=" + vuelo.obtenerCapacidadSinOcupar());
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
                if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                    }
                }

                // 2) llegada: si este almacén es destino y la llegada ya ocurrió (instante >= fin)
                if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)/*vuelo.getIdAlmacenDestino() == idAlmacenSimulado*/) {
                    if (!instante.isBefore(fin)) { // instante >= fin
                        almacenSimuladoHastaInstante.ocuparCapacidad(cantProdsRuta);
                        //esto solo para efectos de mostrar el colapso expresamente
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

    /**
     * Elimina una ruta previamente añadida a la mesa y revierte sus efectos:
     * - desocupa capacidad en los vuelos de la ruta
     * - decrementa la cantidadProgramada del pedido asociado
     * - quita la ruta de rutasSolucionQueGeneraAlgoritmo
     *
     * Requisitos/Asunciones:
     * - La ruta r fue añadida antes mediante anadirRutaSolucion(...) (o su efecto equivalente).
     * - Si ocurre una inconsistencia durante la desocupación, se intenta rollback de lo ya desocupado
     *   y se lanza IllegalStateException para que el llamador lo maneje.
     */
    public synchronized void eliminarRutaSolucion(RutaProgramadaParaAlgoritmo r) {
        if (r == null) return;

        // Si la ruta no está presente, nada que hacer.
        if (!this.rutasSolucionQueGeneraAlgoritmo.contains(r)) {
            // opcional: logging
            if (this.loggingReport != null) {
                this.loggingReport.appendReport("Intentaron eliminar una ruta que no existe en la mesa. Ignorando.");
            }
            return;
        }

        final int cantidad = r.getCantidadTotalOParcial();
        final long idPedido = r.getIdPedidoAsociado();

        // 1) Intentar desocupar capacidad en todos los vuelos de la ruta.
        //    Si falla en alguno, rehacer (rollback) las desocupaciones ya hechas.
        List<Long> vuelosDesocupadosConExito = new ArrayList<>();
        try {
            for (Long idVuelo : r.getIdsVuelosEnOrden()) {
                VueloParaAlgoritmo vuelo = this.vuelos.get(idVuelo);
                if (vuelo == null) {
                    // inconsistencia grave: la ruta contenía un vuelo inexistente
                    throw new IllegalStateException("Vuelo inexistente al eliminar ruta: idVuelo=" + idVuelo);
                }
                boolean desocupado = vuelo.desocuparCapacidad(cantidad); // synchronized en VueloParaAlgoritmo
                if (!desocupado) {
                    // no se pudo desocupar (inconsistencia: quizás otra hebra ya liberó) -> rollback
                    throw new IllegalStateException("No se pudo desocupar capacidad en vuelo al eliminar ruta. idVuelo="
                            + idVuelo + " cantidad=" + cantidad + " capacidadOcupadaActual=" + vuelo.getCapacidadOcupadaProductos());
                }
                vuelosDesocupadosConExito.add(idVuelo);
            }

            // 2) Actualizar el pedido: restar cantidad programada
            PedidoParaAlgoritmo pedido = this.pedidos.get(idPedido);
            if (pedido == null) {
                // inconsistencia: pedido no existe (raro si se añadió antes)
                throw new IllegalStateException("Pedido inexistente al eliminar ruta: idPedido=" + idPedido);
            }
            // Usar método apropiado de tu modelo para restar cantidad programada.
            // Asumo que existe pedido.restarCantidadProgramada(int), si no, usar setter.
            pedido.restarCantidadProgramada(cantidad);

            // 3) Finalmente eliminar la ruta del listado de la mesa
            boolean eliminado = this.rutasSolucionQueGeneraAlgoritmo.remove(r);
            if (!eliminado) {
                // rara inconsistencia: no se pudo eliminar la ruta de la lista; intentar rollback
                throw new IllegalStateException("No se pudo eliminar la ruta de la lista interna (inconsistencia).");
            }

            // 4) (Opcional) actualizar índices auxiliares si los mantienes, p.ej. idsRutasPorAlmacen
            //    Recorre vuelos y, si tienes mapas con listas de rutas por almacén, remover la referencia.
            //    Ejemplo no implementado por falta de identificador de ruta; implementa según tu índice.

            if (this.loggingReport != null) {
                this.loggingReport.appendReport("Ruta eliminada correctamente. pedidoId=" + idPedido + " cantidad=" + cantidad);
            }

        } catch (RuntimeException ex) {
            // Si hubo fallo parcial: intentar rollback de las desocupaciones ya realizadas
            // (re-ocuparamos en los vuelos que sí fuimos capaces de desocupar antes del fallo)
            for (Long idVueloRollback : vuelosDesocupadosConExito) {
                try {
                    VueloParaAlgoritmo vueloRollback = this.vuelos.get(idVueloRollback);
                    if (vueloRollback != null) {
                        boolean reocupo = vueloRollback.ocuparCapacidad(cantidad); // volver a ocupar
                        if (!reocupo && this.loggingReport != null) {
                            this.loggingReport.appendReport("ROLLBACK: No se pudo re-ocupar vuelo id=" + idVueloRollback +
                                    " tras fallo al eliminar ruta. Revisar consistencia.");
                        }
                    }
                } catch (Exception inner) {
                    // ignorar errores en rollback pero registrarlos
                    if (this.loggingReport != null) {
                        this.loggingReport.appendReport("ROLLBACK ERROR en vuelo id=" + idVueloRollback + ": " + inner.getMessage());
                    }
                }
            }

            // No intentamos revertir el pedido.programados porque aún no lo habíamos restado (se habría hecho después de desocupar).
            // Reagregar la ruta a la lista si fue removida explícitamente antes del fallo (no ocurre en la secuencia actual).

            // Registrar y relanzar para que el llamador controle el caso crítico
            if (this.loggingReport != null) {
                this.loggingReport.appendReport("Error al eliminar ruta; rollback parcial efectuado. Error: " + ex.getMessage());
            }
            throw ex;
        }
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

    /**
     * Elimina de la lista 'pedidos' los pedidos que estén completamente satisfechos (remaining == 0).
     * Retorna el número de pedidos removidos.
     */
    public int eliminarPedidosCompletamenteSatisfechos() {
        if (pedidos == null || pedidos.isEmpty()) return 0;
        int removed = 0;
        Iterator<PedidoParaAlgoritmo> it = pedidos.values().iterator();
        while (it.hasNext()) {
            PedidoParaAlgoritmo p = it.next();
            if (p == null) {
                it.remove();
                removed++;
                continue;
            }
//            int total = p.getCantidadProductosPedidos() == null ? 0 : p.getCantidadProductosPedidos();
//            int entregados = p.getCantidadProductosEntregados() == null ? 0 : p.getCantidadProductosEntregados();
//            int programados = p.getCantidadProductosProgramados() == null ? 0 : p.getCantidadProductosProgramados();
            int remaining = p.getCantidadRestanteDeEntregaYProgram();
            if (remaining <= 0) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**Si no existe o aún no está satifecho, retorna false; de otro modo true*/
    public boolean eliminarPedidoYaSatisfecho(Long idPedido) {
        PedidoParaAlgoritmo p = pedidos.get(idPedido);
        if (p == null ) {
            pedidos.remove(idPedido);
            return false; // safarlo?
        }
        int remaining = p.getCantidadRestanteDeEntregaYProgram(); // asegurarte de esta lógica!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if (remaining <= 0) {
            pedidos.remove(idPedido);
            return true;
        }

        return false;
    }


    public Set<Long> devolverIdsAlmacenesNoInfinitos(){
        return almacenes.values().stream()
                .filter(a -> !a.isEsInfinito())
                .map(AlmacenParaAlgoritmo::getId)
                .collect(Collectors.toSet());
    }

    public List<AlmacenParaAlgoritmo> devolverAlmacenesInfinitosOConStockDisponible(){
        return almacenes.values().stream()
                .filter(a -> a.isEsInfinito()
                        || a.getCapacidadOcupada()  > 0)
                .toList();
    }


        /**
     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o no vacíos"
     * hacia destinos que NO son infinitos. NO INCLUYE EL PEDIDO NI CANTIDAD ASOCIADOS.
     *  ¿Debería tener una estructura propia en lugar de la misma ruta de algoritmo?
     * Filtra vuelos que no tengan capacidad disponible y asegura encadenamiento temporal
     * (siguiente.inicio >= anterior.fin).
     */
    public List<RutaProgramadaParaAlgoritmo> generarTodasRutasPosiblesATodosDestinos() {
        loggingReport.appendReport("Generando rutas candidatas");

        // Map de vuelos salientes por almacen origen (idAlmacenOrigen -> lista vuelos)
        Map<Long, List<VueloParaAlgoritmo>> vuelosPorAlmacenOrigenId = new HashMap<>();
        for (VueloParaAlgoritmo v : vuelos.values()) {
            vuelosPorAlmacenOrigenId.computeIfAbsent(v.getIdAlmacenOrigen(), k -> new ArrayList<>()).add(v);
        }
        // Identificar destinos: almacenes que NO son infinitos
        Set<Long> idsDestinos = devolverIdsAlmacenesNoInfinitos();

        // Orígenes candidatos: infinitos o con stock disponible (> reserved)
        List<AlmacenParaAlgoritmo> origenes = devolverAlmacenesInfinitosOConStockDisponible();

        List<RutaProgramadaParaAlgoritmo> resultado = new ArrayList<>();

        // Para evitar rutas duplicadas, guardamos un hash de secuencia de vuelos
        Set<String> rutasVistas = new HashSet<>();

        for (Long destId : idsDestinos) {
            int rutasEncontradasParaDestino = 0;
            for (AlmacenParaAlgoritmo origen : origenes) {
                if (rutasEncontradasParaDestino >= MAX_RUTAS_POR_DESTINO) break;
                // BFS sobre caminos de vuelos desde origen.id hasta destId
                Queue<List<VueloParaAlgoritmo>> q = new ArrayDeque<>();
                // Inicializar con vuelos salientes del origen que tengan capacidad disponible y estado válido
                List<VueloParaAlgoritmo> iniciales = vuelosPorAlmacenOrigenId.getOrDefault(origen.getId(), Collections.emptyList());
                for (VueloParaAlgoritmo v : iniciales) {
                    if (!v.tieneCapacidadDisponible()) continue;
                    if (v.yaPartio()) continue;
                    List<VueloParaAlgoritmo> path = new ArrayList<>();
                    path.add(v);
                    q.add(path);
                }

                int rutasPorOrigen = 0;
                while (!q.isEmpty() && rutasPorOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasEncontradasParaDestino < MAX_RUTAS_POR_DESTINO) {
                    List<VueloParaAlgoritmo> path = q.poll();
                    if (path == null) continue;

                    // Chequeamos si el último vuelo llega al destino buscado
                    VueloParaAlgoritmo last = path.get(path.size() - 1);
                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
                        // validar ruta: todas las legs tienen capacidad y encadenan tiempos (ya garantizado al expandir)
                        String signature = path.stream().map(vf -> String.valueOf(vf.getId())).collect(Collectors.joining("-"));
                        if (!rutasVistas.contains(signature)) {
                            resultado.add(new RutaProgramadaParaAlgoritmo(path)); // Un List a un LinkedList=?
                            rutasVistas.add(signature);
                            rutasPorOrigen++;
                            rutasEncontradasParaDestino++;
                        }
                        // no expandimos más esta ruta (ya llegó)
                        continue;
                    }

                    // Si no llegó al destino, expandir si no excede MAX_LEGS
                    if (path.size() >= MAX_LEGS) continue;

                    // Expandir: vuelos salientes del almacen destino del último tramo
                    List<VueloParaAlgoritmo> siguientes = vuelosPorAlmacenOrigenId.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
                    for (VueloParaAlgoritmo next : siguientes) {
                        if (!next.tieneCapacidadDisponible()) continue;
                        if (next.yaPartio()) continue;

                        // Chequeo de encadenamiento temporal: next.inicio >= last.fin (permitimos igual)
                        if (next.getInicio() != null && last.getFin() != null && next.getInicio().isBefore(last.getFin())) {
                            continue;
                        }

                        // Evitar ciclos por almacen o por vuelo repetido en path
                        boolean ciclo = false;
                        for (VueloParaAlgoritmo used : path) {
                            if (Objects.equals(used.getId(), next.getId())) {
                                ciclo = true;
                                break;
                            }
                            if (Objects.equals(used.getIdAlmacenOrigen(), next.getIdAlmacenDestino())
                                    && Objects.equals(used.getIdAlmacenDestino(), next.getIdAlmacenOrigen())) {
                                // conservador: evitar volver al mismo par invertido
                                ciclo = true;
                                break;
                            }
                        }
                        if (ciclo) continue;

                        // Nuevo path candidato
                        List<VueloParaAlgoritmo> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        q.add(newPath);
                    }
                } // end BFS for this origin
            } // end origins loop
        } // end destinations loop
        loggingReport.appendReport("Rutas candidatas finalizadas. Total: " + resultado.size());
        loggingReport.appendReport("Rutas candidatas: ");
        for ( RutaProgramadaParaAlgoritmo ruta : resultado) {
            loggingReport.appendReport("Rutas:");
            for(Long idV : ruta.getIdsVuelosEnOrden()) {
                loggingReport.appendReport( "   Vuelo:"+ vuelos.get(idV));
            }
        }
        return resultado;

    }

    /** REDUNDANDO EL CÓDIGO PASADO DE TODAS RUTAS A TODOS DESTINOS
     * Genera rutas candidatas (secuencias de vuelos) desde orígenes "infinitos o con stock"
     * hacia destinos que NO son infinitos y que, además, tienen pedidos pendientes.
     *
     * Filtra vuelos que no tengan capacidad disponible o que ya partieron, asegura encadenamiento temporal
     * (next.inicio >= prev.fin) y evita ciclos por vuelo/almacén. Devuelve rutas representadas por
     * LinkedList<Long> de ids de vuelo (no por referencias a objetos mutables).
     */
    public List<RutaProgramadaParaAlgoritmo> generarRutasParaPedidosPendientes() {
        loggingReport.appendReport("Generando rutas candidatas (inicio)");

        // Snapshot local para consistencia durante la generación
        Map<Long, VueloParaAlgoritmo> vuelosSnapshot = new HashMap<>(this.vuelos);
        Map<Long, AlmacenParaAlgoritmo> almacenesSnapshot = new HashMap<>(this.almacenes);
        Map<Long, PedidoParaAlgoritmo> pedidosSnapshot = new HashMap<>(this.pedidos);

        // 1) destinos: sólo almacenes no infinitos que tengan pedidos pendientes
        Set<Long> idsDestinos = pedidosSnapshot.values().stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getCantidadRestanteDeEntregaYProgram() > 0)
                .map(PedidoParaAlgoritmo::getIdAlmacenDestino)
                .filter(Objects::nonNull)
                .filter(id -> {
                    AlmacenParaAlgoritmo a = almacenesSnapshot.get(id);
                    return a != null && !a.isEsInfinito();
                })
                .collect(Collectors.toSet());

        if (idsDestinos.isEmpty()) {
            loggingReport.appendReport("No hay destinos no infinitos con pedidos pendientes -> no genero rutas.");
            return Collections.emptyList();
        }

        // 2) orígenes candidatos
        List<AlmacenParaAlgoritmo> origenes = devolverAlmacenesInfinitosOConStockDisponible(); // usa mesa (ya definida)

        // 3) index vuelos por origen (preordenados por inicio para eficiencia)
        Map<Long, List<VueloParaAlgoritmo>> vuelosPorAlmacenOrigenId = vuelosSnapshot.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        VueloParaAlgoritmo::getIdAlmacenOrigen,
                        Collectors.mapping(Function.identity(),
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    list.sort(Comparator.comparing(VueloParaAlgoritmo::getInicio, Comparator.nullsLast(Comparator.naturalOrder())));
                                    return list;
                                }))
                ));

        List<RutaProgramadaParaAlgoritmo> resultado = new ArrayList<>();
        Set<String> rutasVistas = new HashSet<>(); // para unicidad por signature "id1-id2-..."

        for (Long destId : idsDestinos) {
            int rutasEncontradasParaDestino = 0;
            for (AlmacenParaAlgoritmo origen : origenes) {
                if (rutasEncontradasParaDestino >= MAX_RUTAS_POR_DESTINO) break;

                List<VueloParaAlgoritmo> iniciales = vuelosPorAlmacenOrigenId.getOrDefault(origen.getId(), Collections.emptyList());
                Queue<List<VueloParaAlgoritmo>> q = new ArrayDeque<>();

                // seed: vuelos iniciales válidos
                for (VueloParaAlgoritmo v : iniciales) {
                    if (v == null) continue;
                    if (!v.tieneCapacidadDisponible()) continue;
                    if (v.yaPartio()) continue;
                    // opcional: ignora vuelos con destino que sea igual al origen (no tiene sentido)
                    List<VueloParaAlgoritmo> p = new ArrayList<>();
                    p.add(v);
                    q.add(p);
                }

                int rutasPorOrigen = 0;
                while (!q.isEmpty() && rutasPorOrigen < MAX_RUTAS_POR_ORIGEN
                        && rutasEncontradasParaDestino < MAX_RUTAS_POR_DESTINO) {

                    List<VueloParaAlgoritmo> path = q.poll();
                    if (path == null || path.isEmpty()) continue;

                    VueloParaAlgoritmo last = path.get(path.size() - 1);

                    // check llegada al destino
                    if (Objects.equals(last.getIdAlmacenDestino(), destId)) {
                        String signature = path.stream().map(vf -> String.valueOf(vf.getId())).collect(Collectors.joining("-"));
                        if (!rutasVistas.contains(signature)) {
                            // convertir a RutaProgramadaParaAlgoritmo usando ids
                            LinkedList<Long> ids = path.stream().map(VueloParaAlgoritmo::getId).collect(Collectors.toCollection(LinkedList::new));
                            RutaProgramadaParaAlgoritmo ruta = new RutaProgramadaParaAlgoritmo(ids, /*idPedidoAsociado*/ -1L, /*cantidad*/ 0);
                            resultado.add(ruta);
                            rutasVistas.add(signature);
                            rutasPorOrigen++;
                            rutasEncontradasParaDestino++;
                        }
                        // no expandir más este path
                        continue;
                    }

                    // límite de escalas/tramos
                    if (path.size() >= MAX_LEGS) continue;

                    // expandir
                    List<VueloParaAlgoritmo> siguientes = vuelosPorAlmacenOrigenId.getOrDefault(last.getIdAlmacenDestino(), Collections.emptyList());
                    for (VueloParaAlgoritmo next : siguientes) {
                        if (next == null) continue;
                        if (!next.tieneCapacidadDisponible()) continue;
                        if (next.yaPartio()) continue;

                        // temporal: next.inicio >= last.fin
                        if (next.getInicio() != null && last.getFin() != null && next.getInicio().isBefore(last.getFin())) {
                            continue;
                        }

                        // evitar repetir vuelo
                        boolean repetido = path.stream().anyMatch(u -> Objects.equals(u.getId(), next.getId()));
                        if (repetido) continue;

                        // evitar ciclos por volver a mismo almacen varias veces (conservador)
                        boolean vuelveMismoAlmacen = path.stream().anyMatch(u -> Objects.equals(u.getIdAlmacenDestino(), next.getIdAlmacenDestino()));
                        if (vuelveMismoAlmacen) continue;

                        // crear nuevo candidato y encolar
                        List<VueloParaAlgoritmo> newPath = new ArrayList<>(path);
                        newPath.add(next);
                        q.add(newPath);
                    }
                } // end BFS per origin
            } // end origins
        } // end destinos

        loggingReport.appendReport("Rutas candidatas finalizadas. Total: " + resultado.size());
        return resultado;
    }





    /**
     * Comprueba conservadoramente si es factible llevar al menos 1 unidad del pedido
     * identificado por idPedido a través de la ruta propuesta (rutaProspecto).
     *
     * No muta la mesa; usa el estado actual (vuelos, almacenes, pedidos y rutas ya añadidas)
     * para simular la factibilidad.
     */ // SE PUEDE HACER MÁS GENERAL, POR EJM: CAPACIDAD MÁXIMA POSIBLE A LLEVAR EN RUTA
    public boolean esFactibleLlevarPedidoEnRuta(Long idPedido, RutaProgramadaParaAlgoritmo rutaProspecto) {
        //calcularCantidadPosibleALlevarEnRuta(RutaProgramadaParaAlgoritmo rutaProspecto)
        // 0) sanity
        if (idPedido == null || rutaProspecto == null || rutaProspecto.getIdsVuelosEnOrden() == null
                || rutaProspecto.getIdsVuelosEnOrden().isEmpty()) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: parámetros inválidos.");
            return false;
        }

        // 1) pedido y remaining
        PedidoParaAlgoritmo pedido = this.pedidos.get(idPedido);
        if (pedido == null) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: pedido no existe id=" + idPedido);
            return false;
        }
//        int total = Math.max(0, pedido.getCantidadProductosPedidos());
//        int entregados = Math.max(0, pedido.getCantidadProductosEntregados());
//        int programados = Math.max(0, pedido.getCantidadProductosProgramados());
//        int remaining = Math.max(0, total - entregados - programados);
        if (pedido.getCantidadRestanteDeEntregaYProgram() <= 0) { // ya se validó que es pendiente.
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: pedido id=" + idPedido + " sin remanente.");
            return false;
        }

        // 2) obtener objetos VueloParaAlgoritmo en orden
        List<Long> idsVuelos = rutaProspecto.getIdsVuelosEnOrden();
        List<VueloParaAlgoritmo> vuelosRuta = idsVuelos.stream()
                .map(vId -> this.vuelos.get(vId))
                .filter(Objects::nonNull)
                .toList();

        if (vuelosRuta.size() != idsVuelos.size()) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: falta info de algún vuelo en la ruta prospecto.");
            return false;
        }

        // 3) La ruta debe terminar en el almacen destino del pedido
        VueloParaAlgoritmo ultimoVuelo = vuelosRuta.get(vuelosRuta.size() - 1);
        if (!Objects.equals(ultimoVuelo.getIdAlmacenDestino(), pedido.getIdAlmacenDestino())) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: ruta no termina en el almacén destino del pedido.");
            return false;
        }

        // 4) comprobar conectividad, orden temporal, capacidad vuelo y capacidad almacenes (incluye escalas)
        VueloParaAlgoritmo prev = null;
        // cache para evitar recomputos de obtenerAlmacenEnInstante por (almacenId|instante)
        Map<String, AlmacenParaAlgoritmo> cacheAlmacenesInstante = new HashMap<>();

        // calculamos la mínima capacidad disponible entre todos los vuelos (conservador)
        int minimaCapacidadVuelos = Integer.MAX_VALUE;

        for (VueloParaAlgoritmo vuelo : vuelosRuta) {
            // conectividad entre tramos
            if (prev != null) {
                if (!Objects.equals(prev.getIdAlmacenDestino(), vuelo.getIdAlmacenOrigen())) {
                    if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: ruta desconectada entre vuelos.");
                    return false;
                }
                // orden temporal
                if (vuelo.getInicio().isBefore(prev.getFin())) {
                    if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: solapamiento temporal entre tramos.");
                    return false;
                }
            }

            // capacidad del vuelo (usar método que recalcula para estado actual)
            int capVuelo = vuelo.obtenerCapacidadSinOcupar();
            minimaCapacidadVuelos = Math.min(minimaCapacidadVuelos, capVuelo);
            if (capVuelo <= 0) {
                if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: vuelo id=" + vuelo.getId() + " sin capacidad.");
                return false;
            }

            // capacidad en almacen origen al inicio del vuelo
            AlmacenParaAlgoritmo almOrigen = this.almacenes.get(vuelo.getIdAlmacenOrigen());
            if (almOrigen == null) {
                if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: almacen origen no encontrado id=" + vuelo.getIdAlmacenOrigen());
                return false;
            }
            String keyOrigen = almOrigen.getId() + "|" + vuelo.getInicio().toString();
            AlmacenParaAlgoritmo simulOrigen = cacheAlmacenesInstante.computeIfAbsent(keyOrigen,
                    k -> obtenerAlmacenEnInstante(almOrigen, vuelo.getInicio()));
            if (simulOrigen.getCapacidadSinOcupar() <= 0) {
                if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: almacen origen id=" + almOrigen.getId() + " sin stock en instante salida.");
                return false;
            }

            // capacidad en almacen destino al fin del vuelo (las escalas depositan)
            AlmacenParaAlgoritmo almDestino = this.almacenes.get(vuelo.getIdAlmacenDestino());
            if (almDestino == null) {
                if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: almacen destino no encontrado id=" + vuelo.getIdAlmacenDestino());
                return false;
            }
            String keyDestino = almDestino.getId() + "|" + vuelo.getFin().toString();
            AlmacenParaAlgoritmo simulDestino = cacheAlmacenesInstante.computeIfAbsent(keyDestino,
                    k -> obtenerAlmacenEnInstante(almDestino, vuelo.getFin()));
            if (simulDestino.getCapacidadSinOcupar() <= 0) {
                if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: almacen destino id=" + almDestino.getId() + " sin espacio a la llegada.");
                return false;
            }

            prev = vuelo;
        }

        // 5) restricción temporal final: llegada final + pickup (2h) <= deadline (si existe)
        Instant llegadaFinal = ultimoVuelo.getFin();
        if (llegadaFinal == null) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: último vuelo sin instante de llegada.");
            return false;
        }
        Instant pickup = llegadaFinal.plusSeconds(this.SEGUNDOS_PARA_RECOGER_PEDIDO);
        Instant deadline = pedido.getInstanteMaximoParaEntregar();
        if (deadline != null && pickup.isAfter(deadline)) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: pickup " + pickup + " posterior al deadline " + deadline);
            return false;
        }

        // 6) cálculo conservador: la cantidad asignable mínima
        int asignable = Math.min(pedido.getCantidadRestanteDeEntregaYProgram(), Math.max(0, minimaCapacidadVuelos));
        if (asignable <= 0) {
            if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: ninguna unidad asignable (remaining=" + pedido.getCantidadRestanteDeEntregaYProgram() + ", minCapVuelos=" + minimaCapacidadVuelos + ").");
            return false;
        }

        // pasa todos los chequeos conservadores: al menos 1 unidad asignable
        if (this.loggingReport != null) this.loggingReport.appendReport("esFactibleLlevarPedidoEnRuta: pedido id=" + idPedido + " factible en la ruta prospecto. asignable>=1");
        return true;
    }

    /**
     * Calcula de forma conservadora la máxima cantidad asignable del pedido en la ruta.
     * 0 significa no factible.
     */
    public int capacidadMaxAsignableEnRuta(Long idPedido, RutaProgramadaParaAlgoritmo rutaProspecto) {
        // Validaciones iniciales...
        PedidoParaAlgoritmo pedido = this.pedidos.get(idPedido);
        if (pedido == null || pedido.getCantidadRestanteDeEntregaYProgram() <= 0) return 0;

        List<Long> idsVuelos = rutaProspecto.getIdsVuelosEnOrden();
        LinkedList<VueloParaAlgoritmo> vuelosRuta = new LinkedList<>(idsVuelos.stream()
                .map(vId -> this.vuelos.get(vId))
                .filter(Objects::nonNull)
                .toList());
        if (vuelosRuta.size() != idsVuelos.size()) return 0;

        VueloParaAlgoritmo ultimoVuelo = vuelosRuta.get(vuelosRuta.size() - 1);
        if (!Objects.equals(ultimoVuelo.getIdAlmacenDestino(), pedido.getIdAlmacenDestino())) return 0;

        int minimaCapacidadVuelos = Integer.MAX_VALUE;
        Map<String, AlmacenParaAlgoritmo> cacheAlmacenesInstante = new HashMap<>();

        VueloParaAlgoritmo prev = null;
        AlmacenParaAlgoritmo ultimoAlmacen = null;
        for (VueloParaAlgoritmo vuelo : vuelosRuta) {
            if (prev != null) {
                if (!Objects.equals(prev.getIdAlmacenDestino(), vuelo.getIdAlmacenOrigen())) return 0;
                if (vuelo.getInicio().isBefore(prev.getFin())) return 0;
            }
            int capVuelo = vuelo.obtenerCapacidadSinOcupar();
            minimaCapacidadVuelos = Math.min(minimaCapacidadVuelos, capVuelo);
            if (capVuelo <= 0) return 0;

            AlmacenParaAlgoritmo almOrigen = this.almacenes.get(vuelo.getIdAlmacenOrigen());
            if (almOrigen == null) return 0;
            String keyOrigen = almOrigen.getId() + "|" + vuelo.getInicio().toString();
            AlmacenParaAlgoritmo simulOrigen = cacheAlmacenesInstante.computeIfAbsent(keyOrigen,
                    k -> obtenerAlmacenEnInstante(almOrigen, vuelo.getInicio()));
            int dispOrigen = simulOrigen.getCapacidadSinOcupar();
            if (dispOrigen <= 0) return 0;

            AlmacenParaAlgoritmo almDestino = this.almacenes.get(vuelo.getIdAlmacenDestino());
            if (almDestino == null) return 0;
            String keyDestino = almDestino.getId() + "|" + vuelo.getFin().toString();
            AlmacenParaAlgoritmo simulDestino = cacheAlmacenesInstante.computeIfAbsent(keyDestino,
                    k -> obtenerAlmacenEnInstante(almDestino, vuelo.getFin()));
            if (simulDestino.getCapacidadSinOcupar() <= 0) return 0;
            int dispDestino = simulDestino.getCapacidadSinOcupar();
            if (dispDestino <= 0) return 0;
            loggingReport.appendReport("\nSimulación del almOrigen en el instante " + Formateador.utcFormatter(vuelo.getInicio()) + ": " + simulOrigen +
                    "\nSimulación del almDestino en el instante " + Formateador.utcFormatter(vuelo.getFin()) + ": " + simulDestino);
            // ahora veremos qué sucede con este mismo almacén de destino de aquí a dos horas, si es el
            // último
            if (vuelo == vuelosRuta.getLast()) {
                loggingReport.appendReport("Encontré al último vuelo de ruta: " + vuelo);
                ultimoAlmacen = almDestino;
            }


            prev = vuelo;
        }

        if (minimaCapacidadVuelos == Integer.MAX_VALUE) minimaCapacidadVuelos = 0;
        loggingReport.appendReport("minimaCapacidadVuelos " + minimaCapacidadVuelos);
        int asignable = Math.min(pedido.getCantidadRestanteDeEntregaYProgram(), minimaCapacidadVuelos);
        // además: podrías intersectar con la capacidad mínima de todos los almacenes clave:
        int minDispAlmacenes = cacheAlmacenesInstante.values().stream()
                .mapToInt(AlmacenParaAlgoritmo::getCapacidadSinOcupar)
                .min().orElse(Integer.MAX_VALUE);
        if (minDispAlmacenes != Integer.MAX_VALUE) {
            asignable = Math.min(asignable, minDispAlmacenes);
        }
        loggingReport.appendReport("minDispAlmacenes "+minDispAlmacenes);
        asignable= Math.max(0, asignable);
        // A PARTIR DE AQUÍ, ES PARA EL CASO EXTRAÑO EN QUE UNA RUTA PROGRAMADA NO ESTÉ CONSIDERANDO
        // QUE UNA RUTA POSTERIOR YA PROGRAMADA ATERRICE EN EL ALMACÉN DONDE ESTÁ DEJANDO LOS PRODUCTOS
        // VERIFICAR SI LÓGICA ES CORRECTA!!
        if (asignable > 0) {
            int maxDiferenciaColapso=0;
            for (VueloParaAlgoritmo vuelo : vuelosRuta) {
                Map.Entry< AlmacenParaAlgoritmo,Integer> almacenPosiblementeColapsado;
                if (vuelo != vuelosRuta.getLast()) {
                    VueloParaAlgoritmo next = vuelosRuta.get( vuelosRuta.indexOf(vuelo)+1);
                    AlmacenParaAlgoritmo almDestino = almacenes.get( vuelo.getIdAlmacenDestino() );
                    almDestino.ocuparCapacidad(asignable);
                    almacenPosiblementeColapsado=
                            simularAlmacenHastaInstanteIlegalmente(
                                    almDestino, vuelo.getFin()
                                            .plus(Duration.between( //duration implements TemporalAmount
                                                    next.getInicio(), vuelo.getFin()
                                            ))); //lo que esperará, debería ser 1h
                }else{
                    AlmacenParaAlgoritmo almFinal = almacenes.get( vuelo.getIdAlmacenDestino() );
                    almFinal.ocuparCapacidad(asignable);
                    almacenPosiblementeColapsado=
                            simularAlmacenHastaInstanteIlegalmente(
                                    almFinal, vuelo.getFin()
                                            .plus(2, ChronoUnit.HOURS));
                }
                loggingReport.appendReport(
                        "Simulación del almacén destino hasta siguiente inicio: "+almacenPosiblementeColapsado
                );
                int diferenciaQueHizoColapso =almacenPosiblementeColapsado.getValue(); /*almacenPosiblementeColapsado.getKey().getCapacidadOcupada()
                        -almacenPosiblementeColapsado.getKey().getCapacidadMaxima();*/
                if (diferenciaQueHizoColapso>0) {//colapsado
                    loggingReport.appendReport("El almacén colapsaría con una diferencia de: "
                            + diferenciaQueHizoColapso);
//                    asignable -= diferenciaQueHizoColapso;
                    maxDiferenciaColapso=Math.max(maxDiferenciaColapso,diferenciaQueHizoColapso);
                }
            }
            if(maxDiferenciaColapso>0)
                loggingReport.appendReport("maxDiferenciaColapso "+maxDiferenciaColapso);
            asignable=asignable - maxDiferenciaColapso;
        }
        loggingReport.appendReport("Asignable final: "+asignable);
        return Math.max(0, asignable);
    }
    //        if(ultimoAlmacen!=null && asignable>0) {
//            ultimoAlmacen.ocuparCapacidad(asignable);
//            AlmacenParaAlgoritmo almacenPosiblementeColapsado=
//                    simularAlmacenHastaInstanteIlegalmente(
//                            ultimoAlmacen, ultimoVuelo.getFin().plus(2, ChronoUnit.HOURS));
//            int diferenciaQueHizoColapso = almacenPosiblementeColapsado.getCapacidadOcupada()
//                    -almacenPosiblementeColapsado.getCapacidadMaxima();
//            if (diferenciaQueHizoColapso>0) {//colapsado
//                loggingReport.appendReport("El almacén colapsaría con una diferencia de: "
//                        + diferenciaQueHizoColapso);
//                asignable -= diferenciaQueHizoColapso;
//            }
//        }

    public Map.Entry<AlmacenParaAlgoritmo,Integer> simularAlmacenHastaInstanteIlegalmente(AlmacenParaAlgoritmo alm, Instant instante){
        AlmacenParaAlgoritmo almacenSimuladoHastaInstante = alm.clone();
        long idAlmacenSimulado = alm.getId();
        int maxDiferenciaColapso=0;
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
                if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.desocuparCapacidadIlegalmente(cantProdsRuta);
                    }
                }

                // 2) llegada: si este almacén es destino y la llegada ya ocurrió (instante >= fin)
                if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)/*vuelo.getIdAlmacenDestino() == idAlmacenSimulado*/) {
                    if (!instante.isBefore(fin)) { // instante >= fin
                        almacenSimuladoHastaInstante.ocuparCapacidadIlegalmente(cantProdsRuta);
                        //esto solo para efectos de mostrar el colapso expresamente
                    }
                    int dif = almacenSimuladoHastaInstante.getCapacidadOcupada()-almacenSimuladoHastaInstante.getCapacidadMaxima();
                    if(dif>0){
                        maxDiferenciaColapso =Math.max(maxDiferenciaColapso,dif);
                    }
                    // 3) si es el último vuelo de la ruta, aplicar pickup (liberación tras ventana)
                    if (i == vuelitos.size() - 1) {
                        Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);
                        if (!instante.isBefore(instantePickup)) { // instante >= fin + ventana
                            almacenSimuladoHastaInstante.desocuparCapacidadIlegalmente(cantProdsRuta);
                        }
                    }
                }
            }
            if(loggingReport!=null)
                if((almacenSimuladoHastaInstante.getCapacidadOcupada()-
                        almacenSimuladoHastaInstante.getCapacidadMaxima())>0)
                loggingReport.appendReport(
                        "simularAlmacenHastaInstanteYDevolverMaxCantidadColapsada: Máximo de diferencia colapsada: "+
                        maxDiferenciaColapso);
        }
        return Map.entry(almacenSimuladoHastaInstante,maxDiferenciaColapso);
    }

    public List<PedidoParaAlgoritmo> removerPedidosSatisfechosOIrrelevantesParaRuta(
//            List<PedidoParaAlgoritmo> pedidosLocal,
            RutaProgramadaParaAlgoritmo ruta
            ) {

        if (pedidos == null || pedidos.isEmpty()) return Collections.emptyList();

//        List<PedidoParaAlgoritmo> nuevaLista = new ArrayList<>();
        for (PedidoParaAlgoritmo p : pedidos.values()) {
            if (p == null) continue;
            int remaining = p.getCantidadRestanteDeEntregaYProgram();

            // eliminar si ya satisfecho
            if (remaining <= 0) {
                loggingReport.appendReport("remover: pedido id=" + p.getId() + " removido (satisfecho).");
                continue;
            }

            // probar factibilidad local con la ruta actual (si falla, lo consideramos irrelevante para esta ruta)
            boolean factible = esFactibleLlevarPedidoEnRuta(p.getId(), ruta);
            if (!factible) {
                loggingReport.appendReport("remover: pedido id=" + p.getId() + " no factible para la ruta actual -> se remueve del pool local");
                continue;
            }
            // si pasó las pruebas, lo mantenemos en la lista local
//            nuevaLista.add(p);
        }
        return pedidos.values().stream().toList();
    }

    public Integer  obtenerCapacidadMaxParaTodosVuelosEnRuta(RutaProgramadaParaAlgoritmo rutaSeleccionada){
        if (rutaSeleccionada == null || rutaSeleccionada.getIdsVuelosEnOrden() == null
                || rutaSeleccionada.getIdsVuelosEnOrden().isEmpty()) return 0;
        return rutaSeleccionada.getIdsVuelosEnOrden().stream()
                .mapToInt(vId -> {
                    VueloParaAlgoritmo v = vuelos.get(vId);
                    if(loggingReport!=null) loggingReport.appendReport(
                            "obtenerCapacidadMaxParaTodosVuelosEnRuta: Evaluando vuelo de ruta(480?): "+v);
                    return v == null ? 0 : v.obtenerCapacidadSinOcupar();
                })
                .min()
                .orElse(0);
    }

    public Integer  obtenerCapacidadMaxParaTodosVuelosYAlmacenesEnRuta(RutaProgramadaParaAlgoritmo rutaSeleccionada){
        if (rutaSeleccionada == null || rutaSeleccionada.getIdsVuelosEnOrden() == null
                || rutaSeleccionada.getIdsVuelosEnOrden().isEmpty()) return 0;
        return rutaSeleccionada.getIdsVuelosEnOrden().stream()
                .mapToInt(vId -> {
                    VueloParaAlgoritmo v = vuelos.get(vId);
                    if(loggingReport!=null) loggingReport.appendReport(
                            "obtenerCapacidadMaxParaTodosVuelosEnRuta: Evaluando vuelo de ruta(480?): "+v);
                    int cantPuedeLlevarVuelo = v!=null? v.getCapacidadSinOcupar():0;
                    AlmacenParaAlgoritmo almOrigen = almacenes.get( v.getIdAlmacenOrigen() );
                    int cantTieneAlmacenOrigenFuturo = obtenerAlmacenEnInstante(almOrigen,v.getInicio()).getCapacidadOcupada();
                    AlmacenParaAlgoritmo almDestino = almacenes.get( v.getIdAlmacenOrigen() );
                    int cantPuedeRecibirAlmacenDestinoFuturo = obtenerAlmacenEnInstante(almDestino,v.getFin()).getCapacidadSinOcupar();
                    //^^^^^^^^ CON FE A LOS NU NULOS XD
                    return Math.min( cantPuedeRecibirAlmacenDestinoFuturo,Math.min(cantPuedeLlevarVuelo,cantTieneAlmacenOrigenFuturo));
                })
                .min()
                .orElse(0);
    }


    public AlmacenParaAlgoritmo getAlmacenFromId(Long id){
        return almacenes.get(id);
    }
    public VueloParaAlgoritmo getVueloFromId(Long id){
        return vuelos.get(id);
    }

    public static HashMap<Long, PedidoParaAxel> pedidosDesdeEstadoGlobal(EstadoGlobalMutableProblemaPlanificacion estadoGlobal) {
        HashMap<Long, PedidoParaAxel> result = new HashMap<>();
        if (estadoGlobal == null) return result;

        // 1) Inicializar entrada para TODOS los pedidos conocidos en el estado (incluso si no tienen rutas)
        Map<Long, PedidoParaAlgoritmo> pedidosMapa = estadoGlobal.getPedidos();
        if (pedidosMapa != null) {
            for (Map.Entry<Long, PedidoParaAlgoritmo> e : pedidosMapa.entrySet()) {
                Long idPedido = e.getKey();
                PedidoParaAlgoritmo p = e.getValue();
                if (idPedido == null || p == null) continue;
                result.put(idPedido, new PedidoParaAxel(p));
            }
        }

        // 2) Iterar rutas generadas por el algoritmo y agruparlas por idPedidoAsociado
        List<RutaProgramadaParaAlgoritmo> rutas = estadoGlobal.getRutasSolucionQueGeneraAlgoritmo();
        if (rutas == null || rutas.isEmpty()) {
            // no hay rutas: devolvemos mapa con pedidos y listas vacías
            return result;
        }

        for (RutaProgramadaParaAlgoritmo ruta : rutas) {
            if (ruta == null) continue;
            long idPedidoAsoc = ruta.getIdPedidoAsociado();

            // caso defensivo: id inválido (ej.: -1L) -> log y saltar
            if (idPedidoAsoc <= 0) {
                if (estadoGlobal.getLoggingReport() != null) {
                    estadoGlobal.getLoggingReport().appendReport(
                            "pedidosDesdeEstadoGlobal: ruta con idPedidoAsociado inválido (" + idPedidoAsoc + "), se omite. Ruta: " + ruta);
                }
                continue;
            }

            // obtener/crear entrada en el map
            PedidoParaAxel entry = result.get(idPedidoAsoc);
            if (entry == null) {
                // el pedido no estaba en estadoGlobal.getPedidos() (inconsistencia):
                PedidoParaAlgoritmo pFromState = estadoGlobal.getPedidos().get(idPedidoAsoc);
                if (pFromState != null) {
                    entry = new PedidoParaAxel(pFromState);
                    result.put(idPedidoAsoc, entry);
                } else {
                    // no existe pedido con ese id: log y saltar la ruta
                    if (estadoGlobal.getLoggingReport() != null) {
                        estadoGlobal.getLoggingReport().appendReport(
                                "pedidosDesdeEstadoGlobal: ruta referenció pedido inexistente id=" + idPedidoAsoc + " -> ruta omitida: " + ruta);
                    }
                    continue;
                }
            }

            // Añadir la ruta a la lista del pedido
            entry.getMiniPedidos().add(ruta);
        }

        return result;
    }


    // ola
    @Setter
    private Map<Long, Integer> productosEnTransitoPorPedido = new HashMap<>();

    // Método para validar consistencia
//    public ValidationResult validarConsistencia() {
//        ValidationResult result = new ValidationResult();
//
//        // Validar capacidades de almacenes
//        for (AlmacenParaAlgoritmo almacen : almacenes.values()) {
//            if (!almacen.isEsInfinito() &&
//                    almacen.getCapacidadOcupada() > almacen.getCapacidadMaxima()) {
//                result.addError("Almacén " + almacen.getId() + " sobrecapacidad");
//            }
//        }
//
//        // Validar pedidos
//        for (PedidoParaAlgoritmo pedido : pedidos.values()) {
//            int totalAsignado = pedido.getCantidadProductosEntregados() +
//                    pedido.getCantidadProductosProgramados() +
//                    productosEnTransitoPorPedido.getOrDefault(pedido.getId(), 0);
//
//            if (totalAsignado > pedido.getCantidadProductosPedidos()) {
//                result.addError("Pedido " + pedido.getId() + " sobreasignado");
//            }
//        }
//
//        return result;
//    }

    // Método para snapshot/checkpoint
    public EstadoGlobalMutableProblemaPlanificacion crearSnapshot() {
        // Implementar deep copy para checkpoint
        return SerializationUtils.clone(this); // Usar Apache Commons o implementar manualmente
    }

    public List<PedidoParaAlgoritmo> obtenerPedidosPendientesDeEntregaYProgram(){
        return this.getPedidos().values()
                .stream()
                .filter(pedidoParaAlgoritmo -> pedidoParaAlgoritmo.getCantidadRestanteDeEntregaYProgram()>0)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String cabecera = "EstadoGlobalMutableProblemaPlanificacion\n";
        String almacenes = PrettyPrinter.printList( getAlmacenes().values().stream().toList() ) ;
        String vuelos = PrettyPrinter.printList( getVuelos().values().stream().toList() );
        String pedidos = PrettyPrinter.printList( getPedidos().values().stream().toList() );
        String rutas = PrettyPrinter.printList(getRutasSolucionQueGeneraAlgoritmo());
        result.append(cabecera).append(almacenes).append(vuelos).append(pedidos).append(rutas);
        return result.toString();
    }

    public AlmacenParaAlgoritmo obtenerAlmacenEnInstanteNUEVO(AlmacenParaAlgoritmo alm, Instant instante){
        AlmacenParaAlgoritmo almacenSimuladoHastaInstante = alm.clone(); // asegúrate deep clone
        long idAlmacenSimulado = alm.getId();

        for(RutaProgramadaParaAlgoritmo rutita : rutasSolucionQueGeneraAlgoritmo){
            List<VueloParaAlgoritmo> vuelitos = obtenerVariosVuelosPorIds(rutita.getIdsVuelosEnOrden());
            int cantProdsRuta = rutita.getCantidadTotalOParcial();
            if (cantProdsRuta <= 0) continue; // nada que afectar

            for (int i = 0; i < vuelitos.size(); i++) {
                VueloParaAlgoritmo vuelo = vuelitos.get(i);
                if (vuelo == null) continue;

                Instant inicio = vuelo.getInicio();
                Instant fin = vuelo.getFin();

                // 1) Si es llegada al almacen simulado, primero aplicar pickup (liberación) si corresponde
                if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)) {
                    Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);

                    // Si el pickup ya ocurrió (instante >= fin + ventana) => liberar primero
                    if (!instante.isBefore(instantePickup)) { // instante >= pickup
                        almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                        // Nota: aquí ya se libera; no hacemos ocupación posterior para este vuelo.
                        continue; // pasamos al siguiente vuelo de la ruta
                    }

                    // Si pickup NO ocurrió aún, pero la llegada sí (instante >= fin), entonces ocupar
                    if (!instante.isBefore(fin)) { // instante >= fin && instante < pickup
                        almacenSimuladoHastaInstante.ocuparCapacidad(cantProdsRuta);
                    }
                }

                // 2) Salida del origen: la carga sale del origen desde inicio (instante >= inicio)
                if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                    if (!instante.isBefore(inicio)) { // instante >= inicio
                        almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                    }
                }

                // NOTA: no combine lógicamente ocupación y desocupación en la misma iteración
                // de forma que el orden quede definido: arriba aplicamos pickup/desocupación
                // antes que ocupación por llegada.
            }
        }
        return almacenSimuladoHastaInstante;
    }

}

// Otra versión de Obtener almacén en instante que me dio GPT para evitar la race condition
// de llegada de vuelo y pickup pero no tuve craneo pa leer:
/*
public AlmacenParaAlgoritmo obtenerAlmacenEnInstante(AlmacenParaAlgoritmo alm, Instant instante){
    AlmacenParaAlgoritmo almacenSimuladoHastaInstante = alm.clone(); // asegúrate deep clone
    long idAlmacenSimulado = alm.getId();

    for(RutaProgramadaParaAlgoritmo rutita : rutasSolucionQueGeneraAlgoritmo){
        List<VueloParaAlgoritmo> vuelitos = obtenerVariosVuelosPorIds(rutita.getIdsVuelosEnOrden());
        int cantProdsRuta = rutita.getCantidadTotalOParcial();
        if (cantProdsRuta <= 0) continue; // nada que afectar

        for (int i = 0; i < vuelitos.size(); i++) {
            VueloParaAlgoritmo vuelo = vuelitos.get(i);
            if (vuelo == null) continue;

            Instant inicio = vuelo.getInicio();
            Instant fin = vuelo.getFin();

            // 1) Si es llegada al almacen simulado, primero aplicar pickup (liberación) si corresponde
            if (Objects.equals(vuelo.getIdAlmacenDestino(), idAlmacenSimulado)) {
                Instant instantePickup = fin.plusSeconds(SEGUNDOS_PARA_RECOGER_PEDIDO);

                // Si el pickup ya ocurrió (instante >= fin + ventana) => liberar primero
                if (!instante.isBefore(instantePickup)) { // instante >= pickup
                    almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                    // Nota: aquí ya se libera; no hacemos ocupación posterior para este vuelo.
                    continue; // pasamos al siguiente vuelo de la ruta
                }

                // Si pickup NO ocurrió aún, pero la llegada sí (instante >= fin), entonces ocupar
                if (!instante.isBefore(fin)) { // instante >= fin && instante < pickup
                    almacenSimuladoHastaInstante.ocuparCapacidad(cantProdsRuta);
                }
            }

            // 2) Salida del origen: la carga sale del origen desde inicio (instante >= inicio)
            if (Objects.equals(vuelo.getIdAlmacenOrigen(), idAlmacenSimulado)) {
                if (!instante.isBefore(inicio)) { // instante >= inicio
                    almacenSimuladoHastaInstante.desocuparCapacidad(cantProdsRuta);
                }
            }

            // NOTA: no combine lógicamente ocupación y desocupación en la misma iteración
            // de forma que el orden quede definido: arriba aplicamos pickup/desocupación
            // antes que ocupación por llegada.
        }
    }
    return almacenSimuladoHastaInstante;
}


 */
