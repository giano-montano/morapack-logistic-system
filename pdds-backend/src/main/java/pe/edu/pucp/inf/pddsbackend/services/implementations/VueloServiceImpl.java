package pe.edu.pucp.inf.pddsbackend.services.implementations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.CancelacionVuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.CancelacionVueloRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloProgramadoRepository;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ConfiguracionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoCancelacionVuelo;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VueloServiceImpl implements VueloService
{

    private final VueloRepository vueloRepository;
    private final VueloProgramadoRepository vueloProgramadoRepository;
    private final AlmacenRepository almacenRepository;
    private final PedidoService pedidoService;
    private final CancelacionVueloRepository cancelacionVueloRepository;
    private static final int BATCH_SIZE = 100;

    private final PlanificacionService planificacionService;
    private final SimulacionWebSocketService webSocketService;
    private final ConfiguracionService configuracionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public ProcessResult procesarArchivoPlanesVueloDelProfe(InputStream inputStream)
    {
        List<VueloProgramado> batch = new ArrayList<>(BATCH_SIZE);
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int skipped = 0;
        int lineNo = 0;

        // Nuevo pattern: ORIG-DEST-HH:MM-HH:MM-CAPACITY
        Pattern linePattern = Pattern.compile(
                "^\\s*([A-Za-z0-9]{3,4})-([A-Za-z0-9]{3,4})-(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})-(\\d+)\\s*$");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
        {
            String raw;
            while ((raw = br.readLine()) != null)
            {
                lineNo++;
                String line = raw.replaceAll("\\p{C}", "").trim(); // elimina BOM/caracteres no
                                                                   // imprimibles
                if (line.isEmpty())
                    continue;

                // ignorar comentarios/encabezados
                if (line.startsWith("#") || line.startsWith("//")
                        || line.toLowerCase().startsWith("orig") || line.matches("^\\*+.*"))
                {
                    continue;
                }

                Matcher m = linePattern.matcher(line);
                if (!m.matches())
                {
                    errors.add("Línea " + lineNo + ": formato inválido -> \"" + line + "\"");
                    skipped++;
                    continue;
                }

                try
                {
                    String codigoOrigen = m.group(1).toUpperCase();
                    String codigoDestino = m.group(2).toUpperCase();
                    String horaOrigenUtcStr = m.group(3); // ya en UTC según tu indicación
                    String horaDestinoUtcStr = m.group(4); // ya en UTC según tu indicación
                    String capacidadStr = m.group(5);

                    // Buscar almacenes por código
                    Optional<AlmacenEntidad> optOrigen = almacenRepository
                            .findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoOrigen);
                    Optional<AlmacenEntidad> optDestino = almacenRepository
                            .findByCodigoAeropuertoEn4LetrasIgnoreCase(codigoDestino);
                    if (!optOrigen.isPresent())
                    {
                        errors.add("Línea " + lineNo + ": almacen origen no encontrado: "
                                + codigoOrigen);
                        skipped++;
                        continue;
                    }
                    if (!optDestino.isPresent())
                    {
                        errors.add("Línea " + lineNo + ": almacen destino no encontrado: "
                                + codigoDestino);
                        skipped++;
                        continue;
                    }
                    AlmacenEntidad origen = optOrigen.get();
                    AlmacenEntidad destino = optDestino.get();

                    // parse times (ya son UTC) -> guardamos directamente como LocalTime
                    LocalTime horaInicioUtc;
                    LocalTime horaFinUtc;
                    try
                    {
                        horaInicioUtc = LocalTime.parse(horaOrigenUtcStr);
                        horaFinUtc = LocalTime.parse(horaDestinoUtcStr);
                    }
                    catch (DateTimeParseException dtpe)
                    {
                        errors.add("Línea " + lineNo + ": hora inválida -> \"" + horaOrigenUtcStr
                                + "\" / \"" + horaDestinoUtcStr + "\"");
                        skipped++;
                        continue;
                    }

                    // parse capacidad (ej. "0300" -> 300)
                    int capacidadMaxima;
                    try
                    {
                        capacidadMaxima = Integer.parseInt(capacidadStr);
                    }
                    catch (NumberFormatException nfe)
                    {
                        errors.add("Línea " + lineNo + ": capacidad inválida -> \"" + capacidadStr
                                + "\"");
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
                    if (batch.size() >= BATCH_SIZE)
                    {
                        vueloProgramadoRepository.saveAll(batch);
                        saved += batch.size();
                        batch.clear();
                    }
                }
                catch (Exception ex)
                {
                    errors.add("Línea " + lineNo + " parse error: " + ex.getMessage() + " -> \""
                            + line + "\"");
                    skipped++;
                }
            }

            // flush final
            if (!batch.isEmpty())
            {
                vueloProgramadoRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }

        }
        catch (IOException ex)
        {
            errors.add("IOException: " + ex.getMessage());
        }

        return new ProcessResult(saved, skipped, errors);
    }

    /**
     * Crea vuelos concretos (VueloEntidad) a partir de todos los VueloProgramado
     * existentes en BD.
     *
     * @param startDate
     *            fecha desde la que generar (ej. LocalDate.now())
     * @param days
     *            número de días a planchar (1 = solo startDate; 7 = 7 días
     *            consecutivos)
     * @param skipIfExists
     *            si true evita crear VueloEntidad si ya existe uno con mismo
     *            origen,destino,fechaHoraInicioUtc
     * @return ProcessResult con conteo y errores
     */
    @Transactional
    @Override
    public ProcessResult createConcreteFlights(LocalDate startDate, int days, boolean skipIfExists)
    {
        final int BATCH_SIZE = 3000;
        List<VueloProgramado> programados;

        // 1) Obtener programados con fetch-join para evitar N+1 (si implementaste
        // findAllActiveWithAlmacenes)
        try
        {
            programados = vueloProgramadoRepository.findAllActiveWithAlmacenes();
        }
        catch (Exception e)
        {
            // fallback si no existe el método fetch-join
            programados = vueloProgramadoRepository.findAll();
        }

        if (startDate == null)
            startDate = LocalDate.now(ZoneOffset.UTC);

        List<String> errors = new ArrayList<>();
        List<VueloEntidad> toSaveBatch = new ArrayList<>(BATCH_SIZE);
        int saved = 0;
        int skipped = 0;

        // 2) Precomputar todas las candidate keys (originId, destId, salidaInstant) y
        // también recopilar min/max instants/origins/dests
        class Candidate
        {
            Long origenId;
            Long destinoId;
            Instant salida;
            Instant llegada;
            VueloProgramado vp;
            AlmacenEntidad origen;
            AlmacenEntidad destino;
        }
        List<Candidate> candidates = new ArrayList<>();

        Instant globalMin = Instant.MAX;
        Instant globalMax = Instant.EPOCH;

        for (VueloProgramado vp : programados)
        {
            AlmacenEntidad origen = vp.getAlmacenOrigen();
            AlmacenEntidad destino = vp.getAlmacenDestino();
            if (origen == null || destino == null)
            {
                errors.add("VueloProgramado id=" + vp.getId() + " tiene origen/destino nulo");
                skipped++;
                continue;
            }

            ZoneOffset offsetOrigen = zoneOffsetFromGmt(origen.getGmt(), errors, origen);
            ZoneOffset offsetDestino = zoneOffsetFromGmt(destino.getGmt(), errors, destino);
            if (offsetOrigen == null || offsetDestino == null)
            {
                skipped++;
                continue;
            }

            for (int d = 0; d < days; d++)
            {
                LocalDate dateForDepartureLocal = startDate.plusDays(d);
                LocalTime horaInicioLocal = vp.getHoraInicioEnPropioHuso();
                LocalTime horaFinLocal = vp.getHoraFinEnPropioHuso();

                ZonedDateTime salidaZdt = ZonedDateTime.of(dateForDepartureLocal, horaInicioLocal,
                        offsetOrigen);
                Instant salidaInstant = salidaZdt.toInstant();

                // arrival local date guess
                ZonedDateTime salidaEnDestino = salidaInstant.atZone(offsetDestino);
                LocalDate candidateArrivalLocalDate = salidaEnDestino.toLocalDate();
                ZonedDateTime llegadaZdt = ZonedDateTime.of(candidateArrivalLocalDate, horaFinLocal,
                        offsetDestino);
                Instant llegadaInstant = llegadaZdt.toInstant();

                int addDays = 0;
                while (!llegadaInstant.isAfter(salidaInstant) && addDays < 3)
                {
                    llegadaZdt = llegadaZdt.plusDays(1);
                    llegadaInstant = llegadaZdt.toInstant();
                    addDays++;
                }
                if (!llegadaInstant.isAfter(salidaInstant))
                {
                    errors.add(String.format(
                            "VueloProgramado id=%d: arrival <= departure after adding days (origen=%s,destino=%s,startDate=%s)",
                            vp.getId(), origen.getCodigoAeropuertoEn4Letras(),
                            destino.getCodigoAeropuertoEn4Letras(), dateForDepartureLocal));
                    skipped++;
                    continue;
                }

                Candidate c = new Candidate();
                c.origenId = origen.getId();
                c.destinoId = destino.getId();
                c.salida = salidaInstant;
                c.llegada = llegadaInstant;
                c.vp = vp;
                c.origen = origen;
                c.destino = destino;
                candidates.add(c);

                if (salidaInstant.isBefore(globalMin))
                    globalMin = salidaInstant;
                if (salidaInstant.isAfter(globalMax))
                    globalMax = salidaInstant;
            }
        }

        if (candidates.isEmpty())
        {
            return new ProcessResult(saved, skipped, errors);
        }

        // 3) Bulk query existing VueloEntidad for involved origins/dests and date range
        Set<Long> origenIds = candidates.stream().map(c -> c.origenId).collect(Collectors.toSet());
        Set<Long> destinoIds = candidates.stream().map(c -> c.destinoId)
                .collect(Collectors.toSet());

        List<VueloEntidad> existing = vueloRepository
                .findByAlmacenOrigen_IdInAndAlmacenDestino_IdInAndFechaHoraInicioUtcBetween(
                        origenIds, destinoIds, globalMin.minusSeconds(1), globalMax.plusSeconds(1));

        // Build a lookup set of existing keys
        Set<String> existingKeys = existing.stream()
                .map(v -> v.getAlmacenOrigen().getId() + "|" + v.getAlmacenDestino().getId() + "|"
                        + v.getFechaHoraInicioUtc().toString())
                .collect(Collectors.toSet());

        // 4) Crear entidades VueloEntidad para candidatos que no existen ya (respetando
        // skipIfExists)
        for (Candidate c : candidates)
        {
            String key = c.origenId + "|" + c.destinoId + "|" + c.salida.toString();
            if (skipIfExists && existingKeys.contains(key))
            {
                skipped++;
                continue;
            }

            String codigo = generateFlightCode(c.origen.getCodigoAeropuertoEn4Letras(),
                    c.destino.getCodigoAeropuertoEn4Letras(), c.salida);

            VueloEntidad vuelo = VueloEntidad.builder()
                    .codigo4Letras(codigo)
                    .almacenOrigen(c.origen)
                    .almacenDestino(c.destino)
                    .fechaHoraInicioUtc(c.salida)
                    .fechaHoraFinUtc(c.llegada)
                    .capacidadMaxima(c.vp.getCapacidadMaxima() != null
                            ? c.vp.getCapacidadMaxima()
                            : c.origen.getCapacidadMaxima())
                    .capacidadOcupada(0)
                    .cancelado(false)
                    .esIntercontinental(
                            !Objects.equals(c.origen.getContinente(), c.destino.getContinente()))
                    .activo(true)
                    .build();

            toSaveBatch.add(vuelo);

            if (toSaveBatch.size() >= BATCH_SIZE)
            {
                vueloRepository.saveAll(toSaveBatch);
                // aconsejable flush/clear para memoria
                entityManager.flush();
                entityManager.clear();
                saved += toSaveBatch.size();
                toSaveBatch.clear();
            }
        }

        // final flush
        if (!toSaveBatch.isEmpty())
        {
            vueloRepository.saveAll(toSaveBatch);
            entityManager.flush();
            entityManager.clear();
            saved += toSaveBatch.size();
            toSaveBatch.clear();
        }

        return new ProcessResult(saved, skipped, errors);
    }

    // helper result container
    public static class GenerationResult
    {
        private final List<Vuelo> vuelos;
        private final int skipped;
        private final List<String> errors;

        public GenerationResult(List<Vuelo> vuelos, int skipped, List<String> errors)
        {
            this.vuelos = vuelos;
            this.skipped = skipped;
            this.errors = errors;
        }

        public List<Vuelo> getVuelos()
        {
            return vuelos;
        }

        public int getSkipped()
        {
            return skipped;
        }

        public List<String> getErrors()
        {
            return errors;
        }
    }

    /**
     * Genera en memoria objetos Vuelo a partir de una lista de VueloProgramado.
     *
     * @param programados
     *            lista ya recuperada de VueloProgramado (no hace findAll aquí)
     * @param referenceInstant
     *            fecha desde la que generar, puede ser nulo, se tomará las fechas
     *            en husos para cada alm
     * @param days
     *            número de días a planchar
     * @param skipIfExists
     *            si true evita crear vuelos cuya key esté en existingKeys
     * @param almacenById
     *            mapa pre-fetcheado de almacenes (id -> AlmacenEntidad). Puede ser
     *            null.
     * @param existingKeys
     *            set de keys existentes para evitar duplicados (cada key:
     *            "origenId|destinoId|salidaInstant"). Puede ser null.
     * @return GenerationResult con la lista de Vuelo, cantidad skipped y lista de
     *         errores.
     */
    @Override
    public GenerationResult generateFlightsInMemory(
            List<VueloProgramado> programados,
            Instant referenceInstant,
            int days,
            boolean skipIfExists,
            Map<Long, AlmacenEntidad> almacenById,
            Set<String> existingKeys) {

        if (programados == null || programados.isEmpty()) {
            return new GenerationResult(Collections.emptyList(), 0, Collections.emptyList());
        }
        if (referenceInstant == null)
            referenceInstant = Instant.now();

        if (days <= 0)
            days = 1;

        List<String> errors = new ArrayList<>();
        List<Vuelo> result = new ArrayList<>();
        int skipped = 0;

        class Candidate {
            Long id;
            Long origenId;
            Long destinoId;
            Instant salida;
            Instant llegada;
            VueloProgramado vp;
            AlmacenEntidad origen;
            AlmacenEntidad destino;
        }
        List<Candidate> candidates = new ArrayList<>();

        for (VueloProgramado vp : programados) {
            AlmacenEntidad origen = (almacenById != null)
                    ? almacenById.get(
                            vp.getAlmacenOrigen() != null ? vp.getAlmacenOrigen().getId() : null)
                    : vp.getAlmacenOrigen();
            AlmacenEntidad destino = (almacenById != null)
                    ? almacenById.get(
                            vp.getAlmacenDestino() != null ? vp.getAlmacenDestino().getId() : null)
                    : vp.getAlmacenDestino();

            if (origen == null || destino == null) {
                errors.add("VueloProgramado id=" + vp.getId() + " tiene origen/destino nulo");
                skipped++;
                continue;
            }

            ZoneOffset offsetOrigen = zoneOffsetFromGmt(origen.getGmt(), errors, origen);
            ZoneOffset offsetDestino = zoneOffsetFromGmt(destino.getGmt(), errors, destino);
            if (offsetOrigen == null || offsetDestino == null) {
                skipped++;
                continue;
            }

            LocalTime horaInicioLocal = vp.getHoraInicioEnPropioHuso();
            LocalTime horaFinLocal = vp.getHoraFinEnPropioHuso();
            if (horaInicioLocal == null || horaFinLocal == null) {
                errors.add("VueloProgramado id=" + vp.getId() + " tiene hora inicio/fin nula");
                skipped++;
                continue;
            }

            // startDateLocal: fecha base en el huso del origen, derivada de
            // referenceInstant
            LocalDate startDateLocal = referenceInstant.atZone(offsetOrigen).toLocalDate();

            for (int d = 0; d < days; d++) {
                LocalDate dateForDepartureLocal = startDateLocal.plusDays(d);

                // 1) construir salida en horario local origen -> Instant UTC
                ZonedDateTime salidaZdt = ZonedDateTime.of(dateForDepartureLocal, horaInicioLocal,
                        offsetOrigen);
                Instant salidaInstant = salidaZdt.toInstant();

                // 2) intentar construir llegada asumiendo arrival en la misma fecha local del
                // destino
                // para eso tomamos la fecha de 'salidaInstant' en zona destino como punto de
                // partida
                LocalDate candidateArrivalLocalDate = salidaInstant.atZone(offsetDestino)
                        .toLocalDate();
                ZonedDateTime llegadaZdt = ZonedDateTime.of(candidateArrivalLocalDate, horaFinLocal,
                        offsetDestino);
                Instant llegadaInstant = llegadaZdt.toInstant();

                // 3) si llegadaInstant <= salidaInstant, solo intentamos +1 día (no más: "no
                // más de 1 día")
                if (!llegadaInstant.isAfter(salidaInstant)) {
                    llegadaZdt = llegadaZdt.plusDays(1);
                    llegadaInstant = llegadaZdt.toInstant();
                }

                // 4) si aún no es posterior, es inconsistente; marcar error y skip
                if (!llegadaInstant.isAfter(salidaInstant)) {
                    errors.add(String.format(
                            "VueloProgramado id=%d: arrival <= departure after +0/1 day attempts (origen=%s,destino=%s,startLocal=%s)",
                            vp.getId(), origen.getCodigoAeropuertoEn4Letras(),
                            destino.getCodigoAeropuertoEn4Letras(), dateForDepartureLocal));
                    skipped++;
                    continue;
                }

                // 5) VALIDACIONES: las horas locales derivadas desde los instantes deben
                // coincidir con las programadas
                LocalTime salidaLocalFromInstant = salidaInstant.atZone(offsetOrigen).toLocalTime();
                if (!salidaLocalFromInstant.equals(horaInicioLocal)) {
                    errors.add(String.format(
                            "VueloProgramado id=%d: mismatch salida local hora (esperada=%s, calculada=%s) origen=%s date=%s",
                            vp.getId(), horaInicioLocal, salidaLocalFromInstant,
                            origen.getCodigoAeropuertoEn4Letras(), dateForDepartureLocal));
                    skipped++;
                    continue;
                }

                LocalTime llegadaLocalFromInstant = llegadaInstant.atZone(offsetDestino)
                        .toLocalTime();
                if (!llegadaLocalFromInstant.equals(horaFinLocal)) {
                    errors.add(String.format(
                            "VueloProgramado id=%d: mismatch llegada local hora (esperada=%s, calculada=%s) destino=%s dateGuess=%s",
                            vp.getId(), horaFinLocal, llegadaLocalFromInstant,
                            destino.getCodigoAeropuertoEn4Letras(), llegadaZdt.toLocalDate()));
                    skipped++;
                    continue;
                }

                // 6) VALIDACIÓN de duración: no más de 24 horas (86400 segundos)
                long duracionSegundos = ChronoUnit.SECONDS.between(salidaInstant, llegadaInstant);
                if (duracionSegundos <= 0 || duracionSegundos > 86400L) {
                    errors.add(String.format(
                            "VueloProgramado id=%d: duración no razonable (segundos=%d) entre %s y %s (origen=%s, destino=%s)",
                            vp.getId(), duracionSegundos, salidaInstant, llegadaInstant,
                            origen.getCodigoAeropuertoEn4Letras(),
                            destino.getCodigoAeropuertoEn4Letras()));
                    skipped++;
                    continue;
                }

                Candidate candidato = new Candidate();
                candidato.id = UUID.randomUUID().getMostSignificantBits();
                candidato.origenId = origen.getId();
                candidato.destinoId = destino.getId();
                candidato.salida = salidaInstant;
                candidato.llegada = llegadaInstant;
                candidato.vp = vp;
                candidato.origen = origen;
                candidato.destino = destino;
                candidates.add(candidato);
            }
        }

        if (candidates.isEmpty()){
            return new GenerationResult(Collections.emptyList(), skipped, errors);
        }

        for (Candidate candidato : candidates) {
            String key = candidato.origenId + "|" + candidato.destinoId + "|"
                    + candidato.salida.toString();
            if (skipIfExists && existingKeys != null && existingKeys.contains(key)) {
                skipped++;
                continue;
            }

            String codigo = generateFlightCode(
                    candidato.origen.getCodigoAeropuertoEn4Letras(),
                    candidato.destino.getCodigoAeropuertoEn4Letras(),
                    candidato.salida);

            int capacidadMaxima = (candidato.vp.getCapacidadMaxima() != null)
                    ? candidato.vp.getCapacidadMaxima()
                    : 0;
            Vuelo vuelo = new Vuelo(
                    Almacen.desdeEntidad( candidato.origen ),
                    Almacen.desdeEntidad( candidato.destino ),
                    codigo,
                    candidato.salida,
                    candidato.llegada,
                    Math.max(0, capacidadMaxima),
//                    0,
//                    !Objects.equals(candidato.origen.getContinente(),
//                            candidato.destino.getContinente()),
                    false);
//            vuelo.setEsIntercontinental(!Objects.equals(candidato.origen.getContinente(),
//                    candidato.destino.getContinente()));
//            vuelo.setCancelado(false);
            result.add(vuelo);
        }

        return new GenerationResult(result, skipped, errors);
    }

    // ---------------- CRUD ----------------
    private VueloDTO toDTO(VueloEntidad v)
    {
        return new VueloDTO(
                v.getId(),
                v.getCodigo4Letras(),
                v.getAlmacenOrigen() != null ? v.getAlmacenOrigen().getId() : null,
                v.getAlmacenDestino() != null ? v.getAlmacenDestino().getId() : null,
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),
                v.getCancelado(),
                v.getEsIntercontinental(),
                v.getActivo());
    }

    private void apply(VueloEntidad v, VueloCreateUpdateDTO dto)
    {
        v.setCodigo4Letras(dto.codigo4Letras());
        AlmacenEntidad origen = almacenRepository.findById(dto.idAlmacenOrigen()).orElseThrow(
                () -> new IllegalArgumentException("AlmacenEntidad origen no encontrado"));
        AlmacenEntidad destino = almacenRepository.findById(dto.idAlmacenDestino()).orElseThrow(
                () -> new IllegalArgumentException("AlmacenEntidad destino no encontrado"));
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
    public VueloDTO crear(VueloCreateUpdateDTO dto)
    {
        if (!dto.fechaHoraFinUtc().isAfter(dto.fechaHoraInicioUtc()))
            throw new IllegalArgumentException("fechaHoraFinUtc debe ser posterior a inicio");
        VueloEntidad v = new VueloEntidad();
        apply(v, dto);
        vueloRepository.save(v);
        return toDTO(v);
    }

    @Override
    public VueloDTO actualizar(Long id, VueloCreateUpdateDTO dto)
    {
        VueloEntidad v = vueloRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        apply(v, dto);
        return toDTO(v);
    }

    @Override
    public VueloDTO obtener(Long id)
    {
        VueloEntidad v = vueloRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        return toDTO(v);
    }

    @Override
    public Page<VueloDTO> listar(String q, Pageable pageable)
    {
        Page<VueloEntidad> page = vueloRepository.findAll(pageable);
        List<VueloDTO> content = page.getContent().stream()
                .filter(v -> q == null || q.isBlank()
                        || (v.getCodigo4Letras() != null
                                && v.getCodigo4Letras().toLowerCase().contains(q.toLowerCase())))
                .map(this::toDTO).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }
    @Override
    @Transactional(readOnly = true)
    public List<VueloDTO> buscarVuelosSimulados(String q) throws ExcepcionLogica {

        // fuente de verdad de almacenes
        Map<Long, AlmacenEntidad> fuenteDeVerdad = almacenRepository.findAll().stream()
                .collect(Collectors.toMap(AlmacenEntidad::getId, Function.identity()));

        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        if (ctx == null)
            throw new ExcepcionLogica("No hay contexto de simulación cargado en memoria");

        EstadoGlobal estado = ctx.getEstado();
        Collection<Vuelo> vuelosEnMemoria = estado.getVuelos().values();

        String ql = (q == null) ? null : q.trim().toLowerCase();

        return vuelosEnMemoria.stream()
                .filter(v -> {
                    if (ql == null || ql.isEmpty())
                        return true;
                    AlmacenEntidad origen = fuenteDeVerdad.get(v.getAlmacenSalida().getId());
                    AlmacenEntidad destino = fuenteDeVerdad.get(v.getAlmacenDestino());
                    return Long.toString(v.getId()).equalsIgnoreCase(ql)
                            || (v.getCodigo() != null && v.getCodigo().toLowerCase().contains(ql))
                            || (origen != null && origen.getNombreCiudad() != null
                            && origen.getNombreCiudad().toLowerCase().contains(ql))
                            || (destino != null && destino.getNombreCiudad() != null
                            && destino.getNombreCiudad().toLowerCase().contains(ql))
                            || (origen != null && origen.getContinente() != null
                            && origen.getContinente().name().toLowerCase().contains(ql))
                            || (destino != null && destino.getContinente() != null
                            && destino.getContinente().name().toLowerCase().contains(ql))
                            || Integer.toString(v.getCapacidad()).equalsIgnoreCase(ql)
                            || Integer.toString(v.getInventario().size()).equalsIgnoreCase(ql);
                })
                .map(v -> {
                    boolean cancelado = Boolean.TRUE.equals(v.isCancelado());
                    boolean esInter = Boolean.TRUE.equals(v.isIntercontinental());
                    Instant fin = v.getInstanteLlegada();
                    boolean activo = !cancelado && (fin != null && fin.isAfter(Instant.now()));
                    return new VueloDTO(
                            Long.valueOf(v.getId()),
                            v.getCodigo(),
                            Long.valueOf(v.getAlmacenSalida().getId()),
                            Long.valueOf(v.getAlmacenDestino().getId()),
                            v.getInstanteSalida(),
                            v.getInstanteLlegada(),
                            v.getCapacidad(),
                            v.getInventario().size(),
                            cancelado,
                            esInter,
                            activo);
                })
                // opcional: limitar resultados para no reventar el front
                .limit(200)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<VueloDTO> listarVuelosSimulados(String q, Pageable pageable) throws ExcepcionLogica
    {
        // 1) fuente de verdad de almacenes (para nombres, continentes, etc.)
        Map<Long, AlmacenEntidad> fuenteDeVerdad = almacenRepository.findAll().stream()
                .collect(Collectors.toMap(AlmacenEntidad::getId, Function.identity()));

        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        if (ctx == null)
            throw new ExcepcionLogica("No hay contexto de simulación cargado en memoria");

        EstadoGlobal estado = ctx.getEstado();
        Collection<Vuelo> vuelosEnMemoria = estado.getVuelos().values();

        // 2) Filtrar según q (id, código, ciudad origen/destino, continente,
        // capacidades)
        String ql = (q == null) ? null : q.trim().toLowerCase();
        List<VueloDTO> lista = vuelosEnMemoria.stream()
                .filter(v -> {
                    if (ql == null || ql.isEmpty())
                        return true;
                    // obtener almacenes asociados (pueden ser null si no hay coincidencia)
                    AlmacenEntidad origen = fuenteDeVerdad.get(v.getAlmacenSalida().getId());
                    AlmacenEntidad destino = fuenteDeVerdad.get(v.getAlmacenDestino().getId());
                    return Long.toString(v.getId()).equalsIgnoreCase(ql)
                            || (v.getCodigo() != null && v.getCodigo().toLowerCase().contains(ql))
                            || (origen != null && origen.getNombreCiudad() != null
                                    && origen.getNombreCiudad().toLowerCase().contains(ql))
                            || (destino != null && destino.getNombreCiudad() != null
                                    && destino.getNombreCiudad().toLowerCase().contains(ql))
                            || (origen != null && origen.getContinente() != null
                                    && origen.getContinente().name().toLowerCase().contains(ql))
                            || (destino != null && destino.getContinente() != null
                                    && destino.getContinente().name().toLowerCase().contains(ql))
                            || Integer.toString(v.getCapacidad()).equalsIgnoreCase(ql)
                            || Integer.toString(v.getInventario().size()).equalsIgnoreCase(ql);
                })
                .map(v -> {
                    // mapear a DTO
                    boolean cancelado = Boolean.TRUE.equals(v.isCancelado());
                    boolean esInter = Boolean.TRUE.equals(v.isIntercontinental());
                    Instant fin = v.getInstanteLlegada();
                    boolean activo = !cancelado && (fin != null && fin.isAfter(Instant.now()));
                    return new VueloDTO(
                            Long.valueOf(v.getId()),
                            v.getCodigo(),
                            Long.valueOf(v.getAlmacenSalida().getId()),
                            Long.valueOf(v.getAlmacenDestino().getId()),
                            v.getInstanteSalida(),
                            v.getInstanteLlegada(),
                            v.getCapacidad(),
                            v.getInventario().size(),
                            cancelado,
                            esInter,
                            activo);
                })
                .collect(Collectors.toList());

        // 3) Ordenar según pageable.getSort()
        Sort sort = pageable.getSort();
        if (sort != null && sort.isSorted())
        {
            Comparator<VueloDTO> comparator = null;
            for (Sort.Order order : sort)
            {
                Comparator<VueloDTO> c = comparatorForVuelo(order.getProperty());
                if (c == null)
                    continue;
                if (order.isDescending())
                    c = c.reversed();
                comparator = (comparator == null) ? c : comparator.thenComparing(c);
            }
            if (comparator != null)
                lista.sort(comparator);
        }

        // 4) Paginación (skip/limit)
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, pageable.getPageSize());
        long total = lista.size();
        long offset = (long) page * size;

        List<VueloDTO> content;
        if (offset >= total)
        {
            content = Collections.emptyList();
        }
        else
        {
            content = lista.stream().skip(offset).limit(size).collect(Collectors.toList());
        }

        return new PageImpl<>(content, pageable, total);
    }
    // -------------------------
    // Helpers
    // -------------------------

    /**
     * Mapea nombre de propiedad aceptada en sort a Comparator para VueloDTO.
     * Asegúrate de usar los mismos nombres de propiedad que pueden venir por
     * Pageable.
     */
    private Comparator<VueloDTO> comparatorForVuelo(String property)
    {
        return switch (property)
        {
            case "id" -> Comparator.comparing(VueloDTO::id);
            case "codigo4Letras", "codigo" -> Comparator.comparing(VueloDTO::codigo4Letras,
                    Comparator.nullsLast(String::compareToIgnoreCase));
            case "idAlmacenOrigen" -> Comparator.comparing(VueloDTO::idAlmacenOrigen);
            case "idAlmacenDestino" -> Comparator.comparing(VueloDTO::idAlmacenDestino);
            case "fechaHoraInicioUtc", "inicio" -> Comparator.comparing(
                    VueloDTO::fechaHoraInicioUtc, Comparator.nullsLast(Comparator.naturalOrder()));
            case "fechaHoraFinUtc", "fin" -> Comparator.comparing(VueloDTO::fechaHoraFinUtc,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "capacidadMaxima" ->
                Comparator.comparing(v -> Optional.ofNullable(v.capacidadMaxima()).orElse(0));
            case "capacidadOcupada" ->
                Comparator.comparing(v -> Optional.ofNullable(v.capacidadOcupada()).orElse(0));
            case "cancelado" ->
                Comparator.comparing(v -> Optional.ofNullable(v.cancelado()).orElse(false));
            case "esIntercontinental" -> Comparator
                    .comparing(v -> Optional.ofNullable(v.esIntercontinental()).orElse(false));
            case "activo" ->
                Comparator.comparing(v -> Optional.ofNullable(v.activo()).orElse(false));
            default -> null;
        };
    }

    @Override
    public List<VueloDTO> obtenerTodos()
    {
        // ✅ NUEVO: Devuelve TODOS los vuelos activos sin paginación (para simulación)
        return vueloRepository.findByActivoTrue()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public void eliminar(Long id)
    {
        VueloEntidad v = vueloRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("VueloEntidad no encontrado"));
        v.setActivo(false);
    }

    // --- helpers ---

    /**
     * Crea ZoneOffset a partir del valor entero gmt (horas). Devuelve null y agrega
     * error si no puede.
     */
    private ZoneOffset zoneOffsetFromGmt(Integer gmt, List<String> errors, AlmacenEntidad almacen)
    {
        if (gmt == null)
        {
            errors.add("AlmacenEntidad id=" + almacen.getId() + " sin campo gmt");
            return null;
        }
        try
        {
            return ZoneOffset.ofHours(gmt);
        }
        catch (Exception ex)
        {
            errors.add("AlmacenEntidad id=" + almacen.getId() + " gmt inválido: " + gmt);
            return null;
        }
    }

    /**
     * Genera código legible para vuelo: ORI-DEST-YYYYMMDD-HHMM (hora de salida en
     * UTC)
     */
    private String generateFlightCode(String origenCode, String destinoCode, Instant salidaInstant)
    {
        LocalDateTime ldt = LocalDateTime.ofInstant(salidaInstant, ZoneOffset.UTC);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
        return String.format("%s-%s-%s", origenCode, destinoCode, ldt.format(f));
    }

    @Override
    @Transactional(readOnly = true)
    public VueloCardDTO devolverCard(Long id)
    {
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        assert ctx != null;

        Vuelo wa = ctx.getEstado().getVuelos().get(id);

        List<PedidoResumenDTO> was = pedidoService.obtenerResumenPedidosEnVuelo(wa);

        // ✅ CORRECCIÓN: Obtener capacidad ocupada del objeto de dominio en el estado de
        // simulación
        // El objeto Vuelo en EstadoGlobal es el que se actualiza en tiempo real durante
        // la simulación
        int capacidadOcupada = wa.getInventario().size(); // Default: desde BD
        int capacidadMaxima = wa.getCapacidad();

        // Si hay simulación activa, obtener valores del estado global (actualizados en
        // tiempo real)
        if (ctx != null && ctx.getEstado() != null)
        {
            pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo vueloEnSimulacion = ctx.getEstado()
                    .getVuelos().get(id);

            if (vueloEnSimulacion != null)
            {
                capacidadOcupada = vueloEnSimulacion.getInventario().size();
                capacidadMaxima = vueloEnSimulacion.getCapacidad();
            }
        }
        HashMap<Long, Almacen> alms = ctx.getEstado().getAlmacenes();

        VueloCardDTO res = new VueloCardDTO(
                wa.getId(),
                wa.getCodigo(),
                capacidadOcupada, // ✅ Ahora usa el valor del estado de simulación
                capacidadMaxima, // ✅ También actualizado
                alms.get(wa.getAlmacenSalida().getId()).getNombreCiudad(),
                alms.get(wa.getAlmacenDestino().getId()).getNombreCiudad(),
                wa.getInstanteSalida(),
                wa.getInstanteLlegada(),
                wa.getEstadoEnInstante(ctx.obtenerElAhora()),
                was);
        return res;
    }

    // cancelaciones
    // acepta HHmm, H:mm, HH:mm
    private static final DateTimeFormatter HHMM_FMT_1 = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter HHMM_FMT_2 = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HHMM_FMT_3 = DateTimeFormatter.ofPattern("HH:mm");

    // regex: dd.ORIGEN-DESTINO-Hora
    private static final Pattern LINE_PATTERN = Pattern
            .compile("^(\\d{2})\\.([A-Za-z0-9]{3,})-([A-Za-z0-9]{3,})-([0-2]?\\d:?\\d{2})\\s*$");

    @Override
    public ProcessResult procesarArchivoDeCancelados(MultipartFile file, LocalDate referenceDate, boolean paraMemoria)
            throws Exception {
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int total = 0;

        Map<String, AlmacenEntidad> almacenesPorCodigo = almacenRepository.listarTodosAlmacenes()
                .stream()
                .collect(Collectors.toMap(
                        AlmacenEntidad::getCodigoAeropuertoEn4Letras,
                        almacenEntidad -> almacenEntidad));

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                total++;
                line = line.trim();
                if (line.isEmpty())
                    continue;

                Matcher m = LINE_PATTERN.matcher(line);
                if (!m.matches()) {
                    errors.add("Línea inválida (formato): " + line);
                    continue;
                }
                try {
                    int dd = Integer.parseInt(m.group(1));
                    String origenCode = m.group(2).toUpperCase(Locale.ROOT);
                    String destinoCode = m.group(3).toUpperCase(Locale.ROOT);
                    String horaStr = m.group(4);

                    Optional<AlmacenEntidad> optOrigen = Optional
                            .of(almacenesPorCodigo.get(origenCode));// almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(origenCode);
                    Optional<AlmacenEntidad> optDestino = Optional
                            .of(almacenesPorCodigo.get(destinoCode));// almacenRepository.findByCodigoAeropuertoEn4LetrasIgnoreCase(destinoCode);

                    if (optOrigen.isEmpty()) {
                        errors.add("Origen no encontrado: " + origenCode + " en línea: " + line);
                        continue;
                    }
                    if (optDestino.isEmpty()) {
                        errors.add("Destino no encontrado: " + destinoCode + " en línea: " + line);
                        continue;
                    }

                    AlmacenEntidad origen = optOrigen.get();
                    AlmacenEntidad destino = optDestino.get();

                    LocalTime hora = parseHora(horaStr);
                    if (hora == null) {
                        errors.add("Hora inválida: " + horaStr + " en línea: " + line);
                        continue;
                    }

                    // zona/origen offset
                    ZoneOffset offsetOrigen = ZoneOffset.ofHours(originGmtSafe(origen));
                    // fecha local = referenceDate en offset origen
                    LocalDate dateLocal = referenceDate;

                    // salida instant (UTC) desde local date + hora en offset origen
                    ZonedDateTime salidaZdt = ZonedDateTime.of(dateLocal, hora, offsetOrigen);
                    Instant salidaInstant = salidaZdt.toInstant();

                    // fechaHoraFinUtc = inicio + dd días
                    // Instant finInstant = salidaInstant.plus(Duration.ofDays(Math.max(1, dd)));

                    // generar codigo (usa mismo generador que generó vuelos)
                    String codigoGenerado = generateFlightCode(
                            origen.getCodigoAeropuertoEn4Letras(),
                            destino.getCodigoAeropuertoEn4Letras(), salidaInstant);

                    CancelacionVuelo entity = CancelacionVuelo.builder()
                            .almacenOrigen(origen)
                            .almacenDestino(destino)
                            .fechaCancelacion(salidaInstant)
                            .codigoGeneradoCoincidenteConVuelo(codigoGenerado)
                            .build();

                    cancelacionVueloRepository.save(entity);
                    saved++;
                }
                catch (Exception exLine) {
                    errors.add("Error procesando línea: " + line + " -> " + exLine.getMessage());
                }
            } // while
        }

        return new ProcessResult(total, saved, errors);
    }

    private int originGmtSafe(AlmacenEntidad a)
    {
        if (a == null || a.getGmt() == null)
            return 0;
        return a.getGmt();
    }

    private LocalTime parseHora(String horaStr)
    {
        horaStr = horaStr.trim();
        // aceptar HHmm, H:mm, HH:mm
        try
        {
            if (horaStr.contains(":"))
            {
                // H:mm or HH:mm
                return LocalTime.parse(horaStr, HHMM_FMT_3);
            }
            else
            {
                // HHmm
                return LocalTime.parse(horaStr, HHMM_FMT_1);
            }
        }
        catch (Exception e1)
        {
            try
            {
                return LocalTime.parse(horaStr, HHMM_FMT_2);
            }
            catch (Exception e2)
            {
                return null;
            }
        }
    }

    @Override
    public boolean agregarCanceladoMemoria(Long id, Instant instante){
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        assert ctx != null;

        EstadoGlobal estado = ctx.getEstado();

        Vuelo vueloSeleccionado  = estado.getVuelos().get(id);
        if( vueloSeleccionado == null )
            return false;

        if( vueloSeleccionado.yaPartio(ctx.getAhora())){  // No tiene sentido cancelar vuelo que ya partió en la simu
            return false;
        }

        if( instante == null || instante.isBefore(ctx.getAhora())) // <- defensivo y no damos fechas pasadas incoherentes
            instante = ctx.getAhora(); // Se cancelará inmediatamente

//        vueloSeleccionado.setCancelado(true); <- EL MISMO EVENTO ALTERA EL ESTADO, MEJOR NO HACERLO EN EL SERVICIO

        ctx.programarEvento(new EventoCancelacionVuelo( // Programamos el evento para ahora si mandó null, o para la hora respectiva
                id,
                UUID.randomUUID(),
                instante,
                planificacionService,
                webSocketService,
                configuracionService));

        return true;
    }

}
