-- Permite expresar el inventario en unidades pequeñas aunque la factura venga por cajas.
ALTER TABLE producto
    -- Define la unidad mínima que se suma y descuenta del inventario.
    ADD COLUMN unidad_base VARCHAR(20) NOT NULL DEFAULT 'unidad',
    -- Convierte la cantidad facturada por paquete a unidades mínimas.
    ADD COLUMN unidades_por_presentacion INT NOT NULL DEFAULT 1;
