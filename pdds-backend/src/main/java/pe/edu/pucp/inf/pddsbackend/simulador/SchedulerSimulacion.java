package pe.edu.pucp.inf.pddsbackend.simulador;

import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.util.PriorityQueue;
import java.util.UUID;

public interface SchedulerSimulacion
{
    void programar(EventoSimulacion e);

    boolean cancelar(UUID eventId);

    PriorityQueue<EventoSimulacion> getEventosSimulacionNuevaQueue();

    PriorityQueue<EventoSimulacion> getEventosSimulacionReales();
    // int pendingEvents();
}
