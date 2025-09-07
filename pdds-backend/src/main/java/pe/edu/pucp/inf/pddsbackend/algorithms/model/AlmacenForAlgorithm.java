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
    Integer capacidadReservadaPorEnvios;

    Boolean esInfinito;

    String codigoCiudadEn4Letras;

    public static AlmacenForAlgorithm createFromEntity(Almacen almacen){
        return AlmacenForAlgorithm.builder()
                .id(almacen.getId())
                .capacidadOcupada(almacen.getCapacidadOcupada())
                .capacidadTotal(almacen.getCapacidadTotal())
                .capacidadReservadaPorEnvios(almacen.getCapacidadReservadaPorEnvios())
                .esInfinito(almacen.getEsInfinito())
                .codigoCiudadEn4Letras(almacen.getCodigoCiudadEn4Letras())
                .build()
                ;
    }

}
