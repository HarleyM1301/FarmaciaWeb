-- La cantidad por caja y por blíster ya se guarda en V4 y V8.
-- Valida que las unidades de la caja se puedan dividir en blísteres completos.
ALTER TABLE producto
    ADD CONSTRAINT chk_producto_presentacion_blister
    CHECK (
        unidades_por_presentacion >= 1
        AND unidades_por_blister >= 0
        AND (
            unidades_por_blister = 0
            OR (
                unidades_por_blister <= unidades_por_presentacion
                AND MOD(unidades_por_presentacion, unidades_por_blister) = 0
            )
        )
    );
