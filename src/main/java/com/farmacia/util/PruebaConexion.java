package com.farmacia.util;

import java.sql.Connection;

public class PruebaConexion {

    public static void main(String[] args) {

        Connection con = Conexion.getConexion();

        if (con != null) {
            System.out.println("✅ CONEXIÓN EXITOSA");
        } else {
            System.out.println("❌ CONEXIÓN FALLIDA");
        }

    }
}