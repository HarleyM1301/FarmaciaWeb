package com.farmacia.servlet;

import com.farmacia.dao.UsuarioDAO;
import com.farmacia.modelo.Usuario;
import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/RegistroUsuarioServlet")
public class RegistroUsuarioServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");

        // Recibir datos del formulario
        Usuario usuario = new Usuario();

        usuario.setNombres(request.getParameter("nombres"));
        usuario.setApellidos(request.getParameter("apellidos"));
        usuario.setDocumento(request.getParameter("documento"));
        usuario.setCorreo(request.getParameter("correo"));
        usuario.setUsuario(request.getParameter("usuario"));
        usuario.setClave(request.getParameter("clave"));
        usuario.setTelefono(request.getParameter("telefono"));
        usuario.setDireccion(request.getParameter("direccion"));
        usuario.setRol(request.getParameter("rol"));

        UsuarioDAO dao = new UsuarioDAO();

        try (PrintWriter out = response.getWriter()) {

            // Verificar si existe
            boolean existe = dao.existeUsuario(
                    usuario.getDocumento(),
                    usuario.getCorreo(),
                    usuario.getUsuario()
            );

            if (existe) {

                out.println("<h2 style='color:red;'>❌ Ya existe un usuario con ese documento, correo o usuario.</h2>");
                out.println("<a href='registro.jsp'>Volver al registro</a>");

            } else {

                boolean registrado = dao.registrarUsuario(usuario);

                if (registrado) {

                    out.println("<h2 style='color:green;'>✅ Usuario registrado correctamente.</h2>");
                    out.println("<a href='login.jsp'>Ir al login</a>");

                } else {

                    out.println("<h2 style='color:red;'>❌ No se pudo registrar el usuario.</h2>");
                    out.println("<a href='registro.jsp'>Volver</a>");

                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            response.getWriter().println(
                    "<h2 style='color:red;'>Error: "
                    + e.getMessage()
                    + "</h2>"
            );
        }
    }
}