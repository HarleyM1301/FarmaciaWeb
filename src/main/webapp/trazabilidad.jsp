<%-- Configura la auditoría de inventario y conserva los caracteres en UTF-8. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.*,java.sql.SQLException,com.farmacia.dao.MovimientoDAO,com.farmacia.util.HtmlUtil" %>
<%-- Marca el módulo, consulta hasta 300 movimientos recientes y registra cualquier error SQL. --%>
<% request.setAttribute("activePage", "trazabilidad"); List<Map<String,Object>> movimientos = new ArrayList<>(); boolean error = false; try { movimientos = new MovimientoDAO().listarMovimientosInventario(300); } catch (SQLException e) { application.log("No se pudo consultar la trazabilidad del inventario", e); error = true; } %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Movimientos de inventario | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1>Movimientos de inventario</h1><p>Entradas y salidas registradas en ventas y recepción de compras.</p></div></div>
<% if (error) { %><div class="notice error">No se pudo cargar el historial. Verifica que hayas aplicado la migración V5.</div><% } %>
<section class="card"><div class="toolbar"><label class="search"><input id="buscar" type="search" placeholder="Buscar medicamento, lote, usuario o referencia"></label><span class="muted"><%= movimientos.size() %> movimientos recientes</span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Fecha</th><th>Tipo</th><th>Medicamento / lote</th><th>Cambio</th><th>Stock antes → después</th><th>Referencia</th><th>Usuario</th></tr></thead><tbody>
<% if (movimientos.isEmpty()) { %><tr><td colspan="7" class="empty">Aún no hay movimientos trazables.</td></tr><% } for (Map<String,Object> m : movimientos) { int cantidad = (Integer)m.get("cantidad"); %>
<tr><td><%= HtmlUtil.escapar(m.get("fecha")) %></td><td><span class="badge <%= "VENTA".equals(m.get("tipo")) ? "warn" : "" %>"><%= HtmlUtil.escapar(m.get("tipo")) %></span></td><td><strong><%= HtmlUtil.escapar(m.get("nombre")) %></strong><br><span class="muted">Lote <%= HtmlUtil.escapar(m.get("lote")) %></span></td><td><%= cantidad > 0 ? "+" : "" %><%= cantidad %> <%= HtmlUtil.escapar(m.get("unidad")) %></td><td><%= m.get("anterior") %> → <%= m.get("nuevo") %></td><td><%= HtmlUtil.escapar(m.get("referencia")) %></td><td><%= HtmlUtil.escapar(m.get("usuario")) %></td></tr>
<% } %></tbody></table></div></section>
<div class="form-actions"><a class="btn secondary" href="${pageContext.request.contextPath}/panel.jsp">Volver al Dashboard</a></div>
</main></div><script>document.getElementById('buscar').addEventListener('input',function(){const q=this.value.toLocaleLowerCase('es');document.querySelectorAll('#tabla tbody tr').forEach(f=>f.hidden=!f.innerText.toLocaleLowerCase('es').includes(q))});</script></body></html>
