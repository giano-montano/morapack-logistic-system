package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;

public interface PlanificacionService {

    public PlanificacionResponseDTO realizarPlanificacionDePedidosActualesConPersistencia
            (RealizarPlanificacionDTO params) throws Exception;

    public ResultadoAlgoritmoDTO realizarPlanificacionConDatosDeBD(RealizarPlanificacionDTO params) throws Exception;

    ResultadoAlgoritmoDTO realizarPlanificacionConEntrada(
            RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo) throws Exception;

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    EstadoGlobal obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params, boolean incluirTodo);

    public String obtenerMetaDatos();
}
