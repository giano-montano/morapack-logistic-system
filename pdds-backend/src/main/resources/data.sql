INSERT IGNORE INTO pais (codigo, nombre, continente) VALUES
('CO','Colombia','SUDAMERICA'),
('EC','Ecuador','SUDAMERICA'),
('VE','Venezuela','SUDAMERICA'),
('BR','Brasil','SUDAMERICA'),
('PE','Perú','SUDAMERICA'),
('BO','Bolivia','SUDAMERICA'),
('CL','Chile','SUDAMERICA'),
('AR','Argentina','SUDAMERICA'),
('PY','Paraguay','SUDAMERICA'),
('UY','Uruguay','SUDAMERICA'),

('AL','Albania','EUROPA'),
('DE','Alemania','EUROPA'),
('AT','Austria','EUROPA'),
('BE','Bélgica','EUROPA'),
('BY','Bielorrusia','EUROPA'),
('BG','Bulgaria','EUROPA'),
('CZ','República Checa','EUROPA'),
('HR','Croacia','EUROPA'),
('DK','Dinamarca','EUROPA'),
('NL','Países Bajos','EUROPA'),

('IN','India','ASIA'),
('SY','Siria','ASIA'),
('SA','Arabia Saudita','ASIA'),
('AE','Emiratos Árabes Unidos','ASIA'),
('AF','Afganistán','ASIA'),
('OM','Omán','ASIA'),
('YE','Yemen','ASIA'),
('PK','Pakistán','ASIA'),
('AZ','Azerbaiyán','ASIA'),
('JO','Jordania','ASIA')
;


-- juego de datos para prueba básica de pedido normal y pedido con escalas
-- añadido pedido que debe partirse en 2

insert ignore into almacen (id,codigo_ciudad_en4letras ,capacidad_ocupada, capacidad_total, es_infinito, pais_codigo)  values
(1 ,"LIMA",0 ,   0,   true, 'PE'),
(2 , "QUIT"  ,0 ,   100,   false, 'EC' ), -- si le pongo 10 o 20 en ocupada, se queda sin atender -> colapso logístico.

(3 ,"BRAS",40 ,   100,   false, 'BR' ), -- lo ocupamos para que lo lleve desde brasil hasta SANT partiendo en dos. NO FUNCA PQ ES DEMASIADO HEURÍSTICO.
(4 , "BOGO",0 ,   100,   false, 'CO'),

(5 , "SANT",0 ,   100,   false, 'CL' );
-- AAAA-MM-DD HH:MM:SS
insert ignore into vuelo (id,estado, fecha_hora_inicio, fecha_hora_fin,  almacen_origen_id, almacen_destino_id, capacidad_maxima_productos, capacidad_ocupada_productos)  values
                                                                                                                                                                              (1 ,   "EN_ESPERA" ,   "2025-09-12 00:00:00.000000",   "2025-09-12 08:00:00.000000", 1,2   , 100 , 0),
                                                                                                                                                                              (2 ,   "EN_ESPERA" ,   "2025-09-12 09:00:00.000000",   "2025-09-12 16:00:00.000000", 2,3   , 100 , 0),

                                                                                                                                                                              (3 ,   "EN_ESPERA" ,   "2025-09-13 08:00:00.000000",   "2025-09-13 16:00:00.000000", 3,4   , 20 , 0),
                                                                                                                                                                              (4 ,   "EN_ESPERA" ,   "2025-09-13 20:00:00.000000",   "2025-09-13 23:00:00.000000", 4,5   , 20 , 0);

insert ignore into pedido (id,cantidad_productos_total, almacen_destino_id, instante_registro, instante_maximo_para_entregar, atendido_completamente, colapsado)  values
                                                                                                                                                                      (1 ,   10 ,  2, "2025-09-11 00:00:00.000000", "2025-09-11 03:00:00.000000",  false, false ),
                                                                                                                                                                      (2 ,   20 ,  3, "2025-09-11 00:00:00.000000", "2025-09-11 03:00:00.000000", false, false ),
                                                                                                                                                                      (3 ,   40 ,  5, "2025-09-11 00:00:00.000000", "2025-09-11 03:00:00.000000", false, false );-- colapsado podría ser manejado por eventos de MySQL en lugar de ejecuciones de algoritmo o aplicación encendida


