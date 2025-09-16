DELIMITER $$

DROP PROCEDURE IF EXISTS sp_map_vuelos_diarios_a_vuelos$$

CREATE PROCEDURE sp_map_vuelos_diarios_a_vuelos()
BEGIN
  -- Inserta los vuelos concretos para el día actual usando las horas UTC almacenadas como TIME.
  -- Evita duplicados comprobando existencia exacta por origen/destino/fechas calculadas.
  INSERT INTO vuelo (
      capacidad_maxima_productos,
      capacidad_ocupada_productos,
      capacidad_reservada_productos,
      estado,
      fecha_hora_inicio,
      fecha_hora_fin,
      almacen_destino_id,
      almacen_origen_id
  )
  SELECT
      vdp.capacidad_maxima_productos,
      COALESCE(vdp.capacidad_ocupada_productos, 0),
      COALESCE(vdp.capacidad_reservada_productos, 0),
      'EN_ESPERA',
      -- inicio: fecha actual + hora de inicio UTC (time)
      TIMESTAMP(CURDATE(), vdp.fecha_hora_inicio_utc),
      -- fin: si la hora de fin <= inicio (time) -> día siguiente; sino mismo día
      CASE
        WHEN vdp.fecha_hora_fin_utc <= vdp.fecha_hora_inicio_utc
          THEN TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), vdp.fecha_hora_fin_utc)
        ELSE
          TIMESTAMP(CURDATE(), vdp.fecha_hora_fin_utc)
      END,
      vdp.almacen_destino_id,
      vdp.almacen_origen_id
  FROM vuelo_diario_programado vdp
  WHERE
    -- requisitos mínimos para programar
    vdp.fecha_hora_inicio_utc IS NOT NULL
    AND vdp.almacen_origen_id IS NOT NULL
    AND vdp.almacen_destino_id IS NOT NULL
    -- evita insertar duplicados exactos
    AND NOT EXISTS (
      SELECT 1
      FROM vuelo v
      WHERE v.almacen_origen_id = vdp.almacen_origen_id
        AND v.almacen_destino_id = vdp.almacen_destino_id
        AND v.fecha_hora_inicio = TIMESTAMP(CURDATE(), vdp.fecha_hora_inicio_utc)
        AND v.fecha_hora_fin = (
            CASE
              WHEN vdp.fecha_hora_fin_utc <= vdp.fecha_hora_inicio_utc
                THEN TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), vdp.fecha_hora_fin_utc)
              ELSE TIMESTAMP(CURDATE(), vdp.fecha_hora_fin_utc)
            END
        )
    );
END$$

DROP EVENT IF EXISTS ev_map_vuelos_diarios$$

CREATE EVENT IF NOT EXISTS ev_map_vuelos_diarios
ON SCHEDULE EVERY 1 DAY
STARTS (CURRENT_DATE + INTERVAL 1 DAY)   -- próxima medianoche del servidor
ON COMPLETION PRESERVE
ENABLE
DO
BEGIN
  CALL sp_map_vuelos_diarios_a_vuelos();
END$$

DELIMITER ;


-- iniciar inmediatamente y luego ccada día
-- CREATE EVENT ev_map_vuelos_diarios
-- ON SCHEDULE EVERY 1 DAY
-- STARTS CURRENT_TIMESTAMP

-- forzar ejecución diaria a medianoche UTC
-- ejemplo: calcula próxima medianoche UTC y úsala como STARTS
-- CREATE EVENT ev_map_vuelos_diarios
-- ON SCHEDULE EVERY 1 DAY
# STARTS (CONVERT_TZ(CURRENT_DATE + INTERVAL 1 DAY, @@global.time_zone, '+00:00'))




