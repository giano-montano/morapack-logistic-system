package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.UUID;

@Getter
public class Producto implements Serializable
{
    private UUID uuid;
    @Setter
    private Instant instanteDeDisponibilidad;
    private Instant fechaExistencia;
    private Instant fechaPlanificacion; 
    private boolean prontoParaEntrega = false;
    @Setter
    private boolean existe = false;
    @Setter
    private boolean planificado = false;
    private Almacen almacenOrigen;
    

    public Producto(Almacen almacenOrigen, Instant instanteCreacion)
    {
        this.uuid = UUID.randomUUID();
        this.instanteDeDisponibilidad = null;
        this.fechaExistencia = null;
        this.fechaPlanificacion = instanteCreacion;
        this.prontoParaEntrega = false;
        this.existe = false; 
        this.planificado = true;
        this.almacenOrigen = almacenOrigen;

        this.entregado = false; //legacy
        this.idAlmacenInfinitoOrigen = this.almacenOrigen.getId(); //legacy
        this.idAlmacenActual = null; //legacy
        this.idVueloActual = null; //legacy
    }

    /*
     * Para saber si a un determinado momento el Producto estará disponible. Esto solo tiene sentido si el Producto esta en pleno vuelo
     * 
     * Remplazo de estaDisponible
     */
    public Boolean estaDisponible_v2(Instant instanteActual)
    {
        return !instanteActual.isBefore(this.instanteDeDisponibilidad);
    }

    /*
     * Para marcar un producto como programado. El instanteCreacion es cuando el algoritmo lo crea y es diferente a instanteExistencia, que es cuando el producto existe en el sistema
     * 
     * Remplazo de marcarComoProgramado
     */
    public boolean marcarComoProgramado_v2(Instant instanteCreacion)
    {
        
if(this.planificado && this.existe)
{
    String mensaje = "ERROR (Marcar productos): No debería poder reasignarse un producto planificado y existente";
    Bitacora.escribir(mensaje);
    throw new IllegalStateException(mensaje);
}

        if(!this.planificado)
        {
            if(this.existe)
            {   //ya ha sido creado
                this.planificado = true;

            }else
            {   //es un nuevo producto
                this.planificado = true;
                this.fechaPlanificacion = instanteCreacion;
            }

            return this.planificado;
        }
        return false;
    }

    /*
     * Marca un producto con una programación que no se puede cancelar
     * 
     * Remplazo de marcarProntoParaEntrega
     */
    public boolean marcarProntoParaEntrega_v2()
    {
        if (!this.prontoParaEntrega)
        {
            this.prontoParaEntrega = true;
            this.existe = true;
            this.instanteDeDisponibilidad = null;
            
            return true;
        }
        return false;
    }

/* LEGACY */

    // cuando el algoritmo lo crea


    @Setter
    private boolean entregado = false; // Ni bien llegue a su aeropuerto de destino!

     // Referido a si ha sido planificado ya en medio de la
                                         // planifación (algoritmo).

    /* DEPRECADO? SI ME COMPROMPETO A ACTUALIZARLO ES ÚTIL*/
     // Ser tomados en cuenta en capacidades, pero
                                               // prohibida su replanificación.

 // Se setea cuando estás inicializando el estado global al inicio
    // del algoritmo. SOLO SIRVE PARA SABER QUE PRODS DE UN ALMACÉN INTERMEDIO PUEDO USAR PARA ASIGNARLOS A ALGUNA
    // PROGRAMACIÓN NO ELIMINABLE. SOLO SIRVE DENTRO DE ALGORITMO!!!!

    // Las validaciones deberán tomar en cuenta que si un producto está pronto para
    // entrega, no se le puede considerar como capacidad útil

    private long idAlmacenInfinitoOrigen;

    // Facilitadores, el estado global los podría dar
    // private Continente continenteOrigen;
    private Long idAlmacenActual; // puede ser nulo y el de abajo no; recuerda, a la planif no le importa si ya estaba
    // este planificado para ir a otro lado, lo puede agarrar del almacén intermedio
    // donde se encuentre, siempre y cuando
    // no sea un destino final!!

    private Long idVueloActual; // puede ser nulo y el de arriba no

