-- Historial de entradas y salidas de existencias. No altera el stock actual.
CREATE TABLE IF NOT EXISTS movimiento_inventario (
    -- Identificador secuencial del evento de entrada, salida o ajuste.
    id_movimiento BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Referencia al producto/lote afectado por el movimiento.
    id_producto INT NOT NULL,
    -- Usuario autenticado que originó el cambio, si la cuenta aún existe.
    id_usuario INT NULL,
    -- Copia del nombre para que el historial siga siendo legible con el tiempo.
    nombre_usuario VARCHAR(120) NOT NULL,
    -- Tipo de evento, por ejemplo COMPRA, VENTA o ajuste.
    tipo VARCHAR(20) NOT NULL,
    -- Variación firmada de unidades base aplicada al inventario.
    cantidad INT NOT NULL,
    -- Saldo anterior al evento para reconstruir la trazabilidad.
    stock_anterior INT NOT NULL,
    -- Saldo posterior al evento que quedó registrado.
    stock_nuevo INT NOT NULL,
    -- Número de factura u otra referencia que explica el movimiento.
    referencia VARCHAR(80) NOT NULL,
    -- Momento en que MySQL guardó el evento.
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Índices para consultar cronología y movimientos de un producto sin recorrer toda la tabla.
    INDEX idx_movimiento_fecha (fecha),
    INDEX idx_movimiento_producto (id_producto)
);
SHOW TABLES LIKE 'movimiento_inventario';
