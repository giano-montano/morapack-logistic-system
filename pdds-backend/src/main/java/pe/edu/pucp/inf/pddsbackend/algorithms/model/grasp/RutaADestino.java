package pe.edu.pucp.inf.pddsbackend.algorithms.model.grasp;

import lombok.AllArgsConstructor;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloForAlgorithm;


import java.util.LinkedList;
import java.util.List;

@AllArgsConstructor
@Data
public class RutaADestino {
    LinkedList<VueloForAlgorithm> vuelosOrdenados;
    public RutaADestino(List<VueloForAlgorithm> lista) { this.vuelosOrdenados = new LinkedList<>(lista); }
}