    // Constructor principal para nuevos que provienen desde almacenes infinitos,
    // procuremos mantener coherente el estado del dominio
    public Producto(long idAlmacenInfinitoOrigen, LinkedList<Long> idsVuelosProgramadosActuales,
            Instant fechaPlanif) {
        this.uuid = UUID.randomUUID();
        this.fechaPlanificacion = fechaPlanif != null ? fechaPlanif : Instant.now();
        this.fechaExistencia = null;
        this.existe = false; // nace así y es la simulación quien lo setea en true
        this.entregado = false;
        this.planificado = true;
        this.prontoParaEntrega = false;
        this.idAlmacenInfinitoOrigen = idAlmacenInfinitoOrigen;

        this.idAlmacenActual = null;
        this.idVueloActual = null;
    }

    // Constructor principal para existentes, nadie lo usa, ya que estos SOLO SE RECUPERAN
    public Producto(
            UUID uuid,
            long idAlmacenInfinitoOrigen,
            LinkedList<Long> idsVuelosProgramadosActuales,
            Instant fechaExistencia,
            boolean entregado,
            Long idAlmacenActual,
            Long idVueloActual,
            Instant fechaPlanif)
    {
        this.uuid = uuid;
        this.fechaPlanificacion = fechaPlanif != null ? fechaPlanif : Instant.now();
        this.fechaExistencia = fechaExistencia;
        this.existe = true;
        this.entregado = entregado;
        this.planificado = false;
        this.prontoParaEntrega = false; // (?)
        this.idAlmacenInfinitoOrigen = idAlmacenInfinitoOrigen;
        this.idVueloActual = idVueloActual;
        this.idAlmacenActual = idAlmacenActual;
    }

    public Producto(Producto value)
    {
        this.uuid = value.uuid;
        this.fechaPlanificacion = value.fechaPlanificacion;
        this.fechaExistencia = value.fechaExistencia;
        this.existe = value.existe;
        this.entregado = value.entregado;
        this.planificado = value.planificado;
        this.prontoParaEntrega = value.prontoParaEntrega;
        this.idAlmacenInfinitoOrigen = value.idAlmacenInfinitoOrigen;

        this.idAlmacenActual = value.idAlmacenActual;
        this.idVueloActual = value.idVueloActual;
        this.instanteDeDisponibilidad = value.instanteDeDisponibilidad;
    }

    public void cargarEnAlmacen(Long idAlmacenActual){
        this.idAlmacenActual = idAlmacenActual;
        this.idVueloActual = null; // al cambiar de almacén, ya no está en vuelo
    }

    public void embarcarEnVuelo(Long idVueloActual){
        this.idVueloActual = idVueloActual;
        this.idAlmacenActual = null; // al cambiar de vuelo, ya no está en almacén
    }

    public boolean marcarProntoParaEntrega()
    {
        if (!this.prontoParaEntrega)
        {
            this.prontoParaEntrega = true;
            return true;
        }
        return false;
    }

/* Su única función es que el algoritmo deje de considerarlo como un producto escogible, ya que el que tenga
* planificado true significa que ya hay una programación válida que lo utilizó
* */
    private boolean establecerQueEstaPlanificado()
    {
        if (!this.planificado)
        {
            this.planificado = true;
            return true;
        }
        return false;
    }

    /* Restablecimiento del estado para que el algoritmo pueda usarlo en una nueva planificación*/
    public boolean desestablecerQueEstaPlanificadoParaAlgoritmo(){
        if (this.planificado)
        {
            this.planificado = false;
            return true;
        }
        return false;
    }

/* PARA QUE EL ALGORITMO NO LO VUELVA A TOCAR*/
    public boolean marcarComoProgramado(Instant instant){
        boolean res = true;
        res &= this.establecerQueEstaPlanificado();

        fechaPlanificacion = instant;
        return res;
    }

    /*
     * Para saber si a un determinado momento el Producto estará disponible. Esto
     * solo tiene sentido si el Producto esta en pleno vuelo
     */
    public Boolean estaDisponible(Instant instanteActual)
    {
        return !instanteActual.isBefore(this.instanteDeDisponibilidad);
    }

    @Override
    public String toString()
    {
        return "Producto{" +
                "uuid=" + uuid +
                ", fechaPlanificacion=" + fechaPlanificacion +
                ", fechaExistencia=" + fechaExistencia +
                ", existe=" + existe +
                ", entregado=" + entregado +
                ", planificado=" + planificado +
                ", prontoParaEntrega=" + prontoParaEntrega +
                ", idAlmacenInfinitoOrigen=" + idAlmacenInfinitoOrigen +
                ", idAlmacenActual=" + idAlmacenActual +
                ", idVueloActual=" + idVueloActual +
                '}';
    }

}
