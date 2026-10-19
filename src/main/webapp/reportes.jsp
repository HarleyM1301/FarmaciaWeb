<%-- Reportes de inventario y movimientos consultados desde MySQL. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.farmacia.dao.MedicamentoDAO,com.farmacia.dao.MovimientoDAO,com.farmacia.modelo.medicamento,com.farmacia.util.HtmlUtil,java.util.*,java.sql.SQLException,java.math.BigDecimal" %>
<%
request.setAttribute("activePage", "reportes");
List<medicamento> inventario = new MedicamentoDAO().listar();
List<Map<String,Object>> ventas = new ArrayList<>(), compras = new ArrayList<>();
boolean errorDatos = false;
try { MovimientoDAO dao = new MovimientoDAO(); ventas = dao.listar(true); compras = dao.listar(false); }
catch (SQLException e) { application.log("No se pudieron consultar los reportes", e); errorDatos = true; }
int unidades = 0, stockBajo = 0, vencidosOPorVencer = 0;
for (medicamento m : inventario) {
    unidades += m.getStock();
    if (m.getStock() <= 10) stockBajo++;
    try {
        if (m.getFechaVencimiento() != null && !m.getFechaVencimiento().isBlank()
                && !java.time.LocalDate.parse(m.getFechaVencimiento()).isAfter(java.time.LocalDate.now().plusDays(90))) {
            vencidosOPorVencer++;
        }
    } catch (java.time.format.DateTimeParseException ignorado) { /* Una fecha inválida no detiene los reportes. */ }
}
BigDecimal ingresos = BigDecimal.ZERO;
for (Map<String,Object> v : ventas) ingresos = ingresos.add((BigDecimal)v.get("total"));
%>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Reportes | Farmacia</title><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1>Reportes</h1><p>Resumen de inventario y movimientos registrados.</p></div><button class="btn secondary" onclick="window.print()">Imprimir reporte</button></div>
<% if (errorDatos) { %><div class="notice error">No se pudieron consultar ventas y compras. Revisa la conexión con MySQL.</div><% } %>
<div class="grid grid-4">
<article class="card stat"><span class="stat-icon">💊</span><div><div class="stat-label">Medicamentos</div><div class="stat-value"><%= inventario.size() %></div></div></article>
<article class="card stat"><span class="stat-icon green">▤</span><div><div class="stat-label">Unidades en inventario</div><div class="stat-value"><%= unidades %></div></div></article>
<article class="card stat"><span class="stat-icon amber">⚠</span><div><div class="stat-label">Vencidos o por vencer (90 días)</div><div class="stat-value"><%= vencidosOPorVencer %></div></div></article>
<article class="card stat"><span class="stat-icon purple">↗</span><div><div class="stat-label">Ventas registradas</div><div class="stat-value"><%= ventas.size() %></div></div></article>
</div>
<section class="grid grid-2" style="margin-top:18px">
<article class="card"><h2 style="font-size:17px;margin:0 0 10px">Ingresos por venta</h2><div class="stat-value" style="color:var(--green-dark)">$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", ingresos.doubleValue()) %></div><p class="muted"><%= ventas.size() %> ventas · <%= compras.size() %> compras registradas en total.</p>
<div class="chart-bars"><% List<Map<String,Object>> recientes = ventas.subList(Math.max(0, ventas.size() - Math.min(7, ventas.size())), ventas.size()); BigDecimal maximo = BigDecimal.ONE; for (Map<String,Object> v : recientes) if (((BigDecimal)v.get("total")).compareTo(maximo) > 0) maximo = (BigDecimal)v.get("total"); if (recientes.isEmpty()) { %><div class="empty" style="width:100%">Aún no hay ventas para graficar.</div><% } else { for (Map<String,Object> v : recientes) { int alto = Math.max(10, ((BigDecimal)v.get("total")).multiply(BigDecimal.valueOf(100)).divide(maximo, 0, java.math.RoundingMode.HALF_UP).intValue()); %><div class="bar-col"><span>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", ((BigDecimal)v.get("total")).doubleValue()) %></span><div class="bar" style="height:<%= alto %>%"></div><span><%= HtmlUtil.escapar(v.get("fecha")) %></span></div><% } } %></div></article>
<article class="card"><h2 style="font-size:17px;margin:0">Medicamentos con stock bajo</h2>
<% if (stockBajo == 0) { %><p class="empty">No hay productos con stock bajo.</p><% } else { for (medicamento m : inventario) { if (m.getStock() <= 10) { %><div class="progress-row"><span style="width:130px"><%= HtmlUtil.escapar(m.getNombre()) %></span><div class="progress"><span style="width:<%= Math.min(100, m.getStock() * 10) %>%"></span></div><strong><%= m.getStock() %></strong></div><% } } } %></article>
</section><div class="form-actions"><a class="btn secondary" href="<%= request.getContextPath() %>/panel.jsp">Volver al Dashboard</a></div>
</main></div></body></html>

