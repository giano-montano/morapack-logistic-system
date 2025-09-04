package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import pe.edu.pucp.inf.pddsbackend.models.domain.EstadoVuelo;

import java.time.Instant;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vuelo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_origen_id")
    private Almacen almacenOrigen;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "almacen_destino_id")
    private Almacen almacenDestino;

    private Instant fechaHoraInicio;

    private Instant fechaHoraFin;

    @Enumerated(EnumType.STRING)  // Almacena como VARCHAR en la BD
    @Column(
            name = "estado",
            nullable = false)
    private EstadoVuelo estado;


    Integer capacidadMaximaProductos;

    @ColumnDefault("0")
    Integer capacidadOcupadaProductos; // si el avión aún está en estado EN_ESPERA y esto tiene > 0; significa en reserva (?)
}
