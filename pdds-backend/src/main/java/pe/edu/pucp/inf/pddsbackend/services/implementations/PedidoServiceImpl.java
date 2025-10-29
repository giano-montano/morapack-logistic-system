package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.history.Revision;
import org.springframework.data.history.Revisions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.GuardarPedidoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoCargaMasivaDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoListadoDTO;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoRevisionDto;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Constantes;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.Cliente;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.ClienteRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoAuditRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.PedidoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
//        PedidoEntidad pedidoAGuardar = dto.toEntity();
//        // hasta la primer planificación (estado programado) no se sabrá si tenemos 2 o 3 días para enttregar el pedido como máximo.
//        // más lógica de negocio si la hubiera...
//        Long idAlmacen = dto.idAlmacenDestino();
//        if (idAlmacen != null) {
//            // getReferenceById devuelve un proxy gestionado (no fuerza SELECT)
//            AlmacenEntidad almacenRef = almacenRepository.getReferenceById(idAlmacen);
//            pedidoAGuardar.setAlmacenDestino(almacenRef);
//        } else {
//            throw new IllegalArgumentException("idAlmacenDestino es requerido");
//        }
//        pedidoAGuardar.setCantidadProductosEntregados(0);
//        System.out.println("pedidoAGuardar: " + pedidoAGuardar);
//        PedidoEntidad pedidoGuardado = pedidoRepository.save(pedidoAGuardar);
//        return PedidoListadoDTO.fromEntity(pedidoGuardado);
//    }

    @Override
    @Transactional
    public PedidoListadoDTO insertarUnPedido(GuardarPedidoDTO dto) {
        // Buscar las entidades Cliente y AlmacenDestino
        Cliente cliente = clienteRepository.findById(dto.idCliente())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con id " + dto.idCliente()));
        AlmacenEntidad almacenDestino = almacenRepository.findById(dto.idAlmacenDestino())
                .orElseThrow(() -> new EntityNotFoundException("Almacén no encontrado con id " + dto.idAlmacenDestino()));

        // Crear entidad PedidoEntidad usando el builder o setters
        PedidoEntidad pedido = PedidoEntidad.builder()
                .cliente(cliente)                        // asigna el cliente
                .almacenDestino(almacenDestino)         // asigna el almacén
                .cantidadProductosPedidos(dto.cantProductos()) // cantidad
                .instanteRegistro(dto.instanteRegistro() != null ? dto.instanteRegistro() : Instant.now())
                .cantidadProductosEntregados(0)         // inicializamos en 0
                .build();

        // Guardar en la base de datos
        PedidoEntidad pedidoGuardado = pedidoRepository.save(pedido);

        // Mapear a DTO y devolver al frontend
        return PedidoListadoDTO.fromEntity(pedidoGuardado);
    }




    @Override
    @Transactional
    public PedidoListadoDTO actualizarUnPedido(Long idPedido, GuardarPedidoDTO dto) {
        // 1. Cargar la entidad a actualizar (estado gestionado)
//        PedidoEntidad actual = pedidoRepository.findById(idPedido)
//                .orElseThrow(() -> new EntityNotFoundException("PedidoEntidad no encontrado: " + idPedido));
//
//        // 2. Mapear cambios simples (solo si vienen)
//        if (dto.cantProductos() != null) {
//            actual.setCantidadProductosPedidos(dto.cantProductos());
//        }
////        if (dto.instanteRegistro() != null) {
////            actual.setInstanteRegistro(dto.instanteRegistro());
////        }
//
//        // 3. Resolver y setear la relación AlmacenEntidad (si viene id distinto)
//        Long nuevoIdAlmacen = dto.idAlmacenDestino();
//        if ( nuevoIdAlmacen != null && !nuevoIdAlmacen.equals(actual.getAlmacenDestino().getId())) {
//            // Validamos existencia: findById para dar un mensaje de error claro si no existe
//            AlmacenEntidad almacen = almacenRepository.findById(nuevoIdAlmacen)
//                    .orElseThrow(() -> new EntityNotFoundException("Almacén no encontrado: " + nuevoIdAlmacen));
//            actual.setAlmacenDestino(almacen);
//        }
//
//        // 4. Persistir (merge ocurre automáticamente en contexto transaccional)
//        PedidoEntidad guardado = pedidoRepository.save(actual);
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
    public List<Revision<Integer, PedidoEntidad>> listarRevisionesPedidosPorIdPedido(Long idPedido){

        List<Revision<Integer, PedidoEntidad>> revisiones = pedidoAuditRepository.findRevisions(idPedido).stream().toList();
        System.out.println("revisiones: " + revisiones);
        return revisiones;
    }




    // Mantén transacción abierta mientras mapeas para poder acceder a proxies con seguridad
    @Transactional(readOnly = true)
    public List<PedidoRevisionDto> getAllRevisions(Long pedidoId) {
        Revisions<Integer, PedidoEntidad> revisions = pedidoRepository.findRevisions(pedidoId);

        return revisions.stream()
                .map(this::toDtoFromRevision)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoRevisionDto getRevision(Long pedidoId, Integer revisionNumber) {
        Optional<Revision<Integer, PedidoEntidad>> opt = pedidoRepository.findRevision(pedidoId, revisionNumber);
        return opt.map(this::toDtoFromRevision).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoListadoDTO> listarPedidos() {
        List<PedidoEntidad> pedidos = pedidoRepository.findAllWithAlmacenAndCliente();
        return pedidos.stream()
                .map(PedidoListadoDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public PedidoListadoDTO obtenerPedidoPorId(Long id) {
        PedidoEntidad pedido = pedidoRepository.findByIdConRelaciones(id)
                .orElseThrow(() -> new RuntimeException("PedidoEntidad no encontrado"));
        return PedidoListadoDTO.fromEntity(pedido);
    }

    @Override
    public void eliminarPedido(Long idPedido) {

    }

    @Override
    public List<PedidoListadoDTO> listarPedidosPorDestino(String codigoDestino) {
        List<PedidoEntidad> pedidos = pedidoRepository.findByDestino(codigoDestino);
        return pedidos.stream()
                .map(PedidoListadoDTO::fromEntity)
                .collect(Collectors.toList());
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

    private static final Set<String> ALMACENES_PRINCIPALES =
            new HashSet<>(Arrays.asList("LIMA","BRUS","BAKU")); // códigos  excluir (mayúsculas)

    // 1) Detecta tipo de archivo y delega
    @Override
    public List<PedidoCargaMasivaDTO> leerPedidosDesdeArchivo(MultipartFile file) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (filename.endsWith(".xls") || filename.endsWith(".xlsx")) {
            return leerPedidosDesdeExcel(file); //
        } else {
            return leerPedidosDesdeTextoPlano(file);
        }
    }

    // 2) Parser de texto plano (patrón dd-hh-mm-dest-###-IdClien)
    private List<PedidoCargaMasivaDTO> leerPedidosDesdeTextoPlano(MultipartFile file) {
        List<PedidoCargaMasivaDTO> lista = new ArrayList<>();
        // regex: dd-hh-mm-dest-###-IdClien
        Pattern p = Pattern.compile("^(\\d{2})-(\\d{2})-(\\d{2})-([A-Za-z0-9]{3,6})-(\\d{3})-(\\d{7})$");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int lineno = 0;
            while ((line = br.readLine()) != null) {
                lineno++;
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher m = p.matcher(line);
                if (!m.matches()) {
                    throw new RuntimeException("Formato inválido en línea " + lineno + ": '" + line + "'");
                }

                // grupos
                // String dd = m.group(1);
                // String hh = m.group(2);
                // String mm = m.group(3);
                String dest = m.group(4).toUpperCase();
                String cantidadStr = m.group(5);
                String idClienteStr = m.group(6);

                int cantidad = Integer.parseInt(cantidadStr);
                if (cantidad < 1 || cantidad > 999) {
                    throw new RuntimeException("Cantidad fuera de rango (1-999) en línea " + lineno);
                }

                Long idCliente = null;
                try {
                    idCliente = Long.parseLong(idClienteStr); // acepta leading zeros
                } catch (NumberFormatException ex) {
                    throw new RuntimeException("IdCliente inválido en línea " + lineno);
                }

                // Buscar almacen por código de ciudad (debes tener este método en repo)
                Optional<AlmacenEntidad> optAlm = almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(dest);
                if (optAlm.isEmpty()) {
                    throw new RuntimeException("Almacén destino no encontrado para código '" + dest + "' en línea " + lineno);
                }

                AlmacenEntidad almacen = optAlm.get();
                lista.add(new PedidoCargaMasivaDTO(idCliente, almacen.getId(), cantidad));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo archivo de texto: " + e.getMessage(), e);
        }
        return lista;
    }

    // 3) Cargar (validar + ignorar almacenes principales + persistir) -> devolver DTOs
    @Override
    @Transactional
    public List<PedidoListadoDTO> cargarPedidosMasivos(List<PedidoCargaMasivaDTO> pedidosDTO) {
        List<PedidoEntidad> pedidosParaGuardar = new ArrayList<>();

        for (PedidoCargaMasivaDTO dto : pedidosDTO) {
            // Validaciones básicas
            if (dto.cantProductos() == null || dto.cantProductos() < 1 || dto.cantProductos() > 999) {
                throw new RuntimeException("Cantidad inválida en DTO: " + dto);
            }

            AlmacenEntidad almacen = almacenRepository.findById(dto.idAlmacenDestino())
                    .orElseThrow(() -> new RuntimeException("Almacén no encontrado: " + dto.idAlmacenDestino()));

            // Excluir por almacenes principales (por código)
            String codigo = Optional.ofNullable(almacen.getCodigoCiudadEn4Letras()).orElse("").toUpperCase();
            if (ALMACENES_PRINCIPALES.contains(codigo)) {
                // ignorar este pedido (no se guarda)
                continue;
            }

            PedidoEntidad pedido = dto.toEntity();
            pedido.setAlmacenDestino(almacen);

            if (dto.idCliente() != null) {
                Cliente cliente = clienteRepository.findById(dto.idCliente())
                        .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + dto.idCliente()));
                pedido.setCliente(cliente);
            }
            pedidosParaGuardar.add(pedido);
        }

        List<PedidoEntidad> guardados = pedidoRepository.saveAll(pedidosParaGuardar);

        // Convertir a DTOs listables para frontend
        return guardados.stream()
                .map(PedidoListadoDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // 4) Comodín: leer + guardar en un solo paso para controller
    @Override
    @Transactional
    public List<PedidoListadoDTO> cargarPedidosDesdeArchivo(MultipartFile file) {
        List<PedidoCargaMasivaDTO> dtos = leerPedidosDesdeArchivo(file);
        return cargarPedidosMasivos(dtos);
    }

    private PedidoRevisionDto toDtoFromRevision(Revision<Integer, PedidoEntidad> rev) {
        PedidoEntidad p = rev.getEntity();

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
        List<PedidoEntidad> batch = new ArrayList<>(BATCH_SIZE);
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
                    Optional<AlmacenEntidad> optDestino = almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoDestino);
                    if (!optDestino.isPresent()) {
                        errors.add("Línea " + lineNo + ": almacen destino no encontrado: " + codigoDestino);
                        skipped++;
                        continue;
                    }
                    AlmacenEntidad destino = optDestino.get();

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

                    // construir PedidoEntidad (cliente ignorado)
                    PedidoEntidad pedido = PedidoEntidad.builder()
                            .almacenDestino(destino)
                            .cantidadProductosPedidos(cantidad)
                            .cantidadProductosEntregados(0)
                            .instanteRegistro(instanteRegistro)
                            .esIntercontinental(false)
                            .instanteMaximoParaEntregar(instanteRegistro
                                    .plus(Constantes.DIAS_CONTINENTAL, ChronoUnit.DAYS)) // por defecto, importante!
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
