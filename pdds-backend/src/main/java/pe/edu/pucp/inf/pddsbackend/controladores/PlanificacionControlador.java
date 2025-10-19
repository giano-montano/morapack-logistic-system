package pe.edu.pucp.inf.pddsbackend.controladores;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionParametrosDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionRespuestaDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

@RestController
@RequestMapping("/planificacion")
@RequiredArgsConstructor
public class PlanificacionControlador
{
    private final PlanificacionServicio planificacionServicio;

    @PostMapping("/ejecutar")
    public ResponseEntity<PlanificacionRespuestaDTO> planificar(
            @RequestBody PlanificacionParametrosDTO parametros) throws Exception
    {
        Boolean colapso;
        Planificacion planificacion;

        planificacion = parametros.converitrADominio();
        colapso = planificacionServicio.planificar(planificacion);

        if (!colapso)
        {
            planificacionServicio.persistir(planificacion);

            return ResponseEntity.ok(new PlanificacionRespuestaDTO(parametros.getInstanteActual()));
        }
        else
        {
            return ResponseEntity
                    .badRequest()
                    .body(new PlanificacionRespuestaDTO(parametros.getInstanteActual()));
        }
    }
}
