package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"almacenOrigen","almacenDestino"})
public class CancelacionVuelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_origen_id")
    private AlmacenEntidad almacenOrigen;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private AlmacenEntidad almacenDestino;

    @Column(nullable = false)
    private Instant fechaCancelacion;

    @Column(nullable = false)
    private String codigoGeneradoCoincidenteConVuelo;

}
/*
dd.id-vuelo

Donde
dd: días en dos posiciones 01, 04, 12, 24
id-vuelo : ORIGEN-DESTINO-HoraOrigen

 */