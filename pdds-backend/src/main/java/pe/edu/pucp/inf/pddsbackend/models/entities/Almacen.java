package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Almacen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ColumnDefault("false")
    private Boolean esInfinito;

    private Integer capacidadTotal; //imagino es suficiente, no creo que superemos los 2,147,483,647 prods

    @ColumnDefault("0")
    private Integer capacidadOcupada;
    @ColumnDefault("0")
    Integer capacidadReservadaPorEnvios; // tal vez... pero es TRANSITIVO

//    @ManyToOne(
//            fetch = FetchType.LAZY,
//            optional = false
//    )
//    private Ciudad ciudad;
    private String codigoCiudadEn4Letras; // no está bien, pero porque el caso es simplificado y la relación
    // es siempre Uno a uno, no hay problema práctico.


    // Aeropuerto, oficina,... Son uno a uno.
}
