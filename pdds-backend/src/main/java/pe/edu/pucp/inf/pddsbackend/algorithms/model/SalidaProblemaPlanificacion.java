package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.utils.PrettyPrinter;

import java.util.List;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SalidaProblemaPlanificacion {

       List<Programacion> programaciones;

        @Builder.Default
        boolean huboErrorEjecucion=false;
        @Builder.Default
        boolean colapsado=false;
        String error = null;

        public SalidaProblemaPlanificacion(@NotNull List<Programacion> productosProgramados) {
            this.programaciones = productosProgramados;
        }

        @Override
        public String toString() {
                StringBuilder imprimir = new StringBuilder();
                if( colapsado || huboErrorEjecucion ) {
                        imprimir.append(colapsado?"Estoy colapsado:\n ":"Error en ejecución: \n");
                }
                if( programaciones != null && !programaciones.isEmpty()){
                        imprimir.append(programaciones);
                }
                return imprimir.toString();
        }
}

