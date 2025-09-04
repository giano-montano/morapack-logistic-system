-- juego de datos para prueba básica de pedido normal y pedido con escalas

insert ignore into almacen (id,codigo_ciudad_en4letras ,capacidad_ocupada, capacidad_total, es_infinito)  values
(1 ,"LIMA",0 ,   20,   true),
(2 , "QUIT"  ,0 ,   20,   false),
(3 ,"BRAS",0 ,   20,   false),
(4 , "BOGO",0 ,   20,   false),
(5 , "SANT",0 ,   0,   true);


insert ignore into vuelo (id,estado, fecha_hora_inicio, fecha_hora_fin,  almacen_origen_id, almacen_destino_id, capacidad_maxima_productos, capacidad_ocupada_productos)  values
(1 ,   "EN_ESPERA" ,   "2025-09-06 00:00:00.000000",   "2025-09-06 08:00:00.000000", 1,2   , 200 , 0),
(2 ,   "EN_ESPERA" ,   "2025-09-06 08:00:00.000000",   "2025-09-06 16:00:00.000000", 2,3   , 200 , 0);


insert ignore into pedido (id,cantidad_productos_total, almacen_destino_id, instante_registro, instante_maximo_para_entregar, atendido_completamente, colapsado)  values
(1 ,   10 ,  2, "2025-09-05 00:00:00.000000", "2025-09-05 03:00:00.000000",  false, false ),
(2 ,   20 ,  3, "2025-09-05 00:00:00.000000", "2025-09-05 03:00:00.000000", false, false );
-- colapsado podría ser manejado por eventos de MySQL en lugar de ejecuciones de algoritmo o aplicación encendida


