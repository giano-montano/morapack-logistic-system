package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import org.springframework.context.annotation.Primary;

import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;

@Primary
public class EstrategiaGrasp extends Estrategia
{

    public EstrategiaGrasp(Estado estadoInicial)
    {
        super(estadoInicial);
    }

    /*
     * Punto de entrada del algoritmo. Para este punto ya se cuenta con el Estado
     * inicial. Retorna un booleano que indica el colapso
     */
    @Override
    public Boolean resolverPlanificacion()
    {
        // this.estadoInicial.crearRutas();

        return true;
    }
}
