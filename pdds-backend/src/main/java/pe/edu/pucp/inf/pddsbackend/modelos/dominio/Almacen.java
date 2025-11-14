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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Almacen
{
    @EqualsAndHashCode.Include
    private final String id;

    private final String ciudad, pais;
    private final Long capacidad, utc;
    private final Continente continente;

    private Long inventario;
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

        this.inventario = 0L;
        this.productos = new ArrayList<>();
    }

    /*
     * Método para comparar dos almacenes y saber si están en continentes diferentes
     */
    public static Boolean esIntercontinental(Almacen origen, Almacen destino)
    {
        return origen.continente != destino.continente;
    }
}
