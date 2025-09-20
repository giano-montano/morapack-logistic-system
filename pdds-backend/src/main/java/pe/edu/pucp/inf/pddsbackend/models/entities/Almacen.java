package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Almacen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean esInfinito=false;

    @Column(nullable = false) // obligatorio
    private Integer capacidadMaxima; //imagino es suficiente, no creo que superemos los 2,147,483,647 prods

    @ColumnDefault("0")
    @Column(nullable = false)
    @Builder.Default
    private Integer capacidadOcupada=0;

    @Column(nullable = false)
    private String codigoAeropuertoEn4Letras;

    @Column(nullable = false)
    private String codigoCiudadEn4Letras; // no está bien, pero porque el caso es simplificado y la relación

    @Column(nullable = false)
    private String nombreCiudad; // es siempre Uno a uno, no hay problema práctico.

    @Column(nullable = false)
    private String nombrePais; //... pero no está bien...

    @Column(nullable = false)
    Double latitud; // luego sería bueno hacerlas obligatorias para integridad de datos.

    @Column(nullable = false)
    Double longitud;

    @Column(nullable = false)
    Integer gmt; // no sé si el gmt sea algo más propio del país o del almacén/aeropuerto/oficina

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Continente continente;
    // Aeropuerto, oficina,... Son uno a uno.
    //    @ColumnDefault("0")
//    Integer capacidadReservadaPorEnvios; // tal vez... pero es TRANSITIVO

    @Builder.Default
    @ColumnDefault("true")
    @Column(nullable = false)
    Boolean activo=true; // PORSIA

}
