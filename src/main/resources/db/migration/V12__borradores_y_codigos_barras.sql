-- Conserva códigos de barras de la presentación CUM, no del lote físico.
CREATE TABLE catalogo_medicamento_codigo_barras (
    id_codigo BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_catalogo BIGINT UNSIGNED NOT NULL,
    codigo VARCHAR(32) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_codigo),
    UNIQUE KEY uk_catalogo_codigo_barras_codigo (codigo),
    KEY idx_catalogo_codigo_barras_catalogo (id_catalogo),
    CONSTRAINT fk_catalogo_codigo_barras_catalogo
        FOREIGN KEY (id_catalogo) REFERENCES catalogo_medicamento_invima (id_catalogo)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
