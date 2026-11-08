package com.farmacia.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Configuración y apertura de conexiones JDBC hacia MySQL. */
public class Conexion {

    // Permite configurar los datos de conexión fuera del código y de GitHub.
    private static final String URL = System.getenv().getOrDefault(
            "FARMACIA_DB_URL", "jdbc:mysql://localhost:3306/farmacia_db");
    // Lee el usuario de MySQL desde el entorno y usa root como valor local predeterminado.
    private static final String USER = System.getenv().getOrDefault("FARMACIA_DB_USER", "root");
    // Lee la contraseña del entorno para no escribir credenciales reales en el código.
    private static final String PASSWORD = System.getenv().getOrDefault("FARMACIA_DB_PASSWORD", "");

    /** Abre una conexión usando el controlador MySQL incluido por Maven. */
    public static Connection getConexion() {
        try {
            // Registra el controlador JDBC de MySQL antes de abrir la conexión.
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Solicita una conexión con la URL, el usuario y la contraseña configurados.
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException | SQLException e) {
            // Deja el detalle técnico en los registros para facilitar el diagnóstico.
            // Imprime la excepción completa para identificar problemas de controlador o conexión.
            e.printStackTrace();
        }
        // Informa a quien llamó que la conexión no pudo establecerse.
        return null;
    }
}
