package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;

import java.time.Instant;
import java.util.*;

@Getter
public class Vuelo
{
    long id;
    long idAlmacenOrigen;
    long idAlmacenDestino;

    String codigo;

    Instant inicio;
    Instant fin;

    int capacidadMaxima;
    int capacidadOcupada;
    int capacidadSinOcupar;
    int capacidadReservada;
    int capacidadDisponibleParaReserva;

    @Setter
    boolean esIntercontinental;

    @Setter
    boolean cancelado = false;

    private List<UUID> idsProductosContenidos = new LinkedList<>(); // se volvió fuente de la verdad
    private List<UUID> idsProductosProgramados = new LinkedList<>(); // Nuevo: Gestionado solo dentro de algoritmo
    // Esto al algoritmo debe llegar vacío, pero en el contexto de la simulación estar solo para ofrecer la información al cliente

    public static int correlativo = 1;

    public Vuelo(/* long id, */
            long idAlmacenOrigen,
            long idAlmacenDestino,
            String codigo,
            Instant inicio,
            Instant fin,
            int capacidadMaxima,
            int capacidadOcupada,
            boolean esIntercontinental,
            boolean cancelado)
    {
        this.id = correlativo;
        correlativo++;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.codigo = codigo;
        this.inicio = inicio;
        this.fin = fin;
        this.capacidadMaxima = Math.max(0, capacidadMaxima);
        this.capacidadOcupada = Math.max(0, Math.min(this.capacidadMaxima, capacidadOcupada)); // xd
        this.capacidadReservada = 0;
        this.recalcularDerivados();
        this.idsProductosContenidos = new ArrayList<>();
        this.idsProductosProgramados = new ArrayList<>();
        this.esIntercontinental = esIntercontinental;
        this.cancelado = cancelado;
    }

    public Vuelo(long id,
            long idAlmacenOrigen,
            long idAlmacenDestino,
            String codigo,
            Instant inicio,
            Instant fin,
            int capacidadMaxima,
            int capacidadOcupada,
            boolean esIntercontinental,
            boolean cancelado)
    {
        this.id = id;
        this.idAlmacenOrigen = idAlmacenOrigen;
        this.idAlmacenDestino = idAlmacenDestino;
        this.codigo = codigo;
        this.inicio = inicio;
        this.fin = fin;
        this.capacidadMaxima = Math.max(0, capacidadMaxima);
        this.capacidadOcupada = Math.max(0, Math.min(capacidadMaxima, capacidadOcupada)); // xd
        this.capacidadReservada = 0;
        this.recalcularDerivados();
        this.idsProductosContenidos = new ArrayList<>();
        this.idsProductosProgramados = new ArrayList<>();
        this.esIntercontinental = esIntercontinental;
        this.cancelado = cancelado;
    }

    public Vuelo(Vuelo other)
    {
        this.id = other.id;
        this.inicio = other.inicio;
        this.fin = other.fin;
        this.idAlmacenOrigen = other.idAlmacenOrigen;
        this.idAlmacenDestino = other.idAlmacenDestino;
        this.codigo = other.codigo;
        this.capacidadMaxima = other.capacidadMaxima;
        this.capacidadOcupada = other.capacidadOcupada;
        this.capacidadSinOcupar = other.capacidadSinOcupar;
        this.capacidadReservada = other.capacidadReservada;
        this.capacidadDisponibleParaReserva = other.capacidadDisponibleParaReserva;
        this.idsProductosContenidos = new ArrayList<>(other.idsProductosContenidos);
        this.idsProductosProgramados = new ArrayList<>(other.idsProductosProgramados);
        this.esIntercontinental = other.esIntercontinental;
        this.cancelado = other.cancelado;
    }

