package pe.edu.pucp.inf.pddsbackend.models.domain;

import lombok.Data;

@Data
public class Almacen {

    Long id;
    Integer capacidadOcupada;
    Integer capacidadTotal;
    Boolean esInfinito;

    Ciudad ciudad;

}
