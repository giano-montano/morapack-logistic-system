package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.utils.LoggingReport;

import java.util.Random;

@Data
public abstract class PlanificationStrategy {

    protected LoggingReport loggingReport = new LoggingReport();
    long seed = new Random().nextLong();
    Random generadorAleatorio = new Random(seed);

    public void establecerSeed(Long seed) {
        this.seed = seed!=null?seed:new Random().nextLong();
        generadorAleatorio.setSeed(this.seed);
    }

    public abstract SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) throws Exception;

//    public <T> SalidaProblemaPlanificacion mapearSolucionInternaAContrato(T solucionInterna);

//    public <T> SalidaProblemaPlanificacion mapearContrato(T solucionInterna);
}
