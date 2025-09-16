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

-- Inserta almacenes / aeropuertos (lat/lon en grados decimales)
INSERT IGNORE INTO almacen
(es_infinito, capacidad_total, capacidad_ocupada, capacidad_reservada_por_envios,
 codigo_ciudad_en4letras, nombre_ciudad, codigo_aeropuerto_en4letras,
 pais_codigo, latitud, longitud, gmt)
VALUES
    (FALSE, 430, 0, 0, 'BOGO', 'Bogota',       'SKBO', 'CO',  4.701388888888889,  -74.14694444444446,  -5),
    (FALSE, 410, 0, 0, 'QUIT', 'Quito',        'SEQM', 'EC',  0.11333333333333334, -78.35861111111110,  -5),
    (FALSE, 400, 0, 0, 'CARA', 'Caracas',      'SVMI', 'VE', 10.603055555555555,  -66.99055555555556,  -4),
    (FALSE, 480, 0, 0, 'BRAS', 'Brasilia',     'SBBR', 'BR', -15.864722222222222, -47.91805555555555,  -3),
    (FALSE, 440, 0, 0, 'LIMA', 'Lima',         'SPIM', 'PE', -12.021944444444445, -77.11444444444444,  -5),
    (FALSE, 420, 0, 0, 'LAPA', 'La Paz',       'SLLP', 'BO', -16.513055555555557, -68.19222222222223,  -4),
    (FALSE, 460, 0, 0, 'SANT', 'Santiago',     'SCEL', 'CL', -33.396388888888886, -70.79472222222222,  -3),
    (FALSE, 460, 0, 0, 'BUEN', 'Buenos Aires', 'SABE', 'AR', -34.55916666666667,  -58.41555555555555,  -3),
    (FALSE, 400, 0, 0, 'ASUN', 'Asuncion',     'SGAS', 'PY', -25.240000000000002, -57.52000000000000,  -4),
    (FALSE, 400, 0, 0, 'MONT', 'Montevideo',   'SUAA', 'UY', -34.78916666666667,  -56.26472222222222,  -3),

    (FALSE, 410, 0, 0, 'TIRA', 'Tirana',       'LATI', 'AL', 41.41472222222222,   19.720555555555553,   2),
    (FALSE, 480, 0, 0, 'BERL', 'Berlin',       'EDDI', 'DE', 52.47361111111111,   13.401666666666667,   2),
    (FALSE, 430, 0, 0, 'VIEN', 'Viena',        'LOWW', 'AT', 48.11083333333333,   16.570833333333333,   2),
    (FALSE, 440, 0, 0, 'BRUS', 'Bruselas',     'EBCI', 'BE', 50.45916666666667,    4.453611111111111,    2),
    (FALSE, 400, 0, 0, 'MINS', 'Minsk',        'UMMS', 'BY', 53.88250000000000,   28.032500000000000,   3),
    (FALSE, 400, 0, 0, 'SOFI', 'Sofia',        'LBSF', 'BG', 42.69027777777777,   23.404722222222222,   3),
    (FALSE, 400, 0, 0, 'PRAG', 'Praga',        'LKPR', 'CZ', 50.10138888888889,   14.265555555555556,   2),
    (FALSE, 420, 0, 0, 'ZAGR', 'Zagreb',       'LDZA', 'HR', 45.74277777777778,   16.06861111111111,    2),
    (FALSE, 480, 0, 0, 'COPE', 'Copenhague',   'EKCH', 'DK', 55.61805555555556,   12.65611111111111,    2),
    (FALSE, 480, 0, 0, 'AMST', 'Amsterdam',    'EHAM', 'NL', 52.30000000000000,    4.765000000000000,    2),

    (FALSE, 480, 0, 0, 'DELH', 'Delhi',        'VIDP', 'IN', 28.56638888888889,   77.10305555555556,    5),
    (FALSE, 400, 0, 0, 'DAMA', 'Damasco',      'OSDI', 'SY', 33.41138888888889,   36.51555555555556,    3),
    (FALSE, 420, 0, 0, 'RIAD', 'Riad',         'OERK', 'SA', 24.95777777777778,   46.69888888888889,    3),
    (FALSE, 420, 0, 0, 'DUBA', 'Dubai',        'OMDB', 'AE', 25.252777777777776,  55.364444444444445,   4),
    (FALSE, 480, 0, 0, 'KABU', 'Kabul',        'OAKB', 'AF', 34.565555555555555,  69.21083333333334,    4),
    (FALSE, 460, 0, 0, 'MASC', 'Mascate',      'OOMS', 'OM', 23.589444444444442,  58.284166666666664,   4),
    (FALSE, 420, 0, 0, 'SANA', 'Sana',         'OYSN', 'YE', 15.476111111111111,  44.219722222222224,   3),
    (FALSE, 410, 0, 0, 'KARA', 'Karachi',      'OPKC', 'PK', 24.900000000000002,  67.15000000000000,    5),
    (FALSE, 400, 0, 0, 'BAKU', 'Baku',         'UBBB', 'AZ', 40.467222222222226,  50.04666666666667,    2),
    (FALSE, 400, 0, 0, 'AMAN', 'Aman',         'OJAI', 'JO', 31.72250000000000,   35.99333333333333,    3)
;


