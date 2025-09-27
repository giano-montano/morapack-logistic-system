package pe.edu.pucp.inf.pddsbackend.utils;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.LoggedHeuristicAlgorithmStrategy;

import java.io.File;
import java.io.IOException;
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

    @Setter
    private   Logger log = LoggerFactory.getLogger(LoggingReport.class); // Por qué está el heuristic ahí? XD
    public static  final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    @Setter
    private boolean imprimirPorLogger=false;
    StringBuilder report = new StringBuilder();
    @Setter
    private String directory="";

    public  void appendReport(String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = "[" + ts + "] " + msg;
        report.append(line).append(System.lineSeparator());
        // También logueamos inmediatamente con logger y consola
        if(imprimirPorLogger) log.info(line);

//        System.out.println(line);
    }

    public  int safeSize(Collection<?> c) { return c == null ? 0 : c.size(); }

    public void writeReportFile(String reportName) throws Exception {
        if(directory!=null && !directory.isEmpty()) {
            writeReportFileInDirectory(reportName);
            return;
        }
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
        report.delete(0, report.length()); // liberar xd
    }

    public void limpiarReporte() {
        report.delete(0, report.length());
    }

    public void limpiarReporteYDirectorio() {
        report.delete(0, report.length());
        directory="";
    }
    public void limpiarDirectorio() {
        directory="";
    }

    public void writeReportFileInDirectory(String reportName) throws IOException {
        // Nombre de archivo
        String fileName = reportName + LocalDateTime.now().format(TS_FMT) + ".log";

        // Base reports (absoluta y normalizada)
        Path baseDir = Paths.get("reports").toAbsolutePath().normalize();

        // Sanear el input directory (evitar espacios o barras iniciales)
        String d = directory == null ? "" : directory.trim();
        if (d.startsWith(File.separator) || d.startsWith("/") ) {
            d = d.replaceFirst("^[\\/\\\\]+", ""); // quitar slashes iniciales
        }

        // Resolver y normalizar
        Path dir = d.isEmpty() ? baseDir : baseDir.resolve(d).normalize();

        // Evitar path traversal: la ruta resultante debe empezar por baseDir
        if (!dir.startsWith(baseDir)) {
            throw new IOException("Ruta inválida o intento de escape del directorio base: " + directory);
        }

        // Crear directorios si no existen
        Files.createDirectories(dir);

        // Archivo final
        Path file = dir.resolve(fileName);

        // Escribir (CREATE_NEW -> falla si ya existe)
        Files.write(file, report.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);

        log.info("Reporte guardado en {}", file.toAbsolutePath());
        System.out.println("Reporte guardado en " + file.toAbsolutePath());
    }

    @Override
    public String toString() {
        return "Logger, mi directorio para subcarpeta en reports es :" + directory;
    }

}
