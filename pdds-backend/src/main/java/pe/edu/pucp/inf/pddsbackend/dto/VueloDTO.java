package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

@Data
public class VueloDTO {
    Long idVuelo;
    Long idAlmacenOrigen;
    Long idAlmacenDestino;

    String codigoAeropuertoOrigenEn4Siglas;
    String codigoAeropuertoDestinoEn4Siglas;

    String ciudadOrigenEn4Siglas;
    String ciudadDestinoEn4Siglas;
}
