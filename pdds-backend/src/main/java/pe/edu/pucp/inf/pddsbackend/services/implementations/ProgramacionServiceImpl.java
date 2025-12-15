package pe.edu.pucp.inf.pddsbackend.services.implementations;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.pedidos.PedidoMiniResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RutaProgramadaResumenDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaCardDTO;
import pe.edu.pucp.inf.pddsbackend.dto.rutas.RutaProgramadaListadaDTO;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.PedidoEntidad;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.ProgramacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProgramacionServiceImpl implements ProgramacionService
{

    @Override
    public List<RutaProgramadaResumenDTO> obtenerRutasProgramadasResumenSegunPedido(
            PedidoEntidad pedidoBD)
    {
        List<RutaProgramadaResumenDTO> lista = new ArrayList<>();
        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        assert ctx != null;
        EstadoGlobal estadoGlobal = ctx.getEstado();
        Pedido pedido = estadoGlobal.getPedidos().get(pedidoBD.getId());

        List<AbstractMap.SimpleEntry<LinkedList<Vuelo>, Integer>> rutasDelPedido = estadoGlobal
                .obtenerRutasDePedido(pedido.getId());

        for (AbstractMap.SimpleEntry<LinkedList<Vuelo>, Integer> rutita : rutasDelPedido)
        {
            List<String> almacenesEnRuta = estadoGlobal.obtenerAlmacenesPorRuta(rutita.getKey())
                    .stream()
                    .map(Almacen::getNombreCiudad).toList();

            RutaProgramadaResumenDTO resumen = new RutaProgramadaResumenDTO(
                    almacenesEnRuta,
                    rutita.getValue());

            lista.add(resumen);
        }
        return lista;
    }

    @Override
    public RutaProgramadaCardDTO devolverCardDeRutaProgramada(LinkedList<Long> idsVueloRuta)
    {

        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        EstadoGlobal estadoGlobal = ctx.getEstado();
        // System.out.println(idsVueloRuta);
        LinkedList<Vuelo> vuelosRuta = new LinkedList<>(
                idsVueloRuta.stream().map(aLong -> estadoGlobal.getVuelos().get(aLong)).toList());
        // System.out.println(vuelosRuta);
        // v- ineficiente, lo sé
        List<Producto> prodsRuta = estadoGlobal.obtenerProductosQueUsanRutaActiva(idsVueloRuta);
        List<Programacion> programacionesRuta = estadoGlobal
                .obtenerProgramacionesQueUsanRuta(idsVueloRuta);

        Vuelo vueloFinal = vuelosRuta.getLast();
        Almacen almFinal = estadoGlobal.getAlmacenes().get(vueloFinal.getAlmacenDestino());
        Set<Long> idsPedidosQUeAtiendeRuta = programacionesRuta.stream()
                .collect(Collectors.groupingBy(
                        Programacion::getIdPedido))
                .keySet();
        List<Pedido> pedidosQueAtiende = idsPedidosQUeAtiendeRuta.stream()
                .map(aLong -> estadoGlobal.getPedidos().get(aLong)).toList();

        List<PedidoMiniResumenDTO> pedidosResumidos = pedidosQueAtiende
                .stream().map(
                        pedido -> new PedidoMiniResumenDTO(pedido.getId(),
                                pedido.getCantidadProductos(),
                                pedido.getCantidadProductosSatisfechos(),
                                programacionesRuta.stream()
                                        .filter(p -> p.getIdPedido() == pedido.getId()).count()))
                .toList();

        List<String> nombresCiudades = new ArrayList<>();
        List<String> codigosVuelos = new ArrayList<>();
        for (Vuelo vuelo : vuelosRuta)
        {
            if (vuelo.equals(vuelosRuta.getFirst()))
            {
                nombresCiudades.add(estadoGlobal.getAlmacenes().get(vuelo.getAlmacenSalida().getId())
                        .getNombreCiudad());
            }
            nombresCiudades.add(
                    estadoGlobal.getAlmacenes().get(vuelo.getAlmacenDestino()).getNombreCiudad());
            codigosVuelos.add(vuelo.getCodigo());
        }
        RutaProgramadaCardDTO card = new RutaProgramadaCardDTO(
                prodsRuta.size(),
                almFinal.getNombreCiudad(),
                vueloFinal.getInstanteLlegada(),
                idsPedidosQUeAtiendeRuta.size(),
                idsVueloRuta.size(),
                vueloFinal.getInstanteLlegada().isBefore(ctx.obtenerElAhora()) && vueloFinal.isCancelado(),
                ctx.getUltimaPlanificacion(),
                nombresCiudades,
                codigosVuelos,
                pedidosResumidos);

        return card;
    }

    @Override
    public Page<RutaProgramadaListadaDTO> listarRutasProgramadas(
            int page, int size, String sortBy, boolean ascending)
    {

        ContextoSimulacion ctx = ContextoSimulacion.obtenerUnicaInstanciaSiExiste();
        EstadoGlobal estadoGlobal = ctx.getEstado();

        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        List<RutaProgramadaListadaDTO> pageRutas = estadoGlobal.obtenerRutasProgramadas();

        final int start = (int) pageable.getOffset();
        final int end = Math.min((start + pageable.getPageSize()), pageRutas.size());
        List<RutaProgramadaListadaDTO> pageContent = pageRutas.subList(start, end);
        Page<RutaProgramadaListadaDTO> pagina = new PageImpl<>(pageContent, pageable,
                pageRutas.size());
        return pagina;
    }
}
