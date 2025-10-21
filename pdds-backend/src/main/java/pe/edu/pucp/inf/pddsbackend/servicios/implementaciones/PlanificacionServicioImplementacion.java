package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import lombok.RequiredArgsConstructor;

import java.io.IOException;

import org.springframework.stereotype.Service;

import pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias.Estrategia;
import pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias.EstrategiaGrasp;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Lectora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PlanificacionEntidad;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

@Service
@RequiredArgsConstructor
public class PlanificacionServicioImplementacion implements PlanificacionServicio
{
    private final PlanificacionRepositorio planificacionRepositorio;

    /*
     * Servicio que persiste los objetos Planifiacion en la BD
     */
    @Override
    public Planificacion persistir(Planificacion planificacion)
    {
        PlanificacionEntidad planificacionEntidad;

        planificacionEntidad = new PlanificacionEntidad(planificacion);
        planificacionEntidad = planificacionRepositorio.save(planificacionEntidad);
        planificacion = planificacionEntidad.convertirADominio();

        return planificacion;
    }

    /*
     * Servicio que ejecuta el algoritmo definido en Estrategia
     */
    @Override
    public Boolean planificar(Planificacion planificacion)
    {
        Estado estadoInicial;
        Estrategia estrategia;

        estadoInicial = this.obtenerEstado(planificacion);
        estrategia = new EstrategiaGrasp(planificacion.getSemilla(),
                estadoInicial);

        return estrategia.resolverPlanificacion();
    }

    /*
     * Actualmente esta function obtiene el Estado inicial de archivos. Es una
     * implementación temporal
     */
    private Estado obtenerEstado(Planificacion planificacion)
    {
        Estado estadoInicial = new Estado();
        Lectora lectora = new Lectora();

        try
        {
            lectora.leerArchivoAlmacenes(estadoInicial);
            lectora.leerArchivoVuelos(estadoInicial, planificacion.getInstanteActual());
            lectora.leerArchivoPedidos(estadoInicial, planificacion.getInicioOperaciones());
        }
        catch (IOException e)
        {
            System.out.println("Error en la lectura de archivos");
        }

        return estadoInicial;
    }
}
