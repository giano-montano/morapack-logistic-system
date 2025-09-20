package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.history.Revision;
import org.springframework.data.history.Revisions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoAuditRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {
    private final AlmacenRepository almacenRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoAuditRepository pedidoAuditRepository;

    @Override
    @Transactional
    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto) {
        Pedido pedidoAGuardar = dto.toEntity();
        // hasta la primer planificación (estado programado) no se sabrá si tenemos 2 o 3 días para enttregar el pedido como máximo.
        // más lógica de negocio si la hubiera...
        Long idAlmacen = dto.idAlmacenDestino();
        if (idAlmacen != null) {
            // getReferenceById devuelve un proxy gestionado (no fuerza SELECT)
            Almacen almacenRef = almacenRepository.getReferenceById(idAlmacen);
            pedidoAGuardar.setAlmacenDestino(almacenRef);
        } else {
            throw new IllegalArgumentException("idAlmacenDestino es requerido");
        }
        pedidoAGuardar.setCantidadProductosEntregados(0);
        System.out.println("pedidoAGuardar: " + pedidoAGuardar);
        Pedido pedidoGuardado = pedidoRepository.save(pedidoAGuardar);
        return PedidoListadoDTO.fromEntity(pedidoGuardado);
    }

    @Override
    @Transactional
    public PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto) {
        // 1. Cargar la entidad a actualizar (estado gestionado)
        Pedido actual = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado: " + idPedido));

        // 2. Mapear cambios simples (solo si vienen)
        if (dto.cantProductos() != null) {
            actual.setCantidadProductosPedidos(dto.cantProductos());
        }
//        if (dto.instanteRegistro() != null) {
//            actual.setInstanteRegistro(dto.instanteRegistro());
//        }

        // 3. Resolver y setear la relación Almacen (si viene id distinto)
        Long nuevoIdAlmacen = dto.idAlmacenDestino();
        if ( nuevoIdAlmacen != null && !nuevoIdAlmacen.equals(actual.getAlmacenDestino().getId())) {
            // Validamos existencia: findById para dar un mensaje de error claro si no existe
            Almacen almacen = almacenRepository.findById(nuevoIdAlmacen)
                    .orElseThrow(() -> new EntityNotFoundException("Almacén no encontrado: " + nuevoIdAlmacen));
            actual.setAlmacenDestino(almacen);
        }

        // 4. Persistir (merge ocurre automáticamente en contexto transaccional)
        Pedido guardado = pedidoRepository.save(actual);

        // 5. Mapear a DTO de salida
        return PedidoListadoDTO.fromEntity(guardado);
    }

    @Override
    public List<Revision<Integer, Pedido>> listarRevisionesPedidosPorIdPedido(Long idPedido){

        List<Revision<Integer, Pedido>> revisiones = pedidoAuditRepository.findRevisions(idPedido).stream().toList();
        System.out.println("revisiones: " + revisiones);
        return revisiones;
    }




    // Mantén transacción abierta mientras mapeas para poder acceder a proxies con seguridad
    @Transactional(readOnly = true)
    public List<PedidoRevisionDto> getAllRevisions(Long pedidoId) {
        Revisions<Integer, Pedido> revisions = pedidoRepository.findRevisions(pedidoId);

        return revisions.stream()
                .map(this::toDtoFromRevision)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber) {
        Optional<Revision<Integer, Pedido>> opt = pedidoRepository.findRevision(pedidoId, revisionNumber);
        return opt.map(this::toDtoFromRevision).orElse(null);
    }

    private PedidoRevisionDto toDtoFromRevision(Revision<Integer, Pedido> rev) {
        Pedido p = rev.getEntity();

        // Extraer almacen destino de forma segura sin forzar carga entera:
        PedidoRevisionDto.AlmacenRefDto almacenDto = null;
        if (p.getAlmacenDestino() != null) {
            Object almacenObj = p.getAlmacenDestino();
            if (almacenObj instanceof HibernateProxy proxy) {
                // extraer id del proxy sin inicializar la entidad completa
                Object id = proxy.getHibernateLazyInitializer().getIdentifier();
                Long almacenId = (id instanceof Number) ? ((Number) id).longValue() : null;
                // Nombre no disponible sin inicializar -> dejar null o cargar si lo deseas
                almacenDto = new PedidoRevisionDto.AlmacenRefDto(almacenId, null);
            } else {
                // entidad inicializada: podéis leer campos seguros
                almacenDto = new PedidoRevisionDto.AlmacenRefDto(p.getAlmacenDestino().getId(),
                        p.getAlmacenDestino().getCodigoCiudadEn4Letras());
            }
        }

        Number revNum = rev.getRequiredRevisionNumber();
        // revision instant (si está presente)
        java.time.Instant revInst = rev.getRequiredRevisionInstant();
        String username = null;
        try {
            // si tenés un revision entity custom, metadatos pueden contener username
            Object metadata = rev.getMetadata();
            // en Spring Data's RevisionMetadata puede extrar datos, pero depende de tu config
            // Dejarlo null si no lo tenés
        } catch (Exception ignored){}

        String revType = rev.getMetadata() != null ? rev.getMetadata().toString() : null;

        return new PedidoRevisionDto(
                p.getId(),
                p.getCantidadProductosPedidos(),
                p.getCantidadProductosEntregados(),
                p.getInstanteRegistro(),
                almacenDto,
                revNum,
                revInst,
                username,
                revType
        );
    }




}
