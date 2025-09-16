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
    private String nombreCiudad;
    // es siempre Uno a uno, no hay problema práctico.
    private String codigoAeropuertoEn4Letras; //


    @ManyToOne(
            fetch = FetchType.EAGER // ineficiente pero podría servir
            ,optional = false
    )
    @JoinColumn(name = "pais_codigo", referencedColumnName = "codigo")
    private Pais pais;
    //o de frente le mapeamos el continente?xd

    Double latitud; // luego sería bueno hacerlas obligatorias para integridad de datos.
    Double longitud;
    Integer gmt; // no sé si el gmt sea algo más propio del país o del almacén/aeropuerto/oficina


    // Aeropuerto, oficina,... Son uno a uno.
}
