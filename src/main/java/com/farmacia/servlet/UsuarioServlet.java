package com.farmacia.servlet;

import com.farmacia.dao.UsuarioDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Acciones administrativas para editar y eliminar cuentas. */
@WebServlet("/UsuarioServlet")
public class UsuarioServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Permite procesar correctamente nombres y datos enviados en UTF-8.
        request.setCharacterEncoding("UTF-8");
        // Verifica el permiso también en el servidor, aunque alguien intente enviar la ruta manualmente.
        if (!esAdministrador(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        UsuarioDAO dao = new UsuarioDAO();
        // Lee la acción solicitada y asume error hasta confirmar que el DAO completó el cambio.
        String accion = request.getParameter("accion");
        String estado = "error";
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            // Identifica la cuenta de la sesión para evitar eliminarla o quitarse el rol administrador.
            Object sesionId = request.getSession(false).getAttribute("idUsuario");
            boolean esCuentaActual = sesionId != null && Integer.parseInt(sesionId.toString()) == id;
            if ("editar".equals(accion)) {
                // Limpia campos de texto y conserva la contraseña sin recortarla.
                String nombre = limpio(request.getParameter("nombre"));
                String usuario = limpio(request.getParameter("usuario"));
                String rol = limpio(request.getParameter("rol"));
                String password = request.getParameter("contrasena");
                // Valida límites, formato, perfil permitido y longitud de la nueva contraseña.
                if (!nombre.isBlank() && nombre.length() <= 100
                        && usuario.matches("[A-Za-z0-9._-]{3,40}")
                        && (password == null || password.isBlank()
                            || (password.length() >= 8 && password.getBytes(StandardCharsets.UTF_8).length <= 72))
                        && ("Usuario".equalsIgnoreCase(rol) || "Administrador".equalsIgnoreCase(rol))
                        && (!esCuentaActual || "Administrador".equalsIgnoreCase(rol))) {
                    estado = dao.actualizarUsuario(id, nombre, usuario, rol, password) ? "guardado" : "error";
                }
            } else if ("eliminar".equals(accion)) {
                // Impide que el administrador borre la cuenta con la que está autenticado.
                if (!esCuentaActual) {
                    estado = dao.eliminarUsuario(id) ? "eliminado" : "error";
                }
            }
        } catch (RuntimeException e) {
            // Trata IDs inválidos y parámetros inesperados como una operación fallida.
            estado = "error";
        }
        // Vuelve al listado y muestra un mensaje según el resultado de la operación.
        response.sendRedirect(request.getContextPath() + "/usuarios.jsp?estado=" + estado);
    }

    /** Acepta únicamente sesiones con uno de los nombres de rol administrador. */
    private boolean esAdministrador(HttpServletRequest request) {
        // No crea una sesión nueva si la petición no está autenticada.
        var sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute("rol") == null) return false;
        String rol = sesion.getAttribute("rol").toString();
        // Reconoce las etiquetas Admin y Administrador sin distinguir mayúsculas.
        return "admin".equalsIgnoreCase(rol.trim()) || "administrador".equalsIgnoreCase(rol.trim());
    }

    /** Convierte los parámetros ausentes en texto vacío y quita espacios laterales. */
    private String limpio(String valor) { return valor == null ? "" : valor.trim(); }
}
