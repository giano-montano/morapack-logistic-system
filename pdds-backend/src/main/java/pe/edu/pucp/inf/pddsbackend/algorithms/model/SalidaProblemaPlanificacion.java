package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

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
        Double fitness;
        String error;
        long tiempoEjecucionMs;

        public SalidaProblemaPlanificacion(@NotNull List<RutaProgramadaParaAlgoritmo> rutasSolucionQueGeneraAlgoritmo) {
                rutasProgramadasParaSatisfacerTodoPedido = rutasSolucionQueGeneraAlgoritmo;
        }

        @Override
        public String toString() {
                StringBuilder imprimir = new StringBuilder();
                if( colapsado || huboErrorEjecucion ) {
                        imprimir.append(colapsado?"Colapsado: ":"Error en ejecución: ");
                }
                if( rutasProgramadasParaSatisfacerTodoPedido != null && !rutasProgramadasParaSatisfacerTodoPedido.isEmpty()){
                        imprimir.append(PrettyPrinter.printList(rutasProgramadasParaSatisfacerTodoPedido));
                }
                return imprimir.toString();
        }
}

