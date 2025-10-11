package pe.edu.pucp.inf.pddsbackend.algorithms.model.grasp;

import lombok.AllArgsConstructor;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloParaAlgoritmo;


import java.util.LinkedList;
import java.util.List;

@AllArgsConstructor
@Data
public class RutaADestino {
    LinkedList<VueloParaAlgoritmo> vuelosOrdenados;
    public RutaADestino(List<VueloParaAlgoritmo> lista) { this.vuelosOrdenados = new LinkedList<>(lista); }
}
