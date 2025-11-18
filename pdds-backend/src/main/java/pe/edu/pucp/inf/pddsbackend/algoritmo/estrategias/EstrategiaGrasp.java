package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Primary;

import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Mapa;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Ruta;

@Primary
public class EstrategiaGrasp extends Estrategia
{

    public EstrategiaGrasp(Estado estadoInicial)
    {
        // 1. Se recibe un Estado inicial que debe estar correctamente inicializado.
        // Solo se cuenta con los productos existentes en Almacenes no infinitos
        super(estadoInicial);
    }

    /*
     * Punto de entrada del algoritmo. Para este punto ya se cuenta con el Estado
     * inicial. Retorna un booleano que indica el colapso
     */
    @Override
    public Boolean resolverPlanificacion()
    {
        Estado estadoHormiga;
        List<Producto> mejorSolucion;

        // 2. Iteración sobre MAX_ITER. Cada iteración esta compuesta de MAX_ANTS
        for (Integer iteracion = 0; iteracion != Hiperparametros.MAX_ITER; iteracion++)
        {
            estadoHormiga = this.estadoInicial.copiar();
            estadoHormiga.obtenerMapa();

            for (Integer ant = 0; ant != Hiperparametros.MAX_ITER; ant++)
            {   
                this.construirSolucion(estadoHormiga);
            }
        }

        return true;
    }

    /*
     * Esta es lo que hace un hormiga
     */
    private void construirSolucion(Estado estado)
    {
        
        Map<UUID, Producto> productosExistentes;
        Map<UUID, Pedido> pedidos;
        Ruta rutaElegida;
        Mapa mapa;
        
        productosExistentes = estado.getProductos();
        pedidos = estado.getPedidos();
        mapa = estado.getMapa();

        for(Producto productoExistente : productosExistentes.values())
        {
            //asignas tus productos existentes. Por el momento no existe xd
        }

        for(Pedido pedido : pedidos.values())
        {
            Producto productoNuevo;
            Integer demanda, capacidadMinima, cantidadProductosNuevos;

            demanda = pedido.getDemanda();

            while(demanda != 0)
            {
                rutaElegida = mapa.elegirRutaAleatoria(pedido.getAlmacenDestino());
                capacidadMinima = rutaElegida.getCapacidadMinima();
                cantidadProductosNuevos = (capacidadMinima > demanda)? demanda : capacidadMinima;

                for(Integer cantidad = 0; cantidad != cantidadProductosNuevos; cantidad++)
                {
                    productoNuevo = new Producto(rutaElegida.getAlmacenOrigen(), estado.getInstanteActual(), rutaElegida);

                    //HAY QUE VER BIEN COMO ES LA INTERACCION AL INGRESAR UN NUEVO PRODUCTO

                    // EN TEORIA SE DEBERIA VERIFICAR PRIMERO QUE HAYA CAPACIDAD EN CADA ALMACEN INTERMEDIO DE LA RUTA
                    // LUEGO SE DEBERIA VERIFICAR EL ESPACIO EN LOS VUELOS

                    // LUEGO AL MOMENTO DE ASIGNAR UN PRODUCTO SE DEBE ACTUALIZAR EL INVENTARIO DEL ALMACEN ORIGEN
                    // LUEGO SE AGREGAN CAMBIOS EN EL DELTACHANGE DE CADA ALMACEN INTERMEDIO
                    //LUEGO SE AGREGA AL INVENTARIO DE CADA AVION
                }
            }
        }


    }

}
