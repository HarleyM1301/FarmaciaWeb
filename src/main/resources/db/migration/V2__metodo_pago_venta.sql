-- Conserva la forma de pago que selecciona el cajero junto a cada venta.
ALTER TABLE venta
    -- Guarda la forma de pago sin obligar a rellenarla en las ventas históricas.
    ADD COLUMN metodo_pago VARCHAR(30) NULL AFTER id_cliente;
