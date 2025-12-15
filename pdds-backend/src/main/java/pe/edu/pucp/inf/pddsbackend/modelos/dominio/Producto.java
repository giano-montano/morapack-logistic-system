package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.UUID;

@Getter
public class Producto implements Serializable {
    private UUID id;

    private boolean existente = false;
    private boolean planificado = false;
    private boolean incancelable = false;

    private Almacen almacenOrigen;

    public Producto(Almacen almacenOrigen, Instant instanteCreacion) {
        this.id = UUID.randomUUID();
        this.incancelable = false;
        this.existente = false;
        this.planificado = true;
        this.almacenOrigen = almacenOrigen;

    }


    /*
     * Para marcar un producto como programado. El instanteCreacion es cuando el algoritmo lo crea y es diferente a instanteExistencia, que es cuando el producto existe en el sistema
     *
     * Remplazo de marcarComoProgramado
     */
    public boolean marcarComoProgramado_v2(Instant instantePlanificacion) {

        if (this.planificado && this.existente) {
            String mensaje = "ERROR (Marcar productos): No debería poder reasignarse un producto planificado y existente";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }

        if (!this.planificado) {
            if (this.existente) {   //ya ha sido creado
                this.planificado = true;

            } else {   //es un nuevo producto
                this.planificado = true;
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
    public boolean marcarProntoParaEntrega_v2() {
        if (!this.incancelable) {
            this.incancelable = true;
            this.existente = true;

            return true;
        }
        return false;
    }


/* LEGACY */




    // Constructor principal para nuevos que provienen desde almacenes infinitos,
    // procuremos mantener coherente el estado del dominio
    public Producto(Almacen almacenOrigen) {
        this.id = UUID.randomUUID();
        this.existente = false; // nace así y es la simulación quien lo setea en true
        this.planificado = true;
        this.incancelable = false;

        this.almacenOrigen = almacenOrigen;
    }

//    // Constructor principal para existentes, nadie lo usa, ya que estos SOLO SE RECUPERAN
//    public Producto(
//            UUID id,
//            long idAlmacenInfinitoOrigen,
//            LinkedList<Long> idsVuelosProgramadosActuales,
//            Instant fechaExistencia,
//            boolean entregado,
//            Long idAlmacenActual,
//            Long idVueloActual,
//            Instant fechaPlanif)
//    {
//        this.id = id;
//        this.existente = true;
//        this.planificado = false;
//        this.incancelable = false; // (?)
//
//    }

    // copia
    public Producto(Producto value) {
        this.id = value.id;
        this.existente = value.existente;
        this.planificado = value.planificado;
        this.incancelable = value.incancelable;
        this.almacenOrigen = new Almacen( value.almacenOrigen );
    }




    @Override
    public String toString()
    {
        return "Producto{" +
                "uuid=" + id +
                ", existe=" + existente +
                ", planificado=" + planificado +
                ", prontoParaEntrega=" + incancelable +
                '}';
    }

}
