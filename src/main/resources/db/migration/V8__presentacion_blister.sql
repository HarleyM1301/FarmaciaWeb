-- Permite convertir la cantidad vendida en blísteres a unidades de inventario.
ALTER TABLE producto
    ADD COLUMN unidades_por_blister INT NOT NULL DEFAULT 0
    COMMENT 'Unidades base por blíster; 0 indica que no se configuró';
