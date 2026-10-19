# FarmaciaWeb

Aplicación de gestión para farmacia con Java 21, Jakarta Servlet/JSP, Tomcat 10 y MySQL.

## Preparación

1. Inicia MySQL y confirma que exista la base `farmacia_db`.
2. El proyecto utiliza las tablas del esquema existente: `usuario`, `producto`, `inventario`, `venta`, `detalleventa`, `compra`, `detallecompra`, `cliente` y `proveedor`.
3. Aplica una vez el script `src/main/resources/db/migration/V2__metodo_pago_venta.sql` en MySQL Workbench para guardar también el método de pago de cada venta. Es una columna opcional y no elimina ni cambia registros anteriores.
4. Aplica una vez `src/main/resources/db/migration/V3__factura_y_recepcion_multiple.sql` para guardar número de factura y adjuntos, y evitar facturas duplicadas del mismo proveedor.
5. Configura estas variables de entorno en el usuario o servicio que ejecuta Tomcat: `FARMACIA_DB_URL` (opcional; por defecto `jdbc:mysql://localhost:3306/farmacia_db`), `FARMACIA_DB_USER` (opcional; por defecto `root`) y `FARMACIA_DB_PASSWORD` (contraseña de MySQL). Reinicia Tomcat después de configurarlas.
6. Abre el proyecto Maven en NetBeans y ejecútalo sobre Tomcat 10. El conector MySQL se empaqueta con la aplicación.

## Módulos

- **Acceso:** inicio de sesión, registro de usuarios y control de sesión.
- **Medicamentos:** consulta, búsqueda, alta, edición y eliminación del catálogo y de su stock.
- **Ventas:** registra el encabezado y sus detalles, vincula el cajero y cliente, guarda el método de pago y descuenta existencias en la misma transacción.
- **Compras y recepción técnica:** conserva una fila por producto de la factura. El auxiliar puede seleccionar las líneas que requieren control, dividir sus unidades entre uno o varios lotes y capturar el vencimiento identificado durante la recepción. Las unidades distribuidas deben coincidir con la cantidad facturada. Conserva el archivo original y aumenta existencias en una sola transacción. La lectura automática inicial reconoce CSV y XML; en PDF, Excel e imágenes conserva el adjunto y permite completar el borrador a mano.
- **Reportes:** resume los datos del catálogo y consulta los movimientos almacenados en las tablas originales.

Una compra recibida con un lote distinto crea otro producto-lote para conservar su fecha de vencimiento y sus existencias por separado. El sistema conserva en el historial los productos que ya tengan ventas o compras asociadas. Los CSV y XML importados se presentan como borrador para revisión; el archivo nunca modifica existencias hasta que un usuario confirma la recepción.
