package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

@Data
public class RutaProgramadaParaAlgoritmo {
    private LinkedList<Long> idsVuelosEnOrden;
    private long idPedidoAsociado;
    private int cantidadTotalOParcial;

    //prueba:
    private boolean activo=true;

    public RutaProgramadaParaAlgoritmo(LinkedList<Long> idsVuelosEnOrden, long idPedidoAsociado, int cantidadTotalOParcial) {
        this.idsVuelosEnOrden = idsVuelosEnOrden;
        this.idPedidoAsociado = idPedidoAsociado;
        this.cantidadTotalOParcial = cantidadTotalOParcial;
    }

    public RutaProgramadaParaAlgoritmo (List<VueloParaAlgoritmo> vuelos){
        idsVuelosEnOrden = new LinkedList<>(
                vuelos.stream().map(VueloParaAlgoritmo::getId).toList());
    }


}
