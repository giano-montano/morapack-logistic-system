-- Agregar valor CANCELADA al enum razon_fin
-- Ejecutar este script manualmente en la base de datos

ALTER TABLE simulacion 
MODIFY COLUMN razon_fin ENUM('POR_USUARIO', 'POR_COLAPSO', 'NATURAL', 'ERROR_INTERNO', 'CANCELADA');

-- Verificar que se aplicó correctamente
DESCRIBE simulacion;
