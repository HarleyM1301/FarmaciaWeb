package com.farmacia.dao;

import com.farmacia.util.Conexion;
import com.farmacia.modelo.LineaCompra;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

/** Registra compras y ventas en las tablas relacionales originales de farmacia_db. */
public class MovimientoDAO {

    /** Guarda una venta, su detalle y la salida de stock en una sola transacción. */
    public boolean registrarVenta(Date fecha, String cliente, int productoId,
            int cantidadPresentaciones, String unidadVenta, String metodo, int usuarioId) throws SQLException {
        try (Connection c = Conexion.getConexion()) {
            // Exige base disponible antes de iniciar la operación de venta.
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            // Desactiva el autocommit para que venta, detalle, stock e historial sean indivisibles.
            c.setAutoCommit(false);
            try {
                // Lee y bloquea el producto para que otra venta no use el mismo stock a la vez.
                Producto producto = obtenerProducto(c, productoId);
                // Convierte la cantidad vendida (unidad, blíster o caja) a unidades de inventario.
                int cantidad = convertirCantidadVenta(cantidadPresentaciones, unidadVenta, producto);
                // Rechaza cantidades no positivas y ventas que excedan las existencias actuales.
                if (cantidad < 1 || cantidad > producto.stock) throw new SQLException("Stock insuficiente.");
                // Impide vender lotes cuya fecha de vencimiento ya pasó.
                if (producto.vencimiento != null && producto.vencimiento.toLocalDate().isBefore(java.time.LocalDate.now())) {
                    throw new SQLException("No se puede vender un producto vencido.");
                }
                // Busca al cliente por nombre o lo crea si se indicó uno nuevo.
                int clienteId = obtenerOCrearCliente(c, cliente);
                // Calcula el importe de la venta con el precio vigente del producto.
                double subtotal = producto.precio * cantidad;
                // Prepara la inserción de la cabecera de venta y solicita su ID generado.
                String venta = "INSERT INTO venta (fecha,total,id_usuario,id_cliente,metodo_pago) VALUES (?,?,?,?,?)";
                int ventaId;
                try (PreparedStatement p = c.prepareStatement(venta, Statement.RETURN_GENERATED_KEYS)) {
                    // Asigna fecha, total y cajero a la cabecera de la venta.
                    p.setTimestamp(1, new java.sql.Timestamp(fecha.getTime()));
                    p.setDouble(2, subtotal);
                    p.setInt(3, usuarioId);
                    // Guarda el cliente cuando se seleccionó uno o deja la relación vacía.
                    if (clienteId == 0) p.setNull(4, java.sql.Types.INTEGER); else p.setInt(4, clienteId);
                    // Registra el método de pago seleccionado en el formulario.
                    p.setString(5, metodo);
                    // Inserta la cabecera y obtiene el identificador generado.
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        // Comprueba que MySQL haya devuelto el número de venta.
                        if (!keys.next()) throw new SQLException("MySQL no devolvió el número de venta.");
                        // Conserva el ID para enlazar detalle y trazabilidad.
                        ventaId = keys.getInt(1);
                    }
                }
                // Prepara el detalle que relaciona la venta con producto, unidades y precio.
                try (PreparedStatement p = c.prepareStatement("INSERT INTO detalleventa (id_venta,id_producto,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?)")) {
                    // Vincula los datos calculados al detalle de venta.
                    p.setInt(1, ventaId); p.setInt(2, productoId); p.setInt(3, cantidad);
                    // Guarda precio unitario y subtotal, y ejecuta la inserción del detalle.
                    p.setDouble(4, producto.precio); p.setDouble(5, subtotal); p.executeUpdate();
                }
                // Descuenta del inventario las unidades vendidas.
                guardarStock(c, productoId, producto.stock - cantidad);
                // Registra usuario, cantidad y existencias antes y después de la venta.
                registrarMovimientoInventario(c, productoId, usuarioId, "VENTA", -cantidad,
                        producto.stock, producto.stock - cantidad, "Venta V" + ventaId);
                // Confirma cabecera, detalle, stock e historial como una sola venta.
                c.commit();
                // Informa que la venta se guardó por completo.
                return true;
            } catch (Exception e) {
                // Revierte todos los cambios si algún paso de la venta falla.
                c.rollback();
                // Conserva las excepciones SQL originales para que la capa superior las interprete.
                if (e instanceof SQLException sql) throw sql;
                // Añade contexto a errores inesperados sin perder su causa original.
                throw new SQLException("No se pudo guardar la venta.", e);
            } finally {
                // Restaura el autocommit para devolver la conexión en estado normal.
                c.setAutoCommit(true);
            }
        }
    }

    /** Edita una venta de un producto y ajusta el inventario dentro de la misma transacción. */
    public void actualizarVenta(int ventaId, Date fecha, String cliente, int productoId,
            int cantidadPresentaciones, String unidadVenta, String metodo, int usuarioId) throws SQLException {
        if (fecha == null || ventaId < 1 || productoId < 1 || cantidadPresentaciones < 1
                || !("Efectivo".equals(metodo) || "Tarjeta".equals(metodo) || "Transferencia".equals(metodo))
                || cliente != null && cliente.length() > 100) {
            throw new SQLException("Revisa los datos de la venta.");
        }
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                // Lee la única línea de detalle que puede editar este formulario.
                int productoAnterior;
                int cantidadAnterior;
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT id_producto,cantidad FROM detalleventa WHERE id_venta=? FOR UPDATE")) {
                    p.setInt(1, ventaId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("No se encontró el detalle de la venta.");
                        productoAnterior = r.getInt("id_producto");
                        cantidadAnterior = r.getInt("cantidad");
                        if (r.next()) throw new SQLException("La venta tiene más de un producto y requiere edición detallada.");
                    }
                }
                Producto anterior = obtenerProducto(c, productoAnterior);
                // Reversa primero el consumo original para calcular el stock disponible para la edición.
                int stockAnteriorRepuesto = anterior.stock + cantidadAnterior;
                guardarStock(c, productoAnterior, stockAnteriorRepuesto);

                Producto nuevo = obtenerProducto(c, productoId);
                // Convierte la cantidad elegida a unidades base antes de revisar existencias.
                int cantidad = convertirCantidadVenta(cantidadPresentaciones, unidadVenta, nuevo);
                // Valida la nueva cantidad contra el stock resultante después de revertir la venta original.
                if (nuevo.stock < cantidad) throw new SQLException("No hay existencias suficientes para modificar la venta.");
                if (nuevo.vencimiento != null && nuevo.vencimiento.toLocalDate().isBefore(java.time.LocalDate.now())) {
                    throw new SQLException("No se puede asignar a la venta un lote vencido.");
                }
                int clienteId = obtenerOCrearCliente(c, cliente);
                // Recalcula el total con el precio actual del producto elegido.
                double subtotal = nuevo.precio * cantidad;
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE venta SET fecha=?,total=?,id_cliente=?,metodo_pago=? WHERE idVenta=?")) {
                    p.setTimestamp(1, new java.sql.Timestamp(fecha.getTime())); p.setDouble(2, subtotal);
                    if (clienteId == 0) p.setNull(3, java.sql.Types.INTEGER); else p.setInt(3, clienteId);
                    p.setString(4, metodo); p.setInt(5, ventaId);
                    if (p.executeUpdate() != 1) throw new SQLException("No se encontró la venta para editar.");
                }
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE detalleventa SET id_producto=?,cantidad=?,precio_unitario=?,subtotal=? WHERE id_venta=?")) {
                    p.setInt(1, productoId); p.setInt(2, cantidad); p.setDouble(3, nuevo.precio);
                    p.setDouble(4, subtotal); p.setInt(5, ventaId);
                    if (p.executeUpdate() != 1) throw new SQLException("No se pudo actualizar el detalle de la venta.");
                }
                if (productoAnterior == productoId) {
                    // Para el mismo producto solo registra la diferencia neta de unidades.
                    guardarStock(c, productoId, stockAnteriorRepuesto - cantidad);
                    registrarMovimientoInventario(c, productoId, usuarioId, "AJUSTE_VENTA",
                            cantidadAnterior - cantidad, anterior.stock, stockAnteriorRepuesto - cantidad,
                            "Edición venta V" + ventaId);
                } else {
                    // Si cambió el producto, registra la devolución y el nuevo descuento por separado.
                    registrarMovimientoInventario(c, productoAnterior, usuarioId, "AJUSTE_VENTA",
                            cantidadAnterior, anterior.stock, stockAnteriorRepuesto, "Edición venta V" + ventaId);
                    guardarStock(c, productoId, nuevo.stock - cantidad);
                    registrarMovimientoInventario(c, productoId, usuarioId, "AJUSTE_VENTA",
                            -cantidad, nuevo.stock, nuevo.stock - cantidad, "Edición venta V" + ventaId);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo editar la venta.", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Convierte la presentación solicitada a unidades base sin confiar en factores enviados por el navegador. */
    private int convertirCantidadVenta(int cantidad, String unidadVenta, Producto producto) throws SQLException {
        if (cantidad < 1) throw new SQLException("La cantidad debe ser al menos una unidad.");
        int factor = switch (unidadVenta == null ? "" : unidadVenta) {
            case "base" -> 1;
            case "blister" -> producto.unidadesPorBlister;
            case "presentacion" -> producto.unidadesPorPresentacion;
            default -> 0;
        };
        if (factor < 1) throw new SQLException("La presentación elegida no está configurada para este medicamento.");
        long unidadesBase = (long) cantidad * factor;
        if (unidadesBase > Integer.MAX_VALUE) throw new SQLException("La cantidad de unidades supera el límite permitido.");
        return (int) unidadesBase;
    }

    /** Elimina una venta y devuelve las unidades a su producto respetando el historial. */
    public void eliminarVenta(int ventaId, int usuarioId) throws SQLException {
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                // Agrupa cantidades por producto para devolver todo el stock de la venta.
                Map<Integer, Integer> cantidades = new LinkedHashMap<>();
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT id_producto,cantidad FROM detalleventa WHERE id_venta=? FOR UPDATE")) {
                    p.setInt(1, ventaId);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) cantidades.merge(r.getInt("id_producto"), r.getInt("cantidad"), Integer::sum);
                    }
                }
                if (cantidades.isEmpty()) throw new SQLException("No se encontró el detalle de la venta.");
                for (Map.Entry<Integer, Integer> item : cantidades.entrySet()) {
                    // Repone las unidades y deja trazabilidad antes de borrar los detalles.
                    Producto producto = obtenerProducto(c, item.getKey());
                    int nuevoStock = producto.stock + item.getValue();
                    guardarStock(c, item.getKey(), nuevoStock);
                    registrarMovimientoInventario(c, item.getKey(), usuarioId, "ANULACION_VENTA",
                            item.getValue(), producto.stock, nuevoStock, "Anulación venta V" + ventaId);
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM detalleventa WHERE id_venta=?")) {
                    p.setInt(1, ventaId); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM venta WHERE idVenta=?")) {
                    p.setInt(1, ventaId);
                    if (p.executeUpdate() != 1) throw new SQLException("No se encontró la venta para eliminar.");
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo eliminar la venta.", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Guarda cabecera, varias líneas/lotes y existencias como una recepción indivisible. */
    public boolean registrarCompra(Date fecha, String proveedor, String numeroFactura, String archivoFactura,
            Double totalFactura, List<LineaCompra> lineas, int usuarioId) throws SQLException {
        // Valida la cabecera y el número permitido de líneas antes de abrir la transacción.
        if (fecha == null || proveedor == null || proveedor.isBlank() || numeroFactura == null
                || numeroFactura.isBlank() || lineas == null || lineas.isEmpty() || lineas.size() > 50) {
            throw new SQLException("Completa los datos de la factura y agrega al menos un producto.");
        }
        try (Connection c = Conexion.getConexion()) {
            // Exige una conexión activa para iniciar la recepción.
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            // Mantiene factura, productos, detalles y stock dentro de una transacción única.
            c.setAutoCommit(false);
            try {
                // Reutiliza el proveedor existente o crea uno nuevo para esta factura.
                int proveedorId = obtenerOCrearProveedor(c, proveedor.trim());
                // Busca si ya existe una factura con el mismo proveedor y número.
                try (PreparedStatement duplicado = c.prepareStatement(
                        "SELECT idCompra FROM compra WHERE id_proveedor=? AND numero_factura=? LIMIT 1 FOR UPDATE")) {
                    // Vincula proveedor y número de factura a la búsqueda de duplicados.
                    duplicado.setInt(1, proveedorId);
                    duplicado.setString(2, numeroFactura.trim());
                    // Rechaza la recepción si esa factura ya está guardada.
                    try (ResultSet r = duplicado.executeQuery()) {
                        if (r.next()) throw new SQLException("Esa factura ya fue registrada para este proveedor.");
                    }
                }
                // Inicializa el total calculado a partir de los costos y unidades recibidas.
                double total = 0;
                // Revisa cada línea y acumula su costo extendido.
                for (LineaCompra linea : lineas) {
                    // Valida unidades, costo, lote, vencimiento y datos de productos nuevos.
                    validarLinea(linea);
                    // Suma el costo total de la línea a la factura.
                    total += linea.getCosto() * linea.getCantidad();
                }
                // Rechaza totales ingresados negativos, infinitos o no numéricos.
                if (totalFactura != null && (!Double.isFinite(totalFactura) || totalFactura < 0)) {
                    throw new SQLException("El total de la factura no es válido.");
                }
                // Evita registrar un total que se desbordó durante la suma de líneas.
                if (!Double.isFinite(total)) throw new SQLException("El total calculado supera el límite permitido.");
                // Usa el total impreso en la factura cuando el usuario lo proporcionó.
                if (totalFactura != null) total = totalFactura;
                // Prepara la cabecera de compra con proveedor, usuario y datos del adjunto.
                String sqlCompra = "INSERT INTO compra (fecha,total,id_proveedor,id_usuario,numero_factura,archivo_factura) VALUES (?,?,?,?,?,?)";
                int compraId;
                try (PreparedStatement p = c.prepareStatement(sqlCompra, Statement.RETURN_GENERATED_KEYS)) {
                    // Asigna fecha, total, proveedor, usuario, número y ubicación del archivo.
                    p.setTimestamp(1, new java.sql.Timestamp(fecha.getTime())); p.setDouble(2, total);
                    p.setInt(3, proveedorId); p.setInt(4, usuarioId); p.setString(5, numeroFactura.trim());
                    // Inserta la cabecera y obtiene el ID para las líneas de detalle.
                    p.setString(6, archivoFactura); p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        // Comprueba que MySQL haya devuelto el número de compra.
                        if (!keys.next()) throw new SQLException("MySQL no devolvió el número de compra.");
                        // Conserva el ID para enlazar todos los detalles recibidos.
                        compraId = keys.getInt(1);
                    }
                }
                // Procesa cada línea para asignarla al producto y lote que recibió la farmacia.
                for (LineaCompra linea : lineas) {
                    // Resuelve o crea el registro de producto/lote donde sumará el stock.
                    int destinoId = prepararProductoRecibido(c, linea);
                    // Cambia el precio del producto/lote recibido solo cuando la persona lo confirmó.
                    if (linea.isActualizarPrecioVenta()) {
                        if (linea.getPrecioVenta() <= 0 || !Double.isFinite(linea.getPrecioVenta())) {
                            throw new SQLException("El precio público nuevo debe ser mayor que cero.");
                        }
                        try (PreparedStatement precio = c.prepareStatement(
                                "UPDATE producto p JOIN producto origen ON origen.idProducto=? "
                                + "SET p.precio_venta=? WHERE p.nombre=origen.nombre "
                                + "AND p.registro_invima <=> origen.registro_invima")) {
                            precio.setInt(1, destinoId);
                            precio.setDouble(2, linea.getPrecioVenta());
                            precio.executeUpdate();
                        }
                    }
                    // Vuelve a leer las existencias resultantes para completar la trazabilidad.
                    Producto recibido = obtenerProducto(c, destinoId);
                    // Registra la entrada con el saldo anterior, posterior y factura de referencia.
                    registrarMovimientoInventario(c, destinoId, usuarioId, "COMPRA", linea.getCantidad(),
                            recibido.stock - linea.getCantidad(), recibido.stock, "Factura " + numeroFactura.trim());
                    // Calcula el costo extendido de esta línea en unidades mínimas.
                    double subtotal = linea.getCosto() * linea.getCantidad();
                    // Prepara el detalle que conecta la factura con el producto recibido.
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO detallecompra (id_compra,id_producto,cantidad,costo_unitario,subtotal) VALUES (?,?,?,?,?)")) {
                        // Vincula IDs, unidades, costo y subtotal al detalle de compra.
                        p.setInt(1, compraId); p.setInt(2, destinoId); p.setInt(3, linea.getCantidad());
                        p.setDouble(4, linea.getCosto()); p.setDouble(5, subtotal); p.executeUpdate();
                    }
                }
                // Confirma la factura y todos sus efectos de inventario al finalizar las líneas.
                c.commit();
                // Informa que la recepción se completó correctamente.
                return true;
            } catch (Exception e) {
                // Deshace la factura completa si falla cualquier línea o ajuste de stock.
                c.rollback();
                // Mantiene disponible el detalle de errores SQL conocidos.
                if (e instanceof SQLException sql) throw sql;
                // Agrega contexto a cualquier error inesperado durante la recepción.
                throw new SQLException("No se pudo guardar la compra.", e);
            } finally {
                // Devuelve la conexión al modo de confirmación automática.
                c.setAutoCommit(true);
            }
        }
    }

    /** Comprueba límites de la base y el estado de recepción técnica de cada lote. */
    private void validarLinea(LineaCompra linea) throws SQLException {
        // Rechaza líneas vacías o con cantidad, costo, unidad, factor o lote fuera de rango.
        if (linea == null || linea.getCantidad() < 1 || linea.getCosto() < 0
                || linea.getUnidadBase() == null || !linea.getUnidadBase().matches("unidad|caja|paquete|tableta|capsula|sobre|ml|gota|ampolla|frasco")
                || linea.getUnidadesPorPresentacion() < 1
                || linea.getUnidadesPorBlister() < 0 || linea.getUnidadesPorBlister() > linea.getUnidadesPorPresentacion()
                || linea.getUnidadesPorBlister() > 0 && linea.getUnidadesPorPresentacion() % linea.getUnidadesPorBlister() != 0
                || !Double.isFinite(linea.getCosto()) || linea.getLote() != null && linea.getLote().length() > 30) {
            throw new SQLException("Revisa cantidad, costo y número de lote de cada producto.");
        }
        // No permite incorporar al stock disponible productos que ya están vencidos.
        if (linea.getVencimiento() != null && linea.getVencimiento().toLocalDate().isBefore(java.time.LocalDate.now())) {
            throw new SQLException("El lote indicado ya está vencido y no se puede ingresar como existencias disponibles.");
        }
        // Exige nombre y precio de venta cuando la línea crea un producto nuevo.
        if (linea.getProductoId() <= 0 && (linea.getNombreNuevo() == null || linea.getNombreNuevo().isBlank()
                || linea.getNombreNuevo().trim().length() > 100 || linea.getPrecioVenta() <= 0
                || !Double.isFinite(linea.getPrecioVenta()) || linea.getCategoria() == null
                || linea.getCategoria().isBlank() || linea.getCategoria().trim().length() > 50)) {
            throw new SQLException("Para un producto nuevo, indica nombre, grupo farmacológico y precio de venta.");
        }
    }

    /** Resuelve o crea el lote, y suma la cantidad recibida al inventario. */
    private int prepararProductoRecibido(Connection c, LineaCompra linea) throws SQLException {
        // Trata por separado productos nuevos porque no tienen todavía ID en el catálogo.
        if (linea.getProductoId() <= 0) {
            // Busca un producto nuevo ya creado con el mismo nombre, lote y vencimiento.
            int existente = buscarProductoLote(c, linea.getNombreNuevo(), linea.getLote(), linea.getVencimiento());
            if (existente > 0) {
                // Recupera la ficha existente y verifica que la presentación coincida.
                Producto base = obtenerProducto(c, existente);
                // Impide mezclar unidades mínimas o factores de empaque incompatibles.
                validarPresentacion(base, linea);
                // Suma las unidades recibidas al inventario del lote encontrado.
                guardarStock(c, existente, base.stock + linea.getCantidad());
                // Devuelve el ID reutilizado para enlazarlo al detalle de compra.
                return existente;
            }
            // Prepara una ficha nueva con la presentación indicada en la factura.
            String sql = "INSERT INTO producto (nombre,categoria,registro_invima,catalogo_medicamento_id,precio_venta,lote,fecha_vencimiento,unidad_base,unidades_por_presentacion,unidades_por_blister) VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                // Asigna nombre, precio y lote a la nueva ficha.
                p.setString(1, linea.getNombreNuevo().trim()); p.setString(2, linea.getCategoria().trim());
                p.setString(3, vacioANull(linea.getRegistroInvima()));
                if (linea.getCatalogoMedicamentoId() == null) p.setNull(4, java.sql.Types.BIGINT); else p.setLong(4, linea.getCatalogoMedicamentoId());
                p.setDouble(5, linea.getPrecioVenta()); p.setString(6, vacioANull(linea.getLote()));
                // Guarda la fecha como NULL si no se capturó un vencimiento.
                if (linea.getVencimiento() == null) p.setNull(7, java.sql.Types.DATE); else p.setDate(7, linea.getVencimiento());
                // Asigna unidad mínima y factor de presentación para conversiones futuras.
                p.setString(8, linea.getUnidadBase()); p.setInt(9, linea.getUnidadesPorPresentacion());
                p.setInt(10, linea.getUnidadesPorBlister());
                // Ejecuta la creación de la ficha del producto nuevo.
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    // Confirma que la base haya generado un ID válido para el producto.
                    if (!keys.next()) throw new SQLException("No se pudo crear el producto de la factura.");
                    // Lee el ID para crear su fila de inventario.
                    int nuevoId = keys.getInt(1);
                    // Registra las unidades recibidas inicialmente para este nuevo lote.
                    guardarStock(c, nuevoId, linea.getCantidad());
                    // Devuelve el ID que se usará en el detalle de compra.
                    return nuevoId;
                }
            }
        }

        // Recupera la ficha elegida en el catálogo y valida su presentación.
        Producto base = obtenerProducto(c, linea.getProductoId());
        // Evita registrar unidades recibidas con una presentación distinta a la ficha.
        validarPresentacion(base, linea);
        // Normaliza el lote ingresado y el lote actualmente asociado a la ficha.
        String lote = linea.getLote() == null ? "" : linea.getLote().trim();
        String loteActual = base.lote == null ? "" : base.lote.trim();
        // Crea o reutiliza otra fila de producto cuando la recepción corresponde a otro lote.
        if (!lote.isEmpty() && !lote.equalsIgnoreCase(loteActual)) {
            // Comprueba si ese nombre y lote ya se habían recibido con la misma fecha.
            int existente = buscarProductoLote(c, base.nombre, lote, linea.getVencimiento());
            if (existente > 0) {
                // Recupera el lote encontrado y confirma que su presentación sea compatible.
                Producto productoExistente = obtenerProducto(c, existente);
                validarPresentacion(productoExistente, linea);
                // Suma las nuevas unidades al lote ya registrado.
                guardarStock(c, existente, productoExistente.stock + linea.getCantidad());
                // Devuelve el ID de ese lote para guardar el detalle de compra.
                return existente;
            }
            // Clona los datos de catálogo y crea una fila aparte para conservar el lote nuevo.
            String crearLote = "INSERT INTO producto (nombre,categoria,registro_invima,catalogo_medicamento_id,descripcion,precio_venta,lote,fecha_vencimiento,unidad_base,unidades_por_presentacion,unidades_por_blister) "
                    + "SELECT nombre,categoria,registro_invima,catalogo_medicamento_id,descripcion,precio_venta,?,?,unidad_base,unidades_por_presentacion,unidades_por_blister FROM producto WHERE idProducto=?";
            try (PreparedStatement p = c.prepareStatement(crearLote, Statement.RETURN_GENERATED_KEYS)) {
                // Asigna el lote nuevo, su vencimiento y la ficha de origen que se clonará.
                p.setString(1, lote);
                if (linea.getVencimiento() == null) p.setNull(2, java.sql.Types.DATE); else p.setDate(2, linea.getVencimiento());
                p.setInt(3, linea.getProductoId());
                // Ejecuta la creación de la ficha separada para este lote.
                if (p.executeUpdate() != 1) throw new SQLException("No se pudo crear el lote recibido.");
                try (ResultSet keys = p.getGeneratedKeys()) {
                    // Comprueba que MySQL haya generado el ID de la nueva fila de lote.
                    if (!keys.next()) throw new SQLException("MySQL no devolvió el ID del lote recibido.");
                    // Guarda el ID para asociar las existencias recibidas.
                    int nuevoId = keys.getInt(1);
                    // Inicializa el stock de la nueva fila con la cantidad de la factura.
                    guardarStock(c, nuevoId, linea.getCantidad());
                    // Devuelve el producto-lote que se usará en el detalle de compra.
                    return nuevoId;
                }
            }
        }
        // Impide cambiar silenciosamente el vencimiento de un lote que ya existe.
        if (linea.getVencimiento() != null && base.vencimiento != null
                && !linea.getVencimiento().equals(base.vencimiento)) {
            throw new SQLException("La fecha no coincide con el lote ya registrado; revisa la recepción.");
        }
        // Completa el vencimiento de la ficha existente cuando antes no estaba informado.
        if (linea.getVencimiento() != null && base.vencimiento == null) {
            try (PreparedStatement p = c.prepareStatement("UPDATE producto SET fecha_vencimiento=? WHERE idProducto=?")) {
                p.setDate(1, linea.getVencimiento()); p.setInt(2, linea.getProductoId()); p.executeUpdate();
            }
        }
        // Aumenta el stock del mismo producto-lote cuando no hace falta crear otra fila.
        guardarStock(c, linea.getProductoId(), base.stock + linea.getCantidad());
        // Devuelve la ficha donde quedó registrada la recepción.
        return linea.getProductoId();
    }

    /** Evita mezclar presentaciones distintas dentro de una misma unidad de inventario. */
    private void validarPresentacion(Producto producto, LineaCompra linea) throws SQLException {
        // Exige igualdad en la unidad base y el factor para mantener inventarios comparables.
        if (!producto.unidadBase.equals(linea.getUnidadBase())
                || producto.unidadesPorPresentacion != linea.getUnidadesPorPresentacion()
                || producto.unidadesPorBlister != linea.getUnidadesPorBlister()) {
            throw new SQLException("La unidad o el contenido del paquete no coincide con la ficha del producto. Corrige la ficha antes de recibirlo.");
        }
    }

    /** Reutiliza el mismo producto/lote si varias filas de la factura se refieren a él. */
    private int buscarProductoLote(Connection c, String nombre, String lote, Date vencimiento) throws SQLException {
        // Busca una coincidencia de producto, lote y fecha, y bloquea la fila para actualizarla.
        String sql = "SELECT idProducto FROM producto WHERE LOWER(nombre)=LOWER(?) AND COALESCE(lote,'')=? "
                + "AND fecha_vencimiento <=> ? ORDER BY idProducto LIMIT 1 FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            // Vincula el nombre y lote normalizados a los parámetros de búsqueda.
            p.setString(1, nombre == null ? "" : nombre.trim());
            p.setString(2, lote == null ? "" : lote.trim());
            // Busca la fecha exacta o NULL según la recepción.
            if (vencimiento == null) p.setNull(3, java.sql.Types.DATE); else p.setDate(3, vencimiento);
            // Devuelve el primer ID encontrado o cero cuando el lote no existe.
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        }
    }

    /** Convierte un campo de lote vacío en NULL para respetar el catálogo existente. */
    private String vacioANull(String valor) {
        // Convierte lote ausente o vacío en NULL y recorta el valor cuando sí existe.
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Consulta el historial desde venta/detalleventa o compra/detallecompra. */
    public List<Map<String, Object>> listar(boolean ventas) throws SQLException {
        // Selecciona la consulta que corresponde al tipo de historial solicitado.
        String sql = ventas
                ? "SELECT v.idVenta AS id,v.fecha,v.total,COALESCE(c.nombre,'Ocasional') AS tercero,"
                    + "GROUP_CONCAT(DISTINCT p.nombre ORDER BY p.nombre SEPARATOR ', ') AS medicamento,"
                    + "SUM(d.cantidad) AS cantidad,MIN(d.id_producto) AS productoId,v.metodo_pago "
                    + "FROM venta v LEFT JOIN cliente c ON c.idCliente=v.id_cliente "
                    + "JOIN detalleventa d ON d.id_venta=v.idVenta JOIN producto p ON p.idProducto=d.id_producto "
                    + "GROUP BY v.idVenta,v.fecha,v.total,c.nombre,v.metodo_pago ORDER BY v.idVenta DESC"
                : "SELECT co.idCompra AS id,co.fecha,co.total,co.numero_factura,co.archivo_factura,pr.nombre AS tercero,"
                    + "GROUP_CONCAT(DISTINCT p.nombre ORDER BY p.nombre SEPARATOR ', ') AS medicamento,"
                    + "SUM(d.cantidad) AS cantidad FROM compra co LEFT JOIN proveedor pr ON pr.idProveedor=co.id_proveedor "
                    + "JOIN detallecompra d ON d.id_compra=co.idCompra JOIN producto p ON p.idProducto=d.id_producto "
                    + "GROUP BY co.idCompra,co.fecha,co.total,pr.nombre ORDER BY co.idCompra DESC";
        // Acumula las filas formateadas para que las páginas JSP puedan mostrarlas.
        List<Map<String, Object>> datos = new ArrayList<>();
        try (Connection c = Conexion.getConexion()) {
            // Evita presentar como vacío un historial que no se pudo consultar.
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                // Recorre cada compra o venta y prepara su representación para la vista.
                while (r.next()) {
                    // Crea el mapa con los nombres de campos que esperan las páginas.
                    Map<String, Object> fila = new LinkedHashMap<>();
                    // Copia los datos comunes a ventas y compras desde la fila SQL.
                    fila.put("id", r.getInt("id"));
                    fila.put("fecha", r.getTimestamp("fecha"));
                    fila.put("tercero", r.getString("tercero"));
                    fila.put("medicamento", r.getString("medicamento"));
                    fila.put("cantidad", r.getInt("cantidad"));
                    fila.put("total", r.getBigDecimal("total"));
                    // Añade datos de factura solamente cuando se listan compras.
                    if (!ventas) {
                        fila.put("numeroFactura", r.getString("numero_factura"));
                        fila.put("archivoFactura", r.getString("archivo_factura"));
                    } else {
                        // Expone los datos necesarios para el formulario de edición de una venta.
                        fila.put("productoId", r.getInt("productoId"));
                        fila.put("metodo", r.getString("metodo_pago"));
                    }
                    // Agrega la fila procesada a la colección de resultados.
                    datos.add(fila);
                }
            }
        }
        // Devuelve la lista completa de movimientos consultados.
        return datos;
    }

    /** Obtiene un adjunto de compra validando que siga dentro de la carpeta privada. */
    public Path buscarArchivoCompra(int compraId) throws SQLException {
        // Prepara la consulta para obtener la ruta de la factura de una compra.
        String sql = "SELECT archivo_factura FROM compra WHERE idCompra=?";
        try (Connection c = Conexion.getConexion()) {
            // Informa si no es posible consultar la ubicación del archivo.
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement(sql)) {
                // Vincula el ID recibido para buscar una compra específica.
                p.setInt(1, compraId);
                try (ResultSet r = p.executeQuery()) {
                    // Devuelve ausencia de archivo si la compra no existe o no tiene adjunto.
                    if (!r.next() || r.getString(1) == null) return null;
                    // Define la carpeta privada desde la que se permiten descargas.
                    Path carpeta = Path.of(System.getProperty("user.home"), "FarmaciaWeb", "facturas").toAbsolutePath().normalize();
                    // Normaliza la ruta almacenada para verificar que no salga de esa carpeta.
                    Path archivo = Path.of(r.getString(1)).toAbsolutePath().normalize();
                    // Devuelve solo rutas dentro del directorio seguro de facturas.
                    return archivo.startsWith(carpeta) ? archivo : null;
                }
            }
        }
    }

    /** Recupera cabecera y líneas de una factura para revisar sus datos guardados. */
    public Map<String, Object> buscarCompra(int compraId) throws SQLException {
        Map<String, Object> compra = new LinkedHashMap<>();
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement("SELECT co.idCompra,co.fecha,co.total,co.numero_factura,pr.nombre AS proveedor FROM compra co LEFT JOIN proveedor pr ON pr.idProveedor=co.id_proveedor WHERE co.idCompra=?")) {
                p.setInt(1, compraId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) return null;
                    compra.put("id", r.getInt("idCompra")); compra.put("fecha", r.getDate("fecha"));
                    compra.put("total", r.getBigDecimal("total")); compra.put("numeroFactura", r.getString("numero_factura"));
                    compra.put("proveedor", r.getString("proveedor"));
                }
            }
            List<Map<String, Object>> lineas = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT d.idDetalleCompra,d.id_producto,p.nombre,p.lote,p.fecha_vencimiento,d.cantidad,d.costo_unitario,d.subtotal FROM detallecompra d JOIN producto p ON p.idProducto=d.id_producto WHERE d.id_compra=? ORDER BY p.nombre")) {
                p.setInt(1, compraId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        Map<String, Object> linea = new LinkedHashMap<>();
                        linea.put("id", r.getInt("idDetalleCompra")); linea.put("productoId", r.getInt("id_producto"));
                        linea.put("nombre", r.getString("nombre")); linea.put("lote", r.getString("lote"));
                        linea.put("vencimiento", r.getDate("fecha_vencimiento")); linea.put("cantidad", r.getInt("cantidad"));
                        linea.put("costo", r.getBigDecimal("costo_unitario")); linea.put("subtotal", r.getBigDecimal("subtotal"));
                        lineas.add(linea);
                    }
                }
            }
            compra.put("lineas", lineas);
        }
        return compra;
    }

    /** Edita los datos de cabecera sin alterar las recepciones ya aplicadas al inventario. */
    public void actualizarCompra(int compraId, Date fecha, String numeroFactura, Double total) throws SQLException {
        if (fecha == null || numeroFactura == null || numeroFactura.isBlank() || numeroFactura.length() > 60
                || total == null || !Double.isFinite(total) || total < 0) throw new SQLException("Los datos de la factura no son válidos.");
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement("UPDATE compra SET fecha=?,numero_factura=?,total=? WHERE idCompra=?")) {
                p.setDate(1, fecha); p.setString(2, numeroFactura.trim()); p.setDouble(3, total); p.setInt(4, compraId);
                if (p.executeUpdate() != 1) throw new SQLException("No se encontró la factura para editar.");
            }
        }
    }

    /** Edita cabecera, cantidades y costos; ajusta el stock sin borrar el historial de recepción. */
    public void actualizarCompra(int compraId, Date fecha, String numeroFactura,
            Double totalFactura, int[] detalleIds, int[] cantidadesNuevas, double[] costosNuevos, int usuarioId) throws SQLException {
        if (compraId < 1 || fecha == null || numeroFactura == null || numeroFactura.isBlank()
                || numeroFactura.length() > 60 || detalleIds == null || detalleIds.length == 0
                || detalleIds.length != cantidadesNuevas.length || detalleIds.length != costosNuevos.length
                || totalFactura != null && (!Double.isFinite(totalFactura) || totalFactura < 0)) {
            throw new SQLException("Los datos de la factura no son válidos.");
        }
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                Map<Integer, int[]> originales = new LinkedHashMap<>();
                // Bloquea las líneas actuales para impedir que otra operación las edite a la vez.
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT idDetalleCompra,id_producto,cantidad FROM detallecompra WHERE id_compra=? FOR UPDATE")) {
                    p.setInt(1, compraId);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) originales.put(r.getInt("idDetalleCompra"),
                                new int[]{r.getInt("id_producto"), r.getInt("cantidad")});
                    }
                }
                if (originales.isEmpty() || originales.size() != detalleIds.length) {
                    throw new SQLException("Las líneas de la factura cambiaron; vuelve a cargarla e intenta otra vez.");
                }
                double total = 0;
                // Comprueba que se envió cada línea exactamente una vez y aplica sus diferencias.
                java.util.Set<Integer> enviados = new java.util.HashSet<>();
                for (int i = 0; i < detalleIds.length; i++) {
                    int detalleId = detalleIds[i];
                    int cantidad = cantidadesNuevas[i];
                    double costo = costosNuevos[i];
                    int[] original = originales.get(detalleId);
                    if (original == null || !enviados.add(detalleId) || cantidad < 1
                            || !Double.isFinite(costo) || costo < 0) {
                        throw new SQLException("Revisa cantidades y costos de las líneas.");
                    }
                    Producto producto = obtenerProducto(c, original[0]);
                    int delta = cantidad - original[1];
                    int stockNuevo = producto.stock + delta;
                    // Impide retirar del inventario más unidades de las que quedan disponibles.
                    if (stockNuevo < 0) {
                        throw new SQLException("No se puede reducir esta compra: esas unidades ya no están disponibles en inventario.");
                    }
                    if (delta != 0) {
                        guardarStock(c, original[0], stockNuevo);
                        registrarMovimientoInventario(c, original[0], usuarioId, "AJUSTE_COMPRA", delta,
                                producto.stock, stockNuevo, "Edición compra " + compraId);
                    }
                    double subtotal = cantidad * costo;
                    // Acumula el total por líneas; el total de factura puede incluir otros cargos.
                    total += subtotal;
                    if (!Double.isFinite(subtotal) || !Double.isFinite(total)) {
                        throw new SQLException("El total de la factura supera el límite permitido.");
                    }
                    try (PreparedStatement p = c.prepareStatement(
                            "UPDATE detallecompra SET cantidad=?,costo_unitario=?,subtotal=? WHERE idDetalleCompra=? AND id_compra=?")) {
                        p.setInt(1, cantidad); p.setDouble(2, costo); p.setDouble(3, subtotal);
                        p.setInt(4, detalleId); p.setInt(5, compraId);
                        if (p.executeUpdate() != 1) throw new SQLException("No se pudo actualizar una línea de la factura.");
                    }
                }
                if (totalFactura != null) total = totalFactura;
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE compra SET fecha=?,numero_factura=?,total=? WHERE idCompra=?")) {
                    p.setDate(1, fecha); p.setString(2, numeroFactura.trim()); p.setDouble(3, total); p.setInt(4, compraId);
                    if (p.executeUpdate() != 1) throw new SQLException("No se encontró la factura para editar.");
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo editar la factura.", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Elimina factura y revierte sus existencias solo cuando el stock disponible permite hacerlo. */
    public void eliminarCompra(int compraId, int usuarioId) throws SQLException {
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                Map<Integer, Integer> cantidades = new LinkedHashMap<>();
                try (PreparedStatement p = c.prepareStatement("SELECT id_producto,cantidad FROM detallecompra WHERE id_compra=? FOR UPDATE")) {
                    p.setInt(1, compraId);
                    try (ResultSet r = p.executeQuery()) { while (r.next()) cantidades.merge(r.getInt("id_producto"), r.getInt("cantidad"), Integer::sum); }
                }
                if (cantidades.isEmpty()) throw new SQLException("No se encontró el detalle de esa factura.");
                for (Map.Entry<Integer, Integer> item : cantidades.entrySet()) {
                    Producto producto = obtenerProducto(c, item.getKey());
                    int cantidad = item.getValue();
                    if (producto.stock < cantidad) throw new SQLException("No se puede eliminar: parte de los productos de esta factura ya no está disponible en inventario.");
                    guardarStock(c, item.getKey(), producto.stock - cantidad);
                    registrarMovimientoInventario(c, item.getKey(), usuarioId, "ANULACION", -cantidad,
                            producto.stock, producto.stock - cantidad, "Anulación compra " + compraId);
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM detallecompra WHERE id_compra=?")) { p.setInt(1, compraId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM compra WHERE idCompra=?")) {
                    p.setInt(1, compraId);
                    if (p.executeUpdate() != 1) throw new SQLException("No se encontró la factura para eliminar.");
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo eliminar la factura.", e);
            } finally { c.setAutoCommit(true); }
        }
    }

    /** Recupera nombre, lote, precio y existencias con bloqueo para evitar sobreventas. */
    private Producto obtenerProducto(Connection c, int id) throws SQLException {
        // Consulta ficha y stock consolidado, bloqueando la fila durante la transacción.
        String sql = "SELECT p.nombre,p.lote,p.fecha_vencimiento,p.precio_venta,p.unidad_base,p.unidades_por_presentacion,p.unidades_por_blister,COALESCE(SUM(i.cantidad),0) AS stock "
                + "FROM producto p LEFT JOIN inventario i ON i.id_producto=p.idProducto "
                + "WHERE p.idProducto=? GROUP BY p.idProducto,p.nombre,p.lote,p.fecha_vencimiento,p.precio_venta,p.unidad_base,p.unidades_por_presentacion,p.unidades_por_blister FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            // Limita la consulta al producto solicitado.
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                // Detiene la operación si el producto fue eliminado o no existe.
                if (!r.next()) throw new SQLException("El producto ya no existe.");
                // Empaqueta los datos consultados para usarlos sin repetir consultas.
                return new Producto(r.getString("nombre"), r.getString("lote"), r.getDate("fecha_vencimiento"),
                        r.getDouble("precio_venta"), r.getInt("stock"), r.getString("unidad_base"), r.getInt("unidades_por_presentacion"), r.getInt("unidades_por_blister"));
            }
        }
    }

    /** Encuentra el cliente por nombre o lo registra para vincular la venta. */
    private int obtenerOCrearCliente(Connection c, String nombre) throws SQLException {
        // Usa cero para indicar que la venta no debe relacionarse con un cliente.
        if (nombre == null || nombre.isBlank()) return 0;
        // Busca clientes existentes ignorando diferencias de mayúsculas.
        try (PreparedStatement p = c.prepareStatement("SELECT idCliente FROM cliente WHERE LOWER(nombre)=LOWER(?) ORDER BY idCliente LIMIT 1 FOR UPDATE")) {
            // Asigna el nombre normalizado al parámetro de búsqueda.
            p.setString(1, nombre.trim());
            // Reutiliza el ID encontrado para evitar crear clientes repetidos.
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        // Prepara la creación del cliente cuando la búsqueda no encuentra coincidencias.
        try (PreparedStatement p = c.prepareStatement("INSERT INTO cliente (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            // Inserta el nombre y solicita el ID asignado por MySQL.
            p.setString(1, nombre.trim()); p.executeUpdate();
            // Devuelve el nuevo ID para guardarlo en la cabecera de venta.
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        // Informa que el registro del cliente no produjo un identificador válido.
        throw new SQLException("No se pudo registrar el cliente.");
    }

    /** Reutiliza proveedores por nombre y solo crea uno cuando aún no existe. */
    private int obtenerOCrearProveedor(Connection c, String nombre) throws SQLException {
        // Busca proveedores por nombre para reutilizar una ficha ya existente.
        try (PreparedStatement p = c.prepareStatement("SELECT idProveedor FROM proveedor WHERE LOWER(nombre)=LOWER(?) ORDER BY idProveedor LIMIT 1 FOR UPDATE")) {
            // Vincula el nombre del proveedor a la consulta protegida.
            p.setString(1, nombre);
            // Devuelve el ID existente si la búsqueda tiene resultados.
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        // Prepara una fila nueva cuando todavía no hay proveedor con ese nombre.
        try (PreparedStatement p = c.prepareStatement("INSERT INTO proveedor (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            // Inserta el proveedor y ejecuta la consulta de creación.
            p.setString(1, nombre); p.executeUpdate();
            // Devuelve el nuevo ID para asociarlo a la compra.
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        // Informa que la creación no devolvió una clave utilizable.
        throw new SQLException("No se pudo registrar el proveedor.");
    }

    /** Reemplaza las existencias sin perder el punto de reorden configurado. */
    private void guardarStock(Connection c, int productoId, int cantidad) throws SQLException {
        // Bloquea filas del producto para que otras transacciones no sobrescriban el stock.
        try (PreparedStatement p = c.prepareStatement("SELECT idInventario FROM inventario WHERE id_producto=? ORDER BY idInventario FOR UPDATE")) {
            // Limita el bloqueo al producto que está recibiendo o descontando existencias.
            p.setInt(1, productoId);
            try (ResultSet r = p.executeQuery()) {
                // Actualiza la primera fila y elimina duplicados si el producto ya tiene inventario.
                if (r.next()) {
                    // Conserva el identificador de la fila principal de stock.
                    int idInventario = r.getInt(1);
                    // Prepara la actualización de cantidad sin alterar el punto de reorden.
                    try (PreparedStatement update = c.prepareStatement("UPDATE inventario SET cantidad=? WHERE idInventario=?")) {
                        // Escribe el saldo nuevo en la fila principal.
                        update.setInt(1, cantidad); update.setInt(2, idInventario); update.executeUpdate();
                    }
                    // Prepara la eliminación de cualquier fila duplicada del mismo producto.
                    try (PreparedStatement borrar = c.prepareStatement("DELETE FROM inventario WHERE id_producto=? AND idInventario<>?")) {
                        // Conserva solamente el registro que se actualizó arriba.
                        borrar.setInt(1, productoId); borrar.setInt(2, idInventario); borrar.executeUpdate();
                    }
                    // Termina porque el inventario existente ya quedó actualizado.
                    return;
                }
            }
        }
        // Crea la fila inicial de inventario con un punto de reorden predeterminado.
        try (PreparedStatement p = c.prepareStatement("INSERT INTO inventario (id_producto,cantidad,punto_reorden) VALUES (?,?,10)")) {
            p.setInt(1, productoId); p.setInt(2, cantidad); p.executeUpdate();
        }
    }

    /** Inserta el evento de trazabilidad dentro de la misma transacción que cambia el stock. */
    private void registrarMovimientoInventario(Connection c, int productoId, int usuarioId, String tipo,
            int cantidad, int anterior, int nuevo, String referencia) throws SQLException {
        // Inserta el movimiento y guarda una copia del nombre del usuario para el historial.
        String sql = "INSERT INTO movimiento_inventario (id_producto,id_usuario,nombre_usuario,tipo,cantidad,stock_anterior,stock_nuevo,referencia) "
                + "SELECT ?,u.idUsuario,COALESCE(u.nombre,CONCAT('Usuario ',u.idUsuario)),?,?,?,?,? FROM usuario u WHERE u.idUsuario=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            // Vincula producto, tipo, cambio, saldos y referencia a la consulta.
            p.setInt(1, productoId); p.setString(2, tipo); p.setInt(3, cantidad);
            p.setInt(4, anterior); p.setInt(5, nuevo); p.setString(6, referencia); p.setInt(7, usuarioId);
            // Exige que el historial haya guardado exactamente un movimiento.
            if (p.executeUpdate() != 1) throw new SQLException("No se pudo registrar la trazabilidad del inventario.");
        }
    }

    /** Devuelve movimientos recientes con producto, lote, unidad, saldos y usuario para la pantalla. */
    public List<Map<String, Object>> listarMovimientosInventario(int limite) throws SQLException {
        // Consulta cambios recientes junto con el nombre, lote y unidad del producto.
        String sql = "SELECT m.fecha,m.tipo,m.cantidad,m.stock_anterior,m.stock_nuevo,m.referencia,m.nombre_usuario,"
                + "p.nombre,p.lote,p.unidad_base FROM movimiento_inventario m JOIN producto p ON p.idProducto=m.id_producto ORDER BY m.fecha DESC,m.id_movimiento DESC LIMIT ?";
        // Prepara la lista de resultados para que la vista muestre cada movimiento.
        List<Map<String, Object>> datos = new ArrayList<>();
        try (Connection c = Conexion.getConexion()) {
            // Informa el problema si no se puede consultar la trazabilidad.
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement(sql)) {
                // Limita el rango de resultados entre 1 y 500 filas.
                p.setInt(1, Math.max(1, Math.min(limite, 500)));
                try (ResultSet r = p.executeQuery()) {
                    // Convierte cada registro de trazabilidad en un mapa para la JSP.
                    while (r.next()) {
                        // Crea la fila de salida para el movimiento actual.
                        Map<String, Object> fila = new LinkedHashMap<>();
                        // Copia sus datos desde el resultado SQL al formato esperado por la vista.
                        fila.put("fecha", r.getTimestamp("fecha")); fila.put("tipo", r.getString("tipo"));
                        fila.put("cantidad", r.getInt("cantidad")); fila.put("anterior", r.getInt("stock_anterior"));
                        fila.put("nuevo", r.getInt("stock_nuevo")); fila.put("referencia", r.getString("referencia"));
                        fila.put("usuario", r.getString("nombre_usuario")); fila.put("nombre", r.getString("nombre"));
                        fila.put("lote", r.getString("lote")); fila.put("unidad", r.getString("unidad_base"));
                        // Agrega el movimiento ya convertido a la lista de resultados.
                        datos.add(fila);
                    }
                }
            }
        }
        // Devuelve los movimientos más recientes dentro del límite solicitado.
        return datos;
    }

    /** Estructura interna para reutilizar los valores bloqueados del producto. */
    private static final class Producto {
        private final String nombre;
        private final String lote;
        private final Date vencimiento;
        private final double precio;
        private final int stock;
        private final String unidadBase;
        private final int unidadesPorPresentacion;
        private final int unidadesPorBlister;
        // Construye el snapshot del producto usado dentro de la transacción actual.
        private Producto(String nombre, String lote, Date vencimiento, double precio, int stock, String unidadBase, int unidadesPorPresentacion, int unidadesPorBlister) {
            // Copia los datos de producto que requieren las reglas de venta y recepción.
            this.nombre = nombre; this.lote = lote; this.vencimiento = vencimiento; this.precio = precio; this.stock = stock;
            this.unidadBase = unidadBase; this.unidadesPorPresentacion = unidadesPorPresentacion;
            this.unidadesPorBlister = unidadesPorBlister;
        }
    }
}
