package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Vuelo
{
    @EqualsAndHashCode.Include
    private final UUID id;

    private final Boolean esIntercontinental;
    private final Long capacidad;
    private final Almacen origen, destino;
    private final Instant instanteSalida, instanteLlegada;

    private Long capacidadOcupada;

    public Vuelo(UUID id,
            Long capacidad,
            Almacen origen,
            Almacen destino,
            Instant horaSalida,
            Instant horaLlegada)
    {
        this.id = id;
        this.capacidad = capacidad;
        this.origen = origen;
        this.destino = destino;
        this.instanteSalida = horaSalida;
        this.instanteLlegada = horaLlegada;
        this.esIntercontinental = Almacen.esIntercontinental(origen, destino);

        this.capacidadOcupada = 0L;
    }
}
