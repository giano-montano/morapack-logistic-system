package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.*;

public record AlmacenCreateUpdateDTO(
        @NotBlank String codigoAeropuertoEn4Letras,
        @NotBlank String codigoCiudadEn4Letras,
        @NotBlank String nombreCiudad,
        @NotBlank String nombrePais,
        @NotNull Double latitud,
        @NotNull Double longitud,
        @NotNull Integer gmt,
        @NotBlank String continente,
        @NotNull @Positive Integer capacidadMaxima,
        @NotNull @Min(0) Integer capacidadOcupada,
        @NotNull Boolean esInfinito
) {}
