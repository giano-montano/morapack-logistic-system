package pe.edu.pucp.inf.pddsbackend.algorithms;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;

import java.util.Random;

@Data
public abstract class EstrategiaPlanificacion {

    protected LoggingReport lr = new LoggingReport();
    long semilla = new Random().nextLong();
    Random generadorAleatorio = new Random(semilla);

    public void setSemilla(Long semilla) {
        this.semilla = semilla!=null?semilla:new Random().nextLong();
        generadorAleatorio.setSeed(this.semilla);
    }

    public abstract SalidaProblemaPlanificacion planificar(
            EntradaProblemaPlanificacion input
    ) throws Exception;

//    public <T> SalidaProblemaPlanificacion mapearSolucionInternaAContrato(T solucionInterna);

//    public <T> SalidaProblemaPlanificacion mapearContrato(T solucionInterna);
}
