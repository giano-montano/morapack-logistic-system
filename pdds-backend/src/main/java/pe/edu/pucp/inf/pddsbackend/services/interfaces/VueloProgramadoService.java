package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloDTO;

public interface VueloProgramadoService {

    Page<VueloDTO> listarVuelosProgramados(Pageable pageable, boolean soloActivos);

}
