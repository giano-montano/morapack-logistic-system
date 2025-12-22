package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;

@Builder

public class EntradaProblemaPlanificacion
{

    private EstadoGlobal estadoGlobal;
    ArrayList<Object> parametrosOpcionalesPersonalizados;
    @Getter
    Long semilla;
    @Getter
    Instant instanteActual;

    @Getter
    Instant instantePrograsIndispensables;

    @Builder.Default
    @Getter
    Long idSimul=null; // <- lo necesitaba wa

    public EstadoGlobal getEstadoGlobalCopia()
    {
        return new EstadoGlobal(estadoGlobal);
    }

    public EstadoGlobal getEstadoGlobal()
    {
        return estadoGlobal;
    }
}

// otros parámetros relevantes del problema (maxTime, sla, restricciones)
// int maxDays,
// boolean allowBacktracking
