package com.farmacia.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/LogoutServlet")
/** Cierra la sesión activa y regresa al formulario de acceso. */
public class LagoutServlet extends HttpServlet {

    /** Invalida los datos de sesión para impedir que se reutilicen. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Recupera la sesión existente sin crear una nueva si ya expiró.
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            // Elimina los datos de autenticación y permisos de la sesión.
            sesion.invalidate();
        }

        // Devuelve al usuario al formulario de inicio de sesión.
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }
}
