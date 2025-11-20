package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedList;
import java.util.UUID;

@Getter
public class Producto {

    private UUID uuid;

    private Instant fechaPlanificacion; // cuando el algoritmo lo crea
    private Instant fechaExistencia; // cuando spawnea en un vuelo
    @Setter
    private boolean existe=false;

    @Setter
    private boolean entregado=false; // Ni bien llegue a su aeropuerto de destino!

    @Setter
    private boolean planificado=false; // Referido a si ha sido planificado ya en medio de la planifación (algoritmo).

    private long idAlmacenInfinitoOrigen;
    private LinkedList<Long> idsVuelosProgramadosActuales;

    // Facilitadores, el estado global los podría dar
//    private Continente continenteOrigen;
    private Long idAlmacenActual; // puede ser nulo y el de abajo no; recuerda, a la planif no le importa si ya estaba
    // este planificado para ir a otro lado, lo puede agarrar del almacén intermedio donde se encuentre, siempre y cuando
    // no sea un destino final!!
    private Long idVueloActual; // puede ser nulo y el de arriba no

    // Constructor principal para nuevos,
    // procuremos mantener coherente el estado del dominio
    public Producto(long idAlmacenInfinitoOrigen, LinkedList<Long> idsVuelosProgramadosActuales, Instant fechaPlanif) {
        this.uuid = UUID.randomUUID();
        this.fechaPlanificacion = fechaPlanif!=null?fechaPlanif:Instant.now();
        this.existe=false;
        this.entregado=false;
        this.planificado=true;
        this.idAlmacenInfinitoOrigen=idAlmacenInfinitoOrigen;
        this.idsVuelosProgramadosActuales=idsVuelosProgramadosActuales!=null?
                new LinkedList<>(idsVuelosProgramadosActuales):new LinkedList<>();
    }

    // Constructor principal para existentes
    public Producto(
            UUID uuid,
            long idAlmacenInfinitoOrigen,
            LinkedList<Long> idsVuelosProgramadosActuales,
            Instant fechaExistencia,
            boolean entregado,
            Long idAlmacenActual,
            Long idVueloActual,
            Instant fechaPlanif
    ) {
        this.uuid = uuid;
        this.fechaPlanificacion = fechaPlanif!=null?fechaPlanif: Instant.now();
        this.fechaExistencia = fechaExistencia;
        this.existe=true;
        this.entregado=entregado;
        this.planificado=false;
        this.idAlmacenInfinitoOrigen=idAlmacenInfinitoOrigen;
        this.idVueloActual=idVueloActual;
        this.idAlmacenActual=idAlmacenActual;

        this.idsVuelosProgramadosActuales=new LinkedList<>(idsVuelosProgramadosActuales);

    }

    public Producto(Producto value) {
        this.uuid = value.uuid;
        this.fechaPlanificacion = value.fechaPlanificacion;
        this.fechaExistencia = value.fechaExistencia;
        this.existe=value.existe;
        this.entregado=value.entregado;
        this.planificado=value.planificado;
        this.idAlmacenInfinitoOrigen=value.idAlmacenInfinitoOrigen;
        this.idsVuelosProgramadosActuales=new LinkedList<>(value.idsVuelosProgramadosActuales); // linked?
        this.idAlmacenActual=value.idAlmacenActual;
        this.idVueloActual=value.idVueloActual;
    }

    public void cargarEnAlmacen(Long idAlmacenActual) {
        this.idAlmacenActual = idAlmacenActual;
        this.idVueloActual = null; // al cambiar de almacén, ya no está en vuelo
    }

    public void embarcarEnVuelo(Long idVueloActual) {
        this.idVueloActual = idVueloActual;
        this.idAlmacenActual = null; // al cambiar de vuelo, ya no está en almacén
    }

    @Override
    public String toString() {
        return "Producto{" +
                "uuid=" + uuid +
                ", fechaPlanificacion=" + fechaPlanificacion +
                ", fechaExistencia=" + fechaExistencia +
                ", existe=" + existe +
                ", entregado=" + entregado +
                ", planificado=" + planificado +
                ", idAlmacenInfinitoOrigen=" + idAlmacenInfinitoOrigen +
                ", idsVuelosProgramadosActuales=" + idsVuelosProgramadosActuales +
                ", idAlmacenActual=" + idAlmacenActual +
                ", idVueloActual=" + idVueloActual +
                '}';
    }

}