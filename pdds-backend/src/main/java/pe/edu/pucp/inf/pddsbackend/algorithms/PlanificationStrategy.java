package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.utils.LoggingReport;

@Data
public abstract class PlanificationStrategy {

    protected LoggingReport loggingReport = new LoggingReport();



    public abstract SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) throws Exception;

//    public <T> SalidaProblemaPlanificacion mapearSolucionInternaAContrato(T solucionInterna);

//    public <T> SalidaProblemaPlanificacion mapearContrato(T solucionInterna);
}
