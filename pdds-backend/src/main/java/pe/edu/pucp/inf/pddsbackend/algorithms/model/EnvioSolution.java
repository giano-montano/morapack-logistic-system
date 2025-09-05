package pe.edu.pucp.inf.pddsbackend.algorithms.model;


import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class EnvioSolution {

    Integer cantProductos; // inferible, transitivo (con las cantidades de PedidoSolution), pero para tenerlo más rápido mejor acá.
    Long idAlmacenDestino; // inferible, transitivo (con el idVuelo destino del vuelo más futuro), pero para tenerlo más rápido mejor acá.
    Instant fechaHoraDestino;// inferible, transitivo (con la fechaHora destino del vuelo más futuro), pero para tenerlo más rápido mejor acá.

    //Puedo abstraerlo más con una clase que indique el orden, pero na.
    List<Long> idsVuelosATomar; // recordar que no está ocupando necesariamente toda la capacidad del vuelo. Un vuelo puede
    // también ayudar a hacer varios pedidos.

    List<PedidoSolution> pedidosAAtenderTotalOParcialmente;

}
