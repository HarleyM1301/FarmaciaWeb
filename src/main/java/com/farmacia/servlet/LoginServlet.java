
package com.farmacia.servlet;

import com.farmacia.dao.UsuarioDAO;
import com.farmacia.modelo.Usuario;
import com.farmacia.util.PasswordUtil;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet encargado de iniciar sesión.
 *
 * Comprueba las credenciales, migra contraseñas antiguas
 * a BCrypt y crea la sesión del usuario autenticado.
 */
@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        // Permite recibir y mostrar correctamente caracteres especiales.
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        // Recibe los campos del formulario login.jsp.
        String nombreUsuario = request.getParameter("usuario");
        String contrasenaIngresada = request.getParameter("contraseña");
        String rolIngresado = request.getParameter("rol");

        // No se aceptan campos vacíos.
        if (nombreUsuario == null || contrasenaIngresada == null || rolIngresado == null
                || nombreUsuario.trim().isEmpty()
                || contrasenaIngresada.isEmpty()) {

            mostrarError(request, response);
            return;
        }

        // Busca el usuario en la base de datos.
        UsuarioDAO dao = new UsuarioDAO();
        Usuario usuarioEncontrado =
                dao.buscarPorUsuario(nombreUsuario.trim());

        // Si el usuario no existe, se rechaza el acceso.
        if (usuarioEncontrado == null
                || usuarioEncontrado.getContrasena() == null) {

            mostrarError(request, response);
            return;
        }

        // Obtiene la contraseña almacenada.
        String contrasenaGuardada =
                usuarioEncontrado.getContrasena();

        boolean contrasenaCorrecta = false;

        // Comprueba si la contraseña almacenada parece un hash BCrypt.
        if (contrasenaGuardada.startsWith("$2a$")
                || contrasenaGuardada.startsWith("$2b$")
                || contrasenaGuardada.startsWith("$2y$")) {

            // Para BCrypt se verifica, no se compara con equals().
            contrasenaCorrecta = PasswordUtil.verificar(
                    contrasenaIngresada,
                    contrasenaGuardada
            );

        } else {

            // Compatibilidad temporal con las contraseñas antiguas
            // que todavía están guardadas como texto legible.
            contrasenaCorrecta =
                    contrasenaGuardada.equals(contrasenaIngresada);

            // Si la contraseña antigua es correcta, se migra a BCrypt.
            if (contrasenaCorrecta) {

                boolean actualizada = dao.actualizarContrasenaHash(
                        usuarioEncontrado.getIdUsuario(),
                        contrasenaIngresada
                );

                if (!actualizada) {
                    // Informa el problema sin revelar la contraseña.
                    System.err.println(
                        "No se pudo migrar la contraseña del usuario "
                        + usuarioEncontrado.getUsuario()
                    );
                }
            }
        }

        // Si las credenciales no coinciden, se deniega el acceso.
        if (!contrasenaCorrecta || !mismoRol(rolIngresado, usuarioEncontrado.getRol())) {
            mostrarError(request, response);
            return;
        }

        // Evita reutilizar una sesión anterior al autenticar.
        HttpSession sesionAnterior = request.getSession(false);

        if (sesionAnterior != null) {
            sesionAnterior.invalidate();
        }

        // Crea una sesión nueva para el usuario autenticado.
        HttpSession sesion = request.getSession(true);

        sesion.setAttribute(
                "usuario", usuarioEncontrado.getUsuario());

        sesion.setAttribute(
                "nombre", usuarioEncontrado.getNombre());

        sesion.setAttribute(
                "rol", usuarioEncontrado.getRol());

        sesion.setAttribute(
                "idUsuario", usuarioEncontrado.getIdUsuario());

        // Redirige al panel principal.
        response.sendRedirect(
                request.getContextPath() + "/panel.jsp");
    }

    /**
     * Muestra un mensaje genérico para no revelar si el usuario
     * o la contraseña fueron los incorrectos.
     */
    private void mostrarError(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        // Regresa al formulario para mostrar el mensaje con el diseño común.
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=1");
    }

    /** Compara perfiles ignorando mayúsculas, acentos y espacios exteriores. */
    private boolean mismoRol(String rolIngresado, String rolGuardado) {
        return normalizarRol(rolIngresado).equals(normalizarRol(rolGuardado));
    }

    /** Normaliza el texto de rol para aceptar, por ejemplo, Farmaceutico/Farmacéutico. */
    private String normalizarRol(String rol) {
        if (rol == null) return "";
        String sinAcentos = Normalizer.normalize(rol.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }
}
