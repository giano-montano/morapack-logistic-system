package pe.edu.pucp.inf.pddsbackend.dto.almacenes;

public record AlmacenDTO(
        Long id,
        String codigoAeropuertoEn4Letras,
        String codigoCiudadEn4Letras,
        String nombreCiudad,
        String nombrePais,
        Double latitud,
        Double longitud,
        Integer gmt,
        String continente,
        Integer capacidadMaxima,
        Integer capacidadOcupada,
        Boolean esInfinito,
        Boolean activo
) {}
