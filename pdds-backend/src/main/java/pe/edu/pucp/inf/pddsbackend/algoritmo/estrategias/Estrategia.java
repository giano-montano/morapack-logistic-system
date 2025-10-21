package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;

@Getter
@Setter
public abstract class Estrategia
{
    private final RandomGenerator random;
    private final Estado estadoInicial;

    public Estrategia(Long semilla, Estado estadoInicial)
    {
        this.random = RandomGeneratorFactory.of("L64X128MixRandom")
                .create(semilla);
        this.estadoInicial = estadoInicial;
    }

    /*
     * Por el momento es void pero debería ser la salida del problema de
     * planificación.Pensaría que puede ir en el mismo objeto Planificacion
     */
    public abstract Boolean resolverPlanificacion();
}
