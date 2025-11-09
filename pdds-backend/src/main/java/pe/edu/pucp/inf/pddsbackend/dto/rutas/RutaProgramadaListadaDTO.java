package pe.edu.pucp.inf.pddsbackend.dto.rutas;

import pe.edu.pucp.inf.pddsbackend.dto.vuelos.VueloResumidoDTO;

import java.util.LinkedList;

public record RutaProgramadaListadaDTO(
    LinkedList<VueloResumidoDTO> vuelosResumidos,
    LinkedList<Long>idsVuelos
){}
