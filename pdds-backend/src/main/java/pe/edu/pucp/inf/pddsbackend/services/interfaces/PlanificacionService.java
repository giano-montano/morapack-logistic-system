package pe.edu.pucp.inf.pddsbackend.services.interfaces;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.PlanificacionResponseDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;

import java.util.HashMap;

public interface PlanificacionService
{

    public PlanificacionResponseDTO realizarPlanificacionDePedidosActualesConPersistencia(
            RealizarPlanificacionDTO params) throws Exception;

    public ResultadoAlgoritmoDTO realizarPlanificacionConDatosDeBD(RealizarPlanificacionDTO params)
            throws Exception;

    ResultadoAlgoritmoDTO realizarPlanificacionConEntrada(
            RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo)
            throws Exception;

    ResultadoAlgoritmoDTO realizarPlanificacionConEntrada_v2(EntradaProblemaPlanificacion dataEntradaAlgoritmo)
            throws Exception;

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null
    // pointers en lo más posible.
    EstadoGlobal obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params, boolean incluirTodo);

    HashMap<Long, Almacen> obtenerAlmacenesParaAlgoritmo();

    public String obtenerMetaDatos();
}
