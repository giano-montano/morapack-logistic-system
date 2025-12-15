package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.VueloEntidad;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Getter
public class Vuelo implements Serializable
{
    long id;
    int capacidad;
    Instant instanteSalida;
    Instant instanteLlegada;

    Almacen almacenSalida;
    Almacen almacenDestino;
    String codigo;

    List<Producto> inventario;

    boolean intercontinental;
    boolean cancelado = false;

    /*
     * Verifica si hay capacidad disponible
     * 
     * Remplazo de getCapacidadDisponibleParaReserva
     */
    public int obtenerCapacidadDisponible_v2()
    {
        return this.capacidad - inventario.size();
    }

    /*
     * En base a un instante, devuelve si el vuelo ya partió
     */
    public boolean yaPartio_v2(Instant instanteActual)
    {
        return instanteSalida.isBefore(instanteActual);
    }
    
    /*
     * Añade una lista de productos al inventario (llamado idsProductosContenidos)
     */
    public boolean registrarInventario_v2(List<Producto> productos)
    {
        int inventarioTotal;

        inventarioTotal = this.inventario.size() + productos.size();

        if(inventarioTotal <= this.capacidad) {
            this.inventario.addAll(productos);

            return true;
        }

        return false;
    }

    /*
     * Añade un solo producto  al inventario (llamado idsProductosContenidos)
     */
    public boolean registrarInventario_v2(Producto producto)
    {
        int inventarioTotal;
        
        inventarioTotal = this.inventario.size() + 1;

        if(inventarioTotal <= this.capacidad)
        {
            this.inventario.add(producto);

            return true;
        }

        return false;
    }

/* LEGACY */


    public static int correlativo = 1;

    public Vuelo(/* long id, */
            Almacen idAlmacenOrigen,
            Almacen almacenDestino,
            String codigo,
            Instant instanteSalida,
            Instant instanteLlegada,
            int capacidad,
            boolean intercontinental,
            boolean cancelado)
    {
        this.id = correlativo;
        correlativo++;
        this.almacenSalida = idAlmacenOrigen;
        this.almacenDestino = almacenDestino;
        this.codigo = codigo;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.capacidad = Math.max(0, capacidad);


        this.inventario = new ArrayList<>();
        this.intercontinental = intercontinental;
        this.cancelado = cancelado;
    }

    public Vuelo(long id,
            Almacen almacenOrigen,
             Almacen almacenDestino,
            String codigo,
            Instant instanteSalida,
            Instant instanteLlegada,
            int capacidad,
            int capacidadOcupada,
            boolean intercontinental,
            boolean cancelado)
    {
        this.id = id;
        this.almacenSalida = almacenOrigen;
        this.almacenDestino = almacenDestino;
        this.codigo = codigo;
        this.instanteSalida = instanteSalida;
        this.instanteLlegada = instanteLlegada;
        this.capacidad = Math.max(0, capacidad);

//        this.recalcularDerivados();
        this.inventario = new ArrayList<>();
        this.intercontinental = intercontinental;
        this.cancelado = cancelado;
    }

    public Vuelo(Vuelo other)
    {
        this.id = other.id;
        this.instanteSalida = other.instanteSalida;
        this.instanteLlegada = other.instanteLlegada;
        this.almacenSalida = other.almacenSalida;
        this.almacenDestino = other.almacenDestino;
        this.codigo = other.codigo;
        this.capacidad = other.capacidad;
        this.inventario = other.inventario;
        this.intercontinental = other.intercontinental;
        this.cancelado = other.cancelado;
    }

    public static Vuelo desdeEntidad(VueloEntidad v)
    {
        return new Vuelo(
                v.getId(),
                Almacen.desdeEntidad( v.getAlmacenOrigen() ),
                Almacen.desdeEntidad( v.getAlmacenDestino() ),
                v.getCodigo4Letras(),
                v.getFechaHoraInicioUtc(),
                v.getFechaHoraFinUtc(),
                v.getCapacidadMaxima(),
                v.getCapacidadOcupada(),
                v.getEsIntercontinental(),
                v.getCancelado());
    }



//    /** Recalcula campos derivados según ocupados/reservados. */
//    private void recalcularDerivados()
//    {
//        capacidadSinOcupar = Math.max(0, capacidad - capacidadOcupada);
//        capacidadDisponibleParaReserva = Math.max(0,
//                capacidad - capacidadOcupada - capacidadReservada); // <- JAAAAAAAAAAAAAAAA
//    }

    public boolean yaPartio(Instant ahora){
        return instanteSalida.isBefore(ahora != null ? ahora : Instant.now());
    }

    public boolean yaLlego(Instant ahora){
        return !instanteLlegada.isAfter(ahora != null ? ahora : Instant.now());
    }

