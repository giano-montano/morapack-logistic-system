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
    private final Almacen almacenOrigen, almacenDestino;
    private final Instant instanteSalida, instanteLlegada;

    private Long inventario;

    public Vuelo(UUID id,
            Long capacidad,
            Almacen almacenOrigen,
            Almacen almacenDestino,
            Instant horaSalida,
            Instant horaLlegada)
    {
        this.id = id;
        this.capacidad = capacidad;
        this.almacenOrigen = almacenOrigen;
        this.almacenDestino = almacenDestino;
        this.instanteSalida = horaSalida;
        this.instanteLlegada = horaLlegada;
        this.esIntercontinental = Almacen.esIntercontinental(almacenOrigen, almacenDestino);

        this.inventario = 0L;
    }
}
