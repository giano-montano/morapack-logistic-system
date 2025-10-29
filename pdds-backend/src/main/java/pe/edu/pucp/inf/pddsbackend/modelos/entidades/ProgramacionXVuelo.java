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
public class ProgramacionXVuelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProgramacionEntidad rutaProgramada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private VueloEntidad vuelo;

    private Byte orden;
}
