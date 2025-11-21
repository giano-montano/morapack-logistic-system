package pe.edu.pucp.inf.pddsbackend.miscelaneo;


public final class Hiperparametros
{
    //legacy
    public static final int DIAS_CONTINENTAL = 2;
    public static final int DIAS_INTERCONTINENTAL = 3;
    public static final int HORAS_ESPERA_PARA_RECOJO = 2;

    public static final int ROTACION_PEDIDOS_DIAS = 7;
    public static final int INTERVALO_DIAS_AGREGAR_PEDIDOS = 1;
    public static final int INTERVALO_DIAS_AGREGAR_VUELOS = 1;

    // RELACIONADOS A GeneradorAleatorio
    public static final Long SEMILLA = 18112001L;

    private Hiperparametros()
    {
        throw new AssertionError("No se inicializa los Hiperparametros");
    }
}
