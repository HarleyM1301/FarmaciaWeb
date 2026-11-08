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

    /** Resultado de borrado para distinguir historial comercial de un fallo técnico. */
    public enum ResultadoEliminacion { ELIMINADO, CON_HISTORIAL, ERROR }

    /** Inserta producto y existencias juntos para que nunca quede un alta incompleta. */
    public boolean insertar(medicamento m) {
        // Detiene el alta si faltan datos o si algún valor no cumple los límites del modelo.
        if (!datosValidos(m)) return false;
        // Prepara la instrucción que crea la ficha del medicamento en el catálogo.
        String producto = "INSERT INTO producto (nombre,categoria,registro_invima,lote,fecha_vencimiento,precio_venta,unidad_base,unidades_por_presentacion,unidades_por_blister) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = Conexion.getConexion()) {
            // No intenta guardar datos si la conexión a MySQL no pudo abrirse.
            if (c == null) return false;
            // Inicia una transacción para guardar ficha y stock como una sola operación.
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(producto, Statement.RETURN_GENERATED_KEYS)) {
                // Asigna el nombre limpio al primer parámetro de la consulta.
                p.setString(1, m.getNombre().trim());
                // Guarda el grupo farmacológico en la categoría del producto.
                p.setString(2, m.getCategoria().trim());
                // Guarda el registro sanitario en su campo propio, separado del lote.
                p.setString(3, vacioANull(m.getRegistroInvima()));
                // Asigna el lote real al cuarto parámetro de la consulta.
                p.setString(4, vacioANull(m.getLote()));
                // Guarda NULL cuando no se informó una fecha de vencimiento.
                if (m.getFechaVencimiento() == null || m.getFechaVencimiento().isBlank()) p.setNull(5, java.sql.Types.DATE);
                else p.setDate(5, java.sql.Date.valueOf(m.getFechaVencimiento()));
                // Asigna el precio de venta registrado para el medicamento.
                p.setDouble(6, m.getPrecio());
                // Guarda la unidad mínima usada para controlar las existencias.
                p.setString(7, m.getUnidadBase());
                // Guarda cuántas unidades mínimas contiene cada presentación.
                p.setInt(8, m.getUnidadesPorPresentacion());
                p.setInt(9, m.getUnidadesPorBlister());
                // Ejecuta el alta de la ficha en la tabla producto.
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    // Comprueba que MySQL haya devuelto el identificador generado.
                    if (!keys.next()) throw new SQLException("MySQL no devolvió el ID del producto nuevo.");
                    // Crea o actualiza el inventario usando el ID recién generado.
                    guardarStock(c, keys.getInt(1), m.getStock());
                }
                // Confirma ficha y existencias solo después de completar ambos guardados.
                c.commit();
                // Informa que el producto y su stock quedaron registrados.
                return true;
            } catch (Exception e) {
                // Revierte ficha y stock si cualquier paso del alta falla.
                c.rollback();
                // Deja el detalle técnico en la consola del servidor para diagnóstico.
                System.err.println("Error insertando medicamento: " + e.getMessage());
                // Informa al servlet que el alta no se completó.
                return false;
            } finally {
                // Restaura el modo habitual de confirmación antes de devolver la conexión.
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Registra fallos al abrir o cerrar la conexión con la base de datos.
            System.err.println("Error conectando al guardar medicamento: " + e.getMessage());
            // Informa que no fue posible completar el alta.
            return false;
        }
    }

    /** Actualiza la ficha del producto y normaliza su cantidad en inventario. */
    public boolean actualizar(medicamento m) {
        // Rechaza datos inválidos y registros sin un identificador persistido.
        if (!datosValidos(m) || m.getIdMedicamento() < 1) return false;
        // Define los campos de catálogo que se actualizarán para el producto indicado.
        String sql = "UPDATE producto SET nombre=?,categoria=?,registro_invima=?,lote=?,fecha_vencimiento=?,precio_venta=?,unidad_base=?,unidades_por_presentacion=?,unidades_por_blister=? WHERE idProducto=?";
        try (Connection c = Conexion.getConexion()) {
            // Finaliza sin cambios si MySQL no está disponible.
            if (c == null) return false;
            // Agrupa la actualización del catálogo y del stock dentro de una transacción.
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql)) {
                // Completa los parámetros del nombre, lote, fecha, precio y presentación.
                p.setString(1, m.getNombre().trim());
                p.setString(2, m.getCategoria().trim());
                p.setString(3, vacioANull(m.getRegistroInvima()));
                p.setString(4, vacioANull(m.getLote()));
                if (m.getFechaVencimiento() == null || m.getFechaVencimiento().isBlank()) p.setNull(5, java.sql.Types.DATE);
                else p.setDate(5, java.sql.Date.valueOf(m.getFechaVencimiento()));
                p.setDouble(6, m.getPrecio());
                p.setString(7, m.getUnidadBase());
                p.setInt(8, m.getUnidadesPorPresentacion());
                p.setInt(9, m.getUnidadesPorBlister());
                // Asocia la actualización con el identificador del medicamento existente.
                p.setInt(10, m.getIdMedicamento());
                // Exige que exactamente un registro exista y se haya actualizado.
                if (p.executeUpdate() != 1) throw new SQLException("El medicamento ya no existe.");
                // Sincroniza las existencias con el valor enviado desde el formulario.
                guardarStock(c, m.getIdMedicamento(), m.getStock());
                // Confirma juntos los cambios en la ficha y en el inventario.
                c.commit();
                // Informa que la modificación se guardó correctamente.
                return true;
            } catch (Exception e) {
                // Deshace todos los cambios si falla la actualización o el ajuste de stock.
                c.rollback();
                // Registra la causa para que pueda diagnosticarse en el servidor.
                System.err.println("Error actualizando medicamento: " + e.getMessage());
                // Informa al servlet que la actualización no se completó.
                return false;
            } finally {
                // Restaura la confirmación automática para futuros usos de esta conexión.
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Registra los errores de conexión producidos fuera de la transacción.
            System.err.println("Error conectando al actualizar medicamento: " + e.getMessage());
            // Informa que la operación no pudo llegar a la base de datos.
            return false;
        }
    }

    /** Lista el catálogo aunque aún falte una fila de inventario para un producto. */
    public List<medicamento> listar() {
        // Prepara la lista que se devolverá aunque la consulta falle.
        List<medicamento> lista = new ArrayList<>();
        // Consulta productos y suma cualquier fila de inventario asociada a cada uno.
        String sql = "SELECT p.idProducto,p.nombre,p.categoria,p.registro_invima,p.lote,p.fecha_vencimiento,p.precio_venta,p.unidad_base,p.unidades_por_presentacion,p.unidades_por_blister,"
                + "COALESCE(i.stock,0) AS stock FROM producto p "
                + "LEFT JOIN (SELECT id_producto,SUM(COALESCE(cantidad,0)) AS stock "
                + "FROM inventario GROUP BY id_producto) i ON i.id_producto=p.idProducto ORDER BY p.nombre,p.lote";
        try (Connection c = Conexion.getConexion()) {
            // Devuelve una lista vacía si no hay conexión disponible.
            if (c == null) return lista;
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                // Convierte cada fila obtenida en un objeto de medicamento para la vista.
                while (r.next()) {
                    // Crea el objeto que representa la fila actual de la consulta.
                    medicamento m = new medicamento();
                    m.setIdMedicamento(r.getInt("idProducto"));
                    m.setNombre(r.getString("nombre"));
                    m.setCategoria(r.getString("categoria"));
                    m.setRegistroInvima(r.getString("registro_invima"));
                    m.setLote(r.getString("lote"));
                    java.sql.Date vencimiento = r.getDate("fecha_vencimiento");
                    m.setFechaVencimiento(vencimiento == null ? "" : vencimiento.toString());
                    m.setStock(r.getInt("stock"));
                    m.setPrecio(r.getDouble("precio_venta"));
                    m.setUnidadBase(r.getString("unidad_base"));
                    m.setUnidadesPorPresentacion(r.getInt("unidades_por_presentacion"));
                    m.setUnidadesPorBlister(r.getInt("unidades_por_blister"));
                    // Agrega el medicamento completo a la lista de resultados.
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            // Registra el fallo sin exponer detalles de la base de datos en la página.
            System.err.println("Error listando medicamentos: " + e.getMessage());
            // Mantiene visible el catálogo si todavía falta V6 o V8.
            if (e.getErrorCode() == 1054 || "42S22".equals(e.getSQLState())) return listarSinInvima();
        }
        // Entrega los registros leídos o la lista vacía en caso de error.
        return lista;
    }

    /** Lee el esquema previo a V6 para que un despliegue pendiente no oculte los medicamentos existentes. */
    private List<medicamento> listarSinInvima() {
        List<medicamento> lista = new ArrayList<>();
        String sql = "SELECT p.idProducto,p.nombre,p.categoria,p.lote,p.fecha_vencimiento,p.precio_venta,p.unidad_base,p.unidades_por_presentacion,p.unidades_por_blister,"
                + "COALESCE(i.stock,0) AS stock FROM producto p "
                + "LEFT JOIN (SELECT id_producto,SUM(COALESCE(cantidad,0)) AS stock FROM inventario GROUP BY id_producto) i "
                + "ON i.id_producto=p.idProducto ORDER BY p.nombre,p.lote";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) return lista;
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    medicamento m = new medicamento();
                    m.setIdMedicamento(r.getInt("idProducto"));
                    m.setNombre(r.getString("nombre"));
                    m.setCategoria(r.getString("categoria"));
                    m.setRegistroInvima(null);
                    m.setLote(r.getString("lote"));
                    java.sql.Date vencimiento = r.getDate("fecha_vencimiento");
                    m.setFechaVencimiento(vencimiento == null ? "" : vencimiento.toString());
                    m.setStock(r.getInt("stock"));
                    m.setPrecio(r.getDouble("precio_venta"));
                    m.setUnidadBase(r.getString("unidad_base"));
                    m.setUnidadesPorPresentacion(r.getInt("unidades_por_presentacion"));
                    m.setUnidadesPorBlister(r.getInt("unidades_por_blister"));
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            // Registra la causa si tampoco es posible consultar el esquema anterior.
            System.err.println("Error listando medicamentos sin INVIMA: " + e.getMessage());
            if (e.getErrorCode() == 1054 || "42S22".equals(e.getSQLState())) return listarSinInvimaNiBlister();
        }
        return lista;
    }

    /** Conserva el catálogo previo a las migraciones de INVIMA y presentación de blíster. */
    private List<medicamento> listarSinInvimaNiBlister() {
        List<medicamento> lista = new ArrayList<>();
        String sql = "SELECT p.idProducto,p.nombre,p.categoria,p.lote,p.fecha_vencimiento,p.precio_venta,p.unidad_base,p.unidades_por_presentacion,"
                + "COALESCE(i.stock,0) AS stock FROM producto p "
                + "LEFT JOIN (SELECT id_producto,SUM(COALESCE(cantidad,0)) AS stock FROM inventario GROUP BY id_producto) i "
                + "ON i.id_producto=p.idProducto ORDER BY p.nombre,p.lote";
        try (Connection c = Conexion.getConexion()) {
            if (c == null) return lista;
            try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    medicamento m = new medicamento();
                    m.setIdMedicamento(r.getInt("idProducto"));
                    m.setNombre(r.getString("nombre"));
                    m.setCategoria(r.getString("categoria"));
                    m.setRegistroInvima(null);
                    m.setLote(r.getString("lote"));
                    java.sql.Date vencimiento = r.getDate("fecha_vencimiento");
                    m.setFechaVencimiento(vencimiento == null ? "" : vencimiento.toString());
                    m.setStock(r.getInt("stock"));
                    m.setPrecio(r.getDouble("precio_venta"));
                    m.setUnidadBase(r.getString("unidad_base"));
                    m.setUnidadesPorPresentacion(r.getInt("unidades_por_presentacion"));
                    m.setUnidadesPorBlister(0);
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listando medicamentos sin INVIMA ni factor de blíster: " + e.getMessage());
        }
        return lista;
    }

    /** Elimina solo productos que no forman parte del historial de ventas o compras. */
    public ResultadoEliminacion eliminar(int id) {
        // Cuenta ventas y compras para proteger el historial asociado al producto.
        String historial = "SELECT (SELECT COUNT(*) FROM detalleventa WHERE id_producto=?) + "
                + "(SELECT COUNT(*) FROM detallecompra WHERE id_producto=?)";
        try (Connection c = Conexion.getConexion()) {
            // Rechaza IDs inválidos o una conexión no disponible antes de borrar.
            if (c == null || id < 1) return ResultadoEliminacion.ERROR;
            // Agrupa las eliminaciones para evitar dejar filas huérfanas.
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(historial)) {
                // Comprueba si el producto aparece en alguna venta o compra.
                p.setInt(1, id);
                p.setInt(2, id);
                try (ResultSet r = p.executeQuery()) {
                    // Impide borrar productos que ya forman parte del historial comercial.
                    if (!r.next()) throw new SQLException("No se encontró el producto.");
                    if (r.getInt(1) > 0) {
                        c.rollback();
                        return ResultadoEliminacion.CON_HISTORIAL;
                    }
                }
                try (PreparedStatement borrarStock = c.prepareStatement("DELETE FROM inventario WHERE id_producto=?")) {
                    // Elimina primero las existencias relacionadas con el producto.
                    borrarStock.setInt(1, id);
                    borrarStock.executeUpdate();
                }
                try (PreparedStatement borrarProducto = c.prepareStatement("DELETE FROM producto WHERE idProducto=?")) {
                    // Limita la eliminación de catálogo al ID solicitado.
                    borrarProducto.setInt(1, id);
                    // Comprueba que la ficha realmente se haya eliminado.
                    if (borrarProducto.executeUpdate() != 1) throw new SQLException("El producto ya no existe.");
                }
                // Confirma la eliminación de stock y ficha cuando ambos pasos finalizaron.
                c.commit();
                // Informa que el producto se eliminó correctamente.
                return ResultadoEliminacion.ELIMINADO;
            } catch (SQLException e) {
                // Restaura el stock y la ficha si cualquier borrado falla.
                c.rollback();
                // Registra la causa del fallo para facilitar su diagnóstico.
                System.err.println("Error eliminando medicamento: " + e.getMessage());
                // Separa las restricciones de historial de errores de conexión o SQL.
                return "El producto tiene movimientos y debe conservarse en el historial.".equals(e.getMessage())
                        ? ResultadoEliminacion.CON_HISTORIAL : ResultadoEliminacion.ERROR;
            } finally {
                // Restaura el comportamiento predeterminado de la conexión.
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Registra errores ocurridos al conectar o liberar recursos JDBC.
            System.err.println("Error conectando al eliminar medicamento: " + e.getMessage());
            // Informa que no se pudo completar la eliminación.
            return ResultadoEliminacion.ERROR;
        }
    }

    /** Comprueba campos usados por la base antes de iniciar transacciones. */
    private boolean datosValidos(medicamento m) {
        // Revisa presencia, longitudes, valores positivos y formatos admitidos por la base.
        if (m == null || m.getNombre() == null || m.getNombre().isBlank()
                || m.getNombre().trim().length() > 100
                || m.getLote() != null && m.getLote().trim().length() > 30
                || m.getStock() < 0
                || m.getCategoria() == null || m.getCategoria().isBlank() || m.getCategoria().length() > 50
                || m.getRegistroInvima() != null && m.getRegistroInvima().trim().length() > 60
                || m.getUnidadBase() == null || !m.getUnidadBase().matches("unidad|caja|paquete|tableta|capsula|sobre|ml|gota|ampolla|frasco")
                || m.getUnidadesPorPresentacion() < 1 || m.getUnidadesPorPresentacion() > 100000
                || m.getUnidadesPorBlister() < 0 || m.getUnidadesPorBlister() > m.getUnidadesPorPresentacion()
                || m.getUnidadesPorBlister() > 0 && m.getUnidadesPorPresentacion() % m.getUnidadesPorBlister() != 0
                || !Double.isFinite(m.getPrecio()) || m.getPrecio() < 0) return false;
        if (m.getFechaVencimiento() == null || m.getFechaVencimiento().isBlank()) return true;
        try {
            // Intenta convertir la fecha para descartar fechas que no respeten AAAA-MM-DD.
            java.sql.Date.valueOf(m.getFechaVencimiento());
            // Confirma que todos los datos pasaron las validaciones.
            return true;
        } catch (IllegalArgumentException e) {
            // Rechaza la fecha si el formato o el día indicado no son válidos.
            return false;
        }
    }

    /** Guarda el registro INVIMA como NULL cuando el formulario deja el campo opcional vacío. */
    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Guarda el stock, consolidando filas repetidas heredadas del mismo producto. */
    private void guardarStock(Connection c, int productoId, int cantidad) throws SQLException {
        // Reúne IDs de inventario existentes y bloquea sus filas durante la transacción.
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT idInventario FROM inventario WHERE id_producto=? ORDER BY idInventario FOR UPDATE")) {
            // Vincula la consulta al producto cuyo stock se va a consolidar.
            p.setInt(1, productoId);
            // Guarda cada ID encontrado para actualizar una fila y borrar duplicados.
            try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getInt(1)); }
        }
        if (ids.isEmpty()) {
            // Crea la primera fila de inventario cuando el producto aún no tiene stock.
            try (PreparedStatement p = c.prepareStatement("INSERT INTO inventario (id_producto,cantidad,punto_reorden) VALUES (?,?,10)")) {
                p.setInt(1, productoId); p.setInt(2, cantidad); p.executeUpdate();
            }
            return;
        }
        // Actualiza la fila principal conservando su identificador y punto de reorden.
        try (PreparedStatement p = c.prepareStatement("UPDATE inventario SET cantidad=? WHERE idInventario=?")) {
            p.setInt(1, cantidad); p.setInt(2, ids.get(0)); p.executeUpdate();
        }
        if (ids.size() > 1) {
            // Prepara la eliminación de filas duplicadas que inflarían el stock total.
            try (PreparedStatement p = c.prepareStatement("DELETE FROM inventario WHERE idInventario=?")) {
                // Añade a un lote cada fila repetida excepto la que quedó como principal.
                for (int i = 1; i < ids.size(); i++) { p.setInt(1, ids.get(i)); p.addBatch(); }
                // Ejecuta de una vez todas las eliminaciones agrupadas.
                p.executeBatch();
            }
        }
    }
}
