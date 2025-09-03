package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import lombok.Builder;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;

@Data
@Builder
public class AlmacenForAlgorithm {
    Long id;
    Integer capacidadOcupada;
    Integer capacidadTotal;
    Boolean esInfinito;

    String codigoCiudadEn4Letras;

    public static AlmacenForAlgorithm createFromEntity(Almacen almacen){
        return new AlmacenForAlgorithm(
                almacen.getId(), almacen.getCapacidadOcupada(), almacen.getCapacidadOcupada(), almacen.getEsInfinito(),
                almacen.getCodigoCiudadEn4Letras()
        );
    }

}
