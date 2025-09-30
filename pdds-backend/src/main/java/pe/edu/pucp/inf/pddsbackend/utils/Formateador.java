package pe.edu.pucp.inf.pddsbackend.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class Formateador {

    public static DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    public static String utcFormatter(Instant instante) {
        return utcFormatter.format(instante);
    }
}
