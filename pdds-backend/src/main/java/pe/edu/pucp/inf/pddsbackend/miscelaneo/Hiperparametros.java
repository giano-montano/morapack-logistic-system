package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.time.Duration;

public final class Hiperparametros
{
    // legacy
    public static final int DIAS_CONTINENTAL = 2;
    public static final int DIAS_INTERCONTINENTAL = 3;
    public static final int HORAS_ESPERA_PARA_RECOJO = 2;

    public static final int ROTACION_PEDIDOS_DIAS = 7;
    public static final int INTERVALO_DIAS_AGREGAR_PEDIDOS = 1;
    public static final int INTERVALO_DIAS_AGREGAR_VUELOS = 1;

    // RELACIONADOS A GeneradorAleatorio
    public static final Long SEMILLA = 18112001L;

    // RELACIONADOS A EstadoGlobal
    public static final int MAX_RUTAS_POR_DESTINO = 2000; //2000 200;
    public static final int MAX_RUTAS_POR_ORIGEN = 600;// 195
    public static final int CAPACIDAD_INFINITA_SANA = 10_000;
    public static final Duration MINIMA_ESPERA_ENTRE_VUELOS = Duration.ofHours(1);

    // RELACIONADOS a la simulacion
    // a la que lo ejecutemos
    public static final int MAX_MINUTOS_ALGORITMO = 3; // 3 minutos
    public static double HORAS_SIMULADAS_1_MIN_REAL = 1.7; // Deberíamos modificarla en tiempo de ejecución a convieniencia.
    //Por eso no es final. Para las op día a día

    public static int HORAS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX = 4;
    // (int) Math.ceil( HORAS_SIMULADAS_1_MIN_REAL * MAX_MINUTOS_ALGORITMO );
    //            4;
    //Tampoco final porque deberíamos modificarla en tiempo de ejecución...
    // depende totalmente de la velocidad, pero lo estamos fijando en x250, no cambiar eso


    private Hiperparametros()
    {
        throw new AssertionError("No se inicializa los Hiperparametros");
    }
}
