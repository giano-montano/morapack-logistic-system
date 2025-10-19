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
@EqualsAndHashCode
public class Vuelo
{
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

        this.capacidadOcupada = 0L;

        if (origen.getContinente() == destino.getContinente())
        {
            this.esIntercontinental = true;
        }
        else
        {
            this.esIntercontinental = false;
        }
    }

}
