
package com.farmacia.servlet;

import com.farmacia.dao.UsuarioDAO;
import com.farmacia.modelo.Usuario;
import com.farmacia.util.PasswordUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@WebServlet("/api/auth/*")
public class AuthApiServlet extends HttpServlet {

    // DAO encargado de consultar y registrar usuarios.
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        // Identifica el endpoint solicitado.
        String ruta = request.getPathInfo();

        if ("/login".equals(ruta)) {
            realizarLogin(request, response);
        } else if ("/registro".equals(ruta)) {
            registrarUsuario(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);

            try (PrintWriter out = response.getWriter()) {
                out.println("""
                    {
                        "success": false,
                        "mensaje": "Endpoint no encontrado"
                    }
                    """);
            }
        }
    }

    // ==========================================
    // INICIO DE SESIÓN
    // ==========================================
    private void realizarLogin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String datos = leerJSON(request);
        String nombreUsuario = obtenerValor(datos, "usuario");
        String contrasena = obtenerValor(datos, "contrasena");

        try (PrintWriter out = response.getWriter()) {

            // Comprueba que se hayan recibido los datos necesarios.
            if (nombreUsuario == null || nombreUsuario.isBlank()
                    || contrasena == null || contrasena.isBlank()) {

                response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
                );

                out.println("""
                    {
                        "success": false,
                        "mensaje": "Usuario y contraseña son obligatorios"
                    }
                    """);
                return;
            }

            // Busca al usuario en la base de datos.
            Usuario encontrado =
                    usuarioDAO.buscarPorUsuario(nombreUsuario.trim());

            // Si la cuenta no existe, rechaza el acceso.
            if (encontrado == null
                    || encontrado.getContrasena() == null) {

                responderCredencialesIncorrectas(response, out);
                return;
            }

            String guardada = encontrado.getContrasena();
            boolean correcta;

            // Detecta los formatos BCrypt habituales.
            boolean esBCrypt =
                    guardada.startsWith("$2a$")
                    || guardada.startsWith("$2b$")
                    || guardada.startsWith("$2y$");

            if (esBCrypt) {

                // Comprueba la contraseña contra el hash almacenado.
                correcta = PasswordUtil.verificar(
                        contrasena,
                        guardada
                );

            } else {

                // Compatibilidad temporal con cuentas antiguas.
                // Solo se debe retirar después de migrar todas las cuentas.
                correcta = guardada.equals(contrasena);

                if (correcta) {
                    // Migra la contraseña después de verificarla.
                    boolean migrada =
                            usuarioDAO.actualizarContrasenaHash(
                                encontrado.getIdUsuario(),
                                contrasena
                            );

                    if (!migrada) {
                        // No revela la contraseña en el registro de errores.
                        System.err.println(
                            "No se pudo migrar la contraseña de una cuenta."
                        );
                    }
                }
            }

            // Si la contraseña no coincide, rechaza el acceso.
            if (!correcta) {
                responderCredencialesIncorrectas(response, out);
                return;
            }

            // Informa que el inicio de sesión fue correcto.
            // Este endpoint API no crea una sesión HTTP.
            response.setStatus(HttpServletResponse.SC_OK);

            out.println("{");
            out.println("  \"success\": true,");
            out.println("  \"mensaje\": \"Inicio de sesión exitoso\",");
            out.println("  \"usuario\": \"" + escaparJSON(
                    encontrado.getUsuario()) + "\",");
            out.println("  \"nombre\": \"" + escaparJSON(
                    encontrado.getNombre()) + "\",");
            out.println("  \"rol\": \"" + escaparJSON(
                    encontrado.getRol()) + "\"");
            out.println("}");
        }
    }

    // Respuesta genérica para credenciales incorrectas.
    private void responderCredencialesIncorrectas(
            HttpServletResponse response,
            PrintWriter out) {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        out.println("""
            {
                "success": false,
                "mensaje": "Usuario o contraseña incorrectos"
            }
            """);
    }

    // ==========================================
    // REGISTRO DE USUARIOS
    // ==========================================
    private void registrarUsuario(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String datos = leerJSON(request);

        String nombre = obtenerValor(datos, "nombre");
        String nombreUsuario = obtenerValor(datos, "usuario");
        String contrasena = obtenerValor(datos, "contrasena");

        try (PrintWriter out = response.getWriter()) {

            // Verifica los campos obligatorios.
            if (nombre == null || nombre.isBlank()
                    || nombreUsuario == null || nombreUsuario.isBlank()
                    || contrasena == null || contrasena.isBlank()) {

                response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
                );

                out.println("""
                    {
                        "success": false,
                        "mensaje": "Nombre, usuario y contraseña son obligatorios"
                    }
                    """);
                return;
            }

            // Evita aceptar contraseñas excesivamente largas.
            if (contrasena.getBytes(StandardCharsets.UTF_8).length > 72) {
                response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST
                );

                out.println("""
                    {
                        "success": false,
                        "mensaje": "La contraseña supera el límite permitido"
                    }
                    """);
                return;
            }

            // Comprueba que el nombre de usuario no esté registrado.
            if (usuarioDAO.existeUsuario(nombreUsuario.trim())) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);

                out.println("""
                    {
                        "success": false,
                        "mensaje": "El nombre de usuario ya existe"
                    }
                    """);
                return;
            }

            Usuario nuevo = new Usuario();

            nuevo.setNombre(nombre.trim());
            nuevo.setUsuario(nombreUsuario.trim());
            nuevo.setContrasena(contrasena);

            // El registro público nunca puede elegir el rol.
            nuevo.setRol("Usuario");

            // UsuarioDAO guarda la contraseña usando BCrypt.
            boolean registrado = usuarioDAO.registrarUsuario(nuevo);

            if (registrado) {
                response.setStatus(HttpServletResponse.SC_CREATED);

                out.println("""
                    {
                        "success": true,
                        "mensaje": "Usuario registrado correctamente"
                    }
                    """);

            } else {
                response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                );

                out.println("""
                    {
                        "success": false,
                        "mensaje": "No se pudo registrar el usuario"
                    }
                    """);
            }
        }
    }

    // ==========================================
    // LECTURA DEL CUERPO JSON
    // ==========================================
    private String leerJSON(HttpServletRequest request)
            throws IOException {

        StringBuilder json = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {
            String linea;

            while ((linea = reader.readLine()) != null) {
                json.append(linea);
            }
        }

        return json.toString();
    }

    // ==========================================
    // LECTURA SIMPLE DE CAMPOS JSON
    // ==========================================
    // Este método admite JSON sencillo con valores de texto.
    // Para producción conviene utilizar un analizador JSON real.
    private String obtenerValor(String json, String campo) {

        if (json == null || campo == null) {
            return null;
        }

        String buscado = "\"" + campo + "\"";
        int posicion = json.indexOf(buscado);

        if (posicion == -1) {
            return null;
        }

        int dosPuntos = json.indexOf(":", posicion);

        if (dosPuntos == -1) {
            return null;
        }

        int comillaInicio = json.indexOf("\"", dosPuntos + 1);

        if (comillaInicio == -1) {
            return null;
        }

        int comillaFin = json.indexOf("\"", comillaInicio + 1);

        if (comillaFin == -1) {
            return null;
        }

        return json.substring(comillaInicio + 1, comillaFin);
    }

    // Escapa caracteres especiales antes de insertar texto en JSON.
    private String escaparJSON(String texto) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
