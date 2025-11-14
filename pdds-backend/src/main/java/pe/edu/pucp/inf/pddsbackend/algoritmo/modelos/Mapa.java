package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.GeneradorAleatorio;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

public class Mapa
{
    private Map<String, TreeSet<Ruta>> rutas;
    private Map<String, List<Vuelo>> adyacencia;

    /*
     * Construye un lista de Rutas para cada almacen con destino en ese almacen y origen en diversos almacenes
     */
    Mapa(Map<UUID, Vuelo> vuelos, Instant inicioOperaciones, List<Almacen> almacenes)
    {
        TreeSet<Ruta> rutasPosibles;

        adyacencia = this.construirAdyacencia(vuelos);
        this.rutas = new HashMap<>();

        for (Almacen almacen : almacenes)
        {
            rutasPosibles = this.construirRutasPorAlmacen(almacen, inicioOperaciones, almacenes);
            this.rutas.put(almacen.getId(), rutasPosibles);
        }
    }

    /*
     * Esta función inicializa la lista de adyacencia que empareja los almacenes con
     * todos aquellos vuelos que tiene com origen ese almacén
     */
    private Map<String, List<Vuelo>> construirAdyacencia(
            Map<UUID, Vuelo> vuelos)
    {
        Map<String, List<Vuelo>> adyacencia;
        
        adyacencia = new HashMap<>();

        for (Vuelo vuelo : vuelos.values())
        {
            Almacen almacenOrigen = vuelo.getAlmacenOrigen();
            adyacencia.computeIfAbsent(almacenOrigen.getId(), k -> new ArrayList<>())
                    .add(vuelo);
        }

        for (List<Vuelo> lista : adyacencia.values())
        {
            lista.sort(Comparator.comparing(Vuelo::getInstanteSalida));
        }

        return adyacencia;
    }

    /*
     * Construye las posibles rutas para un almacén
     */
    private TreeSet<Ruta> construirRutasPorAlmacen(Almacen almacenDestino, Instant inicioOperaciones,
            List<Almacen> almacenesOrigen)
    {
        Integer cantidad, indiceAleatorio;
        Almacen almacenOrigen;
        Ruta ruta;
        List<Integer> probabilidades;
        TreeSet<Ruta> rutas;

        probabilidades = this.calcularProbabilidades(almacenesOrigen);
        rutas = new TreeSet<>(Comparator.comparing(Ruta::getAptitud));

        for (Integer i = 0; i != Hiperparametros.MAX_RUTAS_POR_ALMACEN; i++)
        {
            indiceAleatorio = GeneradorAleatorio.eleccionProbabilistica(probabilidades);
            almacenOrigen = almacenesOrigen.get(indiceAleatorio);

            ruta = this.construirRuta(almacenOrigen, inicioOperaciones, almacenDestino);

            if (ruta.getEsVacia())
            {
                i--;
            }
            else
            {
                rutas.add(ruta);
            }
        }

        return rutas;
    }

    /*
     * Calcula las probabilidades para cada almacén debido a que se desea escoger
     * los almacenes infinitos preferentemente porque a este punto todavía no se
     * conoce el estado de los almacenes escala
     * 
     * TODO: Asignar probabilidades en base al volumen ocupado
     */
    private List<Integer> calcularProbabilidades(List<Almacen> almacenesOrigen)
    {
        List<Integer> probabilidades;

        probabilidades = new ArrayList<>();

        for (Almacen almacen : almacenesOrigen)
        {
            if (almacen.getCapacidad() < 0L)
            {
                probabilidades.add(Hiperparametros.PROB_ALMACEN_INFINITO);
            }
            else
            {
                probabilidades.add(Hiperparametros.PROB_ALMACEN_NORMAL);
            }
        }

        return probabilidades;
    }

    /*
     * Se está implementado un random walk entre los vuelos factibles
     */
    private Ruta construirRuta(Almacen almacenOrigen, Instant inicioOperaciones,
            Almacen almacenDestino)
    {
        Instant instanteActual;
        Almacen almacenActual;
        Vuelo vueloElegido;
        List<Vuelo> vuelosFactibles, ruta;

        almacenActual = almacenOrigen;
        instanteActual = inicioOperaciones;
        ruta = new ArrayList<>();

        while (!almacenActual.equals(almacenDestino))
        {

            vuelosFactibles = this.construirVuelosFactibles(almacenActual, instanteActual);

            if (vuelosFactibles.size() == 0)
            {
                break;
            }

            vueloElegido = this.elegirAleatorio(vuelosFactibles);

            almacenActual = vueloElegido.getAlmacenDestino();
            instanteActual = vueloElegido.getInstanteLlegada();
            ruta.add(vueloElegido);
        }

        return new Ruta(ruta, almacenDestino);
    }

    /*
     * Esta función devuelve los vuelos factible dada una instante inicial
     */
    private List<Vuelo> construirVuelosFactibles(Almacen almacen, Instant inicioOperaciones)
    {
        List<Vuelo> vuelos, vuelosFactibles;

        vuelosFactibles = new ArrayList<>();
        vuelos = this.adyacencia.get(almacen.getId());

        for (Vuelo vuelo : vuelos)
        {
            if (!vuelo.getInstanteSalida().isBefore(inicioOperaciones))
            {
                vuelosFactibles.add(vuelo);
            }
        }

        return vuelosFactibles;
    }

    /*
     * Se elige una vuelo aleatorio de entre los vuelos factibles
     */
    private Vuelo elegirAleatorio(List<Vuelo> vuelosFactibles)
    {
        Integer indiceAleatorio, limiteSuperior;

        limiteSuperior = vuelosFactibles.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return vuelosFactibles.get(indiceAleatorio);
    }
}
