package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

@Data
public class RutaProgramadaParaAlgoritmo {
    private LinkedList<Long> idsVuelosEnOrden;
    private long idPedidoAsociado;

    private int cantidadTotalOParcial; // solo por ahora le dejo el nombre, pero son de los prods escogidos
    HashSet<Long> idsProductosEscogidos;
    private Integer cantidadProductosNuevosDesdeInfinitos; // puede ser nulo

    private int cantidadTotal;

    private boolean esIntercontinentalPorVuelos =false;
    private boolean esIntercontinentalPorProductos=false;
    //prueba:
    private boolean activo=true;

    public RutaProgramadaParaAlgoritmo(LinkedList<Long> idsVuelosEnOrden, long idPedidoAsociado, int cantidadTotalOParcial,
                                       HashMap<Long,VueloParaAlgoritmo> vuelosContexto) {
        this.idsVuelosEnOrden = idsVuelosEnOrden;
        this.idPedidoAsociado = idPedidoAsociado;
        this.cantidadTotalOParcial = cantidadTotalOParcial;
        // saber que es intercontinental de fresa:
        this.esIntercontinentalPorVuelos = idsVuelosEnOrden.stream().anyMatch(aLong ->
                vuelosContexto.get(aLong).esIntercontinental=true);
        System.out.println("va siendo intercontinental? " + esIntercontinentalPorVuelos);
    }

    public RutaProgramadaParaAlgoritmo (List<VueloParaAlgoritmo> vuelos){
        idsVuelosEnOrden = new LinkedList<>(
                vuelos.stream().map(VueloParaAlgoritmo::getId).toList());
    }

    public int anadirProdsDeAlmacenIntermedio(List<Long> idsPRodsExsitentes){
        idsProductosEscogidos.addAll(idsPRodsExsitentes);
        cantidadTotal += idsPRodsExsitentes.size();
        return 1;
    }

    public int anadirPordsNuevos(int cant){
        cantidadProductosNuevosDesdeInfinitos+= cant;
        cantidadTotal+=cant;
        return cantidadTotal;
    }

    public int DAMETODO(){
        return cantidadTotal;
    }



}
