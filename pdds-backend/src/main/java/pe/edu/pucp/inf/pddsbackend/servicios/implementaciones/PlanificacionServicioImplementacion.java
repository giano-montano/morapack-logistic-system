package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.algorithms.EstrategiaGraspHibrido;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.utils.CalculadorDeFitness;
import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionParametrosDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PlanificacionEntidad;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class PlanificacionServicioImplementacion implements PlanificacionServicio
{
    private final PlanificacionRepositorio planificacionRepositorio;
    private final EstrategiaGraspHibrido graspAndGeneticAlgorithmStrategy;

    @Override
    public Planificacion persistir(Planificacion planificacion)
    {
        PlanificacionEntidad planificacionEntidad;

        planificacionEntidad = new PlanificacionEntidad(planificacion);
        planificacionEntidad = planificacionRepositorio.save(planificacionEntidad);
        planificacion = planificacionEntidad.convertirADominio();

        return planificacion;
    }

    @Override
    public void planificar(PlanificacionParametrosDTO parametros) throws Exception {
        // Mejor que el servicio inicialice el estado por el algoritmo.
        EstadoGlobal estadoInicial = obtenerDatosParaAlgoritmo();

        EntradaProblemaPlanificacion entradaProblemaPlanificacion = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoInicial)
                .semilla(parametros.getSemilla())
                .parametrosOpcionalesPersonalizados(parametros.getParametros())
                .build();

        long startTime = System.nanoTime();
        SalidaProblemaPlanificacion salida =
                graspAndGeneticAlgorithmStrategy.planificar(entradaProblemaPlanificacion);
        long endTime = System.nanoTime(); // Record end time in nanoseconds
        long duration = (endTime - startTime) /  1000000;
        // se asignan después
        Double fitness = CalculadorDeFitness.calcularFitnessSalidaProblema(salida, entradaProblemaPlanificacion);
        long tiempoEjecucionMs = duration;

        System.out.println("Programaciones hechas: "+salida.getProgramaciones()
        + "\nFitness: "+fitness+"\nTiempo en ms:"+tiempoEjecucionMs );
    }

    // Recordar que el algoritmo recibe datos limpios, no debe preocuparse por null pointers en lo más posible.
    public EstadoGlobal obtenerDatosParaAlgoritmo(){

        HashMap<Long, Almacen> almacenes = obtenerAlmacenes();
        HashMap<Long, Vuelo> vuelos = obtenerVuelos();
        HashMap<Long, Pedido> pedidos = obtenerPedidos();

        return new EstadoGlobal(almacenes, vuelos, pedidos,null);
    }

    private HashMap<Long, Almacen> obtenerAlmacenes() {
        HashMap<Long, Almacen> almacenes = new HashMap<>();
        // ... traigan la bd...
        return almacenes;
    }

    private HashMap<Long, Vuelo> obtenerVuelos(){
        HashMap<Long, Vuelo> vuelos = new HashMap<>();

        return vuelos;
    }

    private HashMap<Long, Pedido> obtenerPedidos(){
        HashMap<Long, Pedido> pedidos = new HashMap<>();

        return pedidos;
    }

}
