package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "programacion")
public class ProgramacionEntidad
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PedidoEntidad pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProductoEntidad producto;

    @Column(nullable = false)
    private Integer cantidadTotalOParcial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Planificacion planificacion;
}
