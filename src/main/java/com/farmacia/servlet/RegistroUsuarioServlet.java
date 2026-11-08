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
        // Configura UTF-8 para procesar nombres con tildes correctamente.
        request.setCharacterEncoding("UTF-8");
        // Lee y limpia el nombre y usuario, y conserva la contraseña como fue ingresada.
        String nombre = limpiar(request.getParameter("nombre"));
        String usuarioNombre = limpiar(request.getParameter("usuario"));
        String contrasena = request.getParameter("contrasena");
        String rol = limpiar(request.getParameter("rol"));
        // Comprueba si la cuenta de la sesión actual puede crear administradores.
        boolean administradorAutenticado = esAdministrador(request);

        // Los registros públicos solo crean usuarios; solo un administrador puede crear otro administrador.
        if ("Administrador".equalsIgnoreCase(rol) && !administradorAutenticado) {
            // Deniega el intento de asignar privilegios de administrador desde una cuenta pública.
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Solo un administrador puede crear cuentas administradoras.");
            // Detiene el registro después de devolver la respuesta de acceso denegado.
            return;
        }

        // Rechaza datos incompletos o fuera de los límites razonables.
        if (nombre.isBlank() || nombre.length() > 100
                || !usuarioNombre.matches("[A-Za-z0-9._-]{3,40}")
                || contrasena == null || contrasena.length() < 8
                || contrasena.getBytes(StandardCharsets.UTF_8).length > 72
                || !("Vendedor".equalsIgnoreCase(rol) || "Usuario".equalsIgnoreCase(rol)
                || (administradorAutenticado && "Administrador".equalsIgnoreCase(rol)))) {
            // Devuelve al formulario con el estado que permite explicar qué datos corregir.
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=datos");
            // No crea la cuenta mientras alguna validación haya fallado.
            return;
        }

        // Crea el DAO que consulta y guarda las cuentas de usuario.
        UsuarioDAO dao = new UsuarioDAO();
        // Comprueba que el identificador de acceso esté disponible.
        if (dao.existeUsuario(usuarioNombre)) {
            // Muestra al usuario que el nombre de acceso ya está ocupado.
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=existe");
            // Termina el proceso para no intentar un alta duplicada.
            return;
        }

        // El DAO almacena la contraseña como hash BCrypt.
        // Construye el modelo con los datos validados para que el DAO lo persista.
        Usuario usuario = new Usuario(nombre, usuarioNombre, contrasena, rol);
        // Intenta registrar la cuenta y decide el destino según el resultado.
        if (dao.registrarUsuario(usuario)) {
            // Envía al inicio de sesión después de crear la cuenta.
            response.sendRedirect(request.getContextPath() + "/login.jsp?registro=1");
        } else {
            // Informa un fallo genérico si la base de datos no guardó el registro.
            response.sendRedirect(request.getContextPath() + "/registro.jsp?estado=error");
        }
    }

    /** Convierte los parámetros ausentes en texto vacío y quita espacios laterales. */
    private String limpiar(String texto) {
        // Sustituye valores ausentes por texto vacío y recorta espacios laterales.
        return texto == null ? "" : texto.trim();
    }

    /** Comprueba que exista una sesión cuyo rol tenga permisos de administrador. */
    private boolean esAdministrador(HttpServletRequest request) {
        // Recupera una sesión ya creada sin abrir una nueva durante la validación.
        var sesion = request.getSession(false);
        // Rechaza la operación si falta una sesión o su rol no está definido.
        if (sesion == null || sesion.getAttribute("rol") == null) return false;
        // Lee el rol guardado en la sesión para compararlo con los perfiles administrativos.
        String rol = sesion.getAttribute("rol").toString().trim();
        // Reconoce las dos denominaciones usadas por el sistema para el mismo perfil.
        return rol.equalsIgnoreCase("Administrador") || rol.equalsIgnoreCase("Admin");
    }
}
