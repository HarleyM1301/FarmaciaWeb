package com.farmacia.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Configuración y apertura de conexiones JDBC hacia MySQL. */
public class Conexion {

    // Permite configurar los datos de conexión fuera del código y de GitHub.
    private static final String URL = System.getenv().getOrDefault(
            "FARMACIA_DB_URL", "jdbc:mysql://localhost:3306/farmacia_db");
    private static final String USER = System.getenv().getOrDefault("FARMACIA_DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("FARMACIA_DB_PASSWORD", "");

    /** Abre una conexión usando el controlador MySQL incluido por Maven. */
    public static Connection getConexion() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
