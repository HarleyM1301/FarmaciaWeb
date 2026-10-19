<%-- Ventas persistidas en MySQL; el DAO descuenta existencias en la misma transacción. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.*,java.sql.SQLException,com.farmacia.dao.MedicamentoDAO,com.farmacia.dao.MovimientoDAO,com.farmacia.modelo.medicamento,com.farmacia.util.HtmlUtil" %>
<%
request.setAttribute("activePage", "ventas");
List<Map<String,Object>> ventas = new ArrayList<>();
List<medicamento> medicamentos = new MedicamentoDAO().listar();
try { ventas = new MovimientoDAO().listar(true); } catch (SQLException e) { application.log("No se pudieron consultar las ventas", e); request.setAttribute("errorBD", true); }
%>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Ventas | Farmacia</title><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1>Gestión de ventas</h1><p>Registra ventas y consulta el historial.</p></div><button class="btn" type="button" onclick="abrir()">＋ Agregar venta</button></div>
<% if ("ok".equals(request.getParameter("estado"))) { %><div class="notice">Venta guardada y existencias actualizadas.</div><% } %>
<% if ("error".equals(request.getParameter("estado")) || Boolean.TRUE.equals(request.getAttribute("errorBD"))) { %><div class="notice error">No se pudo completar la venta. Verifica la base de datos, el medicamento y el stock disponible.</div><% } %>
<section class="card"><div class="toolbar"><label class="search"><input id="buscar" type="search" placeholder="Buscar venta, fecha o cliente"></label><span class="muted"><%= ventas.size() %> ventas</span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Venta</th><th>Fecha</th><th>Cliente</th><th>Medicamento</th><th>Cantidad</th><th>Total</th></tr></thead><tbody>
<% if (ventas.isEmpty()) { %><tr><td colspan="6" class="empty">Aún no hay ventas registradas.</td></tr><% } for (Map<String,Object> v : ventas) { %>
<tr><td>V<%= v.get("id") %></td><td><%= HtmlUtil.escapar(v.get("fecha")) %></td><td><%= HtmlUtil.escapar(v.get("tercero") == null || v.get("tercero").toString().isBlank() ? "Ocasional" : v.get("tercero")) %></td><td><%= HtmlUtil.escapar(v.get("medicamento")) %></td><td><%= v.get("cantidad") %></td><td>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", ((java.math.BigDecimal)v.get("total")).doubleValue()) %></td></tr>
<% } %></tbody></table></div></section><div class="form-actions"><a class="btn secondary" href="<%= request.getContextPath() %>/panel.jsp">Volver al Dashboard</a></div>
</main></div>
<div id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo"><section class="modal-box"><div class="modal-head"><h2 id="titulo">Nueva venta</h2><button class="close" type="button" onclick="cerrar()">×</button></div>
<form action="<%= request.getContextPath() %>/MovimientoServlet" method="post"><input type="hidden" name="tipo" value="venta"><div class="form-grid">
<div class="field"><label for="cliente">Cliente</label><input id="cliente" name="cliente" maxlength="120" placeholder="Cliente ocasional"></div><div class="field"><label for="fecha">Fecha</label><input id="fecha" name="fecha" type="date" required></div>
<div class="field full"><label for="medicamentoId">Medicamento</label><select id="medicamentoId" name="medicamentoId" required><option value="">Selecciona un medicamento</option><% for (medicamento m : medicamentos) { %><option value="<%= m.getIdMedicamento() %>" <%= m.getStock() < 1 ? "disabled" : "" %>><%= HtmlUtil.escapar(m.getNombre()) %> · lote <%= HtmlUtil.escapar(m.getLote()) %> · stock <%= m.getStock() %> · $<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", m.getPrecio()) %></option><% } %></select></div>
<div class="field"><label for="cantidad">Cantidad</label><input id="cantidad" name="cantidad" type="number" min="1" value="1" required></div><div class="field"><label for="metodo">Método de pago</label><select id="metodo" name="metodo"><option>Efectivo</option><option>Tarjeta</option><option>Transferencia</option></select></div></div>
<p class="muted">El precio se toma del inventario y el sistema descuenta las unidades al guardar.</p><div class="form-actions"><button class="btn secondary" type="button" onclick="cerrar()">Cancelar</button><button class="btn" type="submit">Guardar venta</button></div></form></section></div>
<script>
// Filtra el historial sin hacer otra consulta a la base de datos.
document.getElementById('buscar').addEventListener('input',function(){const q=this.value.toLocaleLowerCase('es');document.querySelectorAll('#tabla tbody tr').forEach(f=>f.hidden=!f.innerText.toLocaleLowerCase('es').includes(q))});
// Abre el formulario y coloca la fecha actual.
function abrir(){document.getElementById('fecha').value=new Date().toISOString().slice(0,10);document.getElementById('modal').classList.add('open')}
// Cierra el cuadro de registro.
function cerrar(){document.getElementById('modal').classList.remove('open')}
</script></body></html>

