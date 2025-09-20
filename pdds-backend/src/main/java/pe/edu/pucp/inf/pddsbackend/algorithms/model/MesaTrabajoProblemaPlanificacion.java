package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

@Getter
public class MesaTrabajoProblemaPlanificacion {

    @NotNull
    HashMap<Long, AlmacenParaAlgoritmo> almacenes;
    @NotNull
    HashMap<Long, VueloParaAlgoritmo> vuelos;
    @NotNull
    HashMap<Long, PedidoParaAlgoritmo> pedidos;
    @NotNull
    HashSet<RutaProgramadaParaAlgoritmo> rutasSolucionQueGeneraAlgoritmo;

    public MesaTrabajoProblemaPlanificacion( HashMap<Long, AlmacenParaAlgoritmo> almacenes, HashMap<Long, VueloParaAlgoritmo> vuelos, HashMap<Long, PedidoParaAlgoritmo> pedidos) {
        this.almacenes = almacenes != null?new HashMap<>(almacenes):new HashMap<>();
        this.vuelos = vuelos != null?new HashMap<>(vuelos):new HashMap<>();
        this.pedidos = pedidos != null?new HashMap<>(pedidos):new HashMap<>();
        this.rutasSolucionQueGeneraAlgoritmo = new HashSet<>();
    }

    public MesaTrabajoProblemaPlanificacion desdeEntradaPlanificacion(EntradaProblemaPlanificacion entradaPlanificacion) {
        return new MesaTrabajoProblemaPlanificacion(
                entradaPlanificacion.almacenes,entradaPlanificacion.vuelos,entradaPlanificacion.pedidos);
    }


    /** Devuelve las rutas creadas hasta ahora que están asociadas a un pedido dado. */
//    public synchronized HashSet<RutaProgramadaParaAlgoritmo> getRutasAsociadasAPedido(long idPedido) {
//        return this.rutasSolucionQueGeneraAlgoritmo.stream()
//                .filter(r -> r.getIdPedidoAsociado() != null && r.getIdPedidoAsociado().equals(idPedido))
//                .collect(Collectors.toCollection(HashSet::new));
//    }

    /** Devuelve las rutas que incluyen determinado vuelo (por id). */
    public synchronized HashSet<RutaProgramadaParaAlgoritmo> getRutasQueIncluyenVuelo(long idVuelo) {
        return this.rutasSolucionQueGeneraAlgoritmo.stream()
                .filter(r -> r.getIdsVuelosEnOrden() != null && r.getIdsVuelosEnOrden().contains(idVuelo)).collect(Collectors.toCollection(HashSet::new));
    }

    /** Devuelve las rutas que pasan por un almacén (sirve para ver qué rutas "tocan" un almacén). */
    public synchronized HashSet<RutaProgramadaParaAlgoritmo> getRutasQueIncluyenAlmacen(long idAlmacen) {
        return this.rutasSolucionQueGeneraAlgoritmo.stream()
                .filter(r -> {
                    if (r.getIdsVuelosEnOrden() == null) return false;
                    for (Long vId : r.getIdsVuelosEnOrden()) {
                        VueloParaAlgoritmo v = this.vuelos.get(vId);
                        if (v == null) continue;
                        if (v.getIdAlmacenOrigen() == idAlmacen || v.getIdAlmacenDestino() == idAlmacen) return true;
                    }
                    return false;
                })
                .collect(Collectors.toCollection(HashSet::new));
    }


}
