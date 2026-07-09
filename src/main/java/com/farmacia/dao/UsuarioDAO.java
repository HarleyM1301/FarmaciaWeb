package com.farmacia.dao;

import com.farmacia.modelo.Usuario;
import com.farmacia.util.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UsuarioDAO {

    // Verificar si ya existe documento, correo o usuario
    public boolean existeUsuario(String documento, String correo, String usuario) {

        String sql = "SELECT * FROM usuarios WHERE documento=? OR correo=? OR usuario=?";

        try {

            Connection con = Conexion.getConexion();

            if (con == null) {
                System.out.println("ERROR: No hay conexión con la base de datos");
                return false;
            }

            PreparedStatement ps = con.prepareStatement(sql);

            ps.setString(1, documento);
            ps.setString(2, correo);
            ps.setString(3, usuario);

            ResultSet rs = ps.executeQuery();

            return rs.next();

        } catch (Exception e) {

    System.out.println("===== ERROR AL INSERTAR =====");
    e.printStackTrace();

    return false;
}
    }


    // Registrar usuario en la base de datos
    public boolean registrarUsuario(Usuario u) {

        String sql = "INSERT INTO usuarios "
                + "(nombres, apellidos, documento, correo, usuario, clave, telefono, direccion, rol) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {

            Connection con = Conexion.getConexion();

            if (con == null) {
                System.out.println("ERROR: La conexión es nula");
                return false;
            }

            PreparedStatement ps = con.prepareStatement(sql);

            ps.setString(1, u.getNombres());
            ps.setString(2, u.getApellidos());
            ps.setString(3, u.getDocumento());
            ps.setString(4, u.getCorreo());
            ps.setString(5, u.getUsuario());
            ps.setString(6, u.getClave());
            ps.setString(7, u.getTelefono());
            ps.setString(8, u.getDireccion());
            ps.setString(9, u.getRol());
System.out.println("Ejecutando INSERT...");
            int filas = ps.executeUpdate();

            System.out.println("Usuarios registrados: " + filas);

            ps.close();
            con.close();

            return filas > 0;

        } catch (Exception e) {

    e.printStackTrace();

    throw new RuntimeException(e);

}
    }
}