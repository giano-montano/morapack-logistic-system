package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias.Estrategia;
import pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias.EstrategiaGrasp;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Lectora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PlanificacionEntidad;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

@Service
@RequiredArgsConstructor
public class PlanificacionServicioImplementacion implements PlanificacionServicio
{
    private final PlanificacionRepositorio planificacionRepositorio;

    /*
     * Servicio que persiste los objetos Planificación en la BD
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
        estrategia = new EstrategiaGrasp(estadoInicial);

        return estrategia.resolverPlanificacion();
    }

    /*
     * Actualmente esta function obtiene el Estado inicial de archivos. Es una
     * implementación temporal.
     *
     * El orden en el que se recuperan los datos es el siguiente: 1. Recuperar
     * productos no entregados (se tiene que calcular su almacen/vuelo actual) 2.
     * Recuperar almacenes 3. Recuperar vuelos 4. Asignar productos a vuelos o
     * almacenes 5. Recuperar pedidos. Debe retornar sus Productos entregados para
     * inicializar el atributo instanteEntrega
     *
     * PD: Cada que un Producto es entregado, se crea un evento que aumente la
     * cantidad de prodEntregados en el Pedido PD: Los eventos suceden cada vez que
     * un vuelo sale o llega. Este evento para la planificación actual y la vuelve a
     * empezar. Los eventos modifican las cantidades
     */
    private Estado obtenerEstado(Planificacion planificacion)
    {

        Lectora lectora = new Lectora();
        Map<UUID, Almacen> almacenes;
        Map<UUID, Vuelo> vuelos;
        Map<UUID, Pedido> pedidos;
        Map<UUID, Producto> productosExistentes;

        try
        {
            almacenes = lectora.leerArchivoAlmacenes();

            vuelos = lectora.leerArchivoVuelos(planificacion.getInstanteActual(), almacenes);

            pedidos = lectora.leerArchivoPedidos(planificacion.getInstanteActual(), almacenes);

        }
        catch (IOException e)
        {
            System.out.println("Error en la lectura de archivos");
            almacenes = new HashMap<>();
            vuelos = new HashMap<>();
            pedidos = new HashMap<>();
        }
        finally
        {
            productosExistentes = new HashMap<>();
        }

        return new Estado(productosExistentes, almacenes, vuelos, pedidos,
                planificacion.getInstanteActual());
    }

}
