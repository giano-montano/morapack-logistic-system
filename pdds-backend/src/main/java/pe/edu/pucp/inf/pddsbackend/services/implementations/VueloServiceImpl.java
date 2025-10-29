package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloProgramadoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VueloServiceImpl implements VueloService {

    private final VueloRepository vueloRepository;
    private final VueloProgramadoRepository vueloProgramadoRepository;
    private final AlmacenRepository almacenRepository;
    private static final int BATCH_SIZE = 100;

    @Override
    @Transactional
    public ProcessResult procesarArchivoPlanesVueloDelProfe(InputStream inputStream) {
        List<VueloProgramado> batch = new ArrayList<>(BATCH_SIZE);
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int skipped = 0;
        int lineNo = 0;

        // Nuevo pattern: ORIG-DEST-HH:MM-HH:MM-CAPACITY
        Pattern linePattern = Pattern.compile("^\\s*([A-Za-z0-9]{3,4})-([A-Za-z0-9]{3,4})-(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})-(\\d+)\\s*$");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                lineNo++;
                String line = raw.replaceAll("\\p{C}", "").trim(); // elimina BOM/caracteres no imprimibles
                if (line.isEmpty()) continue;

                // ignorar comentarios/encabezados
                if (line.startsWith("#") || line.startsWith("//") || line.toLowerCase().startsWith("orig") || line.matches("^\\*+.*")) {
                    continue;
                }

                Matcher m = linePattern.matcher(line);
                if (!m.matches()) {
                    errors.add("Línea " + lineNo + ": formato inválido -> \"" + line + "\"");
                    skipped++;
                    continue;
                }

                try {
                    String codigoOrigen = m.group(1).toUpperCase();
                    String codigoDestino = m.group(2).toUpperCase();
                    String horaOrigenUtcStr = m.group(3); // ya en UTC según tu indicación
                    String horaDestinoUtcStr = m.group(4); // ya en UTC según tu indicación
                    String capacidadStr = m.group(5);

                    // Buscar almacenes por código
                    Optional<AlmacenEntidad> optOrigen = almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoOrigen);
                    Optional<AlmacenEntidad> optDestino = almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoDestino);
                    if (!optOrigen.isPresent()) {
                        errors.add("Línea " + lineNo + ": almacen origen no encontrado: " + codigoOrigen);
                        skipped++;
                        continue;
                    }
                    if (!optDestino.isPresent()) {
                        errors.add("Línea " + lineNo + ": almacen destino no encontrado: " + codigoDestino);
                        skipped++;
                        continue;
                    }
                    AlmacenEntidad origen = optOrigen.get();
                    AlmacenEntidad destino = optDestino.get();

                    // parse times (ya son UTC) -> guardamos directamente como LocalTime
                    LocalTime horaInicioUtc;
                    LocalTime horaFinUtc;
                    try {
                        horaInicioUtc = LocalTime.parse(horaOrigenUtcStr);
                        horaFinUtc = LocalTime.parse(horaDestinoUtcStr);
                    } catch (DateTimeParseException dtpe) {
                        errors.add("Línea " + lineNo + ": hora inválida -> \"" + horaOrigenUtcStr + "\" / \"" + horaDestinoUtcStr + "\"");
                        skipped++;
                        continue;
                    }

                    // parse capacidad (ej. "0300" -> 300)
                    int capacidadMaxima;
                    try {
                        capacidadMaxima = Integer.parseInt(capacidadStr);
                    } catch (NumberFormatException nfe) {
                        errors.add("Línea " + lineNo + ": capacidad inválida -> \"" + capacidadStr + "\"");
                        skipped++;
                        continue;
                    }

                    boolean esIntercontinental = origen.getContinente() != destino.getContinente();

                    VueloProgramado vuelo = new VueloProgramado();
                    vuelo.setAlmacenOrigen(origen);
                    vuelo.setAlmacenDestino(destino);
                    vuelo.setEsIntercontinental(esIntercontinental);
                    vuelo.setHoraInicioEnPropioHuso(horaInicioUtc);
                    vuelo.setHoraFinEnPropioHuso(horaFinUtc);
                    vuelo.setCapacidadMaxima(capacidadMaxima);
                    vuelo.setActivo(true);

                    batch.add(vuelo);
                    if (batch.size() >= BATCH_SIZE) {
                        vueloProgramadoRepository.saveAll(batch);
                        saved += batch.size();
                        batch.clear();
                    }
                } catch (Exception ex) {
                    errors.add("Línea " + lineNo + " parse error: " + ex.getMessage() + " -> \"" + line + "\"");
                    skipped++;
                }
            }

            // flush final
            if (!batch.isEmpty()) {
                vueloProgramadoRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }

        } catch (IOException ex) {
            errors.add("IOException: " + ex.getMessage());
        }

        return new ProcessResult(saved, skipped, errors);
    }

    /**
     * Crea vuelos concretos (VueloEntidad) a partir de todos los VueloProgramado existentes en BD.
     *
     * @param startDate fecha desde la que generar (ej. LocalDate.now())
     * @param days número de días a planchar (1 = solo startDate; 7 = 7 días consecutivos)
     * @param skipIfExists si true evita crear VueloEntidad si ya existe uno con mismo origen,destino,fechaHoraInicioUtc
     * @return ProcessResult con conteo y errores
     */
    @Transactional
    @Override
    public ProcessResult createConcreteFlights(LocalDate startDate, int days, boolean skipIfExists) {
        List<VueloProgramado> programados = vueloProgramadoRepository.findAll();
        List<VueloEntidad> batch = new ArrayList<>(BATCH_SIZE);
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int skipped = 0;

        if (startDate == null) startDate = LocalDate.now(ZoneOffset.UTC);

        for (VueloProgramado vp : programados) {
            AlmacenEntidad origen = vp.getAlmacenOrigen();
            AlmacenEntidad destino = vp.getAlmacenDestino();

            // construir ZoneOffset desde origen/destino usando su campo gmt (entero horas)
            ZoneOffset offsetOrigen = zoneOffsetFromGmt(origen.getGmt(), errors, origen);
            ZoneOffset offsetDestino = zoneOffsetFromGmt(destino.getGmt(), errors, destino);
            if (offsetOrigen == null || offsetDestino == null) {
                // error ya agregado
                skipped++;
                continue;
            }

            for (int d = 0; d < days; d++) {
                LocalDate dateForDepartureLocal = startDate.plusDays(d);

                // Hora local de inicio (en horario del almacén origen)
                LocalTime horaInicioLocal = vp.getHoraInicioEnPropioHuso(); // nota: campo se interpreta como hora local del origen
                LocalTime horaFinLocal = vp.getHoraFinEnPropioHuso();       // hora local del destino

                // Construir ZonedDateTime de salida en zona del origen
                ZonedDateTime salidaZdt = ZonedDateTime.of(dateForDepartureLocal, horaInicioLocal, offsetOrigen);
                Instant salidaInstant = salidaZdt.toInstant();

                // Para la llegada: tomar la fecha local del destino correspondiente al instante de salida
                ZonedDateTime salidaEnDestino = salidaInstant.atZone(offsetDestino);
                LocalDate candidateArrivalLocalDate = salidaEnDestino.toLocalDate();

                ZonedDateTime llegadaZdt = ZonedDateTime.of(candidateArrivalLocalDate, horaFinLocal, offsetDestino);
                Instant llegadaInstant = llegadaZdt.toInstant();

                // Si la llegada queda antes de la salida -> asumimos llegada en el siguiente día local del destino
                int addDays = 0;
                while (!llegadaInstant.isAfter(salidaInstant) && addDays < 3) {
                    llegadaZdt = llegadaZdt.plusDays(1);
                    llegadaInstant = llegadaZdt.toInstant();
                    addDays++;
                }
                // Si después de 3 días sigue sin ser after -> considerarlo error y saltar
                if (!llegadaInstant.isAfter(salidaInstant)) {
                    errors.add(String.format("VueloProgramado id=%d: arrival <= departure after adding days (origen=%s,destino=%s,startDate=%s)",
                            vp.getId(), origen.getCodigoAeropuertoEn4Letras(), destino.getCodigoAeropuertoEn4Letras(), dateForDepartureLocal));
                    skipped++;
                    continue;
                }

                // Verificación de existencia (opcional)
                if (skipIfExists && vueloRepository.existsByAlmacenOrigenAndAlmacenDestinoAndFechaHoraInicioUtc(origen, destino, salidaInstant)) {
                    skipped++;
                    continue;
                }

                // Generar codigo identificador: ORI-DEST-YYYYMMDD-HHMM (UTC)
                String codigo = generateFlightCode(origen.getCodigoAeropuertoEn4Letras(), destino.getCodigoAeropuertoEn4Letras(), salidaInstant);

                VueloEntidad vuelo = VueloEntidad.builder()
                        .codigo4Letras(codigo)
                        .almacenOrigen(origen)
                        .almacenDestino(destino)
                        .fechaHoraInicioUtc(salidaInstant)
                        .fechaHoraFinUtc(llegadaInstant)
                        .capacidadMaxima(vp.getCapacidadMaxima() != null ? vp.getCapacidadMaxima() : origen.getCapacidadMaxima())
                        .capacidadOcupada(0)
                        .cancelado(false)
                        .esIntercontinental(!Objects.equals(origen.getContinente(), destino.getContinente()))
                        .activo(true)
                        .build();

                batch.add(vuelo);
                if (batch.size() >= 3000) {
                    vueloRepository.saveAll(batch);
                    saved += batch.size();
                    batch.clear();
                }
            } // end days loop
        } // end programados loop

        // flush final
        if (!batch.isEmpty()) {
            vueloRepository.saveAll(batch);
            saved += batch.size();
            batch.clear();
        }

        return new ProcessResult(saved, skipped, errors);
    }

    // ---------------- CRUD ----------------
    private VueloDTO toDTO(VueloEntidad v){
        return new VueloDTO(
                v.getId(),
                v.getCodigo4Letras(),
                v.getAlmacenOrigen()!=null? v.getAlmacenOrigen().getId(): null,
                v.getAlmacenDestino()!=null? v.getAlmacenDestino().getId(): null,
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),
                v.getCancelado(),
                v.getEsIntercontinental(),
                v.getActivo()
        );
    }

    private void apply(VueloEntidad v, VueloCreateUpdateDTO dto){
        v.setCodigo4Letras(dto.codigo4Letras());
        AlmacenEntidad origen = almacenRepository.findById(dto.idAlmacenOrigen()).orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad origen no encontrado"));
        AlmacenEntidad destino = almacenRepository.findById(dto.idAlmacenDestino()).orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad destino no encontrado"));
        v.setAlmacenOrigen(origen);
        v.setAlmacenDestino(destino);
        v.setFechaHoraInicioUtc(dto.fechaHoraInicioUtc());
        v.setFechaHoraFinUtc(dto.fechaHoraFinUtc());
        v.setCapacidadMaxima(dto.capacidadMaxima());
        v.setCapacidadOcupada(dto.capacidadOcupada());
        v.setCancelado(dto.cancelado());
        v.setEsIntercontinental(dto.esIntercontinental());
        v.setActivo(dto.activo());
    }

    @Override
    public VueloDTO crear(VueloCreateUpdateDTO dto) {
        if(!dto.fechaHoraFinUtc().isAfter(dto.fechaHoraInicioUtc())) throw new IllegalArgumentException("fechaHoraFinUtc debe ser posterior a inicio");
        VueloEntidad v = new VueloEntidad();
        apply(v,dto);
        vueloRepository.save(v);
        return toDTO(v);
    }

    @Override
    public VueloDTO actualizar(Long id, VueloCreateUpdateDTO dto) {
        VueloEntidad v = vueloRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        apply(v,dto);
        return toDTO(v);
    }

    @Override
    public VueloDTO obtener(Long id) {
        VueloEntidad v = vueloRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        return toDTO(v);
    }

    @Override
    public Page<VueloDTO> listar(String q, Pageable pageable) {
        Page<VueloEntidad> page = vueloRepository.findAll(pageable);
        List<VueloDTO> content = page.getContent().stream()
                .filter(v -> q==null || q.isBlank() || (v.getCodigo4Letras()!=null && v.getCodigo4Letras().toLowerCase().contains(q.toLowerCase())))
                .map(this::toDTO).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Override
    public void eliminar(Long id) {
        VueloEntidad v = vueloRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        v.setActivo(false);
    }
    

    // --- helpers ---

    /** Crea ZoneOffset a partir del valor entero gmt (horas). Devuelve null y agrega error si no puede. */
    private ZoneOffset zoneOffsetFromGmt(Integer gmt, List<String> errors, AlmacenEntidad almacen) {
        if (gmt == null) {
            errors.add("AlmacenEntidad id=" + almacen.getId() + " sin campo gmt");
            return null;
        }
        try {
            return ZoneOffset.ofHours(gmt);
        } catch (Exception ex) {
            errors.add("AlmacenEntidad id=" + almacen.getId() + " gmt inválido: " + gmt);
            return null;
        }
    }

    /** Genera código legible para vuelo: ORI-DEST-YYYYMMDD-HHMM (hora de salida en UTC) */
    private String generateFlightCode(String origenCode, String destinoCode, Instant salidaInstant) {
        LocalDateTime ldt = LocalDateTime.ofInstant(salidaInstant, ZoneOffset.UTC);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
        return String.format("%s-%s-%s", origenCode, destinoCode, ldt.format(f));
    }

}
