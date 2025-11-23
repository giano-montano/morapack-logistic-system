package pe.edu.pucp.inf.pddsbackend.websocket.dto;

/**
 * Razones por las cuales puede finalizar una simulación
 */
public enum RazonFinSimulacion
{
    /**
     * Simulación finalizó normalmente al alcanzar el tiempo objetivo (solo SEMANAL)
     */
    FIN_POR_TIEMPO,

    /**
     * Colapso: Almacén de origen no tiene productos suficientes para cargar vuelo
     */
    COLAPSO_ALMACEN_ORIGEN_SIN_PRODUCTOS,

    /**
     * Colapso: Vuelo no tiene capacidad suficiente para cargar productos programados
     */
    COLAPSO_VUELO_SIN_CAPACIDAD,

    /**
     * Colapso: Almacén de destino no tiene espacio para recibir productos del vuelo
     */
    COLAPSO_ALMACEN_DESTINO_SIN_ESPACIO,

    /**
     * Colapso: El algoritmo de planificación no pudo programar todos los pedidos
     */
    COLAPSO_PLANIFICACION_INCOMPLETA,

    /**
     * Simulación cancelada por el usuario
     */
    CANCELADA_POR_USUARIO
}
