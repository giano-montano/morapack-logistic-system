package pe.edu.pucp.inf.pddsbackend.algorithms.model;


import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;

@Builder
public class EntradaProblemaPlanificacion {

    EstadoGlobal estadoGlobal;
    ArrayList<Object> parametrosOpcionalesPersonalizados;
    @Getter
    Long semilla;

    public EstadoGlobal getEstadoGlobalCopia() {
        return new EstadoGlobal(estadoGlobal);
    }
}

// otros parámetros relevantes del problema (maxTime, sla, restricciones)
//    int maxDays,
//    boolean allowBacktracking

