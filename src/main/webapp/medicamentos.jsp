<%-- Inventario: listado, búsqueda y formularios para crear o editar medicamentos. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List,com.farmacia.dao.MedicamentoDAO,com.farmacia.modelo.medicamento,com.farmacia.util.HtmlUtil" %>
<% request.setAttribute("activePage", "medicamentos"); List<medicamento> lista = new MedicamentoDAO().listar(); %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Medicamentos | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1>Gestión de medicamentos</h1><p>Consulta y mantén actualizado el inventario.</p></div><button class="btn" type="button" onclick="abrirFormulario()">＋ Agregar medicamento</button></div>
<%-- Confirma el resultado de la última operación sin mostrar datos sensibles. --%>
<% if ("1".equals(request.getParameter("success"))) { %><div class="notice">Medicamento guardado correctamente.</div><% } %>
<% if ("1".equals(request.getParameter("deleted"))) { %><div class="notice">Medicamento eliminado.</div><% } %>
<% if ("1".equals(request.getParameter("error"))) { %><div class="notice error">No se pudo completar la operación. Revisa los datos y la conexión.</div><% } %>
<section class="card">
<div class="toolbar"><label class="search"><input id="buscar" type="search" placeholder="Buscar por nombre o lote" aria-label="Buscar medicamento"></label><span class="muted"><%= lista.size() %> registros</span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Nombre</th><th>Lote</th><th>Fecha de vencimiento</th><th>Stock</th><th>Precio</th><th>Acciones</th></tr></thead><tbody>
<% if (lista.isEmpty()) { %><tr><td colspan="6" class="empty">No hay medicamentos registrados. Agrega el primero con el botón superior.</td></tr><% } %>
<% for (medicamento m : lista) { String fecha = m.getFechaVencimiento() == null ? "" : m.getFechaVencimiento(); %>
<tr><td><strong><%= HtmlUtil.escapar(m.getNombre()) %></strong></td><td><%= HtmlUtil.escapar(m.getLote()) %></td><td><%= HtmlUtil.escapar(fecha) %></td><td><span class="badge <%= m.getStock() <= 10 ? "warn" : "" %>"><%= m.getStock() %></span></td><td>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", m.getPrecio()) %></td><td><div class="inline-actions">
<button class="btn secondary small" type="button" data-id="<%= m.getIdMedicamento() %>" data-nombre="<%= HtmlUtil.escapar(m.getNombre()) %>" data-lote="<%= HtmlUtil.escapar(m.getLote()) %>" data-fecha="<%= HtmlUtil.escapar(fecha) %>" data-stock="<%= m.getStock() %>" data-precio="<%= m.getPrecio() %>" onclick="editarDesdeBoton(this)">Editar</button>
<form action="${pageContext.request.contextPath}/MedicamentoServlet" method="post" onsubmit="return confirm('¿Eliminar este medicamento?')"><input type="hidden" name="accion" value="eliminar"><input type="hidden" name="id" value="<%= m.getIdMedicamento() %>"><button class="btn danger small" type="submit">Eliminar</button></form>
</div></td></tr><% } %>
</tbody></table></div>
</section>
<div class="form-actions"><a class="btn secondary" href="${pageContext.request.contextPath}/panel.jsp">Volver al Dashboard</a></div>
</main></div>
<%-- El mismo formulario sirve para altas y modificaciones mediante el campo id. --%>
<div id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo-form"><section class="modal-box"><div class="modal-head"><h2 id="titulo-form">Agregar medicamento</h2><button class="close" type="button" onclick="cerrarFormulario()" aria-label="Cerrar">×</button></div>
<form action="${pageContext.request.contextPath}/MedicamentoServlet" method="post"><input type="hidden" id="id" name="id"><input type="hidden" name="accion" value="guardar">
<div class="form-grid"><div class="field"><label for="nombre">Nombre *</label><input id="nombre" name="nombre" maxlength="100" required></div><div class="field"><label for="lote">Lote *</label><input id="lote" name="lote" maxlength="30" required></div><div class="field"><label for="fecha">Fecha de vencimiento *</label><input id="fecha" type="date" name="fecha" required></div><div class="field"><label for="stock">Stock *</label><input id="stock" type="number" name="stock" min="0" required></div><div class="field"><label for="precio">Precio unitario *</label><input id="precio" type="number" name="precio" min="0" step="0.01" required></div></div>
<div class="form-actions"><button class="btn secondary" type="button" onclick="cerrarFormulario()">Cancelar</button><button class="btn" type="submit">Guardar medicamento</button></div></form></section></div>
<script>
// Filtra las filas de la tabla mientras se escribe en el buscador.
document.getElementById('buscar').addEventListener('input', function () {
  const termino = this.value.toLocaleLowerCase('es');
  document.querySelectorAll('#tabla tbody tr').forEach(fila => fila.hidden = !fila.innerText.toLocaleLowerCase('es').includes(termino));
});
// Abre el diálogo limpio para registrar un medicamento nuevo.
function abrirFormulario() {
  document.querySelector('#modal form').reset();
  document.getElementById('id').value = '';
  document.getElementById('titulo-form').textContent = 'Agregar medicamento';
  document.getElementById('modal').classList.add('open');
}
// Carga los datos seleccionados en el formulario de edición.
function editarDesdeBoton(boton) {
  ['id','nombre','lote','fecha','stock','precio'].forEach(campo => document.getElementById(campo).value = boton.dataset[campo] || '');
  document.getElementById('titulo-form').textContent = 'Editar medicamento';
  document.getElementById('modal').classList.add('open');
}
// Cierra el diálogo de medicamento.
function cerrarFormulario() { document.getElementById('modal').classList.remove('open'); }
</script></body></html>


