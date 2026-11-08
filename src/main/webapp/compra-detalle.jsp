<%-- Configura UTF-8 y prepara la vista para revisar o modificar una factura de compra. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.*,java.sql.SQLException,com.farmacia.dao.MovimientoDAO,com.farmacia.util.HtmlUtil" %>
<%
// Marca el módulo activo y consulta la factura pedida mediante el parámetro id.
request.setAttribute("activePage", "compras");
Map<String,Object> compra = null;
try { compra = new MovimientoDAO().buscarCompra(Integer.parseInt(request.getParameter("id"))); }
catch (Exception e) { application.log("No se pudo consultar la factura", e); }
%>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Detalle de factura | Farmacia</title><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<%-- Distingue entre una factura inexistente y una factura que se puede revisar. --%>
<% if (compra == null) { %><div class="notice error">No se encontró la factura solicitada.</div><a class="btn secondary" href="<%= request.getContextPath() %>/compras.jsp">Volver a compras</a><% } else { %>
<div class="page-heading"><div><h1>Revisar factura <%= HtmlUtil.escapar(compra.get("numeroFactura")) %></h1><p>Consulta los productos recibidos y corrige los datos de la factura.</p></div></div>
<%-- Envía datos editados al servidor para validarlos y ajustar el stock de forma segura. --%>
<form action="<%= request.getContextPath() %>/CompraServlet" method="post">
<input type="hidden" name="accion" value="editar"><input type="hidden" name="id" value="<%= compra.get("id") %>">
<section class="card"><h2>Editar productos recibidos</h2><p class="muted">Puedes cambiar las cantidades y costos. El inventario se ajusta con la diferencia; si ya se vendieron unidades, no podrás reducir la compra por debajo del stock disponible.</p><div class="table-wrap"><table><thead><tr><th>Medicamento</th><th>Lote</th><th>Vencimiento</th><th>Cantidad</th><th>Costo unitario</th><th>Subtotal actual</th></tr></thead><tbody>
<% for (Map<String,Object> l : (List<Map<String,Object>>) compra.get("lineas")) { %><tr><td><%= HtmlUtil.escapar(l.get("nombre")) %></td><td><%= HtmlUtil.escapar(l.get("lote")) %></td><td><%= HtmlUtil.escapar(l.get("vencimiento")) %></td><td><input type="hidden" name="detalleId" value="<%= l.get("id") %>"><input type="number" name="cantidad" min="1" step="1" required value="<%= l.get("cantidad") %>"></td><td><input type="number" name="costo" min="0" step="0.01" required value="<%= l.get("costo") %>"></td><td>$<%= l.get("subtotal") %></td></tr><% } %>
</tbody></table></div></section>
<section class="card" style="margin-top:16px"><h2>Datos de factura</h2><div class="form-grid">
<div class="field"><label>Número de factura</label><input name="numeroFactura" maxlength="60" required value="<%= HtmlUtil.escapar(compra.get("numeroFactura")) %>"></div>
<div class="field"><label>Fecha</label><input type="date" name="fecha" required value="<%= compra.get("fecha") %>"></div>
<div class="field"><label>Total de la factura</label><input type="number" name="total" min="0" step="0.01" required value="<%= compra.get("total") %>"></div>
<div class="form-actions"><button class="btn" type="submit">Guardar cambios</button><a class="btn secondary" href="<%= request.getContextPath() %>/compras.jsp">Volver</a></div></div></section></form>
<section class="card" style="margin-top:16px"><h2>Anular factura</h2><p class="muted">Se descontarán del inventario las unidades recibidas. Solo se permite si aún hay existencias suficientes para revertirlas.</p><form action="<%= request.getContextPath() %>/CompraServlet" method="post" onsubmit="return confirm('¿Eliminar esta factura y revertir sus existencias del inventario? Esta acción no se puede deshacer.')"><input type="hidden" name="accion" value="eliminar"><input type="hidden" name="id" value="<%= compra.get("id") %>"><button class="btn danger" type="submit">Eliminar factura</button></form></section>
<% } %>
</main></div></body></html>
