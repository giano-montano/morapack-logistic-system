package pe.edu.pucp.inf.pddsbackend.dto.almacenes;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Almacen;

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
        Boolean activo) {

    public static AlmacenDTO desdeDominio(Almacen almacen) {
        return new AlmacenDTO(
                almacen.getId(),
                almacen.getCodigoAeropuertoEn4Letras(),
                almacen.getCodigoCiudadEn4Letras(),
                almacen.getNombreCiudad(),
                almacen.getNombrePais(),
                null, // ASUMO QUE ESTAS COSAS YA ESTÁN PRECARGADAS EN EL MAPA DEL FRONTEND, NO LAS NECESITA Uu
                null,
                null, // lo mismo
                almacen.getContinente().name(),
                almacen.getCapacidad(),
                almacen.getInventario().size(),
                almacen.isInfinito(),
                true
                );
    }
}
