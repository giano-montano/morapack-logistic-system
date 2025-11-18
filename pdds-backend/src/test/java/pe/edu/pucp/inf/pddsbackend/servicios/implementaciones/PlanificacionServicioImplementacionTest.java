package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;

@ExtendWith(MockitoExtension.class)
class PlanificacionServicioImplementacionTest
{

    @Mock
    private PlanificacionRepositorio planificacionRepositorio;

    private PlanificacionServicioImplementacion servicio;

    @BeforeEach
    void setUp()
    {
        servicio = new PlanificacionServicioImplementacion(planificacionRepositorio);
    }


    private int capacidad = 20;
    private List<Integer> inventario;
    private TreeMap<Integer, Integer> cambios;
    private Boolean esInfinito = false;

    private Integer DELTAAA;

    @Test
    void planificarTest()
    {
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        Bitacora.escribir("planificarTest ejecutando");
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        Planificacion planificacion = crearPlanificacionDummy();

        assertDoesNotThrow(() -> {
            servicio.planificar(planificacion);
        });

        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    }

    private Planificacion crearPlanificacionDummy()
    {
        return Planificacion.builder()
                .id(UUID.randomUUID())
                .instanteActual(Instant.parse("2025-01-01T10:00:00Z"))
                .build();
    }
}
