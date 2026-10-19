package com.farmacia.servlet;

import com.farmacia.dao.UsuarioDAO;
import com.farmacia.modelo.Usuario;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Valida y registra cuentas nuevas sin imprimir datos del usuario en HTML. */
@WebServlet("/RegistroUsuarioServlet")
public class RegistroUsuarioServlet extends HttpServlet {

    /** Procesa el formulario de registro y devuelve un estado por redirección. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String nombre = limpiar(request.getParameter("nombre"));
        String usuarioNombre = limpiar(request.getParameter("usuario"));
        String contrasena = request.getParameter("contrasena");
        String rol = limpiar(request.getParameter("rol"));

        // Rechaza datos incompletos o fuera de los límites razonables.
        if (nombre.isBlank() || nombre.length() > 100
                || !usuarioNombre.matches("[A-Za-z0-9._-]{3,40}")
                || contrasena == null || contrasena.length() < 8
                || contrasena.getBytes(StandardCharsets.UTF_8).length > 72
                || !("Vendedor".equals(rol) || "Usuario".equals(rol))) {
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=datos");
            return;
        }

        UsuarioDAO dao = new UsuarioDAO();
        if (dao.existeUsuario(usuarioNombre)) {
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=existe");
            return;
        }

        // El DAO almacena la contraseña como hash BCrypt.
        Usuario usuario = new Usuario(nombre, usuarioNombre, contrasena, rol);
        if (dao.registrarUsuario(usuario)) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?registro=1");
        } else {
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=error");
        }
    }

    /** Convierte los parámetros ausentes en texto vacío y quita espacios laterales. */
    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
