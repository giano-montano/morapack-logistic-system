package pe.edu.pucp.inf.pddsbackend.algorithms.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.LoggedHeuristicAlgorithmStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@Component
public class LoggingReport {

    private  final Logger log = LoggerFactory.getLogger(LoggedHeuristicAlgorithmStrategy.class);
    private  final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    StringBuilder report = new StringBuilder();

    public  void appendReport(String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = "[" + ts + "] " + msg;
        report.append(line).append(System.lineSeparator());
        // También logueamos inmediatamente con logger y consola
        log.info(line);
//        System.out.println(line);
    }

    public  int safeSize(Collection<?> c) { return c == null ? 0 : c.size(); }

    public void writeReportFile(String reportName) throws Exception {
        String fileName = reportName + LocalDateTime.now().format(TS_FMT) + ".log";
        Path dir = Paths.get("reports");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve(fileName);
        // Crear/Escribir (no append, se crea un archivo nuevo por ejecución)
        Files.write(file, report.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        log.info("Reporte guardado en {}", file.toAbsolutePath());
        System.out.println("Reporte guardado en " + file.toAbsolutePath());
    }

}
