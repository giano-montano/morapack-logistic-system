package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvioProgramado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

//    @ManyToOne(
//            fetch = FetchType.LAZY,
//            optional = false
//    )
//    @JoinColumn(name = "almacen_destino_id")
//    private Almacen almacenDestino; esto se infiere desde el primer vuelo del envío
    private Long cantProductosAEnviar; //puede atender varios o partes de pedidos a la vez.

//    private Boolean fechaHoraLlegada; es transitivo
    private Boolean cumplido; // si ya se cumplió o no

    private Boolean reprogramado; //true: se reprogramó, este ya no tiene validez; false: no se reprogramó



}
