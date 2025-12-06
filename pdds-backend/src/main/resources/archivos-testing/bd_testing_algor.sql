-- select*from almacen;
INSERT INTO almacen 
(id, activo, capacidad_maxima, capacidad_ocupada, codigo_aeropuerto_en4letras, codigo_ciudad_en4letras, continente, es_infinito, gmt, latitud, longitud, nombre_ciudad, nombre_pais ) values 
('1', true, 1, '0', 'A', 'A', 'SUDAMERICA', true, 0, '4.701388888888889', '-74.14694444444446', 'A', 'A'),
('2', true, 2, '0', 'B', 'B', 'SUDAMERICA', false, 0, '4.701388888888889', '-74.14694444444446', 'B', 'B'),
('3', true, 1, '0', 'C', 'C', 'SUDAMERICA', false, 0, '4.701388888888889', '-74.14694444444446', 'C', 'C'),
('4', true, 4, '0', 'D', 'D', 'SUDAMERICA', false, 0, '4.701388888888889', '-74.14694444444446', 'D', 'D'),
('5', true, 4, '0', 'E', 'E', 'SUDAMERICA', false, 0, '4.701388888888889', '-74.14694444444446', 'E', 'E'),
('6', true, 4, '0', 'F', 'F', 'SUDAMERICA', false, 0, '4.701388888888889', '-74.14694444444446', 'F', 'F')
;

-- select*from pedido;
INSERT INTO pedido
(id, cantidad_productos_entregados, cantidad_productos_pedidos, es_intercontinental, almacen_destino_id, instante_registro, instante_maximo_para_entregar)
VALUES
-- 1ra iteración (6 horas)
(1, 0, 1, false, 2, "2025-12-25 01:00:00", "2025-12-27 01:00:00"),
(2, 0, 1, false, 3, "2025-12-25 02:00:00", "2025-12-27 02:00:00"),
(3, 0, 2, false, 4, "2025-12-25 03:00:00", "2025-12-27 03:00:00"),
-- 2da iteración
(4, 0, 2, false, 4, "2025-12-25 08:00:00", "2025-12-27 08:00:00"),
(5, 0, 2, true, 5, "2025-12-25 09:00:00", "2025-12-28 09:00:00")
;

-- select*from vuelo_programado;
INSERT INTO vuelo_programado
(id, activo, capacidad_maxima, es_intercontinental, hora_inicio_en_propio_huso, hora_fin_en_propio_huso, almacen_origen_id, almacen_destino_id )
values
# 1 iteración
-- para resolver pedido 1
(1, true, 1, false, '06:01:00.000000', '08:00:00.000000', 1, 2),

-- para resolver pedido 2
(2, true, 1, false, '06:02:00.000000', '07:02:00.000000', 1, 2),
(3, true, 2, false, '08:03:00.000000', '09:03:00.000000', 2, 3),

-- para resolver pedido 3, que pide 2 prods para el almacén 4 "D"; pero uno de los prods podría ser replanificado (por estar en curso en 2da iter).
(4, true, 2, false, '06:01:00.000000', '12:30:00.000000', 1, 3),
(5, true, 2, false, '13:00:00', '15:00:00', 3, 4),

# 2 iteración
-- para resolver pedido 4 chupando de lo que se hizo para el pedido 3 (dejó prods en almacén 3 "C". fin en almacén 4 "D", lo reprogramo para ir al 5 "E")
(6, true, 1, false, '16:01:00', '17:00:00', 3, 5),
(7, true, 1, false, '14:00:00', '16:02:00', 1, 5), -- apoyándose en una unidad de infinita.
(8, true, 1, false, '14:00:00', '16:02:00', 1, 4), -- el pedido 3 original que iba hacia el almacén 4 puede ayudarse de aquí si lo reprogramaron antes.

-- para resolver pedido 5 simple de noche
(9, true, 2, false, '18:01:00', '19:01:00', 1, 5 ),
(10, true, 2, false, '21:01:00', '22:01:00', 5, 6 )
;


