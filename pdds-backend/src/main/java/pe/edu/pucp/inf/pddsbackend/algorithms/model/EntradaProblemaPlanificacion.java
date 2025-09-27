package pe.edu.pucp.inf.pddsbackend.algorithms.model;


import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Builder
@Data
public class EntradaProblemaPlanificacion {
    @NotNull
    HashMap<Long, AlmacenParaAlgoritmo> almacenes;
    @NotNull
    HashMap<Long, VueloParaAlgoritmo> vuelos;
    @NotNull
    HashMap<Long, PedidoParaAlgoritmo> pedidos;

//    // índices útiles:
//    HashMap<Long, List<Long>> idsVuelosPorOrigen;
//    HashMap<Long, List<Long>> idsVuelosPorDestino;
//    HashMap<Long, List<Long>> idsPedidosPorDestino;

    ArrayList<Object> parametrosOpcionalesPersonalizados;
    Long seed;
//    HashMap<Long, List<VueloParaAlgoritmo>> vuelosPorOrigen;
//    HashMap<Long, List<VueloParaAlgoritmo>> vuelosPorDestino;
//    HashMap<Long, List<PedidoParaAlgoritmo>> pedidosPorDestino;

}

//    List<EnvioForAlgorithm> envios,
        // otros parámetros relevantes del problema (maxTime, sla, restricciones)
//    int maxDays,
//    boolean allowBacktracking

