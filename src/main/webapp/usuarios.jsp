<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.*,com.farmacia.dao.UsuarioDAO,com.farmacia.modelo.Usuario,com.farmacia.util.HtmlUtil" %>
<%-- Carga la lista de cuentas y marca este módulo como activo en la navegación lateral. --%>
<% request.setAttribute("activePage", "usuarios"); List<Usuario> usuarios = new UsuarioDAO().listarUsuarios(); String estado = request.getParameter("estado"); %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Usuarios | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<%-- El botón reutiliza el formulario de registro; al venir de una sesión admin permite crear administradores. --%>
<div class="page-heading"><div><h1>Usuarios</h1><p>Administra las cuentas y sus perfiles.</p></div><a class="btn" href="${pageContext.request.contextPath}/registro.jsp">+ Crear usuario</a></div>
<%-- Informa si la acción anterior guardó, eliminó o no pudo modificar una cuenta. --%>
<% if ("guardado".equals(estado)) { %><div class="notice">Cambios guardados.</div><% } else if ("eliminado".equals(estado)) { %><div class="notice">Usuario eliminado.</div><% } else if (estado != null) { %><div class="notice error">No se pudo completar la operación. Revisa los datos o las relaciones de la cuenta.</div><% } %>
<%-- Presenta una fila editable por cuenta; los valores se escapan antes de insertarse en HTML. --%>
<section class="card"><div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Usuario</th><th>Perfil</th><th>Acciones</th></tr></thead><tbody>
<%-- Cada formulario queda asociado a sus campos mediante el atributo form, aunque estén en varias celdas. --%>
<% for (Usuario u : usuarios) { String formId = "editar-" + u.getIdUsuario(); %><tr>
<td><input form="<%= formId %>" name="nombre" maxlength="100" required value="<%= HtmlUtil.escapar(u.getNombre()) %>"></td><td><input form="<%= formId %>" name="usuario" pattern="[A-Za-z0-9._-]{3,40}" maxlength="40" required value="<%= HtmlUtil.escapar(u.getUsuario()) %>"></td>
<%-- La contraseña solo se cambia si se escribe; el servidor guarda su hash y nunca lo muestra aquí. --%>
<td><select form="<%= formId %>" name="rol"><option value="Usuario" <%= "Usuario".equalsIgnoreCase(u.getRol()) ? "selected" : "" %>>Usuario</option><option value="Administrador" <%= "Administrador".equalsIgnoreCase(u.getRol()) || "Admin".equalsIgnoreCase(u.getRol()) ? "selected" : "" %>>Administrador</option></select><input form="<%= formId %>" name="contrasena" type="password" minlength="8" maxlength="72" placeholder="Nueva contraseña (opcional)" autocomplete="new-password"></td>
<%-- El formulario de edición y el formulario de borrado son independientes para enviar una sola acción. --%>
<td><form id="<%= formId %>" action="${pageContext.request.contextPath}/UsuarioServlet" method="post"><input type="hidden" name="accion" value="editar"><input type="hidden" name="id" value="<%= u.getIdUsuario() %>"><button class="btn secondary" type="submit">Guardar</button></form><form action="${pageContext.request.contextPath}/UsuarioServlet" method="post" onsubmit="return confirm('¿Eliminar esta cuenta?')"><input type="hidden" name="accion" value="eliminar"><input type="hidden" name="id" value="<%= u.getIdUsuario() %>"><button class="btn danger" type="submit">Eliminar</button></form></td></tr><% } %>
</tbody></table></div></section></main></div></body></html>
