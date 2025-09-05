package pe.edu.pucp.inf.pddsbackend.models.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/*
* El envío es el propósito de enviar cierta cantidad de productos (conjunto de productos)
* a un destino determinado en una fecha determinada a través de uno o varios vuelos
* que deberán llevar este conjunto de productos por igual hasta el aeropuerto/almacén/oficina destino
* Nota: Si bien al envío lo pueden componer varios vuelos. Esto no quita que un solo vuelo pueda
* satisfacer más de un envío a la vez.
* Ejemplo: vuelo México a Palestina, para el envío 1 satisface un pedido; para el envío 2 es solo una
* escala más (Envío 2: México - Palestina - Serbia)
* */
@AllArgsConstructor
@Data
public class Envio {
    Long id;

    List<Vuelo> vuelosParaHacerPosibleEnvio;
    Integer cantidadProductosAEnviar;
    List<Pedido> pedidosOPartesDePedidoAAtender;

    //la fecha de llegada se obtiene del último vuelo, así como la fecha de inicio del primer vuelo (con atributo Orden)
    Instant instanteMaximoParaRecogerEnvio; // sería opcional si es que hicieramos envios sin pedido asociado.

    Boolean cumplido; // ya está realizado?
    Boolean reprogramado; // fue cancelado?

}
