# FarmaciaWeb

Aplicación de gestión para farmacia con Java 21, Jakarta Servlet/JSP, Tomcat 10 y MySQL.

## Preparación

1. Inicia MySQL y confirma que exista la base `farmacia_db`.
2. El proyecto utiliza las tablas del esquema existente: `usuario`, `producto`, `inventario`, `venta`, `detalleventa`, `compra`, `detallecompra`, `cliente` y `proveedor`.
3. Aplica una vez el script `src/main/resources/db/migration/V2__metodo_pago_venta.sql` en MySQL Workbench para guardar también el método de pago de cada venta. Es una columna opcional y no elimina ni cambia registros anteriores.
4. Aplica una vez `src/main/resources/db/migration/V3__factura_y_recepcion_multiple.sql` para guardar número de factura y adjuntos, y evitar facturas duplicadas del mismo proveedor.
5. Configura estas variables de entorno en el usuario o servicio que ejecuta Tomcat: `FARMACIA_DB_URL` (opcional; por defecto `jdbc:mysql://localhost:3306/farmacia_db`), `FARMACIA_DB_USER` (opcional; por defecto `root`) y `FARMACIA_DB_PASSWORD` (contraseña de MySQL). Reinicia Tomcat después de configurarlas.
6. Aplica una vez `src/main/resources/db/migration/V4__unidades_y_presentacion.sql` para guardar la unidad base y las unidades que trae cada caja o paquete. Los productos existentes quedan con factor 1; edítalos en Medicamentos y define su unidad y contenido antes de recibir nuevas facturas.
7. Aplica una vez `src/main/resources/db/migration/V5__trazabilidad_inventario.sql` para crear el historial de entradas y salidas del inventario.
8. Aplica una vez `src/main/resources/db/migration/V6__registro_invima.sql` para agregar el campo opcional del registro sanitario INVIMA, separado del lote.
9. Aplica una vez `src/main/resources/db/migration/V7__limpiar_tablas_legacy_vacias.sql` para retirar las tablas antiguas `farmacia_ventas` y `farmacia_compras`. El script las elimina solo si ambas siguen vacías.
10. Aplica una vez `src/main/resources/db/migration/V8__presentacion_blister.sql` para guardar cuántas unidades base contiene cada blíster. En Medicamentos define el factor del blíster; si queda en 0, no estará disponible como unidad de venta.
11. Aplica una vez `src/main/resources/db/migration/V9__validar_contenido_blister.sql` para validar que una caja contenga un número entero de blísteres. Ejecuta V8 antes de V9.
12. Aplica una vez `src/main/resources/db/migration/V10__catalogo_maestro_cum_invima.sql` para crear el catálogo maestro CUM/INVIMA y permitir vincular sus presentaciones con los productos y lotes existentes. No borra ni modifica las existencias actuales.
13. Aplica una vez `src/main/resources/db/migration/V11__detalle_componentes_cum_invima.sql` para guardar principios activos, cantidades/concentraciones por componente, vías, forma farmacéutica e IUM, incluso cuando una presentación tiene varios principios activos.
14. Entra como administrador a **Catálogo INVIMA**, descarga el CSV del conjunto oficial enlazado en esa página e impórtalo. La carga consolida las filas repetidas por CUM y guarda cada principio activo relacionado. Las futuras actualizaciones actualizan el catálogo por CUM sin borrar productos ni existencias.
15. Durante la recepción de una factura, para un producto nuevo puedes buscar por nombre, principio activo, registro sanitario o código CUM y confirmar manualmente la presentación. Revisa concentración/componentes, forma, vía, descripción y empaque antes de confirmar. Se conserva el vínculo CUM y el registro sanitario en el producto.
16. Para importar un CSV desde consola: `java -cp "target/classes;target/FarmaciaWeb-1.0-SNAPSHOT/WEB-INF/lib/mysql-connector-j-9.3.0.jar" com.farmacia.util.ImportarCatalogoInvima ruta\catalogo.csv`. Configura las variables `FARMACIA_DB_URL`, `FARMACIA_DB_USER` y `FARMACIA_DB_PASSWORD` antes de ejecutar. La opción `--verificar` consulta los conteos.
17. Abre el proyecto Maven en NetBeans y ejecútalo sobre Tomcat 10. El conector MySQL se empaqueta con la aplicación.

## Módulos

- **Acceso:** inicio de sesión, registro de usuarios y control de sesión.
- **Medicamentos:** consulta, búsqueda, alta, edición y eliminación del catálogo y de su stock.
- **Ventas:** registra el encabezado y sus detalles, vincula el cajero y cliente, guarda el método de pago y descuenta existencias en la misma transacción.
- **Compras y recepción técnica:** conserva una fila por producto de la factura. El auxiliar puede seleccionar las cajas o paquetes que requieren control, dividirlos entre uno o varios lotes y capturar su vencimiento. Configura por producto la unidad base y el contenido de cada paquete; la recepción convierte paquetes a unidades base y calcula el costo por unidad. La lectura reconoce CSV/XML estructurados y usa OCR local en español para JPG/JPEG/PNG; el lector tabular separa cantidad y precio y sugiere la presentación indicada en la descripción. El borrador se puede corregir antes de confirmar.
- **Reportes:** resume los datos del catálogo y consulta los movimientos almacenados en las tablas originales.
- **Alertas y trazabilidad:** el panel muestra stock bajo y lotes vencidos o próximos a vencer; el historial registra las entradas de compras y salidas de ventas con fecha, usuario, referencia y existencias antes y después.

Una compra recibida con un lote distinto crea otro producto-lote para conservar su fecha de vencimiento y sus existencias por separado. El sistema conserva en el historial los productos que ya tengan ventas o compras asociadas. Los archivos importados se presentan como borrador para revisión; leerlos nunca modifica existencias hasta que un usuario confirma la recepción. El motor OCR y el modelo de idioma español se sirven desde la propia aplicación y la imagen no se envía a un servicio externo. Consulta las licencias de terceros en `src/main/webapp/assets/vendor/ocr/`.
