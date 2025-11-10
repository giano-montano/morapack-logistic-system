package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SalidaProblemaPlanificacion {

       List<Programacion> programaciones;
        HashMap<UUID, Producto> productos;

        @Builder.Default
        boolean huboErrorEjecucion=false;
        @Builder.Default
        boolean colapsado=false;
        String error = null;

        public SalidaProblemaPlanificacion(@NotNull List<Programacion> productosProgramados, @NotNull HashMap<UUID, Producto> productos) {
            this.programaciones = productosProgramados;
            this.productos = productos;
        }

        // con error
        public SalidaProblemaPlanificacion(@NotNull List<Programacion> productosProgramados, String error) {
            this.colapsado=true; // se asume que cualquier excepcion es colapso
            this.programaciones = productosProgramados;
            this.error = error;
            this.huboErrorEjecucion=true;
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

