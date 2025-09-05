package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

@Data
public class VueloDTO {
    Long idVuelo;
    String ciudadOrigenEn4Siglas;
    String ciudadDestinoEn4Siglas;
}
