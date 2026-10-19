-- Conserva la forma de pago que selecciona el cajero junto a cada venta.
ALTER TABLE venta
    ADD COLUMN metodo_pago VARCHAR(30) NULL AFTER id_cliente;
