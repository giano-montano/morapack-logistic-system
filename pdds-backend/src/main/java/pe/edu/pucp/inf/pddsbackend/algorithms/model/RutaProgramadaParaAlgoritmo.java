package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;

import java.util.LinkedList;

@Data
public class RutaProgramadaParaAlgoritmo {
    private LinkedList<Long> idsVuelosEnOrden;
    private long idPedidoAsociado;
    private int cantidadTotalOParcial;
}
