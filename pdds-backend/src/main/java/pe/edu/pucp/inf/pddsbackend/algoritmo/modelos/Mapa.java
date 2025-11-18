package pe.edu.pucp.inf.pddsbackend.algoritmo.modelos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.GeneradorAleatorio;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Ruta;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

@Getter
public class Mapa
{
    private Map<UUID, TreeSet<Ruta>> rutas;
    private Map<UUID, List<Vuelo>> adyacencia;

    /*
     * Construye un lista de Rutas para cada almacen con destino en ese almacen y
     * origen en diversos almacenes.
     *
     * TODO: Implementar las feromonas
     */
    Mapa(Map<UUID, List<Vuelo>> adyacencia, Set<Almacen> almacenesInfinitos,
            Set<Almacen> almacenesConInventario, Set<Almacen> almacenesConDemanda,
            Instant instanteActual)
    {
        List<Almacen> almacenesOrigen;
        TreeSet<Ruta> rutasPosibles;

        this.adyacencia = adyacencia;
        this.rutas = new HashMap<>();
        almacenesInfinitos.addAll(almacenesConInventario);
        almacenesOrigen = new ArrayList<>(almacenesInfinitos);

        /*
        Bitacora.escribir("Almacenes con infinitos: %d", almacenesInfinitos.size());
        Bitacora.escribir(almacenesInfinitos.toString());

        Bitacora.escribir("Almacenes con demanda: %d", almacenesConDemanda.size());
        Bitacora.escribir(almacenesConDemanda.toString());

        Bitacora.escribir("Almacenes con inventario: %d", almacenesConInventario.size());
        Bitacora.escribir(almacenesConInventario.toString());
        */

        for (Almacen almacen : almacenesConDemanda)
        {
            rutasPosibles = this.construirRutasPorAlmacen(almacen, instanteActual, almacenesOrigen);
            this.rutas.put(almacen.getId(), rutasPosibles);
        }
    }

