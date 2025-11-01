package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import java.util.List;

public record RutaProgramadaResumenDTO(
        List<String> almacenesEnRuta,
        Integer cantidadAEntregar
) {
}
