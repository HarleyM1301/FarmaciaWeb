package com.farmacia.util;

import java.sql.Connection;

/** Programa de consola para verificar la configuración JDBC local. */
public class PruebaConexion {

    /** Abre la conexión y muestra un resultado breve para diagnóstico. */
    public static void main(String[] args) {

        Connection con = Conexion.getConexion();

        if (con != null) {
            System.out.println("✅ CONEXIÓN EXITOSA");
        } else {
            System.out.println("❌ CONEXIÓN FALLIDA");
        }

    }
}
