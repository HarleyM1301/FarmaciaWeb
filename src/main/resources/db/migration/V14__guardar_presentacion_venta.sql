-- Conserva la presentación y la cantidad elegidas al momento de vender.
ALTER TABLE detalleventa
    ADD COLUMN unidad_venta VARCHAR(20) NOT NULL DEFAULT 'base' AFTER cantidad,
    ADD COLUMN cantidad_presentacion INT NOT NULL DEFAULT 1 AFTER unidad_venta;

-- Las ventas anteriores almacenaban sus cantidades en unidades mínimas.
UPDATE detalleventa
SET cantidad_presentacion = cantidad;
