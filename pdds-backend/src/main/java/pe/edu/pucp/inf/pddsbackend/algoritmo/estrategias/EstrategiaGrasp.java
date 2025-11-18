package pe.edu.pucp.inf.pddsbackend.algoritmo.estrategias;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Primary;

import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Estado;
import pe.edu.pucp.inf.pddsbackend.algoritmo.modelos.Mapa;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
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
        this.asignarProductosNuevos(estado);
    }

    private void asignarProductosNuevos(Estado estado)
    {
        Boolean asignadoCorrectamente;
        Integer productosASatisfacerDelPedido, productosPendientesEnPedido, espacioVacioEnRuta, intentoAsignarRuta;
        Producto productoNuevo;
        Almacen almacenOrigen;
        Ruta rutaSeleccionada;
        Map<UUID, Pedido> pedidos;
        List<Producto> productosNuevos, productosDisponiblesEnAlmacen;

        pedidos = estado.getPedidos();

        for(Pedido pedido : pedidos.values())
        {
            intentoAsignarRuta = 0;

            do
            {
                //rutaSeleccionada = estado.seleccionarRuta(pedido.getAlmacenDestino(), pedido.getInstanteEntrega(), pedido.esIntercontinental(), intentoAsignarRuta);
                rutaSeleccionada = estado.getMapa().elegirRutaAleatoria(pedido.getAlmacenDestino());
                
                if(rutaSeleccionada != null)
                {
                    productosPendientesEnPedido = pedido.getCantidadProductosPendientes();
                    espacioVacioEnRuta = rutaSeleccionada.calcularEspacioVacioMaximoEnRuta();

                    productosASatisfacerDelPedido = Math.min(productosPendientesEnPedido, espacioVacioEnRuta);
                    productosNuevos = new ArrayList<>();

                    for(int i = 0; i != productosASatisfacerDelPedido; i++)
                    {
                        productosNuevos.add(new Producto(rutaSeleccionada, pedido));
                    }

                    estado.asignarProductosAPedido_Ruta_Almacenes_Vuelos(pedido, rutaSeleccionada, productosNuevos);
                }

                intentoAsignarRuta++;
            }
            while (pedido.getCantidadProductosPendientes() > 0 && intentoAsignarRuta < Hiperparametros.MAX_INTENTOS);

            if(pedido.getCantidadProductosPendientes() > 0)
            {
                Bitacora.escribir("COLAPSO_FALTA_RUTAS");
                return;
            }
        }
    }
}