    public boolean yaPartioEnVidaReal()
    {
        return instanteSalida.isBefore(Instant.now());
    }



//    /**
//     * Intenta ocupar 'cantidad' unidades inmediatamente (sin usar reservas).
//     *
//     * @return true si se pudo ocupar; false si no hay suficiente capacidad sin
//     *         ocupar.
//     */
//    public synchronized boolean ocuparConProducto(Producto producto){
//        if (capacidadSinOcupar >= 1){ // un solo productito
//            capacidadOcupada += 1;
//            recalcularDerivados();
//            inventario.add(producto);
//            return true;
//        }
//        return false;
//    }
//
//    public boolean reservarCapacidad(UUID uuidProducto/* int cantidad */)
//    {
//        if (capacidadDisponibleParaReserva >= 1){
//            capacidadReservada += 1;
//            recalcularDerivados();
//            idsProductosProgramados.add(uuidProducto);
//            return true;
//        }
//        return false;
//    }

//    /**
//     * Libera (desocupa) 'cantidad' unidades que estaban ocupadas.
//     *
//     * @return true si había suficiente ocupado y se desocupó; false en otro caso.
//     */
//    public synchronized boolean desocuparConProducto(Producto producto)
//    {
//        if (capacidadOcupada >= 1)
//        {
//            capacidadOcupada -= 1;
//            recalcularDerivados();
//            inventario.remove(producto);
//            return true;
//        }
//        return false;
//    }

//    public boolean agregarVarios(List<Producto> productos){
//        for (Producto producto : productos){
//            if (!ocuparConProducto(producto))
//                return false;
//        }
//        return true;
//    }

//    public boolean quitarVarios(List<Producto> productos)
//    {
//        for (Producto producto : productos)
//        {
//            if (!desocuparConProducto(producto))
//                return false;
//        }
//        return true;
//    }

    public boolean entregariaPedidoEnPlazoReal(Pedido pedido)
    {
        if (pedido == null)
            return false;
        Instant plazoMaximoReal = pedido.getPlazoParaLlegadaUltimoVuelo();
        if (plazoMaximoReal == null || this.instanteLlegada == null)
            return false;
        // incluir igualdad: instanteLlegada <= plazo
        return !this.instanteLlegada.isAfter(plazoMaximoReal);
    }

//    public boolean tieneContenido()
//    {
//        return capacidadOcupada != 0
//                || capacidadSinOcupar != capacidad
//                || capacidadReservada != 0
//                || capacidadDisponibleParaReserva != capacidad
//                || (!inventario.isEmpty())
//                || (!idsProductosProgramados.isEmpty());
//    }
    
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        java.util.function.Function<Instant, String> formatInstant = instant -> instant.toString()
                .replace("T", " ").replace("Z", "");

//        sb.append("Vuelo (").append(id).append(")\n");
//        sb.append("\tOrigen: (").append(formatInstant.apply(instanteSalida)).append("; ")
//                .append("Almacen ").append(idAlmacenOrigen)
//                .append(")\n");
//        sb.append("\tDestino: (").append(formatInstant.apply(instanteLlegada)).append("; ")
//                .append("Almacen ").append(almacenDestino)
//                .append(")\n");
//
//        int cantidadActual = idsProductosContenidos.size() + idsProductosProgramados.size();
//        sb.append("\tCapacidad: ").append(cantidadActual).append("/").append(capacidad)
//                .append("\n");
//        sb.append("\tInventario (").append(cantidadActual).append(" productos):\n");
//
//        if (idsProductosContenidos.isEmpty() && idsProductosProgramados.isEmpty())
//        {
//            sb.append("\t\tVacio");
//        }
//        else
//        {
//            sb.append("\t\t[");
//            for (int i = 0; i < idsProductosContenidos.size(); i++)
//            {
//                if (i > 0)
//                    sb.append(", ");
//                sb.append(idsProductosContenidos.get(i).toString().substring(0, 8)).append("...");
//            }
//            sb.append("]\n");
//            sb.append("\t\t[");
//            for (int i = 0; i < idsProductosProgramados.size(); i++)
//            {
//                if (i > 0)
//                    sb.append(", ");
//                sb.append(idsProductosProgramados.get(i).toString().substring(0, 8)).append("...");
//            }
//            sb.append("]");
//        }

        return sb.toString();
    }

    public String getEstadoEnInstante(Instant instanteActual) {
        if (instanteActual == null) {
            instanteActual = Instant.now();
        }
        if (instanteSalida.isBefore(instanteActual)) {
            return "Por salir";
        }
        if (!instanteSalida.isBefore(instanteActual) && instanteLlegada.isAfter(instanteActual)) {
            return "En curso";
        }
        return "instanteLlegadaalizado";
    }

//    public void restablecerProductosProgramadosParaAlgoritmo()
//    {
//        this.idsProductosProgramados = new ArrayList<>();
//        this.capacidadReservada = 0;
//        this.recalcularDerivados();
//    }

    public void loggearSalidaConsola(
            @NotNull Instant instanteProgramadoSalidaVuelo,
            int capacidadTotalACargar
    ) {
        System.out.println("\n=============== VUELO SALIENDO ===============");
        System.out.println("Hora: " + instanteProgramadoSalidaVuelo);
        System.out.println("instanteLlegada: " + instanteLlegada);
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + almacenSalida.getId());
        System.out.println("Almacén Destino: ID=" + getAlmacenDestino());
        System.out.println("Cantidad Productos: " + capacidadTotalACargar);
        System.out.println("Cantidad Productos objetos: " + inventario.size());
        System.out.println("===============================================\n");
    }

    public void loggearLlegadaConsola(@NotNull Instant instanteProgramadoLlegadaVuelo) {
        System.out.println("\n=============== VUELO LLEGANDO ===============");
        System.out.println("Hora: " + instanteProgramadoLlegadaVuelo);
        System.out.println("Salio a las: " + instanteSalida);
        System.out.println("ID Vuelo: " + id);
        System.out.println("Almacén Origen: ID=" + almacenSalida.getId());
        System.out.println("Almacén Destino: ID=" + getAlmacenDestino());
        System.out.println("UUIDs productos que lleva: " + inventario);
        System.out.println("===============================================\n");
    }
}
