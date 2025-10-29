package pe.edu.pucp.inf.pddsbackend.dto.productos;

import java.util.UUID;

public record ProductoSolucionDTO(
  UUID uuid, Long idAlmacenInfinitoOrigen, boolean existeAhora // más atributos
){}
