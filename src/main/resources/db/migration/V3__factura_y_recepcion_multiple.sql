-- Identifica cada factura y conserva su archivo original para auditoría.
ALTER TABLE compra
    ADD COLUMN numero_factura VARCHAR(60) NULL,
    ADD COLUMN archivo_factura VARCHAR(255) NULL;

-- Evita registrar dos veces la misma factura del mismo proveedor.
CREATE UNIQUE INDEX ux_compra_proveedor_factura
    ON compra (id_proveedor, numero_factura);
