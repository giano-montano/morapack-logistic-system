package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.GraspAndGeneticAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.LoggedHeuristicAlgorithmStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.PlanificationStrategy;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoPedido;
import pe.edu.pucp.inf.pddsbackend.models.entities.*;
import pe.edu.pucp.inf.pddsbackend.repositories.*;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
@RequiredArgsConstructor
public class PlanificacionServiceImpl implements PlanificacionService {

    private PlanificationStrategy planificationStrategy; // podría variar la estrategia con el tiempo?
    private final VueloRepository vueloRepository;
    private final AlmacenRepository almacenRepository;
    private final EnvioProgramadoRepository envioProgramadoRepository;
    private final EnvioProgramadoVueloRepository envioProgramadoVueloRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoEnvioProgramadoRepository pedidoEnvioProgramadoRepository;
    private final EntityManager em;
    // ver planificaciones pasadas sería relevante para el algo.

    private void escogerEstrategiaInicial(EstrategiaFija estrategiaFija){
        switch(estrategiaFija){
            case AUTO -> planificationStrategy = new LoggedHeuristicAlgorithmStrategy();
            case PROFUNDA ->  planificationStrategy = new GraspAndGeneticAlgorithmStrategy();
            case RAPIDA ->   planificationStrategy = new GraspAndGeneticAlgorithmStrategy();
        }
    }

    @Transactional
    @Override
    public PlanificacionResponseDTO realizarPlanificacionDePedidosActuales(RealizarPlanificacionDTO params) {

        escogerEstrategiaInicial(params.getEstrategiaFija()); // la elección de estrategia puede ser derivada
        // a una clase o método aun más especializado que use por ejemplo, el PlanificationProblemInput para
        // determinar mejor la estrategia si es que el usuario puso EstrategiaFija.AUTO

        PlanificationProblemInput dataEntradaAlgoritmo =  obtenerDatosParaAlgoritmo();
        PlanificationSolutionOutput solucionAlgoritmo = planificationStrategy.planificar(dataEntradaAlgoritmo);

        //... poner los envíos programados en BD y hacer valer la solución.
        // Persistir la solución generada por el algoritmo en la BD
        List<EnvioProgramado>enviosProgramados=persistirSolucionYRetornarEnvios(solucionAlgoritmo);

        PlanificacionResponseDTO response = mapSolutionToResponse(solucionAlgoritmo, enviosProgramados);

        return response;
    }

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    private PlanificationProblemInput obtenerDatosParaAlgoritmo(){
        //ineficiente pero probemos
        //Podemos restringir de por sí la data para el algoritmo,
        //de manera que le ahorramos ver opciones inválidas, ejm:
        //almacenes llenos, vuelos terminados o ya cancelados, pedidos ya enviados
        List<AlmacenForAlgorithm> almacenesParaAlgoritmo =
                almacenRepository.findAlmacenesNoLlenosOInfinitos().stream().map(
                        AlmacenForAlgorithm::createFromEntity
                ).toList();
        List<VueloForAlgorithm> vuelosParaAlgoritmo =
                vueloRepository.findVuelosPorDespegarOEnCurso().stream().map(
                        VueloForAlgorithm::createFromEntity
                ).toList();
        List<PedidoForAlgorithm> pedidosParaAlgoritmo =
                pedidoRepository.findPedidosAunNoProgramados().stream().map(
                        PedidoForAlgorithm::createFromEntity
                ).toList();
        System.out.println("Almacenes: " + almacenesParaAlgoritmo);
        System.out.println("Vuelos: " + vuelosParaAlgoritmo);
        System.out.println("Pedidos: " + pedidosParaAlgoritmo);
        System.out.println("Comenzamos");
        // Como estamos agarrando pedidos por programar, creo que no es necesario obtener los envíos
        // de antes, ya que esos ya habrían hecho que los pedidos figuren con estado "PROGRAMADO"
        return PlanificationProblemInput.builder()
                .almacenes(almacenesParaAlgoritmo)
                .pedidos(pedidosParaAlgoritmo)
                .vuelos(vuelosParaAlgoritmo)
                .build();
    }





