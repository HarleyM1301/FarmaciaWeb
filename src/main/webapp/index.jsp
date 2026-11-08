
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%-- Envía la ruta inicial directamente al formulario de acceso. --%>
<%
    response.sendRedirect(request.getContextPath() + "/login.jsp");
%>