    public static Vuelo desdeEntidad(VueloEntidad v)
    {
        return new Vuelo(
                v.getId(),
                v.getAlmacenOrigen().getId(),
                v.getAlmacenDestino().getId(),
                v.getCodigo4Letras(),
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),
                v.getEsIntercontinental(),
                v.getCancelado());
    }

    public static Vuelo obtenerVueloSimuladoConProductos(
            Vuelo value,
            Instant instante,
            @NotNull List<Programacion> programaciones,
            @NotNull HashMap<UUID, Producto> productos
    ) {
        Vuelo vueloSimulado = new Vuelo(value);
        if(!instante.isBefore(value.getInicio())
            && !instante.isAfter(value.getFin())
        ){ // si lo simulado ya es dsp del despegue y antes de que llegue el vuelo
            List<Programacion> prograsConVuelo =
                    programaciones.stream().filter(
                            programacion -> programacion.getIdsVueloRuta().contains(value.getId())
                    ).toList();
            for(Programacion prog: prograsConVuelo){
                Producto prod = productos.get(prog.getUuidProducto());
                if (!vueloSimulado.ocuparConProducto(prod)){
                    throw new IllegalStateException("Estado incosistente de vuelo, no le entra un prod programado");
                }
            }
        }else{
            // fuera de de esos casos creo que no interesa, simplemente está vacío.
        }
        return vueloSimulado;
    }

    /** Recalcula campos derivados según ocupados/reservados. */
    private void recalcularDerivados()
    {
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
        capacidadDisponibleParaReserva = Math.max(0,
                capacidadMaxima - capacidadOcupada - capacidadReservada); // <- JAAAAAAAAAAAAAAAA
    }

    public boolean yaPartio(Instant ahora)
    {
        return inicio.isBefore(ahora != null ? ahora : Instant.now());
    }

    public boolean yaPartioEnVidaReal()
    {
        return inicio.isBefore(Instant.now());
    }

    public int getCapacidadSinOcupar()
    {
        return capacidadSinOcupar;
    }

    /**
     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas).
     *
     * @return true si se pudo ocupar; false si no hay suficiente capacidad sin
     *         ocupar.
     */
    public synchronized boolean ocuparConProducto(Producto producto){
        if (capacidadSinOcupar >= 1){ // un solo productito
            capacidadOcupada += 1;
            recalcularDerivados();
            idsProductosContenidos.add(producto.getUuid());
            return true;
        }
        return false;
    }

    public boolean reservarCapacidad(UUID uuidProducto/* int cantidad */)
    {
        if (capacidadDisponibleParaReserva >= 1){
            capacidadReservada += 1;
            recalcularDerivados();
            idsProductosProgramados.add(uuidProducto);
            return true;
        }
        return false;
    }

    /**
     * Libera (desocupa) 'cantidad' unidades que estaban ocupadas.
     *
     * @return true si había suficiente ocupado y se desocupó; false en otro caso.
     */
    public synchronized boolean desocuparConProducto(Producto producto)
    {
        if (capacidadOcupada >= 1)
        {
            capacidadOcupada -= 1;
            recalcularDerivados();
            idsProductosContenidos.remove(producto.getUuid());
            return true;
        }
        return false;
    }

    public boolean agregarVarios(List<Producto> productos){
        for (Producto producto : productos){
            if (!ocuparConProducto(producto))
                return false;
        }
        return true;
    }

    public boolean quitarVarios(List<Producto> productos)
    {
        for (Producto producto : productos)
        {
            if (!desocuparConProducto(producto))
                return false;
        }
        return true;
    }

    public boolean entregariaPedidoEnPlazoReal(Pedido pedido)
    {
        if (pedido == null)
            return false;
        Instant plazoMaximoReal = pedido.getPlazoParaLlegadaUltimoVuelo();
        if (plazoMaximoReal == null || this.getFin() == null)
            return false;
        // incluir igualdad: fin <= plazo
        return !this.getFin().isAfter(plazoMaximoReal);
    }

    @Override
    public String toString()
    {
        return "Vuelo{" +
                "id=" + id +
                ", inicio=" + inicio +
                ", fin=" + fin +
                ", idAlmacenOrigen=" + idAlmacenOrigen +
                ", idAlmacenDestino=" + idAlmacenDestino +
                ", capacidadMaximaProductos=" + capacidadMaxima +
                ", capacidadOcupadaProductos=" + capacidadOcupada +
                ", capacidadSinOcupar=" + capacidadSinOcupar +
                ", capacidadReservada=" + capacidadReservada +
                ", capacidadDisponibleParaReserva=" + capacidadDisponibleParaReserva +
                '}';
    }

    public String getEstadoEnInstante(Instant instanteActual)
    {
        if (instanteActual == null)
        {
            instanteActual = Instant.now();
        }
        if (inicio.isBefore(instanteActual))
        {
            return "Por salir";
        }
        if (!inicio.isBefore(instanteActual) && fin.isAfter(instanteActual))
        {
            return "En curso";
        }
        return "Finalizado";
    }

    public void restablecerProductosProgramadosParaAlgoritmo()
    {
        this.idsProductosProgramados = new ArrayList<>();
        this.capacidadReservada = 0;
        this.recalcularDerivados();
    }

    public void loggearSalidaConsola(
            @NotNull Instant instanteProgramadoSalidaVuelo,
            int capacidadTotalACargar
    ) {
        System.out.println("\n=============== VUELO SALIENDO ===============");
        System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
        System.out.println("Fin: " + getFin());
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + getIdAlmacenOrigen());
        System.out.println("Almacén Destino: ID=" + getIdAlmacenDestino());
        System.out.println("Cantidad Productos: " + capacidadTotalACargar);
        System.out.println("Cantidad Productos objetos: " + idsProductosContenidos.size());
        System.out.println("Cantidad Productos atributo: " + capacidadOcupada);
        System.out.println("===============================================\n");
    }

    public void loggearLlegadaConsola(@NotNull Instant instanteProgramadoLlegadaVuelo) {
        System.out.println("\n=============== VUELO LLEGANDO ===============");
        System.out.println("Hora: " + instanteProgramadoLlegadaVuelo);
        System.out.println("Salio a las: " + getInicio());
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + getIdAlmacenOrigen());
        System.out.println("Almacén Destino: ID=" + getIdAlmacenDestino());
        System.out.println("UUIDs productos que lleva: " + getIdsProductosContenidos());
        System.out.println("===============================================\n");
    }
}
