package pe.edu.pucp.inf.pddsbackend.websocket.dto;

/**
 * Razones por las cuales puede finalizar una simulación
 */
public enum RazonFinSimulacion
{
    FIN_POR_TIEMPO,
    COLAPSO_ALMACEN_ORIGEN_SIN_PRODUCTOS,
    COLAPSO_VUELO_SIN_CAPACIDAD,
    COLAPSO_ALMACEN_DESTINO_SIN_ESPACIO,
    COLAPSO_PLANIFICACION_INCOMPLETA,
    CANCELADA_POR_USUARIO
}
