package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import org.springframework.context.annotation.Primary;

import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;

@Primary
public class EstrategiaGrasp extends Estrategia
{

    public EstrategiaGrasp(Long semilla, Estado estadoInicial)
    {
        super(semilla, estadoInicial);
    }

    /*
     * Punto de entrada del algoritmo. Para este punto ya se cuenta con el Estado
     * inicial
     */
    @Override
    public Boolean resolverPlanificacion()
    {




        return true; 
    }
}
