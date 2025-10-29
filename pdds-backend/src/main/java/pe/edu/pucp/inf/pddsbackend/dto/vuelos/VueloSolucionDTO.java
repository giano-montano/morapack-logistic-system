package pe.edu.pucp.inf.pddsbackend.dto.vuelos;

import lombok.Data;

import java.time.Instant;

@Data
public class VueloSolucionDTO {
    Long idVuelo;
    Long idAlmacenOrigen;
    Long idAlmacenDestino;

    String codigoAeropuertoOrigenEn4Siglas;
    String codigoAeropuertoDestinoEn4Siglas;

    String ciudadOrigenEn4Siglas;
    String ciudadDestinoEn4Siglas;

    Instant inicio;
    Instant fin;

    Byte orden;
}
