<%-- Formulario público para crear una cuenta de vendedor o usuario. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Crear cuenta | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body class="login-page"><main class="card" style="width:min(460px,100%);padding:32px">
<div style="text-align:center"><div class="logo-mark" style="width:64px;height:64px;font-size:30px;margin:auto">✚</div><h1 style="font-size:24px">Crear cuenta</h1><p class="muted">Sistema de Gestión para Farmacia</p></div>
<%-- Muestra el resultado del registro sin imprimir datos enviados por el usuario. --%>
<% String estado = request.getParameter("estado"); if ("datos".equals(estado)) { %><div class="notice error">Revisa los datos. La contraseña debe tener al menos 8 caracteres.</div><% } else if ("existe".equals(estado)) { %><div class="notice error">Ese nombre de usuario ya está registrado.</div><% } else if ("error".equals(estado)) { %><div class="notice error">No se pudo crear la cuenta. Revisa la conexión con la base de datos.</div><% } %>
<form action="${pageContext.request.contextPath}/RegistroUsuarioServlet" method="post">
<div class="field"><label for="nombre">Nombre completo</label><input id="nombre" name="nombre" maxlength="100" autocomplete="name" required></div>
<div class="field"><label for="usuario">Usuario</label><input id="usuario" name="usuario" minlength="3" maxlength="40" pattern="[A-Za-z0-9._-]{3,40}" autocomplete="username" required></div>
<div class="field"><label for="contrasena">Contraseña</label><input id="contrasena" name="contrasena" type="password" minlength="8" maxlength="72" autocomplete="new-password" required></div>
<div class="field"><label for="rol">Perfil</label><select id="rol" name="rol" required><option value="Vendedor">Vendedor</option><option value="Usuario">Usuario</option></select></div>
<button class="btn" type="submit" style="width:100%;margin-top:15px">Crear cuenta</button></form>
<p style="text-align:center;margin:22px 0 0">¿Ya tienes cuenta? <a class="back-link" href="${pageContext.request.contextPath}/login.jsp">Inicia sesión</a></p>
</main></body></html>

