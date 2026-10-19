package com.farmacia.dao;

import com.farmacia.modelo.medicamento;
import com.farmacia.util.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso al catálogo de productos y sus existencias del esquema real de farmacia_db. */
public class MedicamentoDAO {

    /** Inserta producto y existencias juntos para que nunca quede un alta incompleta. */
    public boolean insertar(medicamento m) {
        if (!datosValidos(m)) return false;
        String producto = "INSERT INTO producto (nombre,lote,fecha_vencimiento,precio_venta) VALUES (?,?,?,?)";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) return false;
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(producto, Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, m.getNombre().trim());
                p.setString(2, m.getLote().trim());
                p.setDate(3, java.sql.Date.valueOf(m.getFechaVencimiento()));
                p.setDouble(4, m.getPrecio());
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("MySQL no devolvió el ID del producto nuevo.");
                    guardarStock(c, keys.getInt(1), m.getStock());
                }
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                System.err.println("Error insertando medicamento: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error conectando al guardar medicamento: " + e.getMessage());
            return false;
        }
    }

    /** Actualiza la ficha del producto y normaliza su cantidad en inventario. */
    public boolean actualizar(medicamento m) {
        if (!datosValidos(m) || m.getIdMedicamento() < 1) return false;
        String sql = "UPDATE producto SET nombre=?,lote=?,fecha_vencimiento=?,precio_venta=? WHERE idProducto=?";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) return false;
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, m.getNombre().trim());
                p.setString(2, m.getLote().trim());
                p.setDate(3, java.sql.Date.valueOf(m.getFechaVencimiento()));
                p.setDouble(4, m.getPrecio());
                p.setInt(5, m.getIdMedicamento());
                if (p.executeUpdate() != 1) throw new SQLException("El medicamento ya no existe.");
                guardarStock(c, m.getIdMedicamento(), m.getStock());
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                System.err.println("Error actualizando medicamento: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error conectando al actualizar medicamento: " + e.getMessage());
            return false;
        }
    }

    /** Lista el catálogo aunque aún falte una fila de inventario para un producto. */
    public List<medicamento> listar() {
        List<medicamento> lista = new ArrayList<>();
        String sql = "SELECT p.idProducto,p.nombre,p.lote,p.fecha_vencimiento,p.precio_venta,"
                + "COALESCE(i.stock,0) AS stock FROM producto p "
                + "LEFT JOIN (SELECT id_producto,SUM(COALESCE(cantidad,0)) AS stock "
                + "FROM inventario GROUP BY id_producto) i ON i.id_producto=p.idProducto ORDER BY p.nombre,p.lote";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) return lista;
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    medicamento m = new medicamento();
                    m.setIdMedicamento(r.getInt("idProducto"));
                    m.setNombre(r.getString("nombre"));
                    m.setLote(r.getString("lote"));
                    java.sql.Date vencimiento = r.getDate("fecha_vencimiento");
                    m.setFechaVencimiento(vencimiento == null ? "" : vencimiento.toString());
                    m.setStock(r.getInt("stock"));
                    m.setPrecio(r.getDouble("precio_venta"));
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listando medicamentos: " + e.getMessage());
        }
        return lista;
    }

    /** Elimina solo productos que no forman parte del historial de ventas o compras. */
    public boolean eliminar(int id) {
        String historial = "SELECT (SELECT COUNT(*) FROM detalleventa WHERE id_producto=?) + "
                + "(SELECT COUNT(*) FROM detallecompra WHERE id_producto=?)";
        try (Connection c = Conexion.getConexion()) {
            if (c == null || id < 1) return false;
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(historial)) {
                p.setInt(1, id);
                p.setInt(2, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || r.getInt(1) > 0) throw new SQLException("El producto tiene movimientos y debe conservarse en el historial.");
                }
                try (PreparedStatement borrarStock = c.prepareStatement("DELETE FROM inventario WHERE id_producto=?")) {
                    borrarStock.setInt(1, id);
                    borrarStock.executeUpdate();
                }
                try (PreparedStatement borrarProducto = c.prepareStatement("DELETE FROM producto WHERE idProducto=?")) {
                    borrarProducto.setInt(1, id);
                    if (borrarProducto.executeUpdate() != 1) throw new SQLException("El producto ya no existe.");
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                System.err.println("Error eliminando medicamento: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error conectando al eliminar medicamento: " + e.getMessage());
            return false;
        }
    }

    /** Comprueba campos usados por la base antes de iniciar transacciones. */
    private boolean datosValidos(medicamento m) {
        if (m == null || m.getNombre() == null || m.getNombre().isBlank()
                || m.getNombre().trim().length() > 100 || m.getLote() == null
                || m.getLote().isBlank() || m.getLote().trim().length() > 30
                || m.getFechaVencimiento() == null || m.getStock() < 0
                || !Double.isFinite(m.getPrecio()) || m.getPrecio() < 0) return false;
        try {
            java.sql.Date.valueOf(m.getFechaVencimiento());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Guarda el stock, consolidando filas repetidas heredadas del mismo producto. */
    private void guardarStock(Connection c, int productoId, int cantidad) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT idInventario FROM inventario WHERE id_producto=? ORDER BY idInventario FOR UPDATE")) {
            p.setInt(1, productoId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getInt(1)); }
        }
        if (ids.isEmpty()) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO inventario (id_producto,cantidad,punto_reorden) VALUES (?,?,10)")) {
                p.setInt(1, productoId); p.setInt(2, cantidad); p.executeUpdate();
            }
            return;
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE inventario SET cantidad=? WHERE idInventario=?")) {
            p.setInt(1, cantidad); p.setInt(2, ids.get(0)); p.executeUpdate();
        }
        if (ids.size() > 1) {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM inventario WHERE idInventario=?")) {
                for (int i = 1; i < ids.size(); i++) { p.setInt(1, ids.get(i)); p.addBatch(); }
                p.executeBatch();
            }
        }
    }
}
