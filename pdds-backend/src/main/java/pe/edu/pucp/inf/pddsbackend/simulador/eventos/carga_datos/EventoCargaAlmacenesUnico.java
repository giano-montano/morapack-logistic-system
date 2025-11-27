package pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EventoCargaAlmacenesUnico implements EventoSimulacion
{
    @NotNull
    UUID uuid;
    @NotNull
    Instant cuando;

    private final PlanificacionService planificacionService;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return cuando;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception
    {
        ctx.log("Comenzando a procesar EventoCargaAlmacenesUnico");
        System.out.println("Comenzando a procesar EventoCargaAlmacenesUnico");

        EstadoGlobal estado = ctx.getEstado(); // <- es referencia, no copia
        HashMap<Long, Almacen> alms;// <- es referencia, no copia
        alms = planificacionService.obtenerAlmacenesParaAlgoritmo();
        if (alms == null || alms.isEmpty()){
            throw new Exception("No se ha encontrado almacenes");
        }
        estado.setAlmacenes(alms);

        ctx.log("Se ha cargado los almacenes por primera vez y única: " + alms.size());
        System.out.println("Se ha cargado los almacenes por primera vez y única: " + alms);

    }

    @Override
    public int getPriority()
    {
        return 0;
    }
}
