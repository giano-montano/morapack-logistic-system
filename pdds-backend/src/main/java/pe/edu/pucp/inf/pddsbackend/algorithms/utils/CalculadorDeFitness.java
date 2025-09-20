package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobalMutableProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;

@Component
public class CalculadorDeFitness {



    // esto debería ser fijo, o hacemos strategies para calcular fitness de soluciones genéricas?
    public double calcularFitnessSalidaProblema(SalidaProblemaPlanificacion salidaObtenida, EntradaProblemaPlanificacion input){
        EstadoGlobalMutableProblemaPlanificacion estadoGlobal =
                EstadoGlobalMutableProblemaPlanificacion.desdeEntradaPlanificacion(input);
        estadoGlobal.setRutasSolucionQueGeneraAlgoritmo(salidaObtenida.getRutasProgramadasParaSatisfacerTodoPedido());
        /*
        * estadoGlobal: son los almacenes, vuelos y pedidos tal como están AHORITA EN LA VIDA REAL.
        * También contiene rutasSolucionQueGeneraAlgoritmo que estamos inicializando con las rutas que YA
        * generó el planificador/algoritmo/ejecución.
        * Tu responsabilidad es hallar el fitness apoyando de las funciones que NO MUTAN el estadoGlobal
        * por ejm: puedes usar la función obtenerAlmacenEnInstante(...)
        * */




        return 1.0;
    }
}
