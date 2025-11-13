package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.EstrategiaGraspHibrido;
import pe.edu.pucp.inf.pddsbackend.algorithms.EstrategiaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.*;
import pe.edu.pucp.inf.pddsbackend.dto.productos.ProductoSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloSolucionDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.*;
import pe.edu.pucp.inf.pddsbackend.repositories.*;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class PlanificacionServiceImpl implements PlanificacionService {

    private final SimulacionRepository simulacionRepository;
    private final LoggingReport loggingReport;
    private EstrategiaPlanificacion estrategiaPlanificacion; // podría variar la estrategia con el tiempo?
    private final VueloRepository vueloRepository;
    private final AlmacenRepository almacenRepository;
    private final PlanificacionRepository planificacionRepository;
    private final ProgramacionRepository rutaProgramadaRepository;
    private final ProgramacionXVueloRepository rutaProgramadaXVueloRepository;
    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;

    private static Instant instanteUltimoPlanificacion;

    // Inyectar estrategias como beans para evitar instanciarlas con `new`
//    private final LoggedHeuristicAlgorithmStrategy loggedHeuristicAlgorithmStrategy;
    private final EstrategiaGraspHibrido estrategiaGraspHibrido;
    // Mock eliminado - ya no es necesario
//    private final TabuSearchAlgorithmStrategy tabuSearchAlgorithmStrategy;

    private void escogerEstrategiaInicial(EstrategiaFija estrategiaFija, Boolean usarModoMock){
        // Modo mock ya no usa estrategia - se bypasea en realizarPlanificacionConEntrada()
        
        // Usar estrategia normal
        switch(estrategiaFija){
            case AUTO -> estrategiaPlanificacion = estrategiaGraspHibrido;
            case PROFUNDA ->  estrategiaPlanificacion = estrategiaGraspHibrido;
            case RAPIDA ->   estrategiaPlanificacion = estrategiaGraspHibrido;
        }
    }
    private void inicializarEstrategiaInicial(RealizarPlanificacionDTO params){
        if(params.getSubCarpetaReportes() != null){
            LoggingReport loggingReport = new LoggingReport();
            loggingReport.setDirectory(params.getSubCarpetaReportes());
            estrategiaPlanificacion.setLoggingReport(loggingReport);
            estrategiaGraspHibrido.setLoggingReport(loggingReport); //???
        }//vvv !!!!!!!!!!
        else{
            estrategiaPlanificacion.getLoggingReport().limpiarDirectorio();
        }
        estrategiaPlanificacion.getLoggingReport().limpiarReporte();
        estrategiaPlanificacion.setSemilla(params.getSeed());
        System.out.println("Inicializado mi strategy: "+ estrategiaPlanificacion);
    }

    @Transactional
    @Override
    public PlanificacionResponseDTO realizarPlanificacionDePedidosActualesConPersistencia(RealizarPlanificacionDTO params) throws Exception {
        if(params.getLoggear()!=null && params.getLoggear().equals(Boolean.FALSE))
            LoggingReport.imprimir=false;
        else
            LoggingReport.imprimir=true;

        ResultadoAlgoritmoDTO solucionAlgoritmo = realizarPlanificacionConDatosDeBD(params);
        // Persistir la solución generada por el algoritmo en la BD
//        List<ProgramacionEntidad>enviosProgramados= persistirSolucionYRetornarRutas(solucionAlgoritmo, params.getIdSimulacion());

        // Si es modo mock, usar mapeo sin persistencia
        PlanificacionResponseDTO response;
        if (params.getUsarModoMock() != null && params.getUsarModoMock()) {
            response = mapearSolucionMockAResponse(solucionAlgoritmo);
        } else {
            response = mapearSolucionAResponse(solucionAlgoritmo);
        }

        return response;
    }

    @Override
    public ResultadoAlgoritmoDTO realizarPlanificacionConDatosDeBD(RealizarPlanificacionDTO params) throws Exception {
        System.out.println("realizar planificación con datos de BD");
        EstadoGlobal estadoInicialAlgoritmo =  obtenerDatosParaAlgoritmo(params, false);
        EntradaProblemaPlanificacion entrada = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoInicialAlgoritmo)
                .semilla(params.getSeed())
                .parametrosOpcionalesPersonalizados(params.getParametros())
                .instanteActual( params.getInstanteActual()!=null ?params.getInstanteActual():Instant.now() )
                .build();
        ResultadoAlgoritmoDTO solucionAlgoritmo =realizarPlanificacionConEntrada(params, entrada);
        return solucionAlgoritmo;
    }

    @Override
    public ResultadoAlgoritmoDTO realizarPlanificacionConEntrada(
            RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo) throws Exception {
        System.out.println("realizarPlanificacionConEntrada");
        
        // ⚠️ MODO TESTING: Si usarModoMock está activado, generar planificaciones de prueba
        // ⚠️ MODO TESTING: Si usarModoMock está activado, retornar programaciones HARDCODEADAS
        if (params.getUsarModoMock() != null && params.getUsarModoMock()) {
            System.out.println("🧪 MODO TESTING: Generando programaciones HARDCODEADAS para prueba");
            SalidaProblemaPlanificacion solucionPrueba = crearProgramacionesHardcodeadas(dataEntradaAlgoritmo);
            return new ResultadoAlgoritmoDTO(solucionPrueba, 0.0, 0L);
        }
        
        escogerEstrategiaInicial(params.getEstrategiaFija(), params.getUsarModoMock());
        inicializarEstrategiaInicial(params);

        long startTime = System.nanoTime(); // Record start time in nanoseconds
        SalidaProblemaPlanificacion solucionAlgoritmo = estrategiaPlanificacion.planificar(dataEntradaAlgoritmo);
        long endTime = System.nanoTime(); // Record end time in nanoseconds
        long duration = (endTime - startTime) /  1000000; // Calculate duration in seconds, no nanoseconds
        instanteUltimoPlanificacion= params.getInstanteActual()!=null?
                params.getInstanteActual().plus(duration, ChronoUnit.MILLIS)  : Instant.now();
//        solucionAlgoritmo.setTiempoEjecucionMs(duration);
//        System.out.println("A ver esa solución!:\n"+solucionAlgoritmo);
//        if (estrategiaPlanificacion.getLoggingReport() != null)
//            estrategiaPlanificacion.getLoggingReport().appendReport("A ver esa solución!:\n" + solucionAlgoritmo);

        double fitness = obtenerFitnessDeSolucion(solucionAlgoritmo, dataEntradaAlgoritmo);

        return new ResultadoAlgoritmoDTO(solucionAlgoritmo, fitness, duration);
    }

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    @Transactional(readOnly = true)
    @Override
    public EstadoGlobal obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params, boolean incluirTodo) {
            // ✅ Fecha actual de planificación (ahora o la especificada)
            Instant fechaPlanif = params.getInstanteActual() != null ? params.getInstanteActual() : Instant.now();

            // ✅ Fecha desde la cual tomar pedidos
            // Si no se especifica, usar un rango razonable (ej: 30 días atrás)
            Instant fechaInicioSimulacion = params.getInstanteDesdeTomarPedidos() != null ?
                params.getInstanteDesdeTomarPedidos() : 
                fechaPlanif.minus(30, ChronoUnit.DAYS);

            // ✅ Fecha de inicio para buscar vuelos (2 horas después de planificación)
            // Se añaden 2 horas porque el algoritmo demora en ejecutarse y programar
            Instant fechaInicioVuelos = fechaPlanif.plus(2, ChronoUnit.HOURS);
            
            // ✅ Fecha máxima para vuelos (3 días hacia adelante desde fecha planificación)
            Instant fechaMaxLLegadaVuelo = fechaPlanif.plus(3, ChronoUnit.DAYS);
            
            System.out.println("📅 Obteniendo datos para algoritmo desde fecha pedidos: " + fechaInicioSimulacion);
            System.out.println("📅 Fecha planificación: " + fechaPlanif);
            System.out.println("✈️  Fecha inicio vuelos (planif + 2h): " + fechaInicioVuelos);
            System.out.println("📅 Fecha max llegada vuelos: " + fechaMaxLLegadaVuelo);

            HashMap<Long, Almacen> almacenes = obtenerAlmacenesParaAlgoritmo();
//            Bitacora.escribir("almacenes "+almacenes);
            HashMap<Long, Vuelo> vuelos =
                    obtenerVuelosParaAlgoritmo(fechaInicioVuelos, fechaMaxLLegadaVuelo, incluirTodo);
            HashMap<Long, Pedido> pedidos =
                    obtenerPedidosParaAlgoritmo(fechaInicioSimulacion,fechaPlanif, incluirTodo);
            
            // 📊 LOG DETALLADO DE DATOS PARA PLANIFICACIÓN
            System.out.println("\n🎯 ========= DATOS PARA PLANIFICACIÓN =========");
            System.out.println("📦 Total PEDIDOS obtenidos: " + pedidos.size());
            System.out.println("✈️  Total VUELOS obtenidos: " + vuelos.size());
            System.out.println("🏢 Total ALMACENES: " + almacenes.size());
            System.out.println("⏰ Rango de fechas:");
            System.out.println("   - Pedidos desde: " + fechaInicioSimulacion);
            System.out.println("   - Pedidos hasta: " + fechaPlanif);
            System.out.println("   - Vuelos desde: " + fechaInicioVuelos + " (planif + 2h)");
            System.out.println("   - Vuelos hasta: " + fechaMaxLLegadaVuelo + " (planif + 3 días)");
            System.out.println("===============================================\n");
//        Bitacora.escribir("vuelos "+vuelos);
//        Bitacora.escribir("pedidos "+pedidos);

        return new EstadoGlobal(almacenes, vuelos, pedidos,null,null);
    }

    private HashMap<Long, Pedido> obtenerPedidosParaAlgoritmo(
            Instant fechaSimulacionInicio,
            Instant fechaPlanif,
        boolean incluirTodo
    ) {

        List<PedidoEntidad> pedidos;
        if(incluirTodo){
            pedidos = pedidoRepository.findAllByInstanteRegistroAfter(fechaSimulacionInicio);
        }else{
            pedidos =  pedidoRepository
                    .listarPedidosNoAtendidosCompletamenteYNoDeAlmacenesInfinitosEntreMedio(fechaSimulacionInicio,fechaPlanif);

        }

        HashMap<Long, Pedido> resultado = new HashMap<>(
                pedidos.stream().collect(
                        Collectors.toMap(PedidoEntidad::getId, Pedido::desdeEntidad)
                )
        );

        return resultado;
    }

    /**
     * Construye un mapa idAlmacen -> Almacen
     * (versión simple: consulta por cada almacén las listas de ids). Se puede BATCHEAR para más eficiencia
     */
    private HashMap<Long, Almacen> obtenerAlmacenesParaAlgoritmo() {
        List<AlmacenEntidad> almacenesBD = almacenRepository.findAlmacenByActivoTrue();
        HashMap<Long, Almacen> resultado = new HashMap<>(almacenesBD.size());
        for (AlmacenEntidad a : almacenesBD) {
            Almacen apa = Almacen.desdeEntidad(
                    a
            );
            resultado.put(apa.getId(), apa);
        }

        return resultado;
    }

    private HashMap<Long, Vuelo> obtenerVuelosParaAlgoritmo(
            Instant fechaInicio,
            Instant fechaMaxLlegadaVuelos,
            boolean incluirTodo
    ){

        List<VueloEntidad> vuelos;

        if(incluirTodo){
            vuelos = vueloRepository.findAllByFechaHoraInicioUtcAfter(fechaInicio);
        }else{
            vuelos =  vueloRepository.findByActivoTrueAndFechaHoraInicioUtcAfterAndFechaHoraFinUtcBefore
                    (fechaInicio, fechaMaxLlegadaVuelos);
//            System.out.println("vuelos encontrados: " + vuelos.size());
//            System.out.println(vuelos);
        }
        HashMap<Long, Vuelo> resultado = new HashMap<>(
                vuelos.stream().collect(
                Collectors.toMap(VueloEntidad::getId, Vuelo::desdeEntidad)
                )
        );

        return resultado;
    }


    public double obtenerFitnessDeSolucion(SalidaProblemaPlanificacion salidaProblemaPlanificacion, EntradaProblemaPlanificacion entradaProblemaPlanificacion) {
        Double fitness =CalculadorDeFitness.calcularFitnessSalidaProblema(salidaProblemaPlanificacion, entradaProblemaPlanificacion);
//        salidaProblemaPlanificacion.setFitness(fitness);
        return fitness;
    }



    /**
     * Persiste la solución (lista de rutas generadas por el algoritmo) creando:
     *  - una Planificacion nueva
     *  - una ProgramacionEntidad por cada Programacion (vinculada al PedidoEntidad)
     *  - una ProgramacionXVuelo por cada vuelo en la ruta (en el mismo orden)
     *
     * Además marca el PedidoEntidad como PROGRAMADO y actualiza la capacidad ocupada del VueloEntidad
     * incrementándola por la cantidad programada en la ruta (ver notas).
     *
     * La operación es transaccional: ante cualquier error se hace rollback.
     */
    @Transactional
    public List<ProgramacionEntidad> persistirSolucionYRetornarRutas(ResultadoAlgoritmoDTO solucion, Long idSimulacion) {
//        if (solucion == null || solucion.getRutasProgramadasParaSatisfacerTodoPedido() == null) {
//            return List.of();
//        }
//        Simulacion simulacion=null;
//        if(idSimulacion != null) { simulacion = simulacionRepository.getReferenceById(idSimulacion);}
//        // 1) Crear entidad Planificacion (registro de esta ejecución)
//        Planificacion planif = Planificacion.builder()
//                .fechaHoraFinPlanif(Instant.now())
//                .colapsado(false)
//                .reprogramado(false)
//                .fitnessConseguido(solucion.getFitness())
//                .huboErrorEjecucion(false)
//                .razonErrorEjecucion(solucion.getError())
//                .duracionEjecucionAlgoritmo(solucion.getTiempoEjecucionMs())
//                .simulacion(simulacion)
//                .build();
//        planif = planificacionRepository.save(planif);
//        // AUNQUE ESTÉ COLAPSADO LO GUARDAMOS, PARA CURIOSEAR; PERO TENERLO EN CUENTA!!!
//        List<ProgramacionEntidad> rutasPersistidas = new ArrayList<>();
//
//        // iterar rutas generadas por el algoritmo
//        for (Programacion rutaAlgo : solucion.getRutasProgramadasParaSatisfacerTodoPedido()) {
//
//            if (rutaAlgo == null) continue;
//
//            long idPedido = rutaAlgo.getIdPedidoAsociado();
//            int cantidad = rutaAlgo.getCantidadTotalOParcial();
//
//            if (cantidad <= 0) {
//                // saltar rutas vacías (o podrías lanzar excepción si esto es indicio de bug)
//                continue;
//            }
//
//            // Obtener pedido (lanza EntityNotFoundException si no existe)
//            PedidoEntidad pedido = pedidoRepository.findById(idPedido)
//                    .orElseThrow(() -> new EntityNotFoundException("PedidoEntidad no encontrado id=" + idPedido));
//
//            // 2) Crear ProgramacionEntidad y asociarla a planificacion + pedido
//            ProgramacionEntidad rutaEntidad = new ProgramacionEntidad();
//            rutaEntidad.setPedido(pedido);
//            rutaEntidad.setCantidadTotalOParcial(cantidad);
//            rutaEntidad.setPlanificacion(planif);
//            ProgramacionEntidad rutaGuardada = rutaProgramadaRepository.save(rutaEntidad);
//
//            // 3) Crear ProgramacionXVuelo por cada vuelo id (mantener orden)
//            LinkedList<Long> idsVuelos = rutaAlgo.getIdsVuelosEnOrden();
//            if (idsVuelos != null) {
//                byte orden = 0;
//                for (Long idVuelo : idsVuelos) {
//                    if (idVuelo == null) continue;
//
//                    VueloEntidad vuelo = vueloRepository.findById(idVuelo)
//                            .orElseThrow(() -> new EntityNotFoundException("VueloEntidad no encontrado id=" + idVuelo));
//
//                    // opcional: comprobar que vuelo.activo && !vuelo.cancelado
//                    if (Boolean.FALSE.equals(vuelo.getActivo()) || Boolean.TRUE.equals(vuelo.getCancelado())) {
//                        throw new IllegalStateException("VueloEntidad no disponible para programar (inactivo o cancelado) id=" + idVuelo);
//                    }
//
//                    // actualizar capacidad ocupada del vuelo (reserva)
//                    int nuevaOcupada = Math.addExact(vuelo.getCapacidadOcupada(), cantidad);
//                    if (nuevaOcupada > vuelo.getCapacidadMaxima()) {
//                        // si supera capacidad, lanzamos excepción para abortar toda la transacción
//                        throw new IllegalStateException("Capacidad de vuelo excedida (vueloId=" + idVuelo +
//                                " capacidadMaxima=" + vuelo.getCapacidadMaxima() +
//                                " ocupadoAntes=" + vuelo.getCapacidadOcupada() +
//                                " intentoReservar=" + cantidad + ")");
//                    }
////                    vuelo.setCapacidadOcupada(nuevaOcupada); // NOOOOOOOOOOOOOOOOO
//                    vueloRepository.save(vuelo);
//
//                    // crear entrada de asociación (persistir)
//                    ProgramacionXVuelo rpXV = new ProgramacionXVuelo();
//                    rpXV.setRutaProgramada(rutaGuardada);
//                    rpXV.setVuelo(vuelo);
//                    rpXV.setOrden(orden);
//                    rutaProgramadaXVueloRepository.save(rpXV);
//
//                    orden++;
//                }
//            }
//
//            // 4) Marcar pedido como PROGRAMADO (no alterar cantidad entregada)
////            pedido.setEstado(EstadoPedido.PROGRAMADO);
//            pedidoRepository.save(pedido);
//            rutasPersistidas.add(rutaGuardada);
//        }
//
//        // Devolver lista de rutas persistidas (con id y relaciones)
//        return rutasPersistidas;
        return null;
    }

    @Transactional(readOnly = true)
    protected PlanificacionResponseDTO mapearSolucionAResponse(ResultadoAlgoritmoDTO resultadoAlgoritmoDTO) {
        SalidaProblemaPlanificacion solucion = resultadoAlgoritmoDTO.salida();
        if (solucion == null || solucion.getProgramaciones() == null || solucion.getProgramaciones().isEmpty()) {
            return new PlanificacionResponseDTO(null, null, false, null, null, Collections.emptyList(), false, null);
        }

        List<Programacion> programaciones = new ArrayList<>(solucion.getProgramaciones());

        // 1) Recolectar ids/uuids necesarios en batch
        Set<Long> pedidoIds = new HashSet<>();
        Set<Long> vueloIds = new HashSet<>();
        Set<UUID> productoUuids = new HashSet<>();

        for (Programacion p : programaciones) {
            if (p == null) continue;
            pedidoIds.add(p.getIdPedido());
            if (p.getIdsVueloRuta() != null) {
                vueloIds.addAll(p.getIdsVueloRuta());
            }
            if (p.getUuidProducto() != null) {
                productoUuids.add(p.getUuidProducto());
            }
        }

        // 2) Batch fetch: pedidos, vuelos, productos
        Map<Long, PedidoEntidad> pedidosMap = new HashMap<>();
        pedidoRepository.findAllById(pedidoIds).forEach(pe -> pedidosMap.put(pe.getId(), pe));

        Map<Long, VueloEntidad> vuelosMap = new HashMap<>();
        if (!vueloIds.isEmpty()) {
//            vueloRepository.findAllById(vueloIds).forEach(v -> vuelosMap.put(v.getId(), v));
        }
        vueloRepository.findAll().forEach(v -> vuelosMap.put(v.getId(), v));

//        System.out.println("vuelosMap: "+vuelosMap.keySet()+" : "+vuelosMap.values());
        // Productos: intento de batch fetch; si tu repo no tiene findAllByUuidIn, crea un método o cae a per-item abajo
        Map<UUID, ProductoEntidad> productosMap = new HashMap<>();
        if (!productoUuids.isEmpty()) {
            try {
                // suponiendo que existe: List<ProductoEntidad> findAllByUuidIn(Collection<UUID>)
                productoRepository.findAllByUuidIn(productoUuids)
                        .forEach(prod -> productosMap.put(prod.getUuid(), prod));
            } catch (Exception ex) {
                // fallback: intentar obtener por cada uuid (peor caso — raro)
                for (UUID uuid : productoUuids) {
                    productoRepository.findByUuid(uuid).ifPresent(p -> productosMap.put(p.getUuid(), p));
                }
            }
        }

        // 3) Need almacenes: agregarlos a partir de pedidos + vuelos (origen/destino)
        Set<Long> almacenIds = new HashSet<>();
        for (PedidoEntidad pe : pedidosMap.values()) {
            if (pe != null && pe.getAlmacenDestino() != null) almacenIds.add(pe.getAlmacenDestino().getId());
        }
        for (VueloEntidad v : vuelosMap.values()) {
            if (v == null) continue;
            if (v.getAlmacenOrigen() != null) almacenIds.add(v.getAlmacenOrigen().getId());
            if (v.getAlmacenDestino() != null) almacenIds.add(v.getAlmacenDestino().getId());
        }
        Map<Long, AlmacenEntidad> almacenesMap = new HashMap<>();
        if (!almacenIds.isEmpty()) {
            almacenRepository.findAllById(almacenIds).forEach(a -> almacenesMap.put(a.getId(), a));
        }

        // 4) Construcción de DTOs: ahora iteramos solo una vez y usamos los mapas
        List<ProgramacionSolucionDTO> rutasDto = new ArrayList<>(programaciones.size());

        for (Programacion programacion : programaciones) {
            if (programacion == null) continue;

            // PedidoEntidad (pre-batcheado)
            PedidoEntidad pedidoEntidad = pedidosMap.get(programacion.getIdPedido());
            if (pedidoEntidad == null) {
                // fallback defensivo: intentar cargar desde repo (solo si es raro)
                pedidoEntidad = pedidoRepository.findById(programacion.getIdPedido()).orElse(null);
            }
            if (pedidoEntidad == null) {
                // si aun así no existe, se salta (o podrias registrar un warning)
                Bitacora.escribir("mapearSolucionAResponse: pedido no encontrado id=" + programacion.getIdPedido());
                continue;
            }

            Long idPedido = pedidoEntidad.getId();
            Integer cantidadTotalPedido = pedidoEntidad.getCantidadProductosPedidos();

            // ProductoEntidad: chequear en el mapa; si falta, crear/obtener con la función existente
            ProductoEntidad productoEntidad = productosMap.get(programacion.getUuidProducto());
            if (productoEntidad == null) {
                // solo para los faltantes (mucho menos frecuente), llamar al método que crea si no existe
                productoEntidad = obtenerProductoEntidadOCrearlo(programacion, vuelosMap.values().stream().toList());
                if (productoEntidad != null) {
                    productosMap.put(productoEntidad.getUuid(), productoEntidad);
                } else {
                    Bitacora.escribir("mapearSolucionAResponse: producto no encontrado y no pudo crearse uuid=" + programacion.getUuidProducto());
                    continue;
                }
            }

            // AlmacenDestino del pedido (pre-batcheado)
            AlmacenEntidad almacenDestino = pedidoEntidad.getAlmacenDestino();
            AlmacenSolucionDTO almacenDto;
            if (almacenDestino != null) {
                almacenDto = new AlmacenSolucionDTO(
                        almacenDestino.getId(),
                        almacenDestino.getCodigoAeropuertoEn4Letras(),
                        almacenDestino.getCodigoCiudadEn4Letras()
                );
            } else {
                almacenDto = new AlmacenSolucionDTO(null, null, null);
            }

            // PedidoSolucionDTO
            PedidoSolucionDTO pedidoSolDto = new PedidoSolucionDTO(
                    idPedido,
                    cantidadTotalPedido,
                    new ProductoSolucionDTO(productoEntidad.getUuid(),
                            productoEntidad.getAlmacenInfinitoOrigen().getId(),
                            productoEntidad.getExiste()),
                    almacenDto
            );

            // Vuelos asociados: tomamos desde vuelosMap usando ids de la ruta (mantenemos orden)
            List<Long> idsRuta = programacion.getIdsVueloRuta();
            List<VueloSolucionDTO> vuelosDto = new ArrayList<>();
            if (idsRuta != null && !idsRuta.isEmpty()) {
                byte orden = 1;
                for (Long vid : idsRuta) {
                    VueloEntidad ve = vuelosMap.get(vid);
//                    System.out.println("ve: "+ve);
//                    System.out.println("vid: "+vid);
                    if (ve == null) {
                        // fallback raro: cargar individualmente
                        ve = vueloRepository.findById(vid).orElse(null);
                        if (ve == null) {
                            Bitacora.escribir("mapearSolucionAResponse: vuelo no encontrado id=" + vid + " (se omite)");
                            continue;
                        } else {
                            vuelosMap.put(ve.getId(), ve); // cachearlo para siguientes
                        }
                    }
                    AlmacenEntidad origen = ve.getAlmacenOrigen();
                    AlmacenEntidad destino = ve.getAlmacenDestino();

                    VueloSolucionDTO vDto = new VueloSolucionDTO();
                    vDto.setIdVuelo(ve.getId());
                    vDto.setIdAlmacenOrigen(origen != null ? origen.getId() : null);
                    vDto.setIdAlmacenDestino(destino != null ? destino.getId() : null);

                    vDto.setCodigoAeropuertoOrigenEn4Siglas(origen != null ? origen.getCodigoAeropuertoEn4Letras() : null);
                    vDto.setCodigoAeropuertoDestinoEn4Siglas(destino != null ? destino.getCodigoAeropuertoEn4Letras() : null);

                    vDto.setCiudadOrigenEn4Siglas(origen != null ? origen.getCodigoCiudadEn4Letras() : null);
                    vDto.setCiudadDestinoEn4Siglas(destino != null ? destino.getCodigoCiudadEn4Letras() : null);

                    vDto.setOrden(orden++);
                    vuelosDto.add(vDto);
                }
            }

            // Construir ProgramacionSolucionDTO
            ProgramacionSolucionDTO rutaDto = new ProgramacionSolucionDTO();
            rutaDto.setPedido(pedidoSolDto);
            rutaDto.setVuelosDeRutaParaAtenderPedido(vuelosDto);

            rutasDto.add(rutaDto);
        }

        // normalizar colapsado
        boolean colapsadoFlag = solucion.isColapsado();
        boolean conError = resultadoAlgoritmoDTO.salida().isHuboErrorEjecucion();

        return new PlanificacionResponseDTO(
                null, // idPlanificacion (no disponible aquí)
                null, // fechaHoraFinPlanif (si lo necesitas, extraer de solucion)
                colapsadoFlag,
                resultadoAlgoritmoDTO.fitness(),
                resultadoAlgoritmoDTO.tiempoEjecucionMs(),
                rutasDto,
                conError,
                solucion.getError()
        );
    }


    private ProductoEntidad obtenerProductoEntidadOCrearlo(Programacion programacion, List<VueloEntidad> vuelos) {
//        List<VueloEntidad>vuelos = vueloRepository.findAllById(programacion.getIdsVueloRuta());
        return ProductoEntidad.builder()
                .uuid(programacion.getUuidProducto())
                .existe(false)
                .fechaPlanificacion(instanteUltimoPlanificacion)
                .vuelosRuta( vuelos)
                .fechaExistencia(vuelos.get(0).getFechaHoraInicioUtc()) // get first?
                .almacenInfinitoOrigen(vuelos.get(0).getAlmacenOrigen())// get first?
                .build();

//        return productoRepository.findByUuid(programacion.getUuidProducto()).orElse(
//                        ProductoEntidad.builder()
//                                .uuid(programacion.getUuidProducto())
//                                .existe(false)
//                                .fechaPlanificacion(instanteUltimoPlanificacion)
//                                .vuelosRuta( vuelos)
//                                .fechaExistencia(vuelos.get(0).getFechaHoraInicioUtc()) // get first?
//                                .almacenInfinitoOrigen(vuelos.get(0).getAlmacenOrigen())// get first?
//                                .build()
//        );
    }

    @Override
    public String obtenerMetaDatos() {
        StringBuilder builder = new StringBuilder();
        builder.append("Mi estrategia: " + estrategiaPlanificacion.toString());
        return builder.toString();
    }

    /**
     * 🎭 MAPEO MOCK: Convierte solución a response DTO SIN tocar la BD
     * Solo usa datos del dominio (Programacion) para generar DTOs ficticios
     * Útil para testing cuando no se quiere persistir nada
     */
    protected PlanificacionResponseDTO mapearSolucionMockAResponse(ResultadoAlgoritmoDTO resultadoAlgoritmoDTO) {
        SalidaProblemaPlanificacion solucion = resultadoAlgoritmoDTO.salida();
        
        // Defensiva: si no hay nada, devolver vacío
        if (solucion == null || solucion.getProgramaciones() == null) {
            return new PlanificacionResponseDTO(
                null, null, false, null, null, 
                Collections.emptyList(), false, null
            );
        }

        List<ProgramacionSolucionDTO> rutasDto = new ArrayList<>();
        List<Programacion> programaciones = new ArrayList<>(solucion.getProgramaciones());
        
        for (Programacion programacion : programaciones) {
            if (programacion == null) continue;

            try {
                // 🎭 DATOS MOCK - Solo usamos IDs, no cargamos entidades
                Long idPedido = programacion.getIdPedido();
                UUID uuidProducto = programacion.getUuidProducto();
                LinkedList<Long> idsVuelos = programacion.getIdsVueloRuta();

                // ProductoSolucionDTO ficticio
                ProductoSolucionDTO productoDto = new ProductoSolucionDTO(
                    uuidProducto,
                    -1L,  // ID almacen ficticio (no importa para testing)
                    false  // No existe realmente
                );

                // AlmacenSolucionDTO ficticio
                AlmacenSolucionDTO almacenDto = new AlmacenSolucionDTO(
                    -1L,
                    "MOCK",
                    "TEST"
                );

                // PedidoSolucionDTO ficticio
                PedidoSolucionDTO pedidoDto = new PedidoSolucionDTO(
                    idPedido,
                    1,  // Cantidad ficticia
                    productoDto,
                    almacenDto
                );

                // VuelosSolucionDTO ficticios
                List<VueloSolucionDTO> vuelosDto = new ArrayList<>();
                byte orden = 1;
                for (Long idVuelo : idsVuelos) {
                    VueloSolucionDTO vueloDto = new VueloSolucionDTO();
                    vueloDto.setIdVuelo(idVuelo);
                    vueloDto.setIdAlmacenOrigen(-1L);
                    vueloDto.setIdAlmacenDestino(-1L);
                    vueloDto.setCodigoAeropuertoOrigenEn4Siglas("MOCK");
                    vueloDto.setCodigoAeropuertoDestinoEn4Siglas("MOCK");
                    vueloDto.setCiudadOrigenEn4Siglas("TEST");
                    vueloDto.setCiudadDestinoEn4Siglas("TEST");
                    vueloDto.setOrden(orden++);
                    vuelosDto.add(vueloDto);
                }

                // ProgramacionSolucionDTO
                ProgramacionSolucionDTO rutaDto = new ProgramacionSolucionDTO();
                rutaDto.setPedido(pedidoDto);
                rutaDto.setVuelosDeRutaParaAtenderPedido(vuelosDto);
                
                rutasDto.add(rutaDto);
                
            } catch (Exception ex) {
                log.error("🎭 MOCK: Error mapeando programación mock", ex);
                // Continuar con la siguiente
            }
        }

        return new PlanificacionResponseDTO(
            null,
            null,
            solucion.isColapsado(),
            resultadoAlgoritmoDTO.fitness(),
            resultadoAlgoritmoDTO.tiempoEjecucionMs(),
            rutasDto,
            false,
            solucion.getError()
        );
    }

    /**
     * 🧪 MÉTODO PARA TESTING: Crea programaciones HARDCODEADAS para probar la simulación
     * 
     * IMPORTANTE: Debes ajustar los IDs según tu base de datos:
     * - idPedido: ID de un pedido existente en tu BD
     * - uuidProducto: UUID de un producto existente
     * - idsVuelo: IDs de vuelos existentes en tu BD
     * 
     * Para encontrar IDs válidos, ejecuta estas queries:
     * SELECT id FROM pedidos LIMIT 3;
     * SELECT uuid FROM productos LIMIT 1;
     * SELECT id FROM vuelos LIMIT 5;
     */
    private SalidaProblemaPlanificacion crearProgramacionesHardcodeadas(EntradaProblemaPlanificacion datos) {
        System.out.println("🧪 Creando programaciones HARDCODEADAS para testing...");
        
        List<Programacion> programaciones = new ArrayList<>();
        
        // Obtener datos reales de la entrada
        EstadoGlobal estado = datos.getEstadoGlobalCopia();
        
        // ✅ PROGRAMACIÓN 1: Tomar primer pedido y primer vuelo disponibles
        if (!estado.getPedidos().isEmpty() && !estado.getVuelos().isEmpty()) {
            try {
                Pedido primerPedido = estado.getPedidos().values().iterator().next();
                Vuelo primerVuelo = estado.getVuelos().values().iterator().next();
                
                // Generar UUID aleatorio para el producto (se creará dinámicamente)
                UUID uuidProducto = UUID.randomUUID();
                
                LinkedList<Long> ruta1 = new LinkedList<>();
                ruta1.add(primerVuelo.getId());
                
                Programacion prog1 = new Programacion(
                    primerPedido.getId(),
                    uuidProducto,
                    ruta1
                );
                programaciones.add(prog1);
                
                System.out.println("✅ Programación 1: Pedido=" + primerPedido.getId() + 
                                 ", Vuelo=" + primerVuelo.getId() + 
                                 ", Producto=" + uuidProducto);
            } catch (Exception e) {
                System.out.println("⚠️ Error creando programación 1: " + e.getMessage());
            }
        }
        
        // ✅ PROGRAMACIÓN 2: Si hay más pedidos y vuelos, crear otra
        if (estado.getPedidos().size() > 1 && estado.getVuelos().size() > 1) {
            try {
                Iterator<Pedido> pedidosIter = estado.getPedidos().values().iterator();
                pedidosIter.next(); // Skip primero
                Pedido segundoPedido = pedidosIter.next();
                
                Iterator<Vuelo> vuelosIter = estado.getVuelos().values().iterator();
                vuelosIter.next(); // Skip primero
                Vuelo segundoVuelo = vuelosIter.next();
                
                UUID uuidProducto2 = UUID.randomUUID();
                
                LinkedList<Long> ruta2 = new LinkedList<>();
                ruta2.add(segundoVuelo.getId());
                
                Programacion prog2 = new Programacion(
                    segundoPedido.getId(),
                    uuidProducto2,
                    ruta2
                );
                programaciones.add(prog2);
                
                System.out.println("✅ Programación 2: Pedido=" + segundoPedido.getId() + 
                                 ", Vuelo=" + segundoVuelo.getId() + 
                                 ", Producto=" + uuidProducto2);
            } catch (Exception e) {
                System.out.println("⚠️ Error creando programación 2: " + e.getMessage());
            }
        }
        
        System.out.println("🧪 Total programaciones hardcodeadas: " + programaciones.size());
        
        SalidaProblemaPlanificacion salida = new SalidaProblemaPlanificacion();
        salida.setProgramaciones(programaciones);
        return salida;
    }
}
