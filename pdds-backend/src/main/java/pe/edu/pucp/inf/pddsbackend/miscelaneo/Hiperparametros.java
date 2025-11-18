package pe.edu.pucp.inf.pddsbackend.miscelaneo;

public final class Hiperparametros
{
    /*
     * PROB siempre va de 0 a 10
     */
    // RELACIONADOS A GeneradorAleatorio
    public static final Long SEMILLA = 18112001L;

    // RELACIONADOS A Mapa
    public static final Integer MAX_RUTAS_POR_ALMACEN = 50;
    public static final Integer PROB_ALMACEN_INFINITO = 6;
    public static final Integer PROB_ALMACEN_NORMAL = 2;
    public static final Integer MAX_PROFUNIDAD = 15;
    

    // RELACIONADOS A Estado
    public static final Integer UMBRAL_CAPACIDAD_OCUPADA = 50;

    // RELACIONADOS A ACO
    public static final Integer MAX_ITER = 1;
    public static final Integer MAX_HORMIGAS = 1;
    private Hiperparametros()
    {
        throw new AssertionError("No se inicializa los Hiperparametros");
    }
}
