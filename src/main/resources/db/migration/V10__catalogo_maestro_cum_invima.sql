-- Crea el catálogo maestro oficial separado de producto/inventario, que contiene existencias por lote.
CREATE TABLE catalogo_medicamento_invima (
    id_catalogo BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    expediente VARCHAR(40) NULL,
    producto VARCHAR(255) NOT NULL,
    principio_activo VARCHAR(500) NULL,
    concentracion VARCHAR(255) NULL,
    forma_farmaceutica VARCHAR(150) NULL,
    titular VARCHAR(255) NULL,
    registro_sanitario VARCHAR(100) NULL,
    fecha_expedicion DATE NULL,
    fecha_vencimiento_registro DATE NULL,
    estado_registro VARCHAR(40) NULL,
    expediente_cum VARCHAR(40) NULL,
    consecutivo_cum VARCHAR(30) NULL,
    cantidad_cum VARCHAR(30) NULL,
    descripcion_comercial TEXT NULL,
    estado_cum VARCHAR(40) NULL,
    fecha_activo DATE NULL,
    fecha_inactivo DATE NULL,
    muestra_medica VARCHAR(20) NULL,
    unidad VARCHAR(20) NULL,
    atc VARCHAR(20) NULL,
    codigo_cum VARCHAR(110) NULL,
    presentacion_normalizada VARCHAR(255) NULL,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_catalogo),
    UNIQUE KEY uk_catalogo_medicamento_cum (codigo_cum),
    KEY idx_catalogo_producto (producto),
    KEY idx_catalogo_principio (principio_activo(191)),
    KEY idx_catalogo_registro (registro_sanitario),
    KEY idx_catalogo_estado (estado_registro, estado_cum),
    KEY idx_catalogo_atc (atc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Permite vincular varios lotes internos a una misma presentación oficial CUM.
ALTER TABLE producto
    ADD COLUMN catalogo_medicamento_id BIGINT UNSIGNED NULL AFTER registro_invima,
    ADD KEY idx_producto_catalogo_medicamento (catalogo_medicamento_id),
    ADD CONSTRAINT fk_producto_catalogo_medicamento
        FOREIGN KEY (catalogo_medicamento_id)
        REFERENCES catalogo_medicamento_invima (id_catalogo)
        ON UPDATE CASCADE
        ON DELETE SET NULL;
