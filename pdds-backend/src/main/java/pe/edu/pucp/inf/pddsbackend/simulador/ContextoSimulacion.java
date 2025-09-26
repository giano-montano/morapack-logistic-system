package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobalMutableProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.dto.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Clock reloj;
    private final LoggingReport report = new LoggingReport();

    private transient SchedulerSimulacion scheduler; // transient: no persistir
    //nuevos:
    @Builder.Default
    private final Map<String, Double> metricas = new HashMap<>();

    @Builder.Default
    private int contadorPlanificaciones = 0;

    @Builder.Default
    private Instant ultimaPlanificacion = Instant.MIN;


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
        report.appendReport("métricas: " + metricas );
        report.writeReportFile("Reporte de simulación "+ params.tipoSimulacion());
    }


//    public boolean debeDesencadenarPlanificacionAhora() {
//        // Política configurable
//        long pedidosPendientes = estadoGlobal.getPedidos().values().stream()
//                .filter(p -> p.isActivo() &&
//                        p.getCantidadProductosProgramados() < p.getCantidadProductosPedidos())
//                .count();
//
//        Duration tiempoDesdeUltima = Duration.between(ultimaPlanificacion, ahora);
//
//        return pedidosPendientes >= params.getUmbralPedidosPlanificacion() ||
//                tiempoDesdeUltima.toMinutes() >= params.getIntervaloPlanificacionMinutos();
//    }

    public void registrarMetrica(String nombre, double valor) {
        metricas.put(nombre, valor);
        log(String.format("Métrica %s: %.2f", nombre, valor));
    }

    public boolean shouldCheckpointNow() {
        return contadorPlanificaciones % 10 == 0; // Cada 10 planificaciones
    }

}
