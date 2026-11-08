-- Separa el registro sanitario INVIMA del código de lote de cada producto.
ALTER TABLE producto
    ADD COLUMN registro_invima VARCHAR(60) NULL AFTER categoria;

-- Copia al nuevo campo los códigos que tienen el formato usual de registro INVIMA.
-- Conserva el valor original en lote para que pueda verificarse y reemplazarse por el lote real.
UPDATE producto
SET registro_invima = lote
WHERE registro_invima IS NULL
  AND lote REGEXP '^[0-9]{4}[A-Za-z]-[0-9]+(-R[0-9]+)?$';
