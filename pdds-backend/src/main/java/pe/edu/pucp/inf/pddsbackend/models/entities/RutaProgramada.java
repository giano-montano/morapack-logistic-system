package pe.edu.pucp.inf.pddsbackend.models.entities;


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
public class RutaProgramada  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Pedido pedido;

    @Column(nullable = false)
    private Integer cantidadTotalOParcial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Planificacion planificacion;
}
