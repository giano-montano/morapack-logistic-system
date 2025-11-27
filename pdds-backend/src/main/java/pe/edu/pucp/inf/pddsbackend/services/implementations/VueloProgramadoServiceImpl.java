package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloProgramado;
import pe.edu.pucp.inf.pddsbackend.repositories.VueloProgramadoRepository;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.VueloProgramadoService;

import java.time.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VueloProgramadoServiceImpl implements VueloProgramadoService {

    private final VueloProgramadoRepository vueloProgramadoRepository;

    @Override
    public Page<VueloDTO> listarVuelosProgramados(Pageable pageable, boolean soloActivos) {

        Page<VueloProgramado> page = soloActivos
                ? vueloProgramadoRepository.findByActivoTrue(pageable)
                : vueloProgramadoRepository.findAll(pageable);

        return page.map(this::toDto);
    }

    private VueloDTO toDto(VueloProgramado v) {

        int gmt = 0;
        if (v.getAlmacenOrigen() != null && v.getAlmacenOrigen().getGmt() != null) {
            gmt = v.getAlmacenOrigen().getGmt();
        }
        ZoneOffset offsetOrigen = ZoneOffset.ofHours(gmt);
        LocalDate fechaReferencia = LocalDate.now();

        Instant inicioUtc = null;
        Instant finUtc = null;

        if (v.getHoraInicioEnPropioHuso() != null) {
            inicioUtc = ZonedDateTime.of(fechaReferencia, v.getHoraInicioEnPropioHuso(), offsetOrigen).toInstant();
        }
        if (v.getHoraFinEnPropioHuso() != null) {
            finUtc = ZonedDateTime.of(fechaReferencia, v.getHoraFinEnPropioHuso(), offsetOrigen).toInstant();
        }

        // codigo4Letras = código de aeropuerto ORIGEN
        String codigo = null;
        if (v.getAlmacenOrigen() != null) {
            codigo = v.getAlmacenOrigen().getCodigoAeropuertoEn4Letras();
        }

        return new VueloDTO(
                v.getId(),
                codigo,
                v.getAlmacenOrigen() != null ? v.getAlmacenOrigen().getId() : null,
                v.getAlmacenDestino() != null ? v.getAlmacenDestino().getId() : null,
                inicioUtc,
                finUtc,
                v.getCapacidadMaxima(),
                0,
                false,
                v.getEsIntercontinental(),
                v.getActivo()
        );
    }
}