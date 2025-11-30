package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.LinkedList;

import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;

public final class Testeador
{
    private Testeador()
    {
        throw new AssertionError("No se inicializa el Testeador");
    }

    public static void inicializacionTest(EstadoGlobal estado, Instant instanteActual)
            throws Exception
    {
        int nAlmacenes, nVuelos, nPedidos;
        Instant instante;

        nAlmacenes = estado.getAlmacenes().size();
        nVuelos = estado.getVuelos().size();
        nPedidos = estado.getPedidos().size();
        instante = Instant.parse("2025-01-02T00:00:00Z");

        if (!instanteActual.equals(instante))
        {
            throw new Exception(
                    "El instante actual no es el especificado no es 2025-01-02T00:00:00Z");
        }

        if (nAlmacenes != 30)
        {
            throw new Exception("El numero de almacenes no es 30");
        }

        if (nPedidos != 32)
        {
            throw new Exception("El numero de pedidos no es 32");
        }

        if (nVuelos != 7318)
        {
            throw new Exception("El numero de vuelos no es 7318");
        }
    }

    
    public static void generacionRutasTest(EstadoGlobal estado) throws Exception
    {
        int nRutas;
        Long almacenOrigen, almacenDestino;
        Set<Long> almacenesConDemanda, almacenesConStock;
        HashMap<Long, List<LinkedList<Long>>> rutasPorAlmacen;
        List<Vuelo> vuelosEnRuta;

        nRutas = 0;
        almacenesConDemanda = estado.obtenerAlmacenesConDemanda(estado.getPedidos(), estado.getAlmacenes());
        almacenesConStock = estado.obtenerAlmacenesConStock(estado.getAlmacenes());
        rutasPorAlmacen = estado.getRutasPorIdAlmacenDestino();

        for(List<LinkedList<Long>> rutasAUnAlmacen : rutasPorAlmacen.values())
        {
            for(LinkedList<Long> ruta : rutasAUnAlmacen)
            {
                vuelosEnRuta = estado.obtenerVariosVuelosPorIds(ruta, null);
                almacenOrigen = vuelosEnRuta.get(0).getIdAlmacenOrigen();
                almacenDestino = vuelosEnRuta.get(ruta.size() - 1).getIdAlmacenDestino();
                nRutas++;

                if(!almacenesConDemanda.contains(almacenDestino) || !almacenesConStock.contains(almacenOrigen))
                {
                    throw new Exception("Una ruta tiene origen o destino ilegal");
                }
            }
            
        }

        if(nRutas != 10530)
        {
            throw new Exception("El número de rutas no coincide con la semilla");
        }
    }

    //public static void verificar
}
