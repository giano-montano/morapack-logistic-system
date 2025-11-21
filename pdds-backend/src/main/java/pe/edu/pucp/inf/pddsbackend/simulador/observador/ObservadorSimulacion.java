package pe.edu.pucp.inf.pddsbackend.simulador.observador;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Instant;

public interface ObservadorSimulacion
{
    void conEventoProcesado(EventoSimulacion evento, ContextoSimulacion ctx);

    void conPlanificacionCompletada(SalidaProblemaPlanificacion salida);

    void conColapsoDetectado(Instant momento);
}
