-- Conserva la información por principio activo cuando un medicamento combinado tiene varias filas en el CSV CUM.
ALTER TABLE catalogo_medicamento_invima
    -- Guarda el Identificador Único de Medicamento publicado para la presentación.
    ADD COLUMN ium VARCHAR(120) NULL AFTER codigo_cum,
    -- Registra la vía principal que informa la fuente oficial.
    ADD COLUMN via_administracion VARCHAR(100) NULL AFTER forma_farmaceutica;

-- Una presentación puede repetirse en el CSV por tener varios principios activos o roles.
CREATE TABLE catalogo_medicamento_componente_invima (
    -- Clave interna de cada ingrediente asociado con la presentación CUM.
    id_componente BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- Presentación maestra a la que pertenece el principio activo.
    id_catalogo BIGINT UNSIGNED NOT NULL,
    -- Firma calculada para impedir duplicados del mismo componente durante reimportaciones.
    firma_componente CHAR(64) NOT NULL,
    -- Nombre del principio activo declarado por INVIMA.
    principio_activo VARCHAR(500) NULL,
    -- Cantidad o concentración del ingrediente, según la unidad de referencia.
    cantidad VARCHAR(50) NULL,
    unidad_medida VARCHAR(40) NULL,
    unidad_referencia VARCHAR(100) NULL,
    atc VARCHAR(20) NULL,
    descripcion_atc VARCHAR(500) NULL,
    -- Clave primaria e identificador idempotente de cada componente por presentación.
    PRIMARY KEY (id_componente),
    UNIQUE KEY uk_catalogo_componente_firma (id_catalogo, firma_componente),
    KEY idx_componente_principio (principio_activo(191)),
    KEY idx_componente_atc (atc),
    CONSTRAINT fk_componente_catalogo_invima FOREIGN KEY (id_catalogo)
        REFERENCES catalogo_medicamento_invima (id_catalogo)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
