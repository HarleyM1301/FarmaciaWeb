-- Diagnóstico de solo lectura para revisar tablas del esquema farmacia_db.
-- No elimina ni modifica datos.

-- Lista tablas, motor y estimación de filas del esquema.
SELECT table_name, engine, table_rows
FROM information_schema.tables
WHERE table_schema = 'farmacia_db'
ORDER BY table_name;

-- Cuenta filas reales de las dos tablas antiguas que no aparecen en el código de la aplicación.
SELECT 'farmacia_ventas' AS tabla, COUNT(*) AS filas FROM farmacia_db.farmacia_ventas
UNION ALL
SELECT 'farmacia_compras' AS tabla, COUNT(*) AS filas FROM farmacia_db.farmacia_compras;

-- Muestra las columnas y tipos de ambas tablas candidatas.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'farmacia_db'
  AND table_name IN ('farmacia_ventas', 'farmacia_compras')
ORDER BY table_name, ordinal_position;

-- Comprueba si otras tablas tienen claves foráneas que apunten a las candidatas.
SELECT table_name AS tabla_dependiente, column_name AS columna_dependiente,
       referenced_table_name AS tabla_referenciada, referenced_column_name AS columna_referenciada,
       constraint_name
FROM information_schema.key_column_usage
WHERE table_schema = 'farmacia_db'
  AND referenced_table_name IN ('farmacia_ventas', 'farmacia_compras')
ORDER BY table_name, constraint_name;

-- Presenta definiciones y claves foráneas declaradas en las candidatas.
SHOW CREATE TABLE farmacia_db.farmacia_ventas;
SHOW CREATE TABLE farmacia_db.farmacia_compras;
