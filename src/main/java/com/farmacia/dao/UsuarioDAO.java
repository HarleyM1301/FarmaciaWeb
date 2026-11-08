
package com.farmacia.dao;

import com.farmacia.modelo.Usuario;
import com.farmacia.util.Conexion;
import com.farmacia.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Consultas SQL para autenticación, registro y actualización de usuarios. */
public class UsuarioDAO {

    /** Lista las cuentas para el módulo de administración, sin exponer hashes. */
    public List<Usuario> listarUsuarios() {
        // Inicializa una lista vacía para poder responder incluso si MySQL no está disponible.
        List<Usuario> usuarios = new ArrayList<>();
        // Omite la columna de contraseña para no cargar hashes en la pantalla administrativa.
        String sql = "SELECT idUsuario,nombre,usuario,rol FROM usuario ORDER BY nombre,usuario";
        try (Connection conexion = Conexion.getConexion()) {
            // Devuelve la lista vacía cuando no se pudo abrir la conexión.
            if (conexion == null) return usuarios;
            // Ejecuta la consulta y cierra automáticamente la sentencia y el resultado.
            try (PreparedStatement ps = conexion.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                // Convierte cada fila en un objeto que la página puede mostrar.
                while (rs.next()) {
                    usuarios.add(new Usuario(rs.getInt("idUsuario"), rs.getString("nombre"),
                            rs.getString("usuario"), null, rs.getString("rol")));
                }
            }
        } catch (SQLException e) {
            // Registra el fallo técnico sin imprimir datos sensibles en la página.
            System.err.println("Error listando usuarios: " + e.getMessage());
        }
        // Entrega las cuentas encontradas, o una lista vacía si hubo un error.
        return usuarios;
    }

