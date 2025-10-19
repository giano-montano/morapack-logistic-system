package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Almacen
{
    private final String id, ciudad, pais;
    private final Long capacidad, utc;
    private final Continente continente;

    private Long capacidadOcupada;
    private List<Producto> productos;

    public Almacen(String id,
            Long capacidad,
            Long utc,
            String ciudad,
            String pais,
            Continente continente)
    {
        this.id = id;
        this.capacidad = capacidad;
        this.utc = utc;
        this.ciudad = ciudad;
        this.pais = pais;
        this.continente = continente;

        this.capacidadOcupada = 0L;
        this.productos = new ArrayList<>();
    }
}
