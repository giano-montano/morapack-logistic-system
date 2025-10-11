package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;

import java.time.Instant;
import java.util.LinkedList;
import java.util.UUID;

@Getter
public class Producto {

    private UUID uuid;

    private Instant fechaPlanificacion; // cuando el algoritmo lo crea
    private Instant fechaExistencia; // cuando spawnea en un vuelo
    boolean existe=false;
    boolean entregado=false; // Ni bien llegue a su aeropuerto de destino!
    boolean planificado=false; // Referido a si ha sido planificado ya en medio de la planifación (algoritmo).

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
    public Producto(long idAlmacenInfinitoOrigen, LinkedList<Long> idsVuelosProgramadosActuales) {
        this.uuid = UUID.randomUUID();
        this.fechaPlanificacion = Instant.now();
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
            Long idVueloActual
    ) {
        this.uuid = uuid;
        this.fechaPlanificacion = Instant.now();
        this.fechaExistencia = fechaExistencia;
        this.existe=true;
        this.entregado=entregado;
        this.planificado=false;
        this.idAlmacenInfinitoOrigen=idAlmacenInfinitoOrigen;
        this.idVueloActual=idVueloActual;
        this.idAlmacenActual=idAlmacenActual;

        this.idsVuelosProgramadosActuales=new LinkedList<>(idsVuelosProgramadosActuales);

    }





}
