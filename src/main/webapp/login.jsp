<%-- Pantalla de inicio de sesión alineada con el prototipo visual. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List,java.util.Arrays,com.farmacia.dao.UsuarioDAO,com.farmacia.util.HtmlUtil" %>
<% List<String> rolesDisponibles = new UsuarioDAO().listarRoles(); if (rolesDisponibles.isEmpty()) rolesDisponibles = Arrays.asList("Administrador", "Farmacéutico", "Cajero"); %>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Iniciar sesión | Farmacia</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body class="login-page">
    <main class="login-wrap">
        <section class="login-art" aria-label="Sistema de gestión para farmacia">
            <div class="logo-mark" aria-hidden="true">✚</div>
            <h1>Farmacia</h1>
            <p>Sistema de Gestión</p>
        </section>
        <section class="login-form">
            <h2>Iniciar Sesión</h2><div class="underline"></div>
            <%-- Mensaje seguro: no revela si falló el usuario o la contraseña. --%>
            <% if ("1".equals(request.getParameter("error"))) { %>
                <div class="notice error" role="alert">Usuario o contraseña incorrectos. Inténtalo de nuevo.</div>
            <% } %>
            <% if ("1".equals(request.getParameter("registro"))) { %>
                <div class="notice">Cuenta registrada. Ya puedes iniciar sesión.</div>
            <% } %>
            <form action="${pageContext.request.contextPath}/LoginServlet" method="post">
                <div class="field"><label for="usuario">Usuario</label><input id="usuario" name="usuario" autocomplete="username" required autofocus placeholder="Escribe tu usuario"></div>
                <div class="field"><label for="contrasena">Contraseña</label><input id="contrasena" type="password" name="contraseña" autocomplete="current-password" required placeholder="Escribe tu contraseña"></div>
                <%-- Los perfiles se consultan de la base de datos y se envían junto con las credenciales. --%>
                <fieldset class="role-field"><legend>Rol de usuario</legend><div class="role-options">
                    <% for (String rol : rolesDisponibles) { String etiqueta = rol; if ("farmaceutico".equalsIgnoreCase(rol) || "farmacéutico".equalsIgnoreCase(rol)) etiqueta = "Farmacéutico"; else if ("admin".equalsIgnoreCase(rol) || "administrador".equalsIgnoreCase(rol)) etiqueta = "Administrador"; else if ("cajero".equalsIgnoreCase(rol) || "vendedor".equalsIgnoreCase(rol)) etiqueta = "Cajero"; else if ("usuario".equalsIgnoreCase(rol)) etiqueta = "Usuario"; %>
                    <label class="role-option"><input type="radio" name="rol" value="<%= HtmlUtil.escapar(rol) %>" required><span><%= HtmlUtil.escapar(etiqueta) %></span></label>
                    <% } %>
                </div></fieldset>
                <button class="btn" type="submit">Iniciar Sesión <span aria-hidden="true">→</span></button>
            </form>
            <p style="text-align:center;font-size:14px">¿No tienes cuenta? <a class="back-link" href="${pageContext.request.contextPath}/registro.jsp">Regístrate</a></p>
            <div class="security">♙ Sistema seguro</div>
        </section>
    </main>
</body>
</html>


