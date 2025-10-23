package pe.edu.pucp.inf.pddsbackend.controladores;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.inf.pddsbackend.algorithms.EstrategiaGraspHibrido;
import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionParametrosDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificacion.PlanificacionRespuestaDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.servicios.interfaces.PlanificacionServicio;

@RestController
@RequestMapping("/planificacion")
@RequiredArgsConstructor
public class PlanificacionControlador
{
    private final PlanificacionServicio planificacionServicio;

    @PostMapping("/ejecutar") // RESTful lo llora
    public ResponseEntity<PlanificacionRespuestaDTO> planificar(
            @RequestBody PlanificacionParametrosDTO parametros
    ) throws Exception
    {
        Planificacion planificacion;
        planificacion = parametros.converitrADominio(); // obtienes un Bussiness object <- aplicó la de Melgar NO WAY
        // El controlador no maneja lógica de negocio,
        // de preferencia solo debería llamar a un método de servicio
        planificacionServicio.planificar(parametros);
        planificacionServicio.persistir(planificacion); // persiste la planificacion para devolverla

        return ResponseEntity.ok(new PlanificacionRespuestaDTO(parametros.getInstanteActual()));
    }
}
