package pe.edu.pucp.inf.pddsbackend.models.domain;

import lombok.Data;

@Data
public class Almacen {

    Long id;
    Integer capacidadOcupada;
    Integer capacidadTotal;
    Integer capacidadReservadaPorEnvios;

    Boolean esInfinito;

    Ciudad ciudad;

}
