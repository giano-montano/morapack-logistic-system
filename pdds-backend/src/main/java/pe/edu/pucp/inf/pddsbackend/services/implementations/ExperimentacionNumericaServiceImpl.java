package pe.edu.pucp.inf.pddsbackend.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.EstrategiaFija;
import pe.edu.pucp.inf.pddsbackend.dto.otros.ProcessResult;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PedidoService;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperimentacionNumericaServiceImpl
{

    private final PlanificacionService planificacionService;
    private final PedidoService pedidoService;
    private String DEFAULT_PEDIDOS_BASE = "archivos-axel";

    @Transactional
    public void correrPruebasDeAxel(List<String> archivos) throws Exception
    {
        String rutaArchivo = "resultados_exp_num.txt"; // Nombre del archivo a crear
        StringBuilder sbContenido = (new StringBuilder()).append("AXEL VAS A CAER\n");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo, true)))
        {
            int numArchivos = archivos.size();
            for (int i = 0; i < numArchivos; i++)
            {
                // reinicializarBD con pedidos de archivo
                pedidoService.destruirTodosPedidos();
                // buscar archivo y cargarlo
                ProcessResult resPedidos = new ProcessResult();
                String archivito = DEFAULT_PEDIDOS_BASE + "/" + archivos.get(i);
                System.out.println("archivo de pedidos a cargar y buscar: " + archivito);
                try (InputStream is = Utils.openResourceAsStream(archivito))
                {
                    if (is == null)
                    {
                        System.out.println("Archivo de pedidos no encontrado: " + archivito);
                    }
                    else
                    {
                        resPedidos = pedidoService.processOrders(is,
                                LocalDate.now().getMonthValue(), LocalDate.now().getYear());
                        System.out.println("Pedidos -> saved: " + resPedidos.getSavedCount()
                                + ", skipped: " + resPedidos.getSkippedCount());
                        if (!resPedidos.getErrors().isEmpty())
                        {
                            System.out
                                    .println("Pedidos - errores: " + resPedidos.getErrors().size());
                            resPedidos.getErrors().forEach(e -> System.out.println("  " + e));
                        }
                    }
                }
                catch (Exception e)
                {
                    System.err.println("Error procesando archivo pedidos: " + e.getMessage());
                    e.printStackTrace();
                }
                if (resPedidos.getErrors() != null && resPedidos.getErrors().size() > 0)
                {
                    System.out.println("NO PUEDE HABER ERRORESSSSS");
                    return;
                }
                // NADA DE SEMILLAS FIJAS
                // grasp:
                RealizarPlanificacionDTO paraGrasp = RealizarPlanificacionDTO.builder()
                        .estrategiaFija(EstrategiaFija.PROFUNDA).build();
                ResultadoAlgoritmoDTO salidaGrasp = planificacionService
                        .realizarPlanificacionConDatosDeBD(paraGrasp);
                // tabú:
                RealizarPlanificacionDTO paraTabu = RealizarPlanificacionDTO.builder()
                        .estrategiaFija(EstrategiaFija.RAPIDA).build();
                ResultadoAlgoritmoDTO salidaTabu = planificacionService
                        .realizarPlanificacionConDatosDeBD(paraTabu);

                sbContenido.append("(").append(salidaGrasp.fitness()).append(";")
                        .append(salidaTabu.fitness()).append(")\n");
            }
            writer.write(sbContenido.toString());
            System.out.println("Se ha escrito en el archivo correctamente.");
        }
        catch (IOException e)
        {
            System.err.println("Error al escribir en el archivo: " + e.getMessage());
            e.printStackTrace();
        }

    }

}
