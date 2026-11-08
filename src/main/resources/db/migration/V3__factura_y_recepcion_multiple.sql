-- Identifica cada factura y conserva su archivo original para auditoría.
ALTER TABLE compra
    -- Conserva el folio de la factura del proveedor.
    ADD COLUMN numero_factura VARCHAR(60) NULL,
    -- Guarda la ruta del archivo adjunto para consulta y auditoría.
    ADD COLUMN archivo_factura VARCHAR(255) NULL;

-- Evita registrar dos veces la misma factura del mismo proveedor.
CREATE UNIQUE INDEX ux_compra_proveedor_factura
    -- Evita folios repetidos para un proveedor, permitiendo el mismo folio en otro proveedor.
    ON compra (id_proveedor, numero_factura);