    /**
     * Persiste la solución del algoritmo: crea EnviosProgramados, enlaza Vuelo(s) y Pedido(s),
     * y actualiza capacidades de vuelos/almacenes y cantidades entregadas en pedidos.
     *
     * Asegura atomicidad con @Transactional y usa lock PESSIMISTIC_WRITE al actualizar pedidos.
     */
    @Transactional
    public List<EnvioProgramado>  persistirSolucionYRetornarEnvios(PlanificationSolutionOutput solucion) {
        List<EnvioProgramado> persistidos = new ArrayList<>();
        if (solucion == null || solucion.getEnvios() == null) return persistidos;

        for (EnvioSolution envioSol : solucion.getEnvios()) {
            // 1) Crear EnvíoProgramado
            EnvioProgramado envioEntity = EnvioProgramado.builder()
                    .cantProductosAEnviar( /*envioSol.getCantProductos()*/
                            envioSol.getPedidosAAtenderTotalOParcialmente().stream()
                                    .mapToInt(PedidoSolution::getCantidadASerAtendidaDelPedido) // Map to a DoubleStream of attribute values
                                    .sum()
                            )
                    .cumplido(false)
                    .reprogramado(false)
                    .build();
            envioEntity = envioProgramadoRepository.save(envioEntity);

            // 2) Si no hay vuelos, guardamos el envío vacío y saltamos (defensivo)
            List<Long> idsVuelos = envioSol.getIdsVuelosATomar() == null ? Collections.emptyList() : envioSol.getIdsVuelosATomar();

            // 3) Para cada vuelo en la ruta, crear EnvioProgramadoVuelo y reservar capacidad en vuelo
            int orden = 1;
            for (Long vueloId : idsVuelos) {
                // persist enlace vuelo ↔ envío
                Vuelo vuelo = vueloRepository.findById(vueloId)
                        .orElseThrow(() -> new IllegalStateException("Vuelo no encontrado id=" + vueloId));

                EnvioProgramadoVuelo epv = EnvioProgramadoVuelo.builder()
                        .vueloOEscalaQueConformaEnvio(vuelo)
                        .envioQueVueloSatisface(envioEntity)
                        .ordenDelVueloEnEnvio(orden)
                        .build();
                envioProgramadoVueloRepository.save(epv);

                // reservar capacidad en vuelo: incrementar capacidadOcupadaProductos
                // Usamos el delta = total products del shipment (cada leg traslada toda la carga)
                Integer delta = envioSol.getCantProductos() == null ? 0 : envioSol.getCantProductos();
                if (delta > 0) {
                    int updated = vueloRepository.incrementarCapacidadOcupada(vueloId, delta);
                    if (updated == 0) {
                        // fallback: si JPQL update no aplicó (por ejemplo, condición), cargamos entidad y actualizamos
                        vuelo.setCapacidadOcupadaProductos(
                                (vuelo.getCapacidadOcupadaProductos() == null ? 0 : vuelo.getCapacidadOcupadaProductos()) + delta);
                        vueloRepository.save(vuelo);
                    }
                }
                orden++;
            }

            // 4) Si hay un vuelo inicial, deducir origen y decrementar stock del almacen origen si no es infinito
            if (!idsVuelos.isEmpty()) {
                Long firstVueloId = idsVuelos.get(0);
                Vuelo firstVuelo = vueloRepository.findById(firstVueloId)
                        .orElseThrow(() -> new IllegalStateException("Vuelo inicial no encontrado id=" + firstVueloId));
                Almacen origen = firstVuelo.getAlmacenOrigen();
                // si almacén no es infinito, decrementar su capacidadOcupada (stock disponible)
                if (origen != null && Boolean.FALSE.equals(origen.getEsInfinito())) {
                    Integer delta = envioSol.getCantProductos() == null ? 0 : envioSol.getCantProductos();
                    if (delta > 0) {
                        int updated = almacenRepository.decrementarCapacidadOcupadaSiFinito(origen.getId(), delta);
                        if (updated == 0) {
                            // fallback: actualizar entidad en memoria
                            origen.setCapacidadOcupada(Math.max(0, (origen.getCapacidadOcupada() == null ? 0 : origen.getCapacidadOcupada()) - delta));
                            almacenRepository.save(origen);
                        }
                    }
                }
            }

            // 5) Para cada PedidoSolution: lock pedido, actualizar entregados y persistir PedidoEnvioProgramado
            List<PedidoSolution> pedidosAtendidos = envioSol.getPedidosAAtenderTotalOParcialmente() == null
                    ? Collections.emptyList() : envioSol.getPedidosAAtenderTotalOParcialmente();

            for (PedidoSolution ps : pedidosAtendidos) {
                Long pedidoId = ps.getId();
                Integer qty = ps.getCantidadASerAtendidaDelPedido() == null ? 0 : ps.getCantidadASerAtendidaDelPedido();

                if (qty <= 0) continue;

                // lock pedido para evitar race conditions concurrentes
                Pedido pedido = em.find(Pedido.class, pedidoId, LockModeType.PESSIMISTIC_WRITE);
                if (pedido == null) {
                    // no existe pedido -> saltear pero loggear
                    // logger.warn("Pedido no encontrado id=" + pedidoId);
                    continue;
                }

                int nuevosEntregados = (pedido.getCantidadProductosEntregados() == null ? 0 : pedido.getCantidadProductosEntregados()) + qty;
                pedido.setCantidadProductosEntregados(nuevosEntregados);

                // si con esto se completa el pedido, marcar atendidoCompletamente (aunque físicamente la entrega será en futuro)
                if (nuevosEntregados >= (pedido.getCantidadProductosTotal() == null ? 0 : pedido.getCantidadProductosTotal())) {
                    pedido.setAtendidoCompletamente(true);
                    // como se está planificando, establecemos estado a PROGRAMADO
                    pedido.setEstado(EstadoPedido.PROGRAMADO);
                } else {
                    // si aún no se completa, también marcamos PROGRAMADO (parte planificada)
                    pedido.setEstado(EstadoPedido.PROGRAMADO);
                }
                pedidoRepository.save(pedido);

                // crear asociación PedidoEnvioProgramado
                PedidoEnvioProgramado pep = PedidoEnvioProgramado.builder()
                        .envioQueSatisfaceParteOTodoPedido(envioEntity)
                        .pedidoQueElEnvioEstaAtendiendo(pedido)
                        // como simplificación, asigno orden del vuelo que cumple la entrega al tamaño de la ruta
//                        .ordenDelVueloEnEnvioParaAtenderPedido(Math.max(1, idsVuelos.size()))
                        .cantidadDeProductosDelPedidoAtendiendose(qty)
                        .build();
                pedidoEnvioProgramadoRepository.save(pep);
            }
            // Guardar en la lista de persistidos (mismo orden)
            persistidos.add(envioEntity);
        } // end for envios
        return persistidos;
    } // end persistirSolucion


