package pe.edu.pucp.inf.pddsbackend.miscelaneo;

public final class Hiperparametros
{
    /*
     * PROB siempre va de 0 a 10
     */

    // RELACIONADOS A Mapa
    public static final Integer MAX_RUTAS_POR_ALMACEN = 15;
    public static final Integer PROB_ALMACEN_INFINITO = 6;
    public static final Integer PROB_ALMACEN_NORMAL = 2;

    //RELACIONADOS A Esado
    public static final Integer UMBRAL_CAPACIDAD_OCUPADA = 50;

    private Hiperparametros()
    {
        throw new AssertionError("No se inicializa los Hiperparametros");
    }
}
