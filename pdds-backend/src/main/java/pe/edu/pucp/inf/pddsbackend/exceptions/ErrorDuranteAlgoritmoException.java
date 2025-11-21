package pe.edu.pucp.inf.pddsbackend.exceptions;

public class ErrorDuranteAlgoritmoException extends Exception
{
    String mensaje;
    public ErrorDuranteAlgoritmoException(String mensaje)
    {
        super(mensaje);
        this.mensaje = mensaje;
    }
}
