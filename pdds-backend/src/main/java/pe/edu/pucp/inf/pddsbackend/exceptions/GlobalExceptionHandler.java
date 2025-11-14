package pe.edu.pucp.inf.pddsbackend.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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



    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleVaina(ConstraintViolationException e, HttpServletRequest req) {
        List a = new ArrayList<>();
        e.getConstraintViolations().forEach( v -> a.add( v.getMessage()) );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(ExceptionResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(Instant.now())
                .error("VALIDATION_ERROR")
                .message(a.toString())
                .path(req.getRequestURI())
                .build());
    }


    @ExceptionHandler(ExcepcionLogica.class)
    public ResponseEntity<ExceptionResponse> handleLogic(ExcepcionLogica e, HttpServletRequest req) {

        return ResponseEntity.status(HttpStatus.CONFLICT.value()).body(ExceptionResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .timestamp(Instant.now())
                .error("LOGIC_ERROR")
                .message(e.mensaje)
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
