package pe.edu.pucp.inf.pddsbackend.dto.planificaciones;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.TipoSimulacion;

import java.time.Instant;
import java.util.ArrayList;

public record SimulacionRequestDTO(
        @NotNull TipoSimulacion tipoSimulacion,
        ArrayList<Object> parametros,
        Long maximoTimeOutSegundosPorPlanif,
        Double factorDeVelocidad, // todavía no hago que sea dinámico
        Long minutosRealesEntrePlanificaciones,
        Long seed,
        Boolean usarModoMock, // true = usa planificación mock (para testing), false/null = usa
                              // GRASP real
        Instant fechaHoraInicioSimulacion // Nueva: fecha y hora de inicio de la simulación virtual
) {
}
