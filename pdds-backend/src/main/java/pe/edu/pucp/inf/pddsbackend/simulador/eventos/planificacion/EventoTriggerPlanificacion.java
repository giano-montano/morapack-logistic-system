package pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EntradaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.ResultadoAlgoritmoDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.services.interfaces.PlanificacionService;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.websocket.service.SimulacionWebSocketService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class EventoTriggerPlanificacion extends EventoSimulacion
{
    @NotNull
    UUID uuid;
    @NotNull
    Instant instanteProgramado;

    private final PlanificacionService planificacionService;

    // Servicio WebSocket (puede ser null si no está disponible)
    private SimulacionWebSocketService webSocketService;

    private static final int MAXIMO_ESPERA_ALGORITMO_SEGUNDOS = 300;

    public static ExecutorService hiloEjecutorActual = null;

    @Override
    public UUID getId()
    {
        return uuid;
    }

    @Override
    public Instant obtenerInstanteProgramado()
    {
        return instanteProgramado;
    }

    static int contador = 0;

    @Override
    public void procesar(ContextoSimulacion ctx) throws Exception {
        System.out.println("🔄 EventoTriggerPlanificacion nro: " + (contador+1));
        ctx.log("🔄 EventoTriggerPlanificacion nro: " + (contador+1));

        if (ctx.isPlanificacionDesactivada()) {
            return;
        }

Bitacora.escribir("============ ALGORITMO %d ============", ++this.contador);
        
        Instant instanteAlgoritmo, instanteSimulacion;
        EntradaProblemaPlanificacion entradaAlgoritmo;
        EstadoGlobal estadoFiltrado, estadoAvanzado;
        ExecutorService executor;

        instanteSimulacion = ctx.getAhora();
        instanteAlgoritmo = instanteSimulacion.plus(Duration.ofMinutes(Hiperparametros.MINUTOS_SIMULADAS_QUE_TOMARA_ALGORITMO_APROX));
        executor = Executors.newSingleThreadExecutor();
        hiloEjecutorActual = executor; // ACTUAL

Bitacora.escribir("Hora de la simulación: %s", Bitacora.formatearInstante(instanteSimulacion));
Bitacora.escribir("Hora del algoritmo     %s:\n", Bitacora.formatearInstante(instanteAlgoritmo));
ctx.log("🔄 EventoTriggerPlanificacion Hora de la simulación: "+instanteSimulacion);
ctx.log("🔄 EventoTriggerPlanificacion Hora del algoritmo: " + instanteAlgoritmo);

Bitacora.escribir(ctx.getEstado(), "Estado del ctx en algoritmo");

//Testeador.paraUnEkCualquiera(instanteSimulacion, ctx.getEstado(), ctx.getParams().fechaHoraInicioSimulacion());

        /* Aquí debería ir el WebSocket*/

        //estadoAvanzado = EstadoGlobal.obtenerEstadoGlobalEnInstante_v2(ctx.getEstado(), instanteAlgoritmo);
        estadoAvanzado = ctx.simularUnNuevoFuturo(instanteAlgoritmo);
Bitacora.escribir(estadoAvanzado, "EstadoAvanzado");
//Testeador.paraUnEPrimaCualquiera(ctx.getEstado(), instanteSimulacion, estadoAvanzado, instanteAlgoritmo);

        estadoFiltrado = filtrarYModificarEstadoDelFuturo
                (estadoAvanzado, instanteAlgoritmo, ctx.getInicioSimulacion(),ctx);


//Testeador.paraUnEdosPrimaCualquieraTEST(estadoAvanzado, estadoFiltrado);

Bitacora.escribir(estadoFiltrado, "EstadoFiltrado");

/*
if(this.contador == 2)
{
    Bitacora.escribir("Estado en llamada %d guardado", this.contador);
    try {

        Bitacora.guardar(estadoFiltrado, "./Estadi" + this.contador + ".ser");    
    } catch (Exception e) {
        Bitacora.escribir(e.toString());
    }
}*/
        entradaAlgoritmo = EntradaProblemaPlanificacion.builder()
                .estadoGlobal(estadoFiltrado)
                .semilla(18112001L)
                .idSimul(ctx.getIdSimulacion())
                .instanteActual(instanteAlgoritmo)
                .build();

        Future<ResultadoAlgoritmoDTO> respuestaAlgoritmo = executor.submit(() -> {
            ResultadoAlgoritmoDTO resultado;

            resultado = planificacionService.realizarPlanificacionConEntrada_v2(entradaAlgoritmo);

Bitacora.escribir(resultado, "Resultado del algoritmo");

            return resultado;
        });

        executor.submit(() -> {
            try{
                ResultadoAlgoritmoDTO resultado;
                EventoAplicarResultadoPlanificacion eventoAplicarResultados;

                resultado = respuestaAlgoritmo.get(Hiperparametros.MAX_MINUTOS_ALGORITMO, TimeUnit.MINUTES);
                ctx.log("🔄 EventoTriggerPlanificacion EventoAplicarResultado para: " + instanteAlgoritmo);
                eventoAplicarResultados = new EventoAplicarResultadoPlanificacion(UUID.randomUUID(), instanteAlgoritmo, resultado);

                ctx.programarEvento(eventoAplicarResultados);
Bitacora.escribir("============ FIN EVENTO TRIGGER PLANIFICACION ============");
            }
            catch (TimeoutException timeoutEx){
                respuestaAlgoritmo.cancel(true);
                StringWriter sw = new StringWriter();
                timeoutEx.printStackTrace(new PrintWriter(sw));
                Bitacora.escribir("ERROR (Tiempo máximo): " +sw.toString());
                throw new RuntimeException("ERROR");
            }
            catch (Exception ex){
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Bitacora.escribir("ERROR (evento planif): " +sw.toString());
                throw new IllegalStateException("ERROR");
            }
            finally{
                executor.shutdown();
            }
        });
    }

    /* Filtra los datos que recibirá el algoritmo, así como modifica lo necesario para que el algoritmo vea los datos
    * correctos. */
    private EstadoGlobal filtrarYModificarEstadoDelFuturo(
            EstadoGlobal estadoAvanzado,
            Instant instanteAlgoritmo,
            Instant inicioSimulacion,
            ContextoSimulacion ctx) {
        List<Programacion> progs = estadoAvanzado.getProgramaciones();
        Map<UUID, Producto> prods = estadoAvanzado.getProductos();
        Map<Long, Almacen> alms = estadoAvanzado.getAlmacenes();
        Map<Long, Vuelo> vlos = estadoAvanzado.getVuelos();
        Map<Long, Pedido> pedidos = estadoAvanzado.getPedidos();

        // filtremos, recuerda que estadoAvanzado es una copia nomás...

        // Filtro de programaciones
        progs = progs.stream()
                .filter(programacion -> programacion.validarIncancelable_I(instanteAlgoritmo)) // en q instante?
                .collect(Collectors.toList()); // mantiene mutable.

        // Filtro de productos y mapeado desde PLANIFICADO EXISTENTE a NO PLANIFICADO EXISTENTE
        // Se filta para tener solo:
        // prods planif existentes (d), incancelables (b) y no planifs (a); no valen productos planif no existentes (c)
        prods = prods.entrySet().stream()
                .filter(id ->
                        id.getValue().validarPlanificadoExistente_D()
                    ||  id.getValue().validarIncancelable_B()
                    || id.getValue().validarNoPlanificado_A()
                        )
                .map(uuidProductoEntry -> {
                    Producto prod = uuidProductoEntry.getValue();
                    if( prod.validarPlanificadoExistente_D() ){
                        // Si es planificado existente D (NO es incancelable)
                        prod.transPlanificadoExistente_D_NoPlanificado_A();
                        // Es decir nos quedamos con puro A, a excepción de los que eran B,
                        // estos últimos permanecen como estaban
                    }
                    return new AbstractMap.SimpleImmutableEntry<>(uuidProductoEntry.getKey(), prod);
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Filtro de vuelos
        vlos = vlos.entrySet().stream().filter(
                longVueloEntry -> {
                    Vuelo vuelo = longVueloEntry.getValue();
                    return  ( !vuelo.isCancelado() )
                        && (
                            // Vuelos circulante que salieron antes y llegarán despúes del instanteAlg
                            ( vuelo.verificarSalida(instanteAlgoritmo) && !vuelo.verificarLlegada(instanteAlgoritmo) )
                            || // Vuelo que aún no parte para el instanteAlg
                            ( !vuelo.verificarSalida(instanteAlgoritmo))
                        ) && ( !vuelo.verificarSalida(inicioSimulacion) );
                    // y que por lo menos haya partido después del inicio de la simu, sirve para primera iteración
                }
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Filtro de pedidos
        pedidos = pedidos.entrySet().stream().filter(
                longPedidoEntry -> {
                    Pedido pedido = longPedidoEntry.getValue();
                    return  // Registrados antes del instanteAlgoritmo
                            !pedido.getInstanteRegistro().isAfter(instanteAlgoritmo)
                            && // pendientes de ENTREGA mayor que 0, para el momento de instanteAlgoritmo
                            pedido.obtenerCantidadProductosFaltantes()>0
                            ;
                }
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

//        ctx.log(" Instante algoritmo: " + instanteAlgoritmo +" / Instante ini simulacion: " + inicioSimulacion);
//        ctx.log("VUELOS FILTRADOS==========================================:" +
//                " \n" + PrettyPrinter.printList( vlos.values().stream().collect(Collectors.toList())));

        // Como no se puede mutar así de fácil las colecciones, mejor devuelvo un new Estado
        EstadoGlobal porDevolver = new EstadoGlobal(alms, vlos, pedidos, progs, prods);
        estadoAvanzado = null; // limpio memoria, no me fío de nada ni nadie :v
        return porDevolver;
    }



    @Override
    public int getPriority()
    {
        return 4; // después de cualquier llegada de avión.
    }
}
