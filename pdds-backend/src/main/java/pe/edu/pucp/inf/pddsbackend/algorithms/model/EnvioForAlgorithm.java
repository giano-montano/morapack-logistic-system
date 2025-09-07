package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
// Envíos ya existentes; o sea soluciones previas
public class EnvioForAlgorithm {
        Long id;

        Integer cantidadProductosAEnviar;
        List<VueloForAlgorithm>vuelosParaHacerPosibleEnvio;
        List<PedidoForAlgorithm> pedidosOPartesDePedidoAAtender;

        //la fecha de llegada se obtiene del último vuelo, así como la fecha de inicio del primer vuelo {con atributo Orden)
        Instant instanteMaximoParaRecogerEnvio; // sería opcional si es que hicieramos envios sin pedido asociado.

//        // Al algoritmo solo le pasaría solo envíos cumplidos FALSE y reprogramados FALSE
//        Boolean cumplido, // ya está realizado?
//        Boolean reprogramado // creo que le pasaré
}
