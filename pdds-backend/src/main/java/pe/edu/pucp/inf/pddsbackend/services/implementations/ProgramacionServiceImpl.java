package pe.edu.pucp.inf.pddsbackend.services.implementations;

import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RutaProgramadaResumenDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ProgramacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class ProgramacionServiceImpl implements ProgramacionService {

    @Override
    public List<RutaProgramadaResumenDTO> obtenerRutasProgramadasResumenSegunPedido(PedidoEntidad pedidoBD){
        List<RutaProgramadaResumenDTO> lista = new ArrayList<>();
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        assert ctx != null;
        EstadoGlobal estadoGlobal = ctx.getEstado();
        Pedido pedido = estadoGlobal.getPedidos().get(pedidoBD.getId());

        List<  AbstractMap.SimpleEntry< LinkedList<Vuelo>, Integer> > rutasDelPedido = estadoGlobal.obtenerRutasDePedido(pedido.getId());

        for ( AbstractMap.SimpleEntry< LinkedList<Vuelo>, Integer> rutita : rutasDelPedido) {
            List<String> almacenesEnRuta = estadoGlobal.obtenerAlmacenesPorRuta(rutita.getKey()).stream()
                    .map(Almacen::getNombreCiudad).toList();

            RutaProgramadaResumenDTO resumen = new RutaProgramadaResumenDTO(
                    almacenesEnRuta,
                    rutita.getValue()
            );

            lista.add(resumen);
        }
        return  lista;
    }

}