    // ----------------- mapSolutionToResponse -----------------
    private PlanificacionResponseDTO mapSolutionToResponse(PlanificationSolutionOutput solucion, List<EnvioProgramado> enviosPersistidos) {
        List<EnvioSolucionPlanificacionDTO> enviosDto = new ArrayList<>();
        if (solucion == null || solucion.getEnvios() == null) {
            return new PlanificacionResponseDTO(enviosDto);
        }

        List<EnvioSolution> enviosSol = solucion.getEnvios();
        for (int i = 0; i < enviosSol.size(); i++) {
            EnvioSolution es = enviosSol.get(i);
            Long persistedId = null;
            Integer cantidadProductosTotalCalculada = null;
            if (i < enviosPersistidos.size()) {
                persistedId = enviosPersistidos.get(i).getId();
                cantidadProductosTotalCalculada = enviosPersistidos.get(i).getCantProductosAEnviar();
            }

            // Build list of VueloDTOs (usamos almacenOrigen.codigoCiudadEn4Letras)
            List<VueloDTO> vuelosDto = new ArrayList<>();
            List<Long> idsVuelos = es.getIdsVuelosATomar() == null ? Collections.emptyList() : es.getIdsVuelosATomar();
            for (Long vid : idsVuelos) {
                Vuelo vuelo = vueloRepository.findById(vid).orElse(null);
                if (vuelo == null) continue;
                String ciudadOrigen = (vuelo.getAlmacenOrigen() != null ? vuelo.getAlmacenOrigen().getCodigoCiudadEn4Letras() : null);
                String ciudadDestino = (vuelo.getAlmacenDestino() != null ? vuelo.getAlmacenDestino().getCodigoCiudadEn4Letras() : null);
                VueloDTO vdto = new VueloDTO();
                vdto.setId(vuelo.getId());
                vdto.setCiudadOrigenEn4Siglas(ciudadOrigen);
                vdto.setCiudadDestinoEn4Siglas(ciudadDestino);
                vuelosDto.add(vdto);
            }

            // Map pedidos
            List<PedidoDTO> pedidosDto = new ArrayList<>();
            List<PedidoSolution> pedidosSol = es.getPedidosAAtenderTotalOParcialmente() == null ? Collections.emptyList() : es.getPedidosAAtenderTotalOParcialmente();
            for (PedidoSolution ps : pedidosSol) {
                PedidoDTO pdto = new PedidoDTO(ps.getId(), ps.getCantidadASerAtendidaDelPedido());
                pedidosDto.add(pdto);
            }

            EnvioSolucionPlanificacionDTO envioDto = new EnvioSolucionPlanificacionDTO(
                    persistedId,
                    cantidadProductosTotalCalculada,
                    /*es.getCantProductos()*/vuelosDto  ,
                    pedidosDto
            );
            enviosDto.add(envioDto);
        }

        return new PlanificacionResponseDTO(enviosDto);
    }


}
