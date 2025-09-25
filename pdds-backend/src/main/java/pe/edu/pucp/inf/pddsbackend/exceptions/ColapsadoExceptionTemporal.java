package pe.edu.pucp.inf.pddsbackend.exceptions;

public class ColapsadoExceptionTemporal extends Exception{
    String mensaje;
    public ColapsadoExceptionTemporal(String mensaje) {
        super(mensaje);
        this.mensaje = mensaje;
    }
}
