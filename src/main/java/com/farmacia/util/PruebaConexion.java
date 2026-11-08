package com.farmacia.util;

import java.sql.Connection;

/** Programa de consola para verificar la configuración JDBC local. */
public class PruebaConexion {

    /** Abre la conexión y muestra un resultado breve para diagnóstico. */
    public static void main(String[] args) {

        // Solicita una conexión con la configuración JDBC definida en Conexion.
        Connection con = Conexion.getConexion();

        // Informa en consola si la conexión a MySQL quedó disponible.
        if (con != null) {
            // Indica al operador que la configuración JDBC permitió abrir la conexión.
            System.out.println("✅ CONEXIÓN EXITOSA");
        } else {
            // Indica al operador que debe revisar URL, usuario, contraseña o servicio MySQL.
            System.out.println("❌ CONEXIÓN FALLIDA");
        }

    }
}
