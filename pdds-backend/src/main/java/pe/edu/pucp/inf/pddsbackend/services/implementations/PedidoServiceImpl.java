package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.history.Revision;
import org.springframework.data.history.Revisions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.*;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;
import pe.edu.pucp.inf.pddsbackend.models.entities.Cliente;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.ClienteRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoAuditRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {
    private final AlmacenRepository almacenRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoAuditRepository pedidoAuditRepository;
    private final ClienteRepository clienteRepository;
//    @Override
//    @Transactional
//    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto) {
//        Pedido pedidoAGuardar = dto.toEntity();
//        // hasta la primer planificación (estado programado) no se sabrá si tenemos 2 o 3 días para enttregar el pedido como máximo.
//        // más lógica de negocio si la hubiera...
//        Long idAlmacen = dto.idAlmacenDestino();
//        if (idAlmacen != null) {
//            // getReferenceById devuelve un proxy gestionado (no fuerza SELECT)
//            Almacen almacenRef = almacenRepository.getReferenceById(idAlmacen);
//            pedidoAGuardar.setAlmacenDestino(almacenRef);
//        } else {
//            throw new IllegalArgumentException("idAlmacenDestino es requerido");
//        }
//        pedidoAGuardar.setCantidadProductosEntregados(0);
//        System.out.println("pedidoAGuardar: " + pedidoAGuardar);
//        Pedido pedidoGuardado = pedidoRepository.save(pedidoAGuardar);
//        return PedidoListadoDTO.fromEntity(pedidoGuardado);
//    }

    @Override
    @Transactional
    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto) {
        // Buscar las entidades Cliente y AlmacenDestino
        Cliente cliente = clienteRepository.findById(dto.idCliente())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con id " + dto.idCliente()));
        Almacen almacenDestino = almacenRepository.findById(dto.idAlmacenDestino())
                .orElseThrow(() -> new EntityNotFoundException("Almacén no encontrado con id " + dto.idAlmacenDestino()));

        // Crear entidad Pedido usando el builder o setters
        Pedido pedido = Pedido.builder()
                .cliente(cliente)                        // asigna el cliente
                .almacenDestino(almacenDestino)         // asigna el almacén
                .cantidadProductosPedidos(dto.cantProductos()) // cantidad
                .instanteRegistro(dto.instanteRegistro() != null ? dto.instanteRegistro() : Instant.now())
                .cantidadProductosEntregados(0)         // inicializamos en 0
                .build();

        // Guardar en la base de datos
        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        // Mapear a DTO y devolver al frontend
        return PedidoListadoDTO.fromEntity(pedidoGuardado);
    }




    @Override
    @Transactional
    public PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto) {
        // 1. Cargar la entidad a actualizar (estado gestionado)
//        Pedido actual = pedidoRepository.findById(idPedido)
//                .orElseThrow(() -> new EntityNotFoundException("Pedido no encontrado: " + idPedido));
//
//        // 2. Mapear cambios simples (solo si vienen)
//        if (dto.cantProductos() != null) {
//            actual.setCantidadProductosPedidos(dto.cantProductos());
//        }
////        if (dto.instanteRegistro() != null) {
////            actual.setInstanteRegistro(dto.instanteRegistro());
////        }
//
//        // 3. Resolver y setear la relación Almacen (si viene id distinto)
//        Long nuevoIdAlmacen = dto.idAlmacenDestino();
//        if ( nuevoIdAlmacen != null && !nuevoIdAlmacen.equals(actual.getAlmacenDestino().getId())) {
//            // Validamos existencia: findById para dar un mensaje de error claro si no existe
//            Almacen almacen = almacenRepository.findById(nuevoIdAlmacen)
//                    .orElseThrow(() -> new EntityNotFoundException("Almacén no encontrado: " + nuevoIdAlmacen));
//            actual.setAlmacenDestino(almacen);
//        }
//
//        // 4. Persistir (merge ocurre automáticamente en contexto transaccional)
//        Pedido guardado = pedidoRepository.save(actual);
//
//        // 5. Mapear a DTO de salida
//        return PedidoListadoDTO.fromEntity(guardado);
        return null;
    }

    @Transactional
    @Override
    public int destruirTodosPedidos(){
        pedidoRepository.deleteAll();
        return 1;
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

    @Override
    @Transactional(readOnly = true)
    public List<PedidoListadoDTO> listarPedidos() {
        List<Pedido> pedidos = pedidoRepository.findAllWithAlmacenAndCliente();
        return pedidos.stream()
                .map(PedidoListadoDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoListadoDTO obtenerPedidoPorId(Long id) {
        Pedido pedido = pedidoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
        return PedidoListadoDTO.fromEntity(pedido);
    }

    @Override
    public void eliminarPedido(Long idPedido) {

    }
    @Override
    public List<Pedido> cargarPedidosMasivos(List<PedidoCargaMasivaDTO> pedidosDTO) {
        List<Pedido> pedidos = new ArrayList<>();

        for (PedidoCargaMasivaDTO dto : pedidosDTO) {
            Pedido pedido = dto.toEntity();

            // Asignar cliente si viene
            if (dto.idCliente() != null) {
                Cliente cliente = clienteRepository.findById(dto.idCliente())
                        .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + dto.idCliente()));
                pedido.setCliente(cliente);
            }

            // Asignar almacén destino (obligatorio)
            Almacen almacen = almacenRepository.findById(dto.idAlmacenDestino())
                    .orElseThrow(() -> new RuntimeException("Almacén no encontrado: " + dto.idAlmacenDestino()));
            pedido.setAlmacenDestino(almacen);

            pedidos.add(pedido);
        }

        return pedidoRepository.saveAll(pedidos);
    }
    @Override
    public List<PedidoCargaMasivaDTO> leerPedidosDesdeExcel(MultipartFile file) {
        List<PedidoCargaMasivaDTO> lista = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean primeraFila = true;
            for (Row row : sheet) {
                if (primeraFila) { primeraFila = false; continue; }

                Long idCliente = null;
                if (row.getCell(0) != null) {
                    if (row.getCell(0).getCellType() == CellType.NUMERIC)
                        idCliente = (long) row.getCell(0).getNumericCellValue();
                    else if (row.getCell(0).getCellType() == CellType.STRING)
                        idCliente = Long.parseLong(row.getCell(0).getStringCellValue());
                }

                Long idAlmacen = null;
                if (row.getCell(1) != null) {
                    if (row.getCell(1).getCellType() == CellType.NUMERIC)
                        idAlmacen = (long) row.getCell(1).getNumericCellValue();
                    else if (row.getCell(1).getCellType() == CellType.STRING)
                        idAlmacen = Long.parseLong(row.getCell(1).getStringCellValue());
                } else {
                    throw new RuntimeException("ID Almacén es obligatorio en la fila " + (row.getRowNum()+1));
                }

                Integer cantProductos = null;
                if (row.getCell(2) != null) {
                    if (row.getCell(2).getCellType() == CellType.NUMERIC)
                        cantProductos = (int) row.getCell(2).getNumericCellValue();
                    else if (row.getCell(2).getCellType() == CellType.STRING)
                        cantProductos = Integer.parseInt(row.getCell(2).getStringCellValue());
                } else {
                    throw new RuntimeException("Cantidad de Productos es obligatoria en la fila " + (row.getRowNum()+1));
                }

                lista.add(new PedidoCargaMasivaDTO(idCliente, idAlmacen, cantProductos));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo el archivo Excel: " + e.getMessage(), e);
        }
        return lista;
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


    // Método principal para PedidoServiceImpl
    @Transactional
    @Override
    public ProcessResult processOrders(InputStream inputStream, int month, int year) {
        final int BATCH_SIZE = 200;
        List<Pedido> batch = new ArrayList<>(BATCH_SIZE);
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int skipped = 0;
        int lineNo = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                lineNo++;
                if (raw == null) continue;
                String line = raw.replaceAll("\\p{C}", "").trim(); // quitar BOM/caracteres de control
                if (line.isEmpty()) continue;

                // ignorar comentarios/separadores
                String low = line.toLowerCase();
                if (low.startsWith("#") || low.startsWith("//") || low.startsWith("pdds") || low.startsWith("***")) continue;

                try {
                    // Split principal por '-' (en ambos formatos hay guiones, y los ":" quedan dentro del primer token)
                    String[] parts = line.split("-");

                    Integer day = null;
                    Integer hour = null;
                    Integer minute = null;
                    String codigoDestino = null;
                    Integer cantidad = null;

                    // Caso: primer token contiene ":" -> formato tipo "D:HH:MM-DEST-QTY-..."
                    if (parts.length >= 2 && parts[0].contains(":")) {
                        String[] timeTokens = parts[0].split(":");
                        if (timeTokens.length < 3) {
                            errors.add("Línea " + lineNo + ": primer token no tiene 3 números (día:hora:min) -> \"" + parts[0] + "\"");
                            skipped++;
                            continue;
                        }
                        day = parseIntSafe(timeTokens[0]);
                        hour = parseIntSafe(timeTokens[1]);
                        minute = parseIntSafe(timeTokens[2]);

                        // según este formato los siguientes tokens son: parts[1]=DEST, parts[2]=QTY, parts[3]=cliente (ignored)
                        if (parts.length < 3) {
                            errors.add("Línea " + lineNo + ": faltan campos después del primer token -> \"" + line + "\"");
                            skipped++;
                            continue;
                        }
                        codigoDestino = parts[1].trim();
                        cantidad = parseIntSafe(parts[2]);

                    } else if (parts.length >= 6) {
                        // Formato completo con guiones: DD-HH-MM-DEST-QTY-CLIENT
                        day = parseIntSafe(parts[0]);
                        hour = parseIntSafe(parts[1]);
                        minute = parseIntSafe(parts[2]);
                        codigoDestino = parts[3].trim();
                        cantidad = parseIntSafe(parts[4]);
                    } else {
                        // Intento fallback: hay casos con 4 tokens si el primer token tiene D:HH o HH:MM etc.
                        // Para seguridad, intentar extraer los primeros 3 números de la línea sin importar separador.
                        Pattern nums = Pattern.compile("(\\d+)");
                        Matcher m = nums.matcher(line);
                        List<Integer> found = new ArrayList<>();
                        while (m.find() && found.size() < 3) {
                            found.add(Integer.parseInt(m.group(1)));
                        }
                        if (found.size() >= 3) {
                            day = found.get(0);
                            hour = found.get(1);
                            minute = found.get(2);
                            // ahora intentar localizar codigoDestino (primer token compuesto por 3-4 letras)
                            Pattern codeP = Pattern.compile("\\b([A-Za-z]{3,4})\\b");
                            Matcher mc = codeP.matcher(line);
                            String codeGuess = null;
                            while (mc.find()) {
                                String c = mc.group(1);
                                // saltar si coincide con los números ya tomados (no aplicable a letras)
                                codeGuess = c;
                                break;
                            }
                            if (codeGuess == null) {
                                errors.add("Línea " + lineNo + ": no pude inferir codigoDestino -> \"" + line + "\"");
                                skipped++;
                                continue;
                            }
                            codigoDestino = codeGuess;
                            // intentar extraer cantidad como el primer número mayor a 0 después del código
                            Matcher m2 = nums.matcher(line);
                            Integer qtyGuess = null;
                            while (m2.find()) {
                                int val = Integer.parseInt(m2.group(1));
                                if (val != day && val != hour && val != minute) {
                                    qtyGuess = val;
                                    break;
                                }
                            }
                            if (qtyGuess == null) {
                                errors.add("Línea " + lineNo + ": no pude inferir cantidad -> \"" + line + "\"");
                                skipped++;
                                continue;
                            }
                            cantidad = qtyGuess;
                        } else {
                            errors.add("Línea " + lineNo + ": formato no reconocido -> \"" + line + "\"");
                            skipped++;
                            continue;
                        }
                    }

                    // Validaciones básicas
                    if (day == null || hour == null || minute == null || codigoDestino == null || cantidad == null) {
                        errors.add("Línea " + lineNo + ": datos incompletos -> \"" + line + "\"");
                        skipped++;
                        continue;
                    }
                    if (day < 1 || day > 31) {
                        errors.add("Línea " + lineNo + ": día inválido -> " + day);
                        skipped++;
                        continue;
                    }
                    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                        errors.add("Línea " + lineNo + ": hora inválida -> " + hour + ":" + minute);
                        skipped++;
                        continue;
                    }

                    // buscar almacen destino
                    Optional<Almacen> optDestino = almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoDestino);
                    if (!optDestino.isPresent()) {
                        errors.add("Línea " + lineNo + ": almacen destino no encontrado: " + codigoDestino);
                        skipped++;
                        continue;
                    }
                    Almacen destino = optDestino.get();

                    // validar mes/año y que el día exista en el mes
                    YearMonth ym;
                    try {
                        ym = YearMonth.of(year, month);
                    } catch (DateTimeException dte) {
                        errors.add("Año/Mes inválidos: " + year + "-" + month);
                        return new ProcessResult(saved, skipped, errors);
                    }
                    if (day > ym.lengthOfMonth()) {
                        errors.add("Línea " + lineNo + ": día " + day + " fuera del mes " + month + "/" + year);
                        skipped++;
                        continue;
                    }

                    LocalDate localDate = LocalDate.of(year, month, day);
                    LocalTime localTime = LocalTime.of(hour, minute);
                    // interpretar como hora LOCAL del almacenDestino -> convertir a Instant usando gmt (horas)
                    ZoneOffset zoneOffset;
                    try {
                        zoneOffset = ZoneOffset.ofHours(destino.getGmt());
                    } catch (Exception ex) {
                        errors.add("Línea " + lineNo + ": gmt inválido en almacen destino id=" + destino.getId());
                        skipped++;
                        continue;
                    }
                    ZonedDateTime zdtLocal = ZonedDateTime.of(localDate, localTime, zoneOffset);
                    Instant instanteRegistro = zdtLocal.toInstant();

                    // construir Pedido (cliente ignorado)
                    Pedido pedido = Pedido.builder()
                            .almacenDestino(destino)
                            .cantidadProductosPedidos(cantidad)
                            .cantidadProductosEntregados(0)
                            .instanteRegistro(instanteRegistro)
                            .instanteMaximoParaEntregar(null)
                            .cliente(null)
                            .build();

                    batch.add(pedido);
                    if (batch.size() >= BATCH_SIZE) {
                        pedidoRepository.saveAll(batch);
                        saved += batch.size();
                        batch.clear();
                    }

                } catch (Exception exLine) {
                    errors.add("Línea " + lineNo + " parse error: " + exLine.getMessage() + " -> \"" + line + "\"");
                    skipped++;
                }
            } // while

            // flush final
            if (!batch.isEmpty()) {
                pedidoRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }

        } catch (IOException ioe) {
            errors.add("IOException: " + ioe.getMessage());
        }

        return new ProcessResult(saved, skipped, errors);
    }

    // helper usado arriba
    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        s = s.replaceAll("[^0-9\\-]", "").trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }


    // Batch size constant (ajusta según memoria)
    private static final int BATCH_SIZE = 200;



}
