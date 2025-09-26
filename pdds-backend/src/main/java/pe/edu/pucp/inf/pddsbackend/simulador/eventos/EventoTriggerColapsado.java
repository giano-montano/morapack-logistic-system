package pe.edu.pucp.inf.pddsbackend.simulador.eventos;

import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.models.entities.TipoSimulacion;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class EventoTriggerColapsado implements EventoSimulacion {
    private final Instant hora;
    @Setter
    private Duration intervalo = Duration.of(5, ChronoUnit.MINUTES); // en base a param!!! y dinámico?!
    private final UUID id;

    public EventoTriggerColapsado(Instant hora, Duration intervalo, UUID id) {
        this.hora = hora;
        this.intervalo = intervalo;
        this.id = id;
    }


    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public Instant obtenerInstanteProgramado() {
        return hora;
    }

    @Override
    public void procesar(ContextoSimulacion ctx) {

    }
}
