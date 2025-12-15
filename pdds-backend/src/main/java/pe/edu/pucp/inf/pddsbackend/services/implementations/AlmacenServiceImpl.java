package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenCreateUpdateDTO;
import pe.edu.pucp.inf.pddsbackend.dto.almacenes.AlmacenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoResumenDTO;
import pe.edu.pucp.inf.pddsbackend.exceptions.ExcepcionLogica;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Continente;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.repositories.AlmacenRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.AlmacenService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlmacenServiceImpl implements AlmacenService
{

    private final Logger log = LoggerFactory.getLogger(AlmacenServiceImpl.class);
    private final AlmacenRepository almacenRepository;
    private static final int BATCH_SIZE = 100;
    private final PedidoService pedidoService;

    @Override
    @Transactional
    public ProcessResult cargarAlmacenesEnBDDesdeArchivoDelProfe(InputStream inputStream)
    {
        List<AlmacenEntidad> batch = new ArrayList<>(BATCH_SIZE);
        List<String> errors = new ArrayList<>();
        int saved = 0;
        int skipped = 0;
        String currentSection = null; // para detectar continente

        Pattern intPattern = Pattern.compile("(\\d+)");
        int lineNo = 0;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                lineNo++;
                // limpiar control chars (BOM, etc.)
                line = line.replaceAll("\\p{C}", "").trim();
                if (line.isEmpty())
                    continue;

                // saltar encabezados/separadores conocidos
                String low = line.toLowerCase();
                if (low.startsWith("pdds") || low.startsWith("***") || line.startsWith("*****")
                        || low.contains("gmt  capacidad"))
                {
                    continue;
                }
                // detectar sección / continente
                if (low.contains("america") && low.contains("sur"))
                {
                    currentSection = "AMERICA_SUR";
                    continue;
                }
                else if (low.contains("europa"))
                {
                    currentSection = "EUROPA";
                    continue;
                }
                else if (low.contains("asia"))
                {
                    currentSection = "ASIA";
                    continue;
                }

                // detectar línea de datos que comienza con número de 2 dígitos (ej. "01 SKBO
                // ...")
                if (!line.matches("^\\d{2}\\s+.*"))
                {
                    // no es línea de datos, ignorar
                    continue;
                }

                // dividir por 2+ espacios (columnas)
                String[] cols = line.split("\\s{2,}");
                // Esperamos al menos: [num, airport, city, country, codeCity, gmt, capacidad,
                // "Latitude: ... Longitude: ..."]
                if (cols.length < 8)
                {
                    errors.add("Línea " + lineNo + ": formato de columnas inesperado -> \"" + line
                            + "\"");
                    skipped++;
                    continue;
                }

                try
                {
                    String codigoAeropuerto = cols[1].trim();
                    String nombreCiudadRaw = cols[2].trim();
                    String nombrePais = cols[3].trim();
                    String codigoCiudad4 = cols[4].trim();
                    // algunos archivos usan +2 o +3 con signo; usamos trim y parse
                    String gmtStr = cols[5].trim();
                    String capacidadStr = cols[6].trim();
                    String latLonField = cols[7].trim();
                    // en caso la última columna tenga más splits (ej. si la ciudad contiene dobles
                    // espacios), rejoin:
                    if (cols.length > 8)
                    {
                        StringBuilder sb = new StringBuilder(latLonField);
                        for (int i = 8; i < cols.length; i++)
                        {
                            sb.append("  ").append(cols[i]);
                        }
                        latLonField = sb.toString().trim();
                    }

                    // parse gmt y capacidad (tolerante a +/-, espacios)
                    Integer gmt = 0;
                    try
                    {
                        gmt = Integer.parseInt(gmtStr.replace("+", "").trim());
                        // si venía con signo positivo, lo dejamos tal cual (+2 -> 2)
                        if (gmtStr.startsWith("-"))
                            gmt = -Math.abs(gmt);
                    }
                    catch (NumberFormatException nfe)
                    {
                        // fallback: buscar números en el token
                        Matcher im = intPattern.matcher(gmtStr);
                        if (im.find())
                        {
                            gmt = Integer.parseInt(im.group(1));
                        }
                    }
                    Integer capacidad = 0;
                    try
                    {
                        capacidad = Integer.parseInt(capacidadStr.trim());
                    }
                    catch (NumberFormatException nfe)
                    {
                        Matcher im2 = intPattern.matcher(capacidadStr);
                        if (im2.find())
                            capacidad = Integer.parseInt(im2.group(1));
                    }

                    // parse latitude / longitude a partir del campo latLonField
                    // buscamos "Latitude:" y "Longitude:" (case-insensitive)
                    String latPart = null, lonPart = null;
                    int idxLat = indexOfIgnoreCase(latLonField, "latitude:");
                    int idxLon = indexOfIgnoreCase(latLonField, "longitude:");
                    if (idxLat >= 0 && idxLon > idxLat)
                    {
                        latPart = latLonField.substring(idxLat + "latitude:".length(), idxLon)
                                .trim();
                        lonPart = latLonField.substring(idxLon + "longitude:".length()).trim();
                    }
                    else
                    {
                        // si no están en ese orden, intentar encontrar por palabras
                        // fallback: saltar fila si no contiene coordenadas mínimas
                        if (latLonField.toLowerCase().contains("latitude")
                                && latLonField.toLowerCase().contains("longitude"))
                        {
                            // intentar extracción por tokens
                            int lIdx = latLonField.toLowerCase().indexOf("latitude");
                            int loIdx = latLonField.toLowerCase().indexOf("longitude");
                            if (lIdx >= 0 && loIdx > lIdx)
                            {
                                latPart = latLonField.substring(lIdx + 8, loIdx).replace(":", "")
                                        .trim();
                                lonPart = latLonField.substring(loIdx + 9).replace(":", "").trim();
                            }
                        }
                    }

                    if (latPart == null || lonPart == null)
                    {
                        errors.add("Línea " + lineNo
                                + ": coordenadas no encontradas correctamente -> \"" + latLonField
                                + "\"");
                        skipped++;
                        continue;
                    }

                    // extraer 3 números (deg,min,sec) y la letra direccional para lat
                    int[] latNums = extractThreeInts(latPart);
                    int[] lonNums = extractThreeInts(lonPart);
                    Character latDir = extractDir(latPart, "NS");
                    Character lonDir = extractDir(lonPart, "EW");
                    if (latNums == null || lonNums == null || latDir == null || lonDir == null)
                    {
                        errors.add("Línea " + lineNo
                                + ": no se pudo extraer DMS/dirección -> latPart='" + latPart
                                + "' lonPart='" + lonPart + "'");
                        skipped++;
                        continue;
                    }

                    double latDecimal = dmsToDecimal(latNums[0], latNums[1], latNums[2], latDir);
                    double lonDecimal = dmsToDecimal(lonNums[0], lonNums[1], lonNums[2], lonDir);

                    Continente continente = parseContinentFromSection(currentSection);
                    boolean esInfinito = isInfiniteStore(codigoCiudad4.toLowerCase(),
                            nombreCiudadRaw.toLowerCase());

                    AlmacenEntidad almacen = AlmacenEntidad.builder()
                            .esInfinito(esInfinito)
                            .capacidadMaxima(capacidad)
                            .capacidadOcupada(0)
                            .codigoAeropuertoEn4Letras(codigoAeropuerto)
                            .codigoCiudadEn4Letras(codigoCiudad4)
                            .nombreCiudad(nombreCiudadRaw)
                            .nombrePais(nombrePais)
                            .latitud(latDecimal)
                            .longitud(lonDecimal)
                            .gmt(gmt)
                            .continente(continente)
                            .activo(true)
                            .build();

                    batch.add(almacen);
                    if (batch.size() >= BATCH_SIZE)
                    {
                        almacenRepository.saveAll(batch);
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
                almacenRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }
        }
        catch (IOException ex)
        {
            log.error("Error leyendo el stream: ", ex);
            errors.add("IOException: " + ex.getMessage());
        }

        return new ProcessResult(saved, skipped, errors);
    }

    // ------------------ CRUD ------------------
    private AlmacenDTO toDTO(AlmacenEntidad a)
    {
        return new AlmacenDTO(
                a.getId(),
                a.getCodigoAeropuertoEn4Letras(),
                a.getCodigoCiudadEn4Letras(),
                a.getNombreCiudad(),
                a.getNombrePais(),
                a.getLatitud(),
                a.getLongitud(),
                a.getGmt(),
                a.getContinente() != null ? a.getContinente().name() : null,
                a.getCapacidadMaxima(),
                a.getInventario().size(),
                a.getEsInfinito(),
                a.getActivo());
    }

    private void apply(AlmacenEntidad a, AlmacenCreateUpdateDTO dto)
    {
        a.setCodigoAeropuertoEn4Letras(dto.codigoAeropuertoEn4Letras());
        a.setCodigoCiudadEn4Letras(dto.codigoCiudadEn4Letras());
        a.setNombreCiudad(dto.nombreCiudad());
        a.setNombrePais(dto.nombrePais());
        a.setLatitud(dto.latitud());
        a.setLongitud(dto.longitud());
        a.setGmt(dto.gmt());
        try
        {
            a.setContinente(Continente.valueOf(dto.continente()));
        }
        catch (Exception ex)
        {
            a.setContinente(Continente.SUDAMERICA);
        } // fallback
        a.setCapacidadMaxima(dto.capacidadMaxima());
        a.setCapacidadOcupada(dto.capacidadOcupada());
        a.setEsInfinito(dto.esInfinito());
    }

    @Override
    @Transactional
    public AlmacenDTO crear(AlmacenCreateUpdateDTO dto)
    {
        AlmacenEntidad a = new AlmacenEntidad();
        apply(a, dto);
        a.setActivo(true);
        almacenRepository.save(a);
        return toDTO(a);
    }

    @Override
    @Transactional
    public AlmacenDTO actualizar(Long id, AlmacenCreateUpdateDTO dto)
    {
        AlmacenEntidad a = almacenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad no encontrado"));
        apply(a, dto);
        return toDTO(a);
    }

    @Override
    @Transactional(readOnly = true)
    public AlmacenDTO obtener(Long id)
    {
        AlmacenEntidad a = almacenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad no encontrado"));
        return toDTO(a);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlmacenDTO> listar(String q, Pageable pageable)
    {
        // Búsqueda simple en memoria si la cantidad es baja. Para grandes volúmenes
        // crear query.
        Page<AlmacenEntidad> page = almacenRepository.findAll(pageable);
        List<AlmacenDTO> content = page.getContent().stream()
                .filter(a -> q == null || q.isBlank()
                        || a.getCodigoAeropuertoEn4Letras().toLowerCase().contains(q.toLowerCase())
                        || a.getNombreCiudad().toLowerCase().contains(q.toLowerCase()))
                .map(this::toDTO)
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AlmacenDTO> listarSimulados(String q, Pageable pageable) throws ExcepcionLogica
    {

        Map<Long, AlmacenEntidad> fuenteDeVerdad = almacenRepository.findAll().stream()
                .collect(Collectors.toMap(AlmacenEntidad::getId, e -> e));

        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        if (ctx == null)
            throw new ExcepcionLogica("No hay contexto de simulación cargado en memoria");

        EstadoGlobal estado = ctx.getEstado();

        Collection<Almacen> almacenes = estado.getAlmacenes().values().stream().filter(
                a -> q == null || q.isBlank()
                        || a.getCodigoAeropuertoEn4Letras().toLowerCase().contains(q.toLowerCase())
                        || a.getNombreCiudad().toLowerCase().contains(q.toLowerCase()))
                .toList();
        List<AlmacenDTO> lista = almacenes.stream().map(a -> {
            AlmacenEntidad real = fuenteDeVerdad.get(a.getId());
            return new AlmacenDTO(
                    a.getId(), a.getCodigoAeropuertoEn4Letras(), a.getCodigoCiudadEn4Letras(),
                    a.getNombreCiudad(),
                    a.getNombrePais(), real.getLatitud(), real.getLongitud(), real.getGmt(),
                    real.getContinente().name(),
                    a.getCapacidad(), a.getInventario().size(), a.isInfinito(),
                    real.getActivo());
        }).collect(Collectors.toList());

        Page<AlmacenDTO> pages = new PageImpl<AlmacenDTO>(lista, pageable, almacenes.size());

        return pages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlmacenDTO> obtenerTodos()
    {
        // Devuelve TODOS los almacenes activos sin paginación (para simulación)
        return almacenRepository.findAll().stream()
                .filter(AlmacenEntidad::getActivo) // Solo almacenes activos
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public void eliminar(Long id)
    {
        AlmacenEntidad a = almacenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad no encontrado"));
        a.setActivo(false); // soft delete
    }

    private double dmsToDecimal(int deg, int min, int sec, char dir)
    {
        double decimal = deg + min / 60.0 + sec / 3600.0;
        if (dir == 'S' || dir == 's' || dir == 'W' || dir == 'w')
        {
            decimal = -decimal;
        }
        return decimal;
    }

    private Continente parseContinentFromSection(String section)
    {
        if (section == null)
        {
            // fallback por defecto: SUDAMERICA (puedes cambiar)
            return Continente.SUDAMERICA;
        }
        switch (section)
        {
            case "SUDAMERICA" :
                return Continente.SUDAMERICA;
            case "EUROPA" :
                return Continente.EUROPA;
            case "ASIA" :
                return Continente.ASIA;
            default :
                return Continente.SUDAMERICA;
        }
    }

    private boolean isInfiniteStore(String codigoCiudad4Lower, String nombreCiudadLower)
    {
        // Acepta tanto el código "lima"/"brus"/"baku" como el nombreCiudad de ciudad
        if ("lima".equalsIgnoreCase(codigoCiudad4Lower)
                || "lima".equalsIgnoreCase(nombreCiudadLower))
            return true;
        if ("brus".equalsIgnoreCase(codigoCiudad4Lower) || nombreCiudadLower.contains("brus"))
            return true;
        if ("baku".equalsIgnoreCase(codigoCiudad4Lower) || nombreCiudadLower.contains("baku"))
            return true;
        // Si quieres usar otros identificadores (ej.: códigos IATA), agrégalos aquí.
        return false;
    }

    private static int indexOfIgnoreCase(String source, String target)
    {
        return source.toLowerCase().indexOf(target.toLowerCase());
    }

    private static int[] extractThreeInts(String text)
    {
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(text);
        int[] nums = new int[3];
        int i = 0;
        while (m.find() && i < 3)
        {
            nums[i++] = Integer.parseInt(m.group(1));
        }
        return i == 3 ? nums : null;
    }

    private static Character extractDir(String text, String allowed)
    {
        // devuelve primera ocurrencia de char en allowed (ej. 'N' o 'S' para lat)
        for (char c : text.toCharArray())
        {
            if (allowed.indexOf(Character.toUpperCase(c)) >= 0)
                return Character.toUpperCase(c);
        }
        return null;
    }

    @Override
    public AlmacenCardDTO devolverCardAlmacen(Long id)
    {
        AlmacenEntidad wa = almacenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AlmacenEntidad no encontrado"));
        List<PedidoResumenDTO> was = pedidoService.obtenerResumenPedidosParaAlmacen(wa);
        AlmacenCardDTO res = new AlmacenCardDTO(wa.getId(), wa.getNombreCiudad(),
                wa.getInventario().size(), wa.getCapacidadMaxima(), was);
        return res;

    }

}
