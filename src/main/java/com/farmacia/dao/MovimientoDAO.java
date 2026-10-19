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
            int cantidad, String metodo, int usuarioId) throws SQLException {
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                Producto producto = obtenerProducto(c, productoId);
                if (cantidad < 1 || cantidad > producto.stock) throw new SQLException("Stock insuficiente.");
                if (producto.vencimiento != null && producto.vencimiento.toLocalDate().isBefore(java.time.LocalDate.now())) {
                    throw new SQLException("No se puede vender un producto vencido.");
                }
                int clienteId = obtenerOCrearCliente(c, cliente);
                double subtotal = producto.precio * cantidad;
                String venta = "INSERT INTO venta (fecha,total,id_usuario,id_cliente,metodo_pago) VALUES (?,?,?,?,?)";
                int ventaId;
                try (PreparedStatement p = c.prepareStatement(venta, Statement.RETURN_GENERATED_KEYS)) {
                    p.setTimestamp(1, new java.sql.Timestamp(fecha.getTime()));
                    p.setDouble(2, subtotal);
                    p.setInt(3, usuarioId);
                    if (clienteId == 0) p.setNull(4, java.sql.Types.INTEGER); else p.setInt(4, clienteId);
                    p.setString(5, metodo);
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("MySQL no devolvió el número de venta.");
                        ventaId = keys.getInt(1);
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO detalleventa (id_venta,id_producto,cantidad,precio_unitario,subtotal) VALUES (?,?,?,?,?)")) {
                    p.setInt(1, ventaId); p.setInt(2, productoId); p.setInt(3, cantidad);
                    p.setDouble(4, producto.precio); p.setDouble(5, subtotal); p.executeUpdate();
                }
                guardarStock(c, productoId, producto.stock - cantidad);
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo guardar la venta.", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Guarda cabecera, varias líneas/lotes y existencias como una recepción indivisible. */
    public boolean registrarCompra(Date fecha, String proveedor, String numeroFactura, String archivoFactura,
            Double totalFactura, List<LineaCompra> lineas, int usuarioId) throws SQLException {
        if (fecha == null || proveedor == null || proveedor.isBlank() || numeroFactura == null
                || numeroFactura.isBlank() || lineas == null || lineas.isEmpty() || lineas.size() > 50) {
            throw new SQLException("Completa los datos de la factura y agrega al menos un producto.");
        }
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            c.setAutoCommit(false);
            try {
                int proveedorId = obtenerOCrearProveedor(c, proveedor.trim());
                try (PreparedStatement duplicado = c.prepareStatement(
                        "SELECT idCompra FROM compra WHERE id_proveedor=? AND numero_factura=? LIMIT 1 FOR UPDATE")) {
                    duplicado.setInt(1, proveedorId);
                    duplicado.setString(2, numeroFactura.trim());
                    try (ResultSet r = duplicado.executeQuery()) {
                        if (r.next()) throw new SQLException("Esa factura ya fue registrada para este proveedor.");
                    }
                }
                double total = 0;
                for (LineaCompra linea : lineas) {
                    validarLinea(linea);
                    total += linea.getCosto() * linea.getCantidad();
                }
                if (totalFactura != null && (!Double.isFinite(totalFactura) || totalFactura < 0)) {
                    throw new SQLException("El total de la factura no es válido.");
                }
                if (!Double.isFinite(total)) throw new SQLException("El total calculado supera el límite permitido.");
                if (totalFactura != null) total = totalFactura;
                String sqlCompra = "INSERT INTO compra (fecha,total,id_proveedor,id_usuario,numero_factura,archivo_factura) VALUES (?,?,?,?,?,?)";
                int compraId;
                try (PreparedStatement p = c.prepareStatement(sqlCompra, Statement.RETURN_GENERATED_KEYS)) {
                    p.setTimestamp(1, new java.sql.Timestamp(fecha.getTime())); p.setDouble(2, total);
                    p.setInt(3, proveedorId); p.setInt(4, usuarioId); p.setString(5, numeroFactura.trim());
                    p.setString(6, archivoFactura); p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("MySQL no devolvió el número de compra.");
                        compraId = keys.getInt(1);
                    }
                }
                for (LineaCompra linea : lineas) {
                    int destinoId = prepararProductoRecibido(c, linea);
                    double subtotal = linea.getCosto() * linea.getCantidad();
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO detallecompra (id_compra,id_producto,cantidad,costo_unitario,subtotal) VALUES (?,?,?,?,?)")) {
                        p.setInt(1, compraId); p.setInt(2, destinoId); p.setInt(3, linea.getCantidad());
                        p.setDouble(4, linea.getCosto()); p.setDouble(5, subtotal); p.executeUpdate();
                    }
                }
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("No se pudo guardar la compra.", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Comprueba límites de la base y el estado de recepción técnica de cada lote. */
    private void validarLinea(LineaCompra linea) throws SQLException {
        if (linea == null || linea.getCantidad() < 1 || linea.getCosto() < 0
                || !Double.isFinite(linea.getCosto()) || linea.getLote() != null && linea.getLote().length() > 30) {
            throw new SQLException("Revisa cantidad, costo y número de lote de cada producto.");
        }
        if (linea.getVencimiento() != null && linea.getVencimiento().toLocalDate().isBefore(java.time.LocalDate.now())) {
            throw new SQLException("El lote indicado ya está vencido y no se puede ingresar como existencias disponibles.");
        }
        if (linea.getProductoId() <= 0 && (linea.getNombreNuevo() == null || linea.getNombreNuevo().isBlank()
                || linea.getNombreNuevo().trim().length() > 100 || linea.getPrecioVenta() <= 0
                || !Double.isFinite(linea.getPrecioVenta()))) {
            throw new SQLException("Para un producto nuevo, indica nombre y precio de venta.");
        }
    }

    /** Resuelve o crea el lote, y suma la cantidad recibida al inventario. */
    private int prepararProductoRecibido(Connection c, LineaCompra linea) throws SQLException {
        if (linea.getProductoId() <= 0) {
            int existente = buscarProductoLote(c, linea.getNombreNuevo(), linea.getLote(), linea.getVencimiento());
            if (existente > 0) {
                Producto base = obtenerProducto(c, existente);
                guardarStock(c, existente, base.stock + linea.getCantidad());
                return existente;
            }
            String sql = "INSERT INTO producto (nombre,precio_venta,lote,fecha_vencimiento) VALUES (?,?,?,?)";
            try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, linea.getNombreNuevo().trim()); p.setDouble(2, linea.getPrecioVenta());
                p.setString(3, vacioANull(linea.getLote()));
                if (linea.getVencimiento() == null) p.setNull(4, java.sql.Types.DATE); else p.setDate(4, linea.getVencimiento());
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No se pudo crear el producto de la factura.");
                    int nuevoId = keys.getInt(1);
                    guardarStock(c, nuevoId, linea.getCantidad());
                    return nuevoId;
                }
            }
        }

        Producto base = obtenerProducto(c, linea.getProductoId());
        String lote = linea.getLote() == null ? "" : linea.getLote().trim();
        String loteActual = base.lote == null ? "" : base.lote.trim();
        if (!lote.isEmpty() && !lote.equalsIgnoreCase(loteActual)) {
            int existente = buscarProductoLote(c, base.nombre, lote, linea.getVencimiento());
            if (existente > 0) {
                Producto productoExistente = obtenerProducto(c, existente);
                guardarStock(c, existente, productoExistente.stock + linea.getCantidad());
                return existente;
            }
            String crearLote = "INSERT INTO producto (nombre,categoria,descripcion,precio_venta,lote,fecha_vencimiento) "
                    + "SELECT nombre,categoria,descripcion,precio_venta,?,? FROM producto WHERE idProducto=?";
            try (PreparedStatement p = c.prepareStatement(crearLote, Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, lote);
                if (linea.getVencimiento() == null) p.setNull(2, java.sql.Types.DATE); else p.setDate(2, linea.getVencimiento());
                p.setInt(3, linea.getProductoId());
                if (p.executeUpdate() != 1) throw new SQLException("No se pudo crear el lote recibido.");
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("MySQL no devolvió el ID del lote recibido.");
                    int nuevoId = keys.getInt(1);
                    guardarStock(c, nuevoId, linea.getCantidad());
                    return nuevoId;
                }
            }
        }
        if (linea.getVencimiento() != null && base.vencimiento != null
                && !linea.getVencimiento().equals(base.vencimiento)) {
            throw new SQLException("La fecha no coincide con el lote ya registrado; revisa la recepción.");
        }
        if (linea.getVencimiento() != null && base.vencimiento == null) {
            try (PreparedStatement p = c.prepareStatement("UPDATE producto SET fecha_vencimiento=? WHERE idProducto=?")) {
                p.setDate(1, linea.getVencimiento()); p.setInt(2, linea.getProductoId()); p.executeUpdate();
            }
        }
        guardarStock(c, linea.getProductoId(), base.stock + linea.getCantidad());
        return linea.getProductoId();
    }

    /** Reutiliza el mismo producto/lote si varias filas de la factura se refieren a él. */
    private int buscarProductoLote(Connection c, String nombre, String lote, Date vencimiento) throws SQLException {
        String sql = "SELECT idProducto FROM producto WHERE LOWER(nombre)=LOWER(?) AND COALESCE(lote,'')=? "
                + "AND fecha_vencimiento <=> ? ORDER BY idProducto LIMIT 1 FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, nombre == null ? "" : nombre.trim());
            p.setString(2, lote == null ? "" : lote.trim());
            if (vencimiento == null) p.setNull(3, java.sql.Types.DATE); else p.setDate(3, vencimiento);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        }
    }

    /** Convierte un campo de lote vacío en NULL para respetar el catálogo existente. */
    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Consulta el historial desde venta/detalleventa o compra/detallecompra. */
    public List<Map<String, Object>> listar(boolean ventas) throws SQLException {
        String sql = ventas
                ? "SELECT v.idVenta AS id,v.fecha,v.total,COALESCE(c.nombre,'Ocasional') AS tercero,"
                    + "GROUP_CONCAT(DISTINCT p.nombre ORDER BY p.nombre SEPARATOR ', ') AS medicamento,"
                    + "SUM(d.cantidad) AS cantidad FROM venta v LEFT JOIN cliente c ON c.idCliente=v.id_cliente "
                    + "JOIN detalleventa d ON d.id_venta=v.idVenta JOIN producto p ON p.idProducto=d.id_producto "
                    + "GROUP BY v.idVenta,v.fecha,v.total,c.nombre ORDER BY v.idVenta DESC"
                : "SELECT co.idCompra AS id,co.fecha,co.total,co.numero_factura,co.archivo_factura,pr.nombre AS tercero,"
                    + "GROUP_CONCAT(DISTINCT p.nombre ORDER BY p.nombre SEPARATOR ', ') AS medicamento,"
                    + "SUM(d.cantidad) AS cantidad FROM compra co LEFT JOIN proveedor pr ON pr.idProveedor=co.id_proveedor "
                    + "JOIN detallecompra d ON d.id_compra=co.idCompra JOIN producto p ON p.idProducto=d.id_producto "
                    + "GROUP BY co.idCompra,co.fecha,co.total,pr.nombre ORDER BY co.idCompra DESC";
        List<Map<String, Object>> datos = new ArrayList<>();
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("id", r.getInt("id"));
                    fila.put("fecha", r.getTimestamp("fecha"));
                    fila.put("tercero", r.getString("tercero"));
                    fila.put("medicamento", r.getString("medicamento"));
                    fila.put("cantidad", r.getInt("cantidad"));
                    fila.put("total", r.getBigDecimal("total"));
                    if (!ventas) {
                        fila.put("numeroFactura", r.getString("numero_factura"));
                        fila.put("archivoFactura", r.getString("archivo_factura"));
                    }
                    datos.add(fila);
                }
            }
        }
        return datos;
    }

    /** Obtiene un adjunto de compra validando que siga dentro de la carpeta privada. */
    public Path buscarArchivoCompra(int compraId) throws SQLException {
        String sql = "SELECT archivo_factura FROM compra WHERE idCompra=?";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No hay conexión con MySQL.");
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setInt(1, compraId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || r.getString(1) == null) return null;
                    Path carpeta = Path.of(System.getProperty("user.home"), "FarmaciaWeb", "facturas").toAbsolutePath().normalize();
                    Path archivo = Path.of(r.getString(1)).toAbsolutePath().normalize();
                    return archivo.startsWith(carpeta) ? archivo : null;
                }
            }
        }
    }

    /** Recupera nombre, lote, precio y existencias con bloqueo para evitar sobreventas. */
    private Producto obtenerProducto(Connection c, int id) throws SQLException {
        String sql = "SELECT p.nombre,p.lote,p.fecha_vencimiento,p.precio_venta,COALESCE(SUM(i.cantidad),0) AS stock "
                + "FROM producto p LEFT JOIN inventario i ON i.id_producto=p.idProducto "
                + "WHERE p.idProducto=? GROUP BY p.idProducto,p.nombre,p.lote,p.fecha_vencimiento,p.precio_venta FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("El producto ya no existe.");
                return new Producto(r.getString("nombre"), r.getString("lote"), r.getDate("fecha_vencimiento"),
                        r.getDouble("precio_venta"), r.getInt("stock"));
            }
        }
    }

    /** Encuentra el cliente por nombre o lo registra para vincular la venta. */
    private int obtenerOCrearCliente(Connection c, String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) return 0;
        try (PreparedStatement p = c.prepareStatement("SELECT idCliente FROM cliente WHERE LOWER(nombre)=LOWER(?) ORDER BY idCliente LIMIT 1 FOR UPDATE")) {
            p.setString(1, nombre.trim());
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO cliente (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, nombre.trim()); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        throw new SQLException("No se pudo registrar el cliente.");
    }

    /** Reutiliza proveedores por nombre y solo crea uno cuando aún no existe. */
    private int obtenerOCrearProveedor(Connection c, String nombre) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT idProveedor FROM proveedor WHERE LOWER(nombre)=LOWER(?) ORDER BY idProveedor LIMIT 1 FOR UPDATE")) {
            p.setString(1, nombre);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO proveedor (nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, nombre); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        throw new SQLException("No se pudo registrar el proveedor.");
    }

    /** Reemplaza las existencias sin perder el punto de reorden configurado. */
    private void guardarStock(Connection c, int productoId, int cantidad) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT idInventario FROM inventario WHERE id_producto=? ORDER BY idInventario FOR UPDATE")) {
            p.setInt(1, productoId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    int idInventario = r.getInt(1);
                    try (PreparedStatement update = c.prepareStatement("UPDATE inventario SET cantidad=? WHERE idInventario=?")) {
                        update.setInt(1, cantidad); update.setInt(2, idInventario); update.executeUpdate();
                    }
                    try (PreparedStatement borrar = c.prepareStatement("DELETE FROM inventario WHERE id_producto=? AND idInventario<>?")) {
                        borrar.setInt(1, productoId); borrar.setInt(2, idInventario); borrar.executeUpdate();
                    }
                    return;
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO inventario (id_producto,cantidad,punto_reorden) VALUES (?,?,10)")) {
            p.setInt(1, productoId); p.setInt(2, cantidad); p.executeUpdate();
        }
    }

    /** Estructura interna para reutilizar los valores bloqueados del producto. */
    private static final class Producto {
        private final String nombre;
        private final String lote;
        private final Date vencimiento;
        private final double precio;
        private final int stock;
        private Producto(String nombre, String lote, Date vencimiento, double precio, int stock) {
            this.nombre = nombre; this.lote = lote; this.vencimiento = vencimiento; this.precio = precio; this.stock = stock;
        }
    }
}
