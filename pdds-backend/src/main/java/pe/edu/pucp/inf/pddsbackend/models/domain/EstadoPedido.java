package pe.edu.pucp.inf.pddsbackend.models.domain;

/* no hay algo relacionado a reprogramación porque eso es parte de los envíos del pedido, no atado al pedido*/
public enum EstadoPedido {
    POR_PROGRAMAR, PROGRAMADO, EN_CURSO, ENTREGADO,
}
//SI UN PEDIDO ESTÁ RECOGIDO O NO (SU NUM PRODUCTOS EN ALMACÉN) LO DETERMINARÁ EL EVENTO
//PERIÓDICO DE LA BD, ASI COMO EL ESTADO "EN_CURSO" ME PARECE.



