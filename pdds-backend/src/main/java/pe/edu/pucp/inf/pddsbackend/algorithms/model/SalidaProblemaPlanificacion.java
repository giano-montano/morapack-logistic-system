package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SalidaProblemaPlanificacion {
        List<RutaProgramadaParaAlgoritmo> rutasProgramadasParaSatisfacerTodoPedido; // claro que son nuevas! no modifica ya hechas
        @Builder.Default
        boolean huboErrorEjecucion=false;
        @Builder.Default
        boolean colapsado=false;
}

