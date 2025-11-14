package pe.edu.pucp.inf.pddsbackend.exceptions;

public class ExcepcionLogica extends Exception {
    String mensaje;

    public ExcepcionLogica(String mensaje) {
        super(mensaje);
        this.mensaje = mensaje;

    }
}
