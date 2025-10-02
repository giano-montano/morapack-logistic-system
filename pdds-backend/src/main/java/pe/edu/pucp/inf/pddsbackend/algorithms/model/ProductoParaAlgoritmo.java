package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ProductoParaAlgoritmo {

    // bd
    private Long id;
    private Long idAlmacenInfinitoOrigen;
    private Long idRutaProgramadaActual;
    private Instant fechaCreacion;
    private boolean entregado=false;

    // facilidades
    private Long idAlmacenActual;


}
