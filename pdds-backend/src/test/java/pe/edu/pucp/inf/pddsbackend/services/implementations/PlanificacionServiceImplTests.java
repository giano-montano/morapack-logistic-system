package pe.edu.pucp.inf.pddsbackend.services.implementations;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.*;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.repositories.*;

@SpringBootTest
class PlanificacionServiceImplTest
{
    @MockBean
    private SimulacionRepository simulacionRepository;
    @MockBean
    private LoggingReport loggingReport;
    @MockBean
    private PlanificacionRepository planificacionRepository;
    @MockBean
    private ProgramacionRepository rutaProgramadaRepository;
    @MockBean
    private ProgramacionXVueloRepository rutaProgramadaXVueloRepository;
    @MockBean
    private ProductoRepository productoRepository;

    @Autowired
    private VueloRepository vueloRepository;
    @Autowired
    private AlmacenRepository almacenRepository;
    @Autowired
    private PedidoRepository pedidoRepository;

    @InjectMocks
    @Autowired
    private PlanificacionServiceImpl planificacionService;

    private RealizarPlanificacionDTO param = RealizarPlanificacionDTO.builder()
            .instanteDesdeTomarPedidos(Instant.parse("2025-01-01T22:00:00Z"))
            .instanteActual(Instant.parse("2025-01-02T00:00:00Z"))
            // .instanteActual(Instant.parse("2025-01-02T23:59:59Z"))
            .seed(18112001L)
            .build();

    @Test
    void realizarPlanificacionConDatosDeBDTest()
    {
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        Bitacora.escribir("realizarPlanificacionConDatosDeBD ejecutando");
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        assertDoesNotThrow(() -> {
            ResultadoAlgoritmoDTO solucion;
            EstadoGlobal estado;

            estado = this.planificacionService.obtenerDatosParaAlgoritmo(this.param, false);

            solucion = this.planificacionService.realizarPlanificacionConDatosDeBD(this.param);

            Bitacora.escribir("Solución: ");
            Bitacora.escribir(solucion.toString());
        });

        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    }

    // @Test
    void obtenerDatosParaAlgoritmoTest()
    {
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        Bitacora.escribir("obtenerDatosParaAlgoritmo ejecutando");
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        assertDoesNotThrow(() -> {
            this.planificacionService.obtenerDatosParaAlgoritmo(this.param, false);
        });

        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    }

}
