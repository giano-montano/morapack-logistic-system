package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

@Data
@AllArgsConstructor
public class RutaProgramadaParaAlgoritmo {
    private LinkedList<Long> idsVuelosEnOrden;
    private long idPedidoAsociado;
    private int cantidadTotalOParcial;

    public RutaProgramadaParaAlgoritmo (List<VueloParaAlgoritmo> vuelos){
        idsVuelosEnOrden = new LinkedList<>(
                vuelos.stream().map(VueloParaAlgoritmo::getId).toList());
    }
}
