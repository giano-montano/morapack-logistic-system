package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.models.entities.Continente;

import java.time.Instant;

@Getter
public class ProductoParaAlgoritmo {

    private UUID id; // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // BASE DE DATOS NO ESPECIFICA Y NO CONTROLA LÓGICA DE PKs, ESO SE DA DE FORMA EXTERNA, EN ESTE CASO
    // EL ALGORITMO TANTO SI EXISTE EL PROD COMO SI NO EXISTE AÚN.

    private long idAlmacenInfinitoOrigen;
    private long idRutaProgramadaActual;

    private Instant fechaCreacion;
    private boolean entregado=false; // más que seguro que al algoritmo solo le llegarán con entregado=false; ignorarlo
    private Continente continenteOrigen; // BD facilita trabajo en consulta previa

    // facilidades
    private Long idAlmacenActual; // puede ser nulo y el de abajo no
    private Long idVueloActual; // puede ser nulo y el de arriba no


}
