package pe.edu.pucp.inf.pddsbackend.algorithms;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;


public interface PlanificationStrategy {

    public SalidaProblemaPlanificacion planificar(EntradaProblemaPlanificacion parametrosAlgoritmo) throws Exception;

//    public <T> SalidaProblemaPlanificacion mapearSolucionInternaAContrato(T solucionInterna);

//    public <T> SalidaProblemaPlanificacion mapearContrato(T solucionInterna);
}
