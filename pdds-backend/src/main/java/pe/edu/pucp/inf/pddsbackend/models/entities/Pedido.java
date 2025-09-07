package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoPedido;

import java.time.Instant;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private Almacen almacenDestino;

    private Integer cantidadProductosTotal;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer cantidadProductosEntregados;

    @ColumnDefault("0")
    Integer cantidadProductosProgramados; //TRANSITIVO!

    private Instant instanteRegistro;
    private Instant instanteMaximoParaEntregar; // no realmente necesario, pero para evitar recomputar.

    @ColumnDefault("false")
    private Boolean atendidoCompletamente; // cuando cantEntregados >= cantPedidos
    private Boolean colapsado=false;

    //creo que esta columna depende mucho de los envíos (transitivo), mejor consultar los envíos por sobre este estado en sí, luego vemos qué hacemos
    @Enumerated(EnumType.STRING)  // Almacena como VARCHAR en la BD
    @Column(
            columnDefinition = "enum('POR_PROGRAMAR','PROGRAMADO', 'EN_CURSO', 'ENTREGADO', 'FALLIDO' ) default 'POR_PROGRAMAR' ", // no seríanecesario si no quiero default.
            name = "estado",
            nullable = false)
//    @ColumnDefault("POR_PROGRAMAR")
    private EstadoPedido estado;

}
