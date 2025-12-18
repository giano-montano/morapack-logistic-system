package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.*;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaDescargaPedidosDiario;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.carga_datos.EventoCargaDescargaVuelosDiario;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.pedidos.EventoEntregaPedidoTras2h;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoAplicarResultadoPlanificacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoTriggerPlanificacion;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.planificacion.EventoTriggerPlanificacionPeriodica;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoVueloLlegada;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.vuelos.EventoVueloSalida;
import pe.edu.pucp.inf.pddsbackend.websocket.dto.RutaPorPedidoDTO;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class ContextoSimulacion
{

    private Long idSimulacion; // ✅ ID real de la simulación para WebSocket
    private Instant ahora;
    private Instant inicioSimulacion;
    private EstadoGlobal estado;

    private SimulacionRequestDTO params;
    private RealizarPlanificacionDTO formaRealizarPlanificacion;

    @Builder.Default
    private LinkedList<SalidaProblemaPlanificacion> solucionesAcumuladas = new LinkedList<>();
    private Clock reloj;

    @Builder.Default
    @Setter
    private LoggingReport report = new LoggingReport();

    private transient SchedulerSimulacion scheduler; // transient: no persistir

    private boolean colapsado = false;
    private boolean conError = false;
    private String errorMsj = "";
    // nuevos:
    @Builder.Default
    private final Map<String, Double> metricas = new HashMap<>();

    @Builder.Default
    private int contadorPlanificaciones = 0;

    @Builder.Default
    private Instant ultimaPlanificacion = Instant.MIN; // dentro de la simu, no vida real

    // ✅ Flag para desactivar/activar la planificación sin detener la simulación
    private volatile boolean planificacionDesactivada = false;

    private static ContextoSimulacion unicaInstanciaSimulacion = null;
    // singleton, para que todos puedan acceder xd
    private static ContextoSimulacion inicializarContexto(
            Clock relojAEmplear,
            EstadoGlobal estadoInicial,
            RealizarPlanificacionDTO dataBasePlanificacion,
            LoggingReport loggingReport,
            SimulacionRequestDTO params)
    {
        // ✅ CRÍTICO: Extraer el ID de simulación del DTO
        Long idSimulacion = dataBasePlanificacion != null
                ? dataBasePlanificacion.getIdSimulacion()
                : null;
        System.out.println("🔧 Inicializando ContextoSimulacion con ID: " + idSimulacion);

        return ContextoSimulacion.builder()
                .idSimulacion(idSimulacion) // ✅ Configurar ID para WebSocket
                .reloj(relojAEmplear)
                .ahora(relojAEmplear.instant())
                .inicioSimulacion(params.fechaHoraInicioSimulacion() != null
                        ? params.fechaHoraInicioSimulacion()
                        : Instant.now())
                .estado(estadoInicial)
                .params(params)
                .formaRealizarPlanificacion(dataBasePlanificacion)
                .report(loggingReport) // es una orquestación algo horrible y repetitiva, pero todo
                                       // por la carpeta.
                .build();
    }

    public static ContextoSimulacion obtenerOCrearUnicaInstancia(
            Clock relojAEmplear,
            EstadoGlobal estadoInicial,
            RealizarPlanificacionDTO dataBasePlanificacion,
            LoggingReport loggingReport,
            SimulacionRequestDTO params)
    {
        if (unicaInstanciaSimulacion == null)
        {
            unicaInstanciaSimulacion = inicializarContexto(
                    relojAEmplear,
                    estadoInicial,
                    dataBasePlanificacion,
                    loggingReport,
                    params);
            return unicaInstanciaSimulacion;
        }
        else
        {
            return unicaInstanciaSimulacion;
        }
    }

    /**
     * Resetea la instancia singleton del contexto de simulacion. DEBE ser llamado
     * al finalizar cada simulacion para evitar que se reutilice el mismo contexto
     * en simulaciones consecutivas.
     */
    public static void resetInstancia()
    {
        if (unicaInstanciaSimulacion != null)
        {
            System.out.println("🧹 Limpiando instancia singleton de ContextoSimulacion");
            unicaInstanciaSimulacion = null;
        }
    }

    public static ContextoSimulacion obtenerUnicaInstanciaSiExiste()
    {
        if (unicaInstanciaSimulacion == null)
        {
            return null;
        }
        else
        {
            return unicaInstanciaSimulacion;
        }
    }

    // constructores, getters, helpers...
    public void establecerElAhora(Instant ahora)
    {

        this.ahora = ahora;
    }

    public Instant obtenerElAhora()
    {
        return ahora;
    }

    public void programarEvento(EventoSimulacion e)
    {
        if (scheduler == null)
            throw new IllegalStateException("Scheduler no inicializado");
        scheduler.programar(e);
    }

    public void log(String mensaje)
    {
        String antesala = DateTimeFormatter.ISO_INSTANT.format(obtenerElAhora()); // !!! o solo
                                                                                  // ahora?
        String line = " Contexto: [" + antesala + "] " + mensaje;
        report.appendReport(line);
    }

    public synchronized void imprimirReporteLog() throws Exception
    {
        // report.appendReport("métricas: " + metricas );
        report.writeReportFile("Simul. " + params.tipoSimulacion() + " "
                + formaRealizarPlanificacion.getIdSimulacion() + " - ");
    }

    public synchronized void imprimirReporteLogError() throws Exception
    {
        // report.appendReport("métricas: " + metricas );
        report.writeReportFile("Simul. error " + params.tipoSimulacion() + " "
                + formaRealizarPlanificacion.getIdSimulacion() + " - ");
    }

    public void registrarMetrica(String nombre, double valor)
    {
        metricas.put(nombre, valor);
        log(String.format("Métrica %s: %.2f", nombre, valor));
    }

    public boolean shouldCheckpointNow()
    {
        return contadorPlanificaciones % 10 == 0; // Cada 10 planificaciones
    }

    /**
     * Actualiza el campo 'ahora' del contexto usando el clock actual (si existe) y
     * devuelve el Instant actualizado. Sincroniza contexto con el reloj engañado
     * que representa el reloj ficticio dentro de la simulación
     */
    public Instant actualizarAhoraDesdeReloj()
    {
        if (this.reloj == null)
            return this.ahora;
        Instant simNow = this.reloj.instant();
        this.establecerElAhora(simNow);
        return simNow;
    }

    public List<Pedido> obtenerPedidosDeRutasDeVueloFinal(Vuelo v)
    {
        List<Programacion> rutasDondeElVueloEsFinal = getSolucionesAcumuladas().getLast()
                .getProgramaciones()
                // SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL
                // CUAL
                .stream().filter(programacion -> {
                    Ruta vuelosEnOrden = programacion.getRuta();
                    if (vuelosEnOrden.obtenerUltimoVuelo().getId() == v.getId())
                        return true;
                    return false;
                }).toList();
        List<Pedido> pedidosDelVueloAtendiendoFinal = rutasDondeElVueloEsFinal.stream()
                .map((r) -> estado.getPedidos().get(r.getPedido().getId()))
                .toList();
        return pedidosDelVueloAtendiendoFinal;
    }

    public List<Programacion> obtenerMinipedidosDeRutasDeVueloFinal(Vuelo v)
    {
        List<Programacion> rutasDondeElVueloEsFinal = getSolucionesAcumuladas().getLast()
                .getProgramaciones()
                // SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL
                // CUAL
                .stream().filter(Programacion -> {
                    Ruta vuelosEnOrden = Programacion.getRuta();
                    if (vuelosEnOrden.obtenerUltimoVuelo().getId() == v.getId())
                        return true;
                    return false;
                }).toList();
        return rutasDondeElVueloEsFinal;
    }

    public String imprimirMinipedidosDeRutasDeVueloFinal(Vuelo v)
    {
        List<Programacion> rutasDondeElVueloEsFinal = obtenerMinipedidosDeRutasDeVueloFinal(v);
        StringBuilder sb = new StringBuilder();
        rutasDondeElVueloEsFinal.stream().forEach((r) -> {
            sb.append("PedidoEntidad: " + estado.getPedidos().get(r.getPedido().getId()) + "\n"
                    + " Cantidad:" + "1" + "\n"); // debe hablar del producto
        });
        return sb.toString();
    }

    public List<Programacion> obtenerProgramacionesEnVueloIdParaCargarVuelo(long idVuelo){
        // Verificar que haya soluciones disponibles
        if (solucionesAcumuladas.isEmpty()){
            // log("obtenerProgramacionesEnVueloIdParaCargarVuelo: No hay soluciones acumuladas
            // aún para vuelo " + idVuelo); // <- antes no salía porque se planificaba vacío
            // al inicio
            return List.of(); // Retornar lista vacía si no hay soluciones
        }

        SalidaProblemaPlanificacion ultimaSolucion = solucionesAcumuladas.getLast();
        // Procesar rutas activas que usan este vuelo
        List<Programacion> programacionesActivasConVuelo =/* ultimaSolucion.*/ estado.getProgramaciones()
                .stream()
                .filter(r ->  r.getRuta().getVuelos().stream().map(Vuelo::getId).collect(Collectors.toSet())
                        .contains(idVuelo))
                .toList();

        return programacionesActivasConVuelo;
    }

    /**
     * Construye la estructura de rutas agrupadas por pedido desde la última planificación exitosa
     * @return Lista de RutaPorPedidoDTO o lista vacía si no hay planificaciones
     */
    public List<RutaPorPedidoDTO> construirRutasPorPedidoUltimaPlanificacion()
    {
        // Verificar que haya soluciones acumuladas
        if (solucionesAcumuladas == null || solucionesAcumuladas.isEmpty())
        {
            log("construirRutasPorPedidoUltimaPlanificacion: No hay planificaciones disponibles");
            return Collections.emptyList();
        }

        SalidaProblemaPlanificacion ultimaSolucion = solucionesAcumuladas.getLast();
        
        // Agrupar programaciones por pedido
        Map<Long, List<Programacion>> programacionesPorPedido = ultimaSolucion.getProgramaciones()
                .stream()
                .collect(Collectors.groupingBy(programacion -> programacion.getPedido().getId()));

        List<RutaPorPedidoDTO> rutasPorPedido = new ArrayList<>();

        // Para cada pedido, agrupar sus rutas
        for (Map.Entry<Long, List<Programacion>> entry : programacionesPorPedido.entrySet())
        {
            Long idPedido = entry.getKey();
            List<Programacion> programacionesPedido = entry.getValue();

            // Obtener el pedido del estado
            Pedido pedido = estado.getPedidos().get(idPedido);
            if (pedido == null)
            {
                log("⚠️ Pedido " + idPedido + " no encontrado en estado");
                continue;
            }

            // Agrupar por ruta (secuencia de vuelos)
            Map<LinkedList<Long>, List<Programacion>> programacionesPorRuta = 
                programacionesPedido.stream()
                    .collect(Collectors.groupingBy(programacion ->
                            new LinkedList<>(
                            programacion.getRuta().getVuelos()
                            .stream().map(Vuelo::getId).toList() )));

            // Para cada ruta del pedido, crear un DTO
            for (Map.Entry<LinkedList<Long>, List<Programacion>> rutaEntry : programacionesPorRuta.entrySet())
            {
                LinkedList<Long> idsVueloRuta = rutaEntry.getKey();
                List<Programacion> programacionesRuta = rutaEntry.getValue();

                // Obtener información de los vuelos
                List<Vuelo> vuelosRuta = idsVueloRuta.stream()
                        .map(idVuelo -> estado.getVuelos().get(idVuelo))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (vuelosRuta.isEmpty())
                {
                    continue;
                }

                // Vuelo final para obtener destino y fecha de llegada
                Vuelo vueloFinal = vuelosRuta.get(vuelosRuta.size() - 1);
                Almacen almacenDestino = estado.getAlmacenes().get(vueloFinal.getAlmacenDestino());

                // Construir listas de nombres de ciudades y códigos de vuelos
                List<String> nombresCiudades = new ArrayList<>();
                List<String> codigosVuelos = new ArrayList<>();

                for (int i = 0; i < vuelosRuta.size(); i++)
                {
                    Vuelo vuelo = vuelosRuta.get(i);
                    
                    // Añadir origen solo en el primer vuelo
                    if (i == 0)
                    {
                        Almacen almacenOrigen = estado.getAlmacenes().get(vuelo.getAlmacenSalida().getId());
                        if (almacenOrigen != null)
                        {
                            nombresCiudades.add(almacenOrigen.getNombreCiudad());
                        }
                    }
                    
                    // Añadir destino de cada vuelo
                    Almacen almacenDestinoVuelo = estado.getAlmacenes().get(vuelo.getAlmacenDestino());
                    if (almacenDestinoVuelo != null)
                    {
                        nombresCiudades.add(almacenDestinoVuelo.getNombreCiudad());
                    }
                    
                    // Añadir código del vuelo
                    codigosVuelos.add(vuelo.getCodigo() != null ? vuelo.getCodigo() : "V-" + vuelo.getId());
                }

                // Crear el DTO
                RutaPorPedidoDTO rutaDTO = new RutaPorPedidoDTO(
                        idPedido,
                        pedido.getCantidadProductos(),
                        pedido.obtenerCantidadProductosEntregados(), // satisfechos = entregados
                        (int) programacionesRuta.size(), // cantidad programada en esta ruta
                        almacenDestino != null ? almacenDestino.getNombreCiudad() : "Desconocido",
                        vueloFinal.getInstanteLlegada(),
                        vuelosRuta.size(),
                        nombresCiudades,
                        codigosVuelos,
                        idsVueloRuta
                );

                rutasPorPedido.add(rutaDTO);
            }
        }

        log("construirRutasPorPedidoUltimaPlanificacion: " + rutasPorPedido.size() + " rutas construidas");
        return rutasPorPedido;
    }

    // constructor deep copy copia
    public ContextoSimulacion(ContextoSimulacion value) {
        this.estado = new EstadoGlobal(value.getEstado()); // importante, deep copy
        this.params = value.getParams();
        this.reloj = value.getReloj();
        this.report = new LoggingReport();
        this.report.setDirectory("Fantasmón"); // xdd

//        this.scheduler = value.getScheduler();
        this.formaRealizarPlanificacion = value.getFormaRealizarPlanificacion();
        this.ahora = value.getAhora();
        this.solucionesAcumuladas = new LinkedList<>(value.getSolucionesAcumuladas());
        this.colapsado = value.colapsado;
        this.conError = value.conError;
        this.contadorPlanificaciones = value.contadorPlanificaciones;
        this.errorMsj = value.errorMsj;
        this.idSimulacion = value.getIdSimulacion();
        this.inicioSimulacion = value.getInicioSimulacion();
        this.metricas = value.metricas;
        this.planificacionDesactivada = value.planificacionDesactivada;
        this.ultimaPlanificacion = value.ultimaPlanificacion;
    }

    /* Antes de hacer una planificación, simular todos los eventos que pasarán hasta un instante futuro
    * para tener el estado global preciso en ese momento
    * */
    public synchronized EstadoGlobal simularUnNuevoFuturo(Instant instanteFuturo) throws Exception {
        MotorSimulacion motor = ( MotorSimulacion ) this.getScheduler();

        MotorSimulacion nuevoMotor = MotorSimulacion.crearCopiaMotor(motor);
        ContextoSimulacion contextoCopia = nuevoMotor.getCtx();
        // Debemos obtener los eventos que se ejecutarán desde ahora (ctx) hasta el instanteFuturo
        // Como el estado global del contexto ha sido deepcopyado, normal podemos aplicar cada evento encima.
        PriorityQueue<EventoSimulacion> eventitos = motor.getEventosSimulacionNuevaQueue();
        nuevoMotor.setColaDeEventos(eventitos); // recordando que el nuevoMotor sí está asociado al contextoCopia, ¿no es así?
        // ^^ Esto va a funcionar, pero lo más limpio sería usar el método programar del propio motor.

        // Removemos los eventos programados para antes de ahorita y para después del evento futuro
        eventitos.removeIf(eventoSimulacion ->
                !eventoSimulacion.obtenerInstanteProgramado().isAfter(ahora)
                // elimina también el "EventoTriggerPlanifacipon" que llamó a esa función en primer lugar
                ||
                        eventoSimulacion.obtenerInstanteProgramado().isAfter(instanteFuturo)
                ||
                        eventoSimulacion instanceof EventoTriggerPlanificacion
                ||
                        eventoSimulacion instanceof EventoAplicarResultadoPlanificacion
                ||
                        eventoSimulacion instanceof EventoTriggerPlanificacionPeriodica
                ||
                        eventoSimulacion instanceof EventoCargaDescargaPedidosDiario
                ||
                        eventoSimulacion instanceof EventoCargaDescargaVuelosDiario
                );

        // Limpiamos el websocket de cada eventito (tal vez web socket debió ser estático en la clase abstracta padre)
        // 😿😿😿
        limpiarWebSocketsEventos(eventitos);
        
        System.out.println("INICIO SIMULACIÓN DE LA SIMULACIÓN ======================================================");
        Bitacora.workaround = true;
        PrintStream orig = quitarLoggeoConsola();
        // eventitos ahora tiene solo los eventos después del ahora y anteriores o iguales al instante futuro.
        // se incluye el propio instante futuro ya que podrían llegar vuelos en ese mismo momento y el
        // aplicar tiene prioridad 3, por lo que se ejecutaría después de estos.
        while(!eventitos.isEmpty()){
            EventoSimulacion eventoActual = nuevoMotor.peekNextEvent(); // eventitos.peek
            if(eventoActual==null) {
                this.log("Evento nulo, terminando ciclo");
                break;
            }
//            log("Encontrado el evento simu simulacion: " + eventoActual);
            Instant instanteEvento = eventoActual.obtenerInstanteProgramado();
            if (instanteEvento.isAfter(instanteFuturo)) break; //   CONSIDERAR EL EFECTO
            //^^ le pongo que considere como válido si instanteEvento == instanteFuturo para que también lo procese
            // e incluya su efecto en el estado avanzado E'
            contextoCopia.establecerElAhora(instanteEvento);

            eventoActual.procesar(contextoCopia);
            nuevoMotor.pollNextEvent(); // eventitos.poll
        }

        devolverLoggeoConsola(orig);
        Bitacora.workaround = false;
        System.out.println("FIN SIMULACIÓN DE LA SIMULACIÓN ======================================================");

        // Se supone que ya corrimos todos los eventos al estado global (deep copyado) del contexto copia
        this.log("El estado global simulado de la simulación: " + contextoCopia.getEstado());

        return contextoCopia.getEstado();
    }

    // VIVA LA DEUDA TÉCNICAAAAAAAAAAAA
    private void limpiarWebSocketsEventos(PriorityQueue<EventoSimulacion> eventitos) {
        for(EventoSimulacion eventoSimulacion : eventitos){
            EventoSimulacion eventoConcreto = eventoSimulacion;
            eventoConcreto.setWebSocketService(null);
//            if(eventoSimulacion instanceof EventoTriggerPlanificacion){
//                eventoConcreto = (EventoTriggerPlanificacion) eventoSimulacion;
//                eventoConcreto.
//            }else if(eventoSimulacion instanceof EventoAplicarResultadoPlanificacion){
//                eventoConcreto = (EventoAplicarResultadoPlanificacion) eventoSimulacion;
//            } else if(eventoSimulacion instanceof EventoVueloSalida){
//                eventoConcreto = (EventoVueloSalida) eventoSimulacion;
//            }else if(eventoSimulacion instanceof EventoVueloLlegada){
//                eventoConcreto = (EventoVueloLlegada) eventoSimulacion;
//            }else if(eventoSimulacion instanceof  EventoCargaDescargaPedidosDiario){
//                eventoConcreto =  (EventoCargaDescargaPedidosDiario) eventoSimulacion;
//            }else if(eventoSimulacion instanceof EventoCargaDescargaVuelosDiario){
//                eventoConcreto =  (EventoCargaDescargaVuelosDiario) eventoSimulacion;
//            }else if(eventoSimulacion instanceof EventoTriggerPlanificacionPeriodica){
//                eventoConcreto = (EventoTriggerPlanificacionPeriodica) eventoSimulacion;
//            }else if(eventoSimulacion instanceof EventoEntregaPedidoTras2h){
//                eventoConcreto = (EventoEntregaPedidoTras2h) eventoSimulacion;
//            }
        }
    }

    private void devolverLoggeoConsola(PrintStream orig) {
        System.setOut(orig);
    }

    private PrintStream quitarLoggeoConsola() {
        // 1. Save the original System.out
        PrintStream originalOut = System.out;

        // 2. Create a "dummy" PrintStream that does nothing
        PrintStream dummyStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // NO-OP (no operation): data is swallowed and ignored
            }
        });

        // 3. Redirect System.out to the dummy stream
        System.setOut(dummyStream);
        return originalOut;
    }

    // public void anadirPedidoPendiente(Pedido p) {
    // pedidosPendientes.put(p.getId(), p); // No necesario porque ya podemos
    // acceder al estado global
    // }
}
// public boolean debeDesencadenarPlanificacionAhora() {
// // Política configurable
// long pedidosPendientes = estadoGlobal.getPedidos().values().stream()
// .filter(p -> p.isActivo() &&
// p.getCantidadProductosProgramados() < p.getCantidadProductosPedidos())
// .count();
//
// Duration tiempoDesdeUltima = Duration.between(ultimaPlanificacion, ahora);
//
// return pedidosPendientes >= params.getUmbralPedidosPlanificacion() ||
// tiempoDesdeUltima.toMinutes() >= params.getIntervaloPlanificacionMinutos();
// }
