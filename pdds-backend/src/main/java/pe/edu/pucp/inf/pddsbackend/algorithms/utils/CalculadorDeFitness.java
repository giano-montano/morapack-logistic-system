package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;

@Component
public class CalculadorDeFitness {

    // esto debería ser fijo, o hacemos strategies para calcular fitness de soluciones genéricas?
    public double calcularFitnessSalidaProblema(SalidaProblemaPlanificacion salidaObtenida){

        return 1.0;
    }
}
