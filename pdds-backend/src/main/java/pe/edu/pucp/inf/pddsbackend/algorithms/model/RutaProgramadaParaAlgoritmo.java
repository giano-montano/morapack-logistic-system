package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;

import java.util.LinkedList;

@Data
public class RutaProgramadaParaAlgoritmo {
    LinkedList<Long> idsVuelosEnOrden;
    long idPedidoAsociado;
    int cantidadTotalOParcial;
}
