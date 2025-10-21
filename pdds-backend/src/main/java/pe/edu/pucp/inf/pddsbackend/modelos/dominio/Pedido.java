package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.time.Instant;
import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Pedido
{
    private final UUID id;
    private final Long cantidad;
    private final Instant instanteRegistro;
    private final Almacen destino;

    private Boolean esIntercontinental;
    private Long cantidadEntregada, cantidadExistente;
    private Instant instanteEntrega;
    private List<Producto> productosEntregados, productosExistentes;

    public Pedido(UUID id,
            Long cantidad,
            Instant instanteRegistro,
            Almacen destino)
    {
        this.id = id;
        this.cantidad = cantidad;
        this.instanteRegistro = instanteRegistro;
        this.destino = destino;

        this.esIntercontinental = false;
        this.cantidadEntregada = 0L;
        this.cantidadExistente = 0L;
        this.instanteEntrega = instanteRegistro.plus(Duration.ofDays(2));
        this.productosEntregados = new ArrayList<>();
        this.productosExistentes = new ArrayList<>();
    }

}
