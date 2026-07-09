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
public class ConexionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {

            Connection con = Conexion.getConexion();

            if (con != null) {
                out.println("<h2>✅ Conexión exitosa con MySQL</h2>");
                con.close();
            } else {
                out.println("<h2>❌ No fue posible conectar a MySQL</h2>");
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}