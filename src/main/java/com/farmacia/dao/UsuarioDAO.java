
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

    // Obtiene los perfiles existentes para que el formulario ofrezca opciones reales.
    public List<String> listarRoles() {
        List<String> roles = new ArrayList<>();
        String sql = "SELECT DISTINCT rol FROM usuario WHERE rol IS NOT NULL AND TRIM(rol) <> '' ORDER BY rol";
        try (Connection conexion = Conexion.getConexion()) {
            if (conexion == null) return roles;
            try (PreparedStatement ps = conexion.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) roles.add(rs.getString("rol"));
            }
        } catch (SQLException e) {
            System.err.println("Error consultando perfiles: " + e.getMessage());
        }
        return roles;
    }

    // Busca un usuario por su nombre de acceso.
    public Usuario buscarPorUsuario(String nombreUsuario) {

        if (nombreUsuario == null || nombreUsuario.isBlank()) {
            return null;
        }

        // Se utilizan los nombres de la tabla y columnas de tu esquema.
        String sql = "SELECT idUsuario, nombre, usuario, contrasena, rol "
                   + "FROM usuario WHERE usuario = ?";

        try (Connection conexion = Conexion.getConexion()) {

            if (conexion == null) {
                return null;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, nombreUsuario.trim());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Usuario u = new Usuario();

                        u.setIdUsuario(rs.getInt("idUsuario"));
                        u.setNombre(rs.getString("nombre"));
                        u.setUsuario(rs.getString("usuario"));
                        u.setContrasena(rs.getString("contrasena"));
                        u.setRol(rs.getString("rol"));

                        return u;
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error buscando usuario: " + e.getMessage());
        }

        return null;
    }

    // Comprueba si un nombre de usuario ya está registrado.
    public boolean existeUsuario(String nombreUsuario) {

        String sql = "SELECT 1 FROM usuario WHERE usuario = ? LIMIT 1";

        try (Connection conexion = Conexion.getConexion()) {

            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, nombreUsuario);

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }

        } catch (SQLException e) {
            System.err.println("Error comprobando usuario: "
                               + e.getMessage());
            return false;
        }
    }

    // Registra un usuario guardando la contraseña como hash BCrypt.
    public boolean registrarUsuario(Usuario usuario) {

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

        String sql = "INSERT INTO usuario "
                   + "(nombre, usuario, contrasena, rol) "
                   + "VALUES (?, ?, ?, ?)";

        try (Connection conexion = Conexion.getConexion()) {

            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, usuario.getNombre().trim());
                ps.setString(2, usuario.getUsuario().trim());
                ps.setString(3, hash);
                ps.setString(4, usuario.getRol());

                return ps.executeUpdate() == 1;
            }

        } catch (SQLException e) {
            // También captura, por ejemplo, un nombre de usuario duplicado
            // si la base de datos tiene una restricción UNIQUE.
            System.err.println("Error registrando usuario: "
                               + e.getMessage());
            return false;
        }
    }

    // Migra una contraseña antigua a BCrypt después de verificarla.
    public boolean actualizarContrasenaHash(
            int idUsuario, String contrasenaOriginal) {

        if (contrasenaOriginal == null || contrasenaOriginal.isEmpty()) {
            return false;
        }

        String hash = PasswordUtil.encriptar(contrasenaOriginal);

        String sql = "UPDATE usuario SET contrasena = ? "
                   + "WHERE idUsuario = ?";

        try (Connection conexion = Conexion.getConexion()) {

            if (conexion == null) {
                return false;
            }

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, hash);
                ps.setInt(2, idUsuario);

                return ps.executeUpdate() == 1;
            }

        } catch (SQLException e) {
            System.err.println("Error migrando contraseña: "
                               + e.getMessage());
            return false;
        }
    }
}
