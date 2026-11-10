-- Guarda precios de venta independientes para blíster y caja, además de la unidad mínima.
ALTER TABLE producto
    ADD COLUMN precio_blister DECIMAL(12,2) NULL AFTER precio_venta,
    ADD COLUMN precio_presentacion DECIMAL(12,2) NULL AFTER precio_blister;
