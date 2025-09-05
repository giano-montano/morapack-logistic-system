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
public class EnvioProgramadoVuelo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "vuelo_que_conforma_envio_id")
    private Vuelo vueloOEscalaQueConformaEnvio;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "envio_que_vuelo_satisface_id")
    private EnvioProgramado envioQueVueloSatisface;

    // no es tan necesario porque se puede inferir con las fechas; pero para ayudarnos no viene mal
    private Integer ordenDelVueloEnEnvio;// Puede haber diferentes órdenes para un mismo vuelo
    //dependiendo del pedido que está intentando atender.




}
