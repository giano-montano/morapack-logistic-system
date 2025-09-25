package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.GraspAndGeneticAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.LoggedHeuristicAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.PlanificationStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.TabuSearchAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.entities.*;
import pe.edu.pucp.inf.pddsbackend.repositories.*;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PlanificacionServiceImpl implements PlanificacionService {

    private final SimulacionRepository simulacionRepository;
    private PlanificationStrategy planificationStrategy; // podría variar la estrategia con el tiempo?
    private final VueloRepository vueloRepository;
    private final AlmacenRepository almacenRepository;
    private final PlanificacionRepository planificacionRepository;
    private final RutaProgramadaRepository rutaProgramadaRepository;
    private final RutaProgramadaXVueloRepository rutaProgramadaXVueloRepository;
    private final PedidoRepository pedidoRepository;

    // Inyectar estrategias como beans para evitar instanciarlas con `new`
    private final LoggedHeuristicAlgorithmStrategy loggedHeuristicAlgorithmStrategy;
    private final GraspAndGeneticAlgorithmStrategy graspAndGeneticAlgorithmStrategy;
    private final TabuSearchAlgorithmStrategy tabuSearchAlgorithmStrategy;

    private void escogerEstrategiaInicial(EstrategiaFija estrategiaFija){
        switch(estrategiaFija){
            case AUTO -> planificationStrategy = loggedHeuristicAlgorithmStrategy;
            case PROFUNDA ->  planificationStrategy = graspAndGeneticAlgorithmStrategy;
            case RAPIDA ->   planificationStrategy = tabuSearchAlgorithmStrategy;
        }
    }

    @Transactional
    @Override
    public PlanificacionResponseDTO realizarPlanificacionDePedidosActualesConPersistencia(RealizarPlanificacionDTO params) throws Exception {

        SalidaProblemaPlanificacion solucionAlgoritmo = realizarPlanificacionConDatosDeBD(params);
        // Persistir la solución generada por el algoritmo en la BD
        List<RutaProgramada>enviosProgramados= persistirSolucionYRetornarRutas(solucionAlgoritmo, params.getIdSimulacion());

        PlanificacionResponseDTO response = mapearSolucionAResponse(solucionAlgoritmo, enviosProgramados);

        return response;
    }

    @Override
    public SalidaProblemaPlanificacion realizarPlanificacionConDatosDeBD(RealizarPlanificacionDTO params) throws Exception {
        EntradaProblemaPlanificacion dataEntradaAlgoritmo =  obtenerDatosParaAlgoritmo(params);
        SalidaProblemaPlanificacion solucionAlgoritmo =realizarPlanificacionConEntrada(params, dataEntradaAlgoritmo);
        return solucionAlgoritmo;
    }

    @Override
    public SalidaProblemaPlanificacion realizarPlanificacionConEntrada(
            RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo) throws Exception {
        escogerEstrategiaInicial(params.getEstrategiaFija()); // la elección de estrategia puede ser derivada
        // a una clase o método aun más especializado que use por ejemplo, el EntradaProblemaPlanificacion para
        // determinar mejor la estrategia si es que el usuario puso EstrategiaFija.AUTO
        long startTime = System.nanoTime(); // Record start time in nanoseconds
        SalidaProblemaPlanificacion solucionAlgoritmo = planificationStrategy.planificar(dataEntradaAlgoritmo);
        long endTime = System.nanoTime(); // Record end time in nanoseconds
        long duration = (endTime - startTime) /  1000000; // Calculate duration in seconds, no nanoseconds
        solucionAlgoritmo.setTiempoEjecucionMs(duration);
        System.out.println("A ver esa solución!:\n"+solucionAlgoritmo);
        obtenerFitnessDeSolucion(solucionAlgoritmo, dataEntradaAlgoritmo);
        return solucionAlgoritmo;
    }

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    @Override
    public EntradaProblemaPlanificacion obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params){


            HashMap<Long, AlmacenParaAlgoritmo> almacenes = obtenerAlmacenesParaAlgoritmo();
            HashMap<Long, VueloParaAlgoritmo> vuelos = obtenerVuelosParaAlgoritmo();
            HashMap<Long, PedidoParaAlgoritmo> pedidos = obtenerPedidosParaAlgoritmo();


        return EntradaProblemaPlanificacion.builder()
                .almacenes(almacenes)
                .pedidos(pedidos)
                .vuelos(vuelos)
                .seed(params.getSeed())
                .parametrosOpcionalesPersonalizados(params.getParametros())
                .build();
    }

    private HashMap<Long, PedidoParaAlgoritmo> obtenerPedidosParaAlgoritmo() {
        List<Pedido> pedidos = pedidoRepository.listarPedidosNoAtendidosCompletamente();
        HashMap<Long, PedidoParaAlgoritmo> resultado = new HashMap<>(
                pedidos.stream().collect(
                        Collectors.toMap(Pedido::getId, PedidoParaAlgoritmo::desdeEntidad)
                )
        );

        return resultado;
    }

    /**
     * Construye un mapa idAlmacen -> AlmacenParaAlgoritmo
     * (versión simple: consulta por cada almacén las listas de ids). Se puede BATCHEAR para más eficiencia
     */
    private HashMap<Long, AlmacenParaAlgoritmo> obtenerAlmacenesParaAlgoritmo() {
        List<Almacen> almacenesBD = almacenRepository.findAlmacenByActivoTrue();
        HashMap<Long, AlmacenParaAlgoritmo> resultado = new HashMap<>(almacenesBD.size());
        for (Almacen a : almacenesBD) {
            // obtener ids (pueden venir vacíos)
            List<Long> idsVuelosDestino = vueloRepository.findIdByActivoTrueAndAlmacenDestino_Id(a.getId());
            List<Long> idsVuelosOrigen  = vueloRepository.findIdByActivoTrueAndAlmacenOrigen_Id(a.getId());
            List<Long> idsPedidos       = pedidoRepository.findIdByAlmacenDestino_Id(a.getId());

            // convertir a HashSet<Long>
            HashSet<Long> setVuelosDest = idsVuelosDestino == null ? new HashSet<>() : new HashSet<>(idsVuelosDestino);
            HashSet<Long> setVuelosOrig = idsVuelosOrigen  == null ? new HashSet<>() : new HashSet<>(idsVuelosOrigen);
            HashSet<Long> setPedidos    = idsPedidos       == null ? new HashSet<>() : new HashSet<>(idsPedidos);

            AlmacenParaAlgoritmo apa = AlmacenParaAlgoritmo.desdeEntidadYListas(
                    a, setVuelosDest, setVuelosOrig, setPedidos
            );

            resultado.put(apa.getId(), apa);
        }

        return resultado;
    }

    private HashMap<Long, VueloParaAlgoritmo> obtenerVuelosParaAlgoritmo(){
        List<Vuelo> vuelos = vueloRepository.findByActivoTrueAndFechaHoraInicioUtcAfter(Instant.now());
        HashMap<Long, VueloParaAlgoritmo> resultado = new HashMap<>(
                vuelos.stream().collect(
                Collectors.toMap(Vuelo::getId, VueloParaAlgoritmo::desdeEntidad)
                )
        );

        return resultado;
    }


    public double obtenerFitnessDeSolucion(SalidaProblemaPlanificacion salidaProblemaPlanificacion, EntradaProblemaPlanificacion entradaProblemaPlanificacion) {
        Double fitness =CalculadorDeFitness.calcularFitnessSalidaProblema(salidaProblemaPlanificacion, entradaProblemaPlanificacion);
        salidaProblemaPlanificacion.setFitness(fitness);
        return fitness;
    }



    /**
     * Persiste la solución (lista de rutas generadas por el algoritmo) creando:
     *  - una Planificacion nueva
     *  - una RutaProgramada por cada RutaProgramadaParaAlgoritmo (vinculada al Pedido)
     *  - una RutaProgramadaXVuelo por cada vuelo en la ruta (en el mismo orden)
     *
     * Además marca el Pedido como PROGRAMADO y actualiza la capacidad ocupada del Vuelo
     * incrementándola por la cantidad programada en la ruta (ver notas).
     *
     * La operación es transaccional: ante cualquier error se hace rollback.
     */
    @Transactional
    public List<RutaProgramada> persistirSolucionYRetornarRutas(SalidaProblemaPlanificacion solucion, Long idSimulacion) {
        if (solucion == null || solucion.getRutasProgramadasParaSatisfacerTodoPedido() == null) {
            return List.of();
        }
        Simulacion simulacion=null;
        if(idSimulacion != null) { simulacion = simulacionRepository.getReferenceById(idSimulacion);}
        // 1) Crear entidad Planificacion (registro de esta ejecución)
        Planificacion planif = Planificacion.builder()
                .fechaHoraFinPlanif(Instant.now())
                .colapsado(false)
                .reprogramado(false)
                .fitnessConseguido(solucion.getFitness())
                .huboErrorEjecucion(false)
                .razonErrorEjecucion(solucion.getError())
                .duracionEjecucionAlgoritmo(solucion.getTiempoEjecucionMs())
                .simulacion(simulacion)
                .build();
        planif = planificacionRepository.save(planif);
        // AUNQUE ESTÉ COLAPSADO LO GUARDAMOS, PARA CURIOSEAR; PERO TENERLO EN CUENTA!!!
        List<RutaProgramada> rutasPersistidas = new ArrayList<>();

        // iterar rutas generadas por el algoritmo
        for (RutaProgramadaParaAlgoritmo rutaAlgo : solucion.getRutasProgramadasParaSatisfacerTodoPedido()) {

            if (rutaAlgo == null) continue;

            long idPedido = rutaAlgo.getIdPedidoAsociado();
            int cantidad = rutaAlgo.getCantidadTotalOParcial();

            if (cantidad <= 0) {
                // saltar rutas vacías (o podrías lanzar excepción si esto es indicio de bug)
                continue;
            }

            // Obtener pedido (lanza EntityNotFoundException si no existe)
            Pedido pedido = pedidoRepository.findById(idPedido)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado id=" + idPedido));

            // 2) Crear RutaProgramada y asociarla a planificacion + pedido
            RutaProgramada rutaEntidad = new RutaProgramada();
            rutaEntidad.setPedido(pedido);
            rutaEntidad.setCantidadTotalOParcial(cantidad);
            rutaEntidad.setPlanificacion(planif);
            RutaProgramada rutaGuardada = rutaProgramadaRepository.save(rutaEntidad);

            // 3) Crear RutaProgramadaXVuelo por cada vuelo id (mantener orden)
            LinkedList<Long> idsVuelos = rutaAlgo.getIdsVuelosEnOrden();
            if (idsVuelos != null) {
                byte orden = 0;
                for (Long idVuelo : idsVuelos) {
                    if (idVuelo == null) continue;

                    Vuelo vuelo = vueloRepository.findById(idVuelo)
                            .orElseThrow(() -> new EntityNotFoundException("Vuelo no encontrado id=" + idVuelo));

                    // opcional: comprobar que vuelo.activo && !vuelo.cancelado
                    if (Boolean.FALSE.equals(vuelo.getActivo()) || Boolean.TRUE.equals(vuelo.getCancelado())) {
                        throw new IllegalStateException("Vuelo no disponible para programar (inactivo o cancelado) id=" + idVuelo);
                    }

                    // actualizar capacidad ocupada del vuelo (reserva)
                    int nuevaOcupada = Math.addExact(vuelo.getCapacidadOcupada(), cantidad);
                    if (nuevaOcupada > vuelo.getCapacidadMaxima()) {
                        // si supera capacidad, lanzamos excepción para abortar toda la transacción
                        throw new IllegalStateException("Capacidad de vuelo excedida (vueloId=" + idVuelo +
                                " capacidadMaxima=" + vuelo.getCapacidadMaxima() +
                                " ocupadoAntes=" + vuelo.getCapacidadOcupada() +
                                " intentoReservar=" + cantidad + ")");
                    }
//                    vuelo.setCapacidadOcupada(nuevaOcupada); // NOOOOOOOOOOOOOOOOO
                    vueloRepository.save(vuelo);

                    // crear entrada de asociación (persistir)
                    RutaProgramadaXVuelo rpXV = new RutaProgramadaXVuelo();
                    rpXV.setRutaProgramada(rutaGuardada);
                    rpXV.setVuelo(vuelo);
                    rpXV.setOrden(orden);
                    rutaProgramadaXVueloRepository.save(rpXV);

                    orden++;
                }
            }

            // 4) Marcar pedido como PROGRAMADO (no alterar cantidad entregada)
//            pedido.setEstado(EstadoPedido.PROGRAMADO);
            pedidoRepository.save(pedido);
            rutasPersistidas.add(rutaGuardada);
        }

        // Devolver lista de rutas persistidas (con id y relaciones)
        return rutasPersistidas;
    }

    @Transactional(readOnly = true)
    protected PlanificacionResponseDTO mapearSolucionAResponse(SalidaProblemaPlanificacion solucion,
                                                               List<RutaProgramada> rutasPersistidas) {
        // defensiva: si no hay nada, devolver vacío
        if ((solucion == null || solucion.getRutasProgramadasParaSatisfacerTodoPedido() == null)
                && (rutasPersistidas == null || rutasPersistidas.isEmpty())) {
            return new PlanificacionResponseDTO(null, null, false, null, null,Collections.emptyList(), false, null);
        }

        // datos de planificacion (si existen)
        Long idPlanificacion = null;
        Instant fechaHoraFinPlanif = null;
        Boolean colapsado = null;
        Boolean conError = false;
        Double fitness = solucion.getFitness(); // no llenar por ahora

        if (rutasPersistidas != null && !rutasPersistidas.isEmpty()) {
            Planificacion plan = rutasPersistidas.get(0).getPlanificacion();
            if (plan != null) {
                idPlanificacion = plan.getId();
                fechaHoraFinPlanif = plan.getFechaHoraFinPlanif();
                colapsado = plan.getColapsado();
                conError = plan.getHuboErrorEjecucion() != null && plan.getHuboErrorEjecucion();
                fitness = plan.getFitnessConseguido(); // puede quedar null si no se quiere mostrar
            }
        }

        List<RutaProgramadaSolucionDTO> rutasDto = new ArrayList<>();
        if (rutasPersistidas != null) {
            for (RutaProgramada rp : rutasPersistidas) {
                if (rp == null) continue;

                // Pedido
                Pedido pedidoEntidad = rp.getPedido();
                Long idPedido = pedidoEntidad != null ? pedidoEntidad.getId() : null;
                Integer cantidadTotalPedido = pedidoEntidad != null ? pedidoEntidad.getCantidadProductosPedidos() : 0;
                Integer cantidadAtendiendose = rp.getCantidadTotalOParcial() != null ? rp.getCantidadTotalOParcial() : 0;

                // Almacen destino del pedido
                Almacen almacenDestino = null;
                if (pedidoEntidad != null) {
                    almacenDestino = pedidoEntidad.getAlmacenDestino();
                }
                AlmacenSolucionDTO almacenDto = null;
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
                        cantidadAtendiendose,
                        almacenDto
                );

                // Vuelos asociados (ordenados)
                List<RutaProgramadaXVuelo> enlaces = rutaProgramadaXVueloRepository.findByRutaProgramadaIdOrderByOrden(rp.getId());
                List<VueloSolucionDTO> vuelosDto = new ArrayList<>();
                if (enlaces != null) {
                    byte posFallback = 0;
                    for (RutaProgramadaXVuelo enlace : enlaces) {
                        if (enlace == null) continue;
                        Vuelo v = enlace.getVuelo();
                        if (v == null) continue;

                        Almacen origen = v.getAlmacenOrigen();
                        Almacen destino = v.getAlmacenDestino();

                        VueloSolucionDTO vDto = new VueloSolucionDTO();
                        vDto.setIdVuelo(v.getId());
                        vDto.setIdAlmacenOrigen(origen != null ? origen.getId() : null);
                        vDto.setIdAlmacenDestino(destino != null ? destino.getId() : null);

                        vDto.setCodigoAeropuertoOrigenEn4Siglas(origen != null ? origen.getCodigoAeropuertoEn4Letras() : null);
                        vDto.setCodigoAeropuertoDestinoEn4Siglas(destino != null ? destino.getCodigoAeropuertoEn4Letras() : null);

                        vDto.setCiudadOrigenEn4Siglas(origen != null ? origen.getCodigoCiudadEn4Letras() : null);
                        vDto.setCiudadDestinoEn4Siglas(destino != null ? destino.getCodigoCiudadEn4Letras() : null);

                        // orden: preferir campo orden de la entidad, si no usar fallback por posición
                        Byte orden = null;
                        try {
                            orden = enlace.getOrden();
                        } catch (Exception ex) {
                            orden = ++posFallback;
                        }
                        vDto.setOrden(orden);

                        vuelosDto.add(vDto);
                    }
                }

                // construir RutaProgramadaSolucionDTO
                RutaProgramadaSolucionDTO rutaDto = new RutaProgramadaSolucionDTO();
                rutaDto.setIdRuta(rp.getId());
                rutaDto.setPedido(pedidoSolDto);
                rutaDto.setVuelosDeRutaParaAtenderPedido(vuelosDto);

                rutasDto.add(rutaDto);
            }
        }

        // si colapsado es null, normalizamos a false
        if (colapsado == null) colapsado = false;

        return new PlanificacionResponseDTO(
                idPlanificacion,
                fechaHoraFinPlanif,
                solucion.isColapsado(), // tranqui, no dará nulo...
                /*fitnessConseguido*/ fitness,
                solucion.getTiempoEjecucionMs(),
                rutasDto,
                conError,
                solucion.getError()
        );
    }


}
