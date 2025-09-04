package pe.edu.pucp.inf.pddsbackend.dto;

import lombok.Data;

@Data
public class VueloDTO {
    Long id;
    String ciudadOrigenEn4Siglas;
    String ciudadDestinoEn4Siglas;
}