    /** Actualiza datos de una cuenta y cambia la contraseña solo si se indicó una nueva. */
    public boolean actualizarUsuario(int id, String nombre, String usuario, String rol, String contrasena) {
        // Mantiene la contraseña existente si el administrador dejó el campo vacío.
        boolean cambiarContrasena = contrasena != null && !contrasena.isBlank();
        // Usa una consulta distinta para no reemplazar accidentalmente la contraseña actual.
        String sql = cambiarContrasena
                ? "UPDATE usuario SET nombre=?,usuario=?,rol=?,contrasena=? WHERE idUsuario=?"
                : "UPDATE usuario SET nombre=?,usuario=?,rol=? WHERE idUsuario=?";
        try (Connection conexion = Conexion.getConexion()) {
            // No intenta actualizar datos si la conexión no está disponible.
            if (conexion == null) return false;
            // Vincula los campos como parámetros para evitar concatenar datos en SQL.
            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, nombre.trim()); ps.setString(2, usuario.trim()); ps.setString(3, rol);
                int indice = 4;
                // Convierte la nueva contraseña a BCrypt antes de almacenarla.
                if (cambiarContrasena) ps.setString(indice++, PasswordUtil.encriptar(contrasena));
                ps.setInt(indice, id);
                // Informa éxito solo cuando MySQL modificó la cuenta solicitada.
                return ps.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            // Registra errores como nombres de usuario duplicados o restricciones de integridad.
            System.err.println("Error actualizando usuario: " + e.getMessage());
            // Permite que el servlet muestre una respuesta general de operación fallida.
            return false;
        }
    }

    /** Elimina una cuenta por su identificador. */
    public boolean eliminarUsuario(int id) {
        // Prepara un borrado parametrizado y libera la conexión al terminar.
        try (Connection conexion = Conexion.getConexion();
             PreparedStatement ps = conexion == null ? null
                     : conexion.prepareStatement("DELETE FROM usuario WHERE idUsuario=?")) {
            // Rechaza la operación si no fue posible preparar la sentencia.
            if (ps == null) return false;
            ps.setInt(1, id);
            // Solo considera exitosa la eliminación de una fila existente.
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            // Informa el error sin revelar detalles de base de datos al usuario.
            System.err.println("Error eliminando usuario: " + e.getMessage());
            return false;
        }
    }

    // Obtiene los perfiles existentes para que el formulario ofrezca opciones reales.
    public List<String> listarRoles() {
        // Prepara el resultado que se mostrará en las opciones de perfil.
        List<String> roles = new ArrayList<>();
        // Obtiene perfiles distintos y omite los valores vacíos.
        String sql = "SELECT DISTINCT rol FROM usuario WHERE rol IS NOT NULL AND TRIM(rol) <> '' ORDER BY rol";
        try (Connection conexion = Conexion.getConexion()) {
            // Si no hay base de datos, devuelve la lista vacía inicializada.
            if (conexion == null) return roles;
            try (PreparedStatement ps = conexion.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                // Recorre los perfiles devueltos por la consulta.
                while (rs.next()) roles.add(rs.getString("rol"));
            }
        } catch (SQLException e) {
            // Registra el error sin detener la carga de la aplicación.
            System.err.println("Error consultando perfiles: " + e.getMessage());
        }
        // Entrega los perfiles encontrados para poblar el formulario.
        return roles;
    }

    // Busca un usuario por su nombre de acceso.
    public Usuario buscarPorUsuario(String nombreUsuario) {

        // Evita consultar la base con un identificador ausente o vacío.
        if (nombreUsuario == null || nombreUsuario.isBlank()) {
            return null;
        }

        // Se utilizan los nombres de la tabla y columnas de tu esquema.
        String sql = "SELECT idUsuario, nombre, usuario, contrasena, rol "
                   + "FROM usuario WHERE usuario = ?";

        try (Connection conexion = Conexion.getConexion()) {

            // Si no se pudo conectar, no existe información que devolver.
            if (conexion == null) {
                return null;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                // Vincula el nombre sin espacios exteriores al parámetro SQL.
                ps.setString(1, nombreUsuario.trim());

                try (ResultSet rs = ps.executeQuery()) {
                    // Crea un objeto solo si la consulta encontró una cuenta.
                    if (rs.next()) {
                        Usuario u = new Usuario();

                        // Copia las columnas consultadas al objeto de usuario.
                        u.setIdUsuario(rs.getInt("idUsuario"));
                        u.setNombre(rs.getString("nombre"));
                        u.setUsuario(rs.getString("usuario"));
                        u.setContrasena(rs.getString("contrasena"));
                        u.setRol(rs.getString("rol"));

                        // Devuelve la cuenta completa para validar credenciales y rol.
                        return u;
                    }
                }
            }

        } catch (SQLException e) {
            // Registra el error técnico sin mostrarlo al usuario final.
            System.err.println("Error buscando usuario: " + e.getMessage());
        }

        // Señala que no se encontró la cuenta o que la consulta falló.
        return null;
    }

    // Comprueba si un nombre de usuario ya está registrado.
    public boolean existeUsuario(String nombreUsuario) {

        // Consulta solo la existencia, sin cargar el resto de los datos de la cuenta.
        String sql = "SELECT 1 FROM usuario WHERE usuario = ? LIMIT 1";

        try (Connection conexion = Conexion.getConexion()) {

            // Informa que no se puede confirmar disponibilidad si la base está caída.
            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                // Asocia el nombre ingresado al parámetro protegido de la consulta.
                ps.setString(1, nombreUsuario);

                try (ResultSet rs = ps.executeQuery()) {
                    // Devuelve verdadero únicamente si MySQL encontró una coincidencia.
                    return rs.next();
                }
            }

        } catch (SQLException e) {
            // Registra el motivo por el que no se pudo comprobar el nombre.
            System.err.println("Error comprobando usuario: "
                               + e.getMessage());
            // Evita autorizar un registro si la comprobación falló.
            return false;
        }
    }

    // Registra un usuario guardando la contraseña como hash BCrypt.
    public boolean registrarUsuario(Usuario usuario) {

        // Rechaza objetos incompletos antes de generar el hash o abrir una conexión.
        if (usuario == null
                || usuario.getNombre() == null
                || usuario.getUsuario() == null
                || usuario.getContrasena() == null
                || usuario.getRol() == null
                || usuario.getNombre().isBlank()
                || usuario.getUsuario().isBlank()
                || usuario.getContrasena().isBlank()
                || usuario.getRol().isBlank()) {
            return false;
        }

        // Nunca se guarda la contraseña original.
        String hash = PasswordUtil.encriptar(usuario.getContrasena());

        // Define la inserción parametrizada que guardará la cuenta en MySQL.
        String sql = "INSERT INTO usuario "
                   + "(nombre, usuario, contrasena, rol) "
                   + "VALUES (?, ?, ?, ?)";

        try (Connection conexion = Conexion.getConexion()) {

            // No intenta insertar si la conexión de base de datos no está disponible.
            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                // Vincula los datos limpios y el hash, nunca la contraseña original.
                ps.setString(1, usuario.getNombre().trim());
                ps.setString(2, usuario.getUsuario().trim());
                ps.setString(3, hash);
                ps.setString(4, usuario.getRol());

                // Confirma el registro solo si MySQL insertó exactamente una cuenta.
                return ps.executeUpdate() == 1;
            }

        } catch (SQLException e) {
            // También captura, por ejemplo, un nombre de usuario duplicado
            // si la base de datos tiene una restricción UNIQUE.
            // Informa que la cuenta no se pudo guardar, por ejemplo, por usuario duplicado.
            System.err.println("Error registrando usuario: "
                               + e.getMessage());
            // Devuelve un resultado negativo al servlet que procesa el formulario.
            return false;
        }
    }

    // Migra una contraseña antigua a BCrypt después de verificarla.
    public boolean actualizarContrasenaHash(
            int idUsuario, String contrasenaOriginal) {

        // Evita reemplazar una contraseña con un hash de un valor vacío.
        if (contrasenaOriginal == null || contrasenaOriginal.isEmpty()) {
            return false;
        }

        // Convierte la contraseña antigua a BCrypt antes de modificar la fila.
        String hash = PasswordUtil.encriptar(contrasenaOriginal);

        // Actualiza únicamente la cuenta identificada por su clave primaria.
        String sql = "UPDATE usuario SET contrasena = ? "
                   + "WHERE idUsuario = ?";

        try (Connection conexion = Conexion.getConexion()) {

            // No intenta la migración mientras la base de datos no esté disponible.
            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                // Vincula el hash y el ID de la cuenta que se va a migrar.
                ps.setString(1, hash);
                ps.setInt(2, idUsuario);

                // Informa éxito solo si se actualizó una cuenta.
                return ps.executeUpdate() == 1;
            }

        } catch (SQLException e) {
            // Registra el motivo técnico del fallo de migración.
            System.err.println("Error migrando contraseña: "
                               + e.getMessage());
            // Informa al proceso de autenticación que la migración no se completó.
            return false;
        }
    }
}
