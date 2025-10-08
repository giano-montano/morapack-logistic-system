package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.models.entities.Continente;

@Data
public class VueloSolucionDTO {
    Long idVuelo;
    Long idAlmacenOrigen;
    Long idAlmacenDestino;

    String codigoAeropuertoOrigenEn4Siglas;
    String codigoAeropuertoDestinoEn4Siglas;

    String ciudadOrigenEn4Siglas;
    String ciudadDestinoEn4Siglas;

    Continente continenteOrigen;
    Continente continenteDestino;

    Byte orden;
}
