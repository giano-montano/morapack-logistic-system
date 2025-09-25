package pe.edu.pucp.inf.pddsbackend.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ErrorDuranteAlgoritmoException.class)
    public ResponseEntity<ExceptionResponse> manejarErrorAlgoritmo(ErrorDuranteAlgoritmoException e, HttpServletRequest req) {
        return ResponseEntity.status(501).body(ExceptionResponse.builder()
                .status(501)
                .timestamp(Instant.now())
                .error("ERROR_ALGORITMO")
                .message(e.getMessage())
                .path(req.getRequestURI())
                .build());
    }

    @ExceptionHandler(ColapsadoExceptionTemporal.class)
    public ResponseEntity<ExceptionResponse> manejarColapso(ColapsadoExceptionTemporal e, HttpServletRequest req) {
        return ResponseEntity.status(501).body(ExceptionResponse.builder()
                .status(501)
                .timestamp(Instant.now())
                .error("ERROR_COLAPSO")
                .message(e.getMessage())
                .path(req.getRequestURI())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception e, HttpServletRequest req) {
        return ResponseEntity.status(501).body(ExceptionResponse.builder()
                .status(501)
                .timestamp(Instant.now())
                .error("INTERNAL_SERVER_ERROR")
                .message(e.getMessage())
                .path(req.getRequestURI())
                .build());
    }
}
