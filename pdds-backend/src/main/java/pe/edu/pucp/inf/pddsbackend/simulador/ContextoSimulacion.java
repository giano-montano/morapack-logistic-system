package pe.edu.pucp.inf.pddsbackend.simulador;

import lombok.*;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.SalidaProblemaPlanificacion;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.RealizarPlanificacionDTO;
import pe.edu.pucp.inf.pddsbackend.dto.planificaciones.SimulacionRequestDTO;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.LoggingReport;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Pedido;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Programacion;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;
import pe.edu.pucp.inf.pddsbackend.simulador.eventos.EventoSimulacion;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Data
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public  class ContextoSimulacion {

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
    private final LoggingReport report = new LoggingReport();

    private transient SchedulerSimulacion scheduler; // transient: no persistir

    private boolean colapsado = false;
    private boolean conError = false;
    private String errorMsj = "";
    //nuevos:
    @Builder.Default
    private final Map<String, Double> metricas = new HashMap<>();

    @Builder.Default
    private int contadorPlanificaciones = 0;

    @Builder.Default
    private Instant ultimaPlanificacion = Instant.MIN; // dentro de la simu, no vida real

    private static ContextoSimulacion unicaInstanciaSimulacion = null;
    // singleton, para que todos puedan acceder xd
    private static ContextoSimulacion inicializarContexto(
            Clock relojAEmplear,
            EstadoGlobal estadoInicial,
            RealizarPlanificacionDTO dataBasePlanificacion,
            LoggingReport loggingReport,
            SimulacionRequestDTO params
    ){
        // ✅ CRÍTICO: Extraer el ID de simulación del DTO
        Long idSimulacion = dataBasePlanificacion != null ? dataBasePlanificacion.getIdSimulacion() : null;
        System.out.println("🔧 Inicializando ContextoSimulacion con ID: " + idSimulacion);
        
        return ContextoSimulacion.builder()
                .idSimulacion(idSimulacion) // ✅ Configurar ID para WebSocket
                .reloj(relojAEmplear)
                .ahora( relojAEmplear.instant() )
                .inicioSimulacion(params.fechaHoraInicioSimulacion()!=null?params.fechaHoraInicioSimulacion(): Instant.now())
                .estado(estadoInicial)
                .params(params)
                .formaRealizarPlanificacion(dataBasePlanificacion)
                .report(loggingReport) // es una orquestación algo horrible y repetitiva, pero todo por la carpeta.
                .build();
    }

    public static ContextoSimulacion obtenerOCrearUnicaInstancia(
            Clock relojAEmplear,
            EstadoGlobal estadoInicial,
            RealizarPlanificacionDTO dataBasePlanificacion,
            LoggingReport loggingReport,
            SimulacionRequestDTO params
    ){
        if( unicaInstanciaSimulacion == null){
            unicaInstanciaSimulacion = inicializarContexto(
                    relojAEmplear,
                    estadoInicial,
                    dataBasePlanificacion,
                    loggingReport,
                    params);
            return unicaInstanciaSimulacion;
        }else{
            return unicaInstanciaSimulacion;
        }
    }

    /**
     * Resetea la instancia singleton del contexto de simulacion.
     * DEBE ser llamado al finalizar cada simulacion para evitar que se reutilice
     * el mismo contexto en simulaciones consecutivas.
     */
    public static void resetInstancia() {
        if(unicaInstanciaSimulacion != null) {
            System.out.println("🧹 Limpiando instancia singleton de ContextoSimulacion");
            unicaInstanciaSimulacion = null;
        }
    }

    public static ContextoSimulacion obtenerUnicaInstanciaSiExiste(
    ){
        if( unicaInstanciaSimulacion == null){
            return null;
        }else{
            return unicaInstanciaSimulacion;
        }
    }

    // constructores, getters, helpers...
    public void establecerElAhora(Instant ahora) {

        this.ahora = ahora;
    }

    public Instant obtenerElAhora() {
        return ahora;
    }

    public void programarEvento(EventoSimulacion e) {
        if (scheduler == null) throw new IllegalStateException("Scheduler no inicializado");
        scheduler.programar(e);
    }

    public void log(String mensaje) {
        String antesala = DateTimeFormatter.ISO_INSTANT.format(obtenerElAhora()); //!!! o solo ahora?
        String line = " Contexto: [" + antesala + "] " + mensaje;
        report.appendReport(line);
    }

    public synchronized void imprimirReporteLog() throws Exception {
//        report.appendReport("métricas: " + metricas );
        report.writeReportFile("Reporte de simulación " + params.tipoSimulacion() + " " + formaRealizarPlanificacion.getIdSimulacion() + " - ");
    }

    public void registrarMetrica(String nombre, double valor) {
        metricas.put(nombre, valor);
        log(String.format("Métrica %s: %.2f", nombre, valor));
    }

    public boolean shouldCheckpointNow() {
        return contadorPlanificaciones % 10 == 0; // Cada 10 planificaciones
    }

    /**
     * Actualiza el campo 'ahora' del contexto usando el clock actual (si existe)
     * y devuelve el Instant actualizado. Sincroniza contexto con el reloj engañado
     * que representa el reloj ficticio dentro de la simulación
     */
    public Instant actualizarAhoraDesdeReloj() {
        if (this.reloj == null) return this.ahora;
        Instant simNow = this.reloj.instant();
        this.establecerElAhora(simNow);
        return simNow;
    }
    public List<Pedido> obtenerPedidosDeRutasDeVueloFinal(Vuelo v) {
        List<Programacion> rutasDondeElVueloEsFinal = getSolucionesAcumuladas().getLast().getProgramaciones()
                //SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL CUAL
                .stream().filter(programacion -> {
                    LinkedList<Long> vuelosEnOrden = programacion.getIdsVueloRuta();
                    if (vuelosEnOrden.getLast() == v.getId()) return true;
                    return false;
                }).toList();
        List<Pedido> pedidosDelVueloAtendiendoFinal = rutasDondeElVueloEsFinal.stream()
                .map((r) -> estado.getPedidos().get(r.getIdPedido()))
                .toList();
        return pedidosDelVueloAtendiendoFinal;
    }

    public List<Programacion>obtenerMinipedidosDeRutasDeVueloFinal(Vuelo v) {
        List<Programacion> rutasDondeElVueloEsFinal = getSolucionesAcumuladas().getLast().getProgramaciones()
                //SE SUPONE QUE NO METEMOS SOLUCIONES VACÍAS NI INÚTILES, SOLO SOLUCIONES TAL CUAL
                .stream().filter(Programacion -> {
                    LinkedList<Long> vuelosEnOrden = Programacion.getIdsVueloRuta();
                    if (vuelosEnOrden.getLast() == v.getId()) return true;
                    return false;
                }).toList();
        return rutasDondeElVueloEsFinal;
    }

    public String imprimirMinipedidosDeRutasDeVueloFinal(Vuelo v) {
        List<Programacion> rutasDondeElVueloEsFinal = obtenerMinipedidosDeRutasDeVueloFinal(v);
        StringBuilder sb = new StringBuilder();
        rutasDondeElVueloEsFinal.stream().forEach((r) -> {
            sb.append("PedidoEntidad: "+ estado.getPedidos().get(r.getIdPedido())
                    + " Cantidad:"+"1"+"\n"); // debe hablar del producto
        });
        return sb.toString();
    }

    public List<Producto>obtenerProductosEnVueloId(long  idVuelo) {
        // Verificar que haya soluciones disponibles
        if (solucionesAcumuladas.isEmpty()) {
//            log("obtenerProductosEnVueloId: No hay soluciones acumuladas aún para vuelo " + idVuelo); // <- antes no salía porque se planificaba vacío al inicio
            return List.of(); // Retornar lista vacía si no hay soluciones
        }
        
        SalidaProblemaPlanificacion ultimaSolucion = solucionesAcumuladas.getLast();
        // Procesar rutas activas que usan este vuelo
        List<Programacion> programacionesActivasConVuelo =
                ultimaSolucion.getProgramaciones().stream()
                        .filter(r -> r.isActivo() && r.getIdsVueloRuta().contains(idVuelo))
                        .toList();
        if(programacionesActivasConVuelo.size() > 0)
            log("EventoVueloSalida: Rutas con este vuelo "+ idVuelo
                    +" a procesar ("+ programacionesActivasConVuelo.size()+"): " + programacionesActivasConVuelo);


        int capacidadTotalACargar = 0;
        List<Producto> productosACargar = new ArrayList<>(); // o linked?
        for (Programacion programacion : programacionesActivasConVuelo) {
            // Solo cargar si es el primer vuelo de la ruta <- pq?
            if (programacion.getIdsVueloRuta().getFirst().equals(idVuelo)) {
                Producto productoACargar = estado.obtenerProductoPorUuid(programacion.getUuidProducto());
                productosACargar.add(productoACargar);
                capacidadTotalACargar += 1;
            }else{
                Producto productoACargar = estado.obtenerProductoPorUuid(programacion.getUuidProducto());
                productosACargar.add(productoACargar);
                capacidadTotalACargar += 1;
            }
        }
        return productosACargar;
    }

    //    public void anadirPedidoPendiente(Pedido p) {
//        pedidosPendientes.put(p.getId(), p); // No necesario porque ya podemos acceder al estado global
//    }
}
//    public boolean debeDesencadenarPlanificacionAhora() {
//        // Política configurable
//        long pedidosPendientes = estadoGlobal.getPedidos().values().stream()
//                .filter(p -> p.isActivo() &&
//                        p.getCantidadProductosProgramados() < p.getCantidadProductosPedidos())
//                .count();
//
//        Duration tiempoDesdeUltima = Duration.between(ultimaPlanificacion, ahora);
//
//        return pedidosPendientes >= params.getUmbralPedidosPlanificacion() ||
//                tiempoDesdeUltima.toMinutes() >= params.getIntervaloPlanificacionMinutos();
//    }

