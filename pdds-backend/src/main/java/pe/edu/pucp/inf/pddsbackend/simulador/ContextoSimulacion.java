package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Data

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextoSimulacion {

    private Instant ahora;
    private EstadoGlobalMutableProblemaPlanificacion estadoGlobal;

    private SimulacionRequestDTO params;
    private RealizarPlanificacionDTO formaRealizarPlanificacion;

    private List<SalidaProblemaPlanificacion> solucionesAcumuladas;
    private Clock  reloj;
    private final LoggingReport report = new LoggingReport();

    private transient SchedulerSimulacion scheduler; // transient: no persistir


    // constructores, getters, helpers...
    public void establecerElAhora(Instant ahora) { this.ahora = ahora; }

    public Instant obtenerElAhora() { return ahora; }

//    public void anadirPedidoPendiente(PedidoParaAlgoritmo p) {
//        pedidosPendientes.put(p.getId(), p);
//    }

    public boolean debeDesencadenarPlanificacionAhora() {
        /* política: #pedidos>n o every X horas */
        return false;
    }

    public void programarEvento(EventoSimulacion e) {
        if (scheduler == null) throw new IllegalStateException("Scheduler no inicializado");
        scheduler.programar(e);
    }

    public void log(String mensaje) {
        report.appendReport(mensaje);
    }
    public void imprimirReporteLog() throws Exception {
        report.writeReportFile("Reporte de simulación "+ params.tipoSimulacion());
    }

}