    /*
     * Construye las posibles rutas para un almacén
     */
    private TreeSet<Ruta> construirRutasPorAlmacen(Almacen almacenDestino,
            Instant instanteActual,
            List<Almacen> almacenesOrigen)
    {
        Integer cantidad, indiceAleatorio;
        Almacen almacenOrigen;
        Ruta ruta;
        List<Integer> probabilidades;
        TreeSet<Ruta> rutas;

        probabilidades = this.calcularProbabilidades(almacenesOrigen);
        rutas = new TreeSet<>(Comparator.comparing(Ruta::getId));

        for (Integer i = 0, j=0; i != Hiperparametros.MAX_RUTAS_POR_ALMACEN; i++, j++)
        {
            indiceAleatorio = GeneradorAleatorio.eleccionProbabilistica(probabilidades);
            almacenOrigen = almacenesOrigen.get(indiceAleatorio);

            ruta = this.construirRuta_randomWalk(almacenOrigen, instanteActual, almacenDestino);

            if (ruta == null)
            {
                i--;
            }
            else
            {
                /*
                Bitacora.escribir("Ruta generada intento (%d) para: %s -> %s", j, almacenOrigen.getPais(), almacenDestino.getPais());
                Bitacora.escribir(ruta.toString());
                */
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
    private Ruta construirRuta_randomWalk(Almacen almacenOrigen, Instant instanteActual,
            Almacen almacenDestino)
    {
        Almacen almacenActual;
        Vuelo vueloElegido;
        List<Vuelo> vuelosFactibles, ruta;

        almacenActual = almacenOrigen;
        ruta = new ArrayList<>();

        while (!almacenActual.equals(almacenDestino))
        {

            vuelosFactibles = this.construirVuelosFactibles(almacenActual, instanteActual);

            if (vuelosFactibles.size() == 0)
            {
                return null;
            }

            vueloElegido = this.elegirVueloAleatorio(vuelosFactibles);

            almacenActual = vueloElegido.getAlmacenDestino();
            instanteActual = vueloElegido.getInstanteLlegada();
            ruta.add(vueloElegido);
        }

        return new Ruta(almacenOrigen, ruta, almacenDestino);
    }

    /*
    * Best First Search para construir una ruta desde almacenOrigen hasta almacenDestino
    * respetando el tiempo (instanteActual) y evitando ciclos simples. by GPT xd
    */
    private Ruta construirRuta_bestFirst(Almacen almacenOrigen,
                                        Instant instanteInicial,
                                        Almacen almacenDestino)
    {
        class NodoRuta {
            Almacen almacenActual;
            Instant instanteActual;
            List<Vuelo> vuelos;    
            double puntaje;          
            Set<UUID> visitados;

            NodoRuta(Almacen almacenActual,
                    Instant instanteActual,
                    List<Vuelo> vuelos,
                    Set<UUID> visitados,
                    double puntaje) {
                this.almacenActual = almacenActual;
                this.instanteActual = instanteActual;
                this.vuelos = vuelos;
                this.visitados = visitados;
                this.puntaje = puntaje;
            }
        }

        PriorityQueue<NodoRuta> frontera = new PriorityQueue<>(Comparator.comparingDouble(nodo -> nodo.puntaje));

        Set<UUID> visitadosInicial = new HashSet<>();
        visitadosInicial.add(almacenOrigen.getId());

        NodoRuta nodoInicial = new NodoRuta(
            almacenOrigen,
            instanteInicial,
            new ArrayList<>(),
            visitadosInicial,
            0.0  // coste inicial = 0
        );
        frontera.add(nodoInicial);

        int maxExpansiones = 5000;  // límite de seguridad
        int expansiones = 0;

        while (!frontera.isEmpty() && expansiones < maxExpansiones) {
            NodoRuta actual = frontera.poll();
            expansiones++;

            // 1. ¿Hemos llegado al destino?
            if (actual.almacenActual.equals(almacenDestino)) {
                return new Ruta(almacenOrigen, actual.vuelos, almacenDestino);
            }

            // 2. Expandir vuelos factibles desde el almacén actual
            List<Vuelo> vuelosFactibles = this.construirVuelosFactibles(
                actual.almacenActual,
                actual.instanteActual
            );

            for (Vuelo vuelo : vuelosFactibles) {
                Almacen siguienteAlmacen = vuelo.getAlmacenDestino();
                UUID idSig = siguienteAlmacen.getId();

                // Evitar ciclos: no volver a un almacén ya visitado en este camino
                if (actual.visitados.contains(idSig)) {
                    continue;
                }

                // Construir nuevo camino
                List<Vuelo> nuevoCamino = new ArrayList<>(actual.vuelos);
                nuevoCamino.add(vuelo);

                Set<UUID> nuevosVisitados = new HashSet<>(actual.visitados);
                nuevosVisitados.add(idSig);

                Instant nuevoInstante = vuelo.getInstanteLlegada();

                // ---- Cálculo de puntaje (aquí puedes enchufar feromonas más adelante) ----
                // Ejemplo simple: coste = tiempo total hasta ahora + penalización por número de vuelos
                long minutosTotales = Duration.between(instanteInicial, nuevoInstante).toMinutes();
                int numVuelos = nuevoCamino.size();
                double costo = minutosTotales + 10.0 * (numVuelos - 1); // penaliza escalas

                NodoRuta hijo = new NodoRuta(
                    siguienteAlmacen,
                    nuevoInstante,
                    nuevoCamino,
                    nuevosVisitados,
                    costo
                );

                frontera.add(hijo);
            }
        }

        // Si no encontramos ruta dentro del límite de expansiones
        return null;
    }


    /*
     * Esta función devuelve los vuelos factible dada una instante inicial
     */
    private List<Vuelo> construirVuelosFactibles(Almacen almacen, Instant instanteActual)
    {
        List<Vuelo> vuelos, vuelosFactibles;

        vuelosFactibles = new ArrayList<>();
        vuelos = this.adyacencia.get(almacen.getId());

        for (Vuelo vuelo : vuelos)
        {
            if (!vuelo.getInstanteSalida().isBefore(instanteActual))
            {
                vuelosFactibles.add(vuelo);
            }
        }

        return vuelosFactibles;
    }

    /*
     * Se elige una vuelo aleatorio de entre los vuelos factibles
     */
    private Vuelo elegirVueloAleatorio(List<Vuelo> vuelosFactibles)
    {
        Integer indiceAleatorio, limiteSuperior;

        limiteSuperior = vuelosFactibles.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return vuelosFactibles.get(indiceAleatorio);
    }

    /*
     * Se elige una ruta aleatoria entre las rutas disponibles
     * 
     * TODO: Esto debe ser guiado por las feromonas y la heristica
     */
    public Ruta elegirRutaAleatoria(Almacen almacenDestino)
    {
        Integer indiceAleatorio, limiteSuperior;
        TreeSet<Ruta> rutasHaciaAlmacenDestino;
        List<Ruta> rutas; 

        rutasHaciaAlmacenDestino = this.rutas.get(almacenDestino.getId());
        rutas = new ArrayList<>(rutasHaciaAlmacenDestino);
        limiteSuperior = rutasHaciaAlmacenDestino.size() - 1;
        indiceAleatorio = GeneradorAleatorio.entero(0, limiteSuperior);

        return rutas.get(indiceAleatorio);
    }

}
