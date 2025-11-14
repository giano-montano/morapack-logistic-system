package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;

@Getter
@Setter
public abstract class Estrategia
{
    protected final Estado estadoInicial;

    public Estrategia(Long semilla, Estado estadoInicial)
    {
        this.estadoInicial = estadoInicial;
    }

    /*
     * Por el momento es void pero debería ser la salida del problema de
     * planificación.Pensaría que puede ir en el mismo objeto Planificacion
     */
    public abstract Boolean resolverPlanificacion();
}
