package pe.edu.pucp.inf.pddsbackend.exceptions;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@AllArgsConstructor
@Builder
public class ExceptionResponse {
    private Instant timestamp;
    private int status;
    private String error; // Podríamos añadir un código numérico de error tmb
    private String message;
    private String path;
}
