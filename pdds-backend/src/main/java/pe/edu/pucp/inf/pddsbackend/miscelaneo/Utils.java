package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@RequiredArgsConstructor
public class Utils {

    private static final ResourceLoader resourceLoader= new DefaultResourceLoader();


    /**
     * Intenta abrir un InputStream desde:
     *  1) classpath: + path (útil para resources/archivos-inicializador/...)
     *  2) path absoluto en filesystem (si no existe en classpath)
     *  3) si se pasa una URL (file:/...) ResourceLoader lo maneja si se indica explícitamente
     *
     * Devuelve null si no pudo abrirlo.
     */
    public static InputStream openResourceAsStream(String pathOrClasspath) {
        try {
            // Primero intentar classpath: si el path ya contiene "classpath:" no lo preprendemos
            Resource res = resourceLoader.getResource(pathOrClasspath.startsWith("classpath:") ? pathOrClasspath : "classpath:" + pathOrClasspath);
            if (res.exists() && res.isReadable()) {
                return res.getInputStream();
            }
            // Segundo: tratarlo como filesystem absolute/relative
            Path p = Paths.get(pathOrClasspath);
            if (Files.exists(p) && Files.isReadable(p)) {
                return Files.newInputStream(p);
            }
            // Tercero: intentar cargar con resourceLoader tal cual (por si el usuario pasó "file:/..." o "classpath:" ya incluido)
            Resource res2 = resourceLoader.getResource(pathOrClasspath);
            if (res2.exists() && res2.isReadable()) {
                return res2.getInputStream();
            }
        } catch (Exception e) {
            System.err.println("openResourceAsStream: no se pudo abrir '" + pathOrClasspath + "' -> " + e.getMessage());
        }
        return null;
    }
}
