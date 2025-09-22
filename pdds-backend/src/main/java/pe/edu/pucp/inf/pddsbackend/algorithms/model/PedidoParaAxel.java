package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class PedidoParaAxel {
    PedidoParaAlgoritmo pedidoObjeto;
    List<RutaProgramadaParaAlgoritmo> miniPedidos;

    public PedidoParaAxel() {
        this.miniPedidos = new ArrayList<>();
    }

    public PedidoParaAxel(PedidoParaAlgoritmo pedidoObjeto) {
        this.pedidoObjeto = pedidoObjeto;
        this.miniPedidos = new ArrayList<>();
    }

    /** Suma total de cantidades de las mini-rutas asociadas (útil para fitness). */
    public int getCantidadTotalProgramadaEnMiniPedidos() {
        return miniPedidos.stream()
                .filter(Objects::nonNull)
                .mapToInt(RutaProgramadaParaAlgoritmo::getCantidadTotalOParcial)
                .sum();
    }
}
