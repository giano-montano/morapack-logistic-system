package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;

public interface PlanificacionService {

    public PlanificacionResponseDTO realizarPlanificacionDePedidosActualesConPersistencia(RealizarPlanificacionDTO params) throws Exception;

    public SalidaProblemaPlanificacion realizarPlanificacionConDatosDeBD(RealizarPlanificacionDTO params) throws Exception;

    SalidaProblemaPlanificacion realizarPlanificacionConEntrada(
            RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo) throws Exception;

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    EntradaProblemaPlanificacion obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params);

    public String obtenerMetaDatos();
}
