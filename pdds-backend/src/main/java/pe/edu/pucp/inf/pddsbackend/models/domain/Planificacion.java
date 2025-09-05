package pe.edu.pucp.inf.pddsbackend.models.domain;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/*Esta es la abstracción de negocio de la solución que brinda el algoritmo */
@AllArgsConstructor
@Builder
@Data
public class Planificacion {
    Long id;

    List<Envio> enviosProgramados;
}
