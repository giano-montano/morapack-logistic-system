package pe.edu.pucp.inf.pddsbackend.models.domain;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class Vuelo {
    Long id;

    Instant inicio;
    Instant fin;
    Almacen origen;
    Almacen destino;

    Integer capacidadMaximaProductos;
    Integer capacidadOcupadaProductos;

    EstadoVuelo estado; // en curso, cancelado, en espera, finalizado

    List<Envio> enviosQueElVueloAtiendeComoEscalaOComoTodo; // podría no estar este atributo
    // pero para el domino prefiero dejarlo claro

}
