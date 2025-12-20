package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.sql.Time;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class GeneradorAleatorio
{
    private static volatile RandomGenerator random;
    private static volatile boolean inicializada = false;

    private GeneradorAleatorio()
    {
        throw new AssertionError("No se inicializa el GeneradorAleatorio");
    }

    /*
     * Devuelve un entero entre min y max
     */
    public static Integer entero(Integer min, Integer max)
    {
        asegurarInicializacion();

        return random.nextInt(max - min + 1) + min;
    }

    /*
     * Devuelve un decimal en el rango [0.0: 1.0[
     */
    public static Double decimal()
    {
        asegurarInicializacion();

        return random.nextDouble();
    }

    /*
     * En base a unas probabilidades, elije un elemento
     */
    public static Integer eleccionProbabilistica(List<Integer> probabilidades)
    {
        Integer suma, acumulado;
        Double decimalAleatorio;

        suma = 0;

        for (Integer i = 0; i != probabilidades.size(); i++)
        {
            suma += probabilidades.get(i);
        }

        decimalAleatorio = decimal() * suma;
        acumulado = 0;

        for (Integer i = 0; i != probabilidades.size(); i++)
        {
            acumulado += probabilidades.get(i);
            if (decimalAleatorio < acumulado)
            {
                return i;
            }
        }

        return probabilidades.size() - 1;
    }

    private static void inicializar()
    {
        inicializar(Hiperparametros.SEMILLA);
        //System.nanoTime());
    }

    private static void inicializar(Long semilla)
    {
        if (inicializada)
        {
            return;
        }

        random = RandomGeneratorFactory.of("L64X128MixRandom")
                .create(semilla);
        inicializada = true;
    }

    private static void asegurarInicializacion()
    {
        if (!inicializada)
        {
            inicializar();
        }
    }
}
