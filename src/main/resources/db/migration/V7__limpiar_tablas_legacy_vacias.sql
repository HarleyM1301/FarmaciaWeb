-- Elimina dos tablas heredadas que no usa la aplicación y que el diagnóstico encontró vacías.
-- La condición evita borrar información si alguna de ellas recibió filas después de la auditoría.
SET @legacy_filas =
    (SELECT COUNT(*) FROM farmacia_db.farmacia_ventas)
    + (SELECT COUNT(*) FROM farmacia_db.farmacia_compras);

SET @limpieza_sql = IF(
    @legacy_filas = 0,
    'DROP TABLE IF EXISTS farmacia_db.farmacia_ventas, farmacia_db.farmacia_compras',
    'SELECT ''Limpieza omitida: las tablas heredadas ahora contienen datos.'' AS resultado'
);

PREPARE limpieza FROM @limpieza_sql;
EXECUTE limpieza;
DEALLOCATE PREPARE limpieza;
