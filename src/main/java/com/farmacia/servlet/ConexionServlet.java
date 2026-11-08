package com.farmacia.servlet;

import com.farmacia.util.Conexion;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/conexion")
/** Endpoint de diagnóstico para comprobar la disponibilidad de MySQL. */
public class ConexionServlet extends HttpServlet {

    /** Muestra si la conexión de la aplicación puede abrirse y cerrarse. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Indica al navegador que la respuesta es HTML codificado en UTF-8.
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // Solicita una conexión para comprobar la configuración de MySQL.
            Connection con = Conexion.getConexion();

            if (con != null) {
                // Muestra el resultado positivo de la prueba de conectividad.
                out.println("<h2>✅ Conexión exitosa con MySQL</h2>");
                // Libera la conexión JDBC usada únicamente para el diagnóstico.
                con.close();
            } else {
                // Muestra el resultado cuando no fue posible abrir la conexión.
                out.println("<h2>❌ No fue posible conectar a MySQL</h2>");
            }

        } catch (Exception e) {
            // Convierte el error técnico en una excepción que Tomcat pueda registrar.
            throw new ServletException(e);
        }
    }
}
