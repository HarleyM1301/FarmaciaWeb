<%-- Declara UTF-8 y configura la página administrativa del catálogo CUM de INVIMA. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.farmacia.util.HtmlUtil" %>
<%-- Marca el elemento activo del menú lateral compartido. --%>
<% request.setAttribute("activePage","catalogo"); %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Catálogo INVIMA | Farmacia</title><link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/app.css"></head>
<body><div class="shell"><%@include file="/WEB-INF/partials/topbar.jspf"%><%@include file="/WEB-INF/partials/sidebar.jspf"%><main class="main">
<div class="page-heading"><div><h1>Catálogo de medicamentos INVIMA</h1><p>Medicamentos de uso humano registrados en Colombia, por presentación y código CUM.</p></div></div>
<%-- Informa el resultado o error de la última carga sin exponer detalles internos de la base. --%>
<% if(request.getParameter("importados")!=null){ %><div class="notice">Importación finalizada. Filas procesadas: <strong><%=HtmlUtil.escapar(request.getParameter("importados"))%></strong>.</div><% } %>
<% if(request.getParameter("error")!=null){ %><div class="notice error">No se pudo importar el archivo. Comprueba que sea el CSV original del conjunto CUM de INVIMA y revisa el registro de Tomcat.</div><% } %>
<% if(Boolean.TRUE.equals(request.getAttribute("errorCatalogo"))){ %><div class="notice error">No se pudo consultar la base de datos. Verifica que esté aplicada la migración V10.</div><% } %>
<section class="card"><h2>Estado del catálogo</h2><p>Presentaciones CUM almacenadas: <strong><%=request.getAttribute("cantidadCatalogo")==null?"—":request.getAttribute("cantidadCatalogo")%></strong></p>
<h3>Actualizar desde la fuente oficial</h3><p class="muted">Descarga el CSV del conjunto «Código Único de Medicamentos Vigentes» en Datos Abiertos Colombia y súbelo aquí. La importación actualiza los CUM existentes y conserva tus productos, compras e inventario.</p>
<p><a class="btn secondary" href="https://www.datos.gov.co/Salud-y-Protecci-n-Social/C-DIGO-NICO-DE-MEDICAMENTOS-VIGENTES/i7cb-raxc" target="_blank" rel="noopener">Abrir conjunto oficial de INVIMA</a></p>
<form action="<%=request.getContextPath()%>/CatalogoInvimaServlet" method="post" enctype="multipart/form-data" class="form-grid"><div class="field"><label for="archivoCatalogo">Archivo CSV oficial</label><input id="archivoCatalogo" name="archivoCatalogo" type="file" accept=".csv,text/csv" required></div><div class="field" style="align-self:end"><button class="btn" type="submit">Importar / actualizar catálogo</button></div></form>
<p class="muted">La búsqueda del catálogo aparecerá al recibir una factura para confirmar manualmente la marca, concentración, presentación y registro sanitario. El CUM identifica la presentación.</p></section>
<div class="form-actions"><a class="btn secondary" href="<%=request.getContextPath()%>/compras.jsp">Volver a Compras</a></div></main></div></body></html>
