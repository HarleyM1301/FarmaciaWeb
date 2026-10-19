<%-- Panel principal: resumen del inventario y acceso rápido a los módulos. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.farmacia.dao.MedicamentoDAO" %>
<%@ page import="com.farmacia.modelo.medicamento" %>
<%@ page import="java.util.List" %>
<%@ page import="com.farmacia.util.HtmlUtil" %>
<%@ page import="com.farmacia.dao.MovimientoDAO,java.util.Map,java.util.ArrayList,java.sql.SQLException" %>
<% request.setAttribute("activePage", "panel"); List<medicamento> medicamentosPanel = new MedicamentoDAO().listar(); List<Map<String,Object>> ventasPanel = new ArrayList<>(); try { ventasPanel = new MovimientoDAO().listar(true); } catch (SQLException e) { application.log("No se pudo cargar el resumen de ventas", e); } %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Dashboard | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %>
<main class="main">
    <div class="page-heading"><div><h1>Dashboard</h1><p>Bienvenido, <%= HtmlUtil.escapar(session.getAttribute("nombre")) %>. Este es el resumen de tu farmacia.</p></div><% if (gestionCompletaMenu) { %><a class="btn" href="${pageContext.request.contextPath}/medicamentos.jsp">＋ Agregar medicamento</a><% } %></div>
    <%-- Las tarjetas rápidas llevan a cada función principal. --%>
    <section class="grid grid-2" aria-label="Módulos principales">
        <% if (gestionCompletaMenu) { %><a class="card quick-card" href="medicamentos.jsp"><span class="stat-icon">💊</span><span>Gestión de medicamentos</span></a><% } %>
        <% if (puedeVenderMenu) { %><a class="card quick-card" href="ventas.jsp"><span class="stat-icon amber">🛒</span><span>Gestión de ventas</span></a><% } %>
        <% if (gestionCompletaMenu) { %><a class="card quick-card" href="compras.jsp"><span class="stat-icon green">▣</span><span>Gestión de compras</span></a>
        <a class="card quick-card" href="reportes.jsp"><span class="stat-icon purple">▥</span><span>Reportes</span></a><% } %>
    </section>
    <% if (gestionCompletaMenu) { %><h2 style="font-size:19px;margin:28px 0 14px">Resumen del inventario</h2>
    <section class="grid grid-4">
        <article class="card stat"><span class="stat-icon">💊</span><div><div class="stat-label">Medicamentos registrados</div><div class="stat-value"><%= medicamentosPanel.size() %></div></div></article>
        <% int stockTotal = 0; int porVencer = 0; java.time.LocalDate hoy = java.time.LocalDate.now(); for (medicamento med : medicamentosPanel) { stockTotal += med.getStock(); try { if (med.getFechaVencimiento() != null && !med.getFechaVencimiento().isBlank() && !java.time.LocalDate.parse(med.getFechaVencimiento()).isAfter(hoy.plusDays(90))) porVencer++; } catch (java.time.format.DateTimeParseException ignorado) { /* Una fecha inválida no detiene el dashboard. */ } } %>
        <article class="card stat"><span class="stat-icon green">▤</span><div><div class="stat-label">Unidades en stock</div><div class="stat-value"><%= stockTotal %></div></div></article>
        <article class="card stat"><span class="stat-icon amber">⌛</span><div><div class="stat-label">Vencidos o por vencer (90 días)</div><div class="stat-value"><%= porVencer %></div></div></article>
        <article class="card stat"><span class="stat-icon purple">↗</span><div><div class="stat-label">Ventas registradas</div><div class="stat-value"><%= ventasPanel.size() %></div></div></article>
    </section>
    <section class="card" style="margin-top:18px"><h2 style="font-size:17px;margin:0 0 13px">Acciones rápidas</h2><div class="inline-actions"><% if (gestionCompletaMenu) { %><a class="btn secondary" href="medicamentos.jsp">Ver inventario</a><% } if (puedeVenderMenu) { %><a class="btn secondary" href="ventas.jsp">Registrar una venta</a><% } if (gestionCompletaMenu) { %><a class="btn secondary" href="reportes.jsp">Consultar reportes</a><% } %></div></section><% } %>
</main></div></body></html>


