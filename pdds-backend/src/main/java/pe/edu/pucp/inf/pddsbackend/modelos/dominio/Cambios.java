package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Cambios implements Serializable
{
    private Integer inventarioInicial;
    private Map<Instant, Integer> cambios;

    public Cambios(Integer cantidadOriginal)
    {
        this.inventarioInicial = cantidadOriginal;
        this.cambios = new TreeMap<>();
    }

    public void add(Instant instante, Integer cambio)
    {
        this.cambios.merge(instante, cambio, Integer::sum);
    }

    public Integer calcularInventarioEnInstante(Instant instante)
    {
        Integer inventarioEnInstante;

        inventarioEnInstante = this.inventarioInicial;

        for (var cambio : this.cambios.entrySet())
        {
            if (cambio.getKey().isAfter(instante))
            {
                break;
            }
            inventarioEnInstante += cambio.getValue();
        }

        return inventarioEnInstante;
    }
}
