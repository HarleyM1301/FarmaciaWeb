<%-- Inventario: listado, búsqueda y formularios para crear o editar medicamentos. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List,com.farmacia.dao.MedicamentoDAO,com.farmacia.modelo.medicamento,com.farmacia.util.HtmlUtil" %>
<% request.setAttribute("activePage", "medicamentos"); List<medicamento> lista = new MedicamentoDAO().listar(); String filtroInventario = request.getParameter("filtro"); String busquedaInicial = request.getParameter("buscar"); java.time.LocalDate hoyFiltro = java.time.LocalDate.now(); if ("vencidos".equals(filtroInventario)) { lista.removeIf(m -> { try { return m.getFechaVencimiento() == null || m.getFechaVencimiento().isBlank() || java.time.LocalDate.parse(m.getFechaVencimiento()).isAfter(hoyFiltro.plusDays(90)); } catch (java.time.format.DateTimeParseException e) { return true; } }); } else if ("bajo".equals(filtroInventario)) { lista.removeIf(m -> m.getStock() > 10); } String tituloInventario = "vencidos".equals(filtroInventario) ? "Medicamentos vencidos o por vencer" : "bajo".equals(filtroInventario) ? "Medicamentos con stock bajo" : "Gestión de medicamentos"; String rolInventario = java.text.Normalizer.normalize(String.valueOf(session.getAttribute("rol")), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT); boolean puedeAdministrarInventario = rolInventario.equals("admin") || rolInventario.equals("administrador") || rolInventario.equals("farmaceutico"); %>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Medicamentos | Farmacia</title><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1><%= tituloInventario %></h1><p>Consulta y mantén actualizado el inventario.</p></div><% if (puedeAdministrarInventario) { %><button class="btn" type="button" onclick="abrirFormulario()">＋ Agregar medicamento</button><% } %></div>
<%-- Confirma el resultado de la última operación sin mostrar datos sensibles. --%>
<% if ("1".equals(request.getParameter("success"))) { %><div class="notice">Medicamento guardado correctamente.</div><% } %>
<% if ("1".equals(request.getParameter("deleted"))) { %><div class="notice">Medicamento eliminado.</div><% } %>
<% if ("historial".equals(request.getParameter("error"))) { %><div class="notice error">No se puede eliminar este medicamento porque está asociado a compras o ventas. Se conserva para proteger el historial.</div><% } else if ("1".equals(request.getParameter("error"))) { %><div class="notice error">No se pudo completar la eliminación. Revisa la conexión a MySQL o el registro del servidor para ver el motivo.</div><% } %>
<section class="card">
<div class="toolbar"><label class="search"><input id="buscar" type="search" value="<%= HtmlUtil.escapar(busquedaInicial) %>" placeholder="Escribe letras iniciales del medicamento" aria-label="Buscar medicamento"></label><span class="muted"><span id="cantidadResultados"><%= lista.size() %></span> registros <%= "existencias".equals(filtroInventario) ? " · total en unidades base" : "" %></span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Nombre</th><th>Registro INVIMA</th><th>Lote</th><th>Fecha de vencimiento</th><th>Stock</th><th>Precio</th><% if (puedeAdministrarInventario) { %><th>Acciones</th><% } %></tr></thead><tbody>
<% if (lista.isEmpty()) { %><tr><td colspan="<%= puedeAdministrarInventario ? 7 : 6 %>" class="empty">No hay medicamentos registrados.</td></tr><% } %>
<% for (medicamento m : lista) { String fecha = m.getFechaVencimiento() == null ? "" : m.getFechaVencimiento(); %>
<tr><td><strong><%= HtmlUtil.escapar(m.getNombre()) %></strong></td><td><%= HtmlUtil.escapar(m.getRegistroInvima() == null || m.getRegistroInvima().isBlank() ? "Sin registrar" : m.getRegistroInvima()) %></td><td><%= HtmlUtil.escapar(m.getLote() == null || m.getLote().isBlank() ? "Sin registrar" : m.getLote()) %></td><td><%= HtmlUtil.escapar(fecha.isBlank() ? "Sin registrar" : fecha) %></td><td><span class="badge <%= m.getStock() <= 10 ? "warn" : "" %>"><%= HtmlUtil.escapar(m.getStockDesglosado()) %></span></td><td>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", m.getPrecio()) %></td><% if (puedeAdministrarInventario) { %><td><div class="inline-actions">
<button class="btn secondary small" type="button" data-id="<%= m.getIdMedicamento() %>" data-nombre="<%= HtmlUtil.escapar(m.getNombre()) %>" data-categoria="<%= HtmlUtil.escapar(m.getCategoria()) %>" data-registro-invima="<%= HtmlUtil.escapar(m.getRegistroInvima()) %>" data-lote="<%= HtmlUtil.escapar(m.getLote()) %>" data-fecha="<%= HtmlUtil.escapar(fecha) %>" data-stock="<%= m.getStock() %>" data-precio="<%= m.getPrecio() %>" data-unidad-base="<%= HtmlUtil.escapar(m.getUnidadBase()) %>" data-unidades-por-presentacion="<%= m.getUnidadesPorPresentacion() %>" data-unidades-por-blister="<%= m.getUnidadesPorBlister() %>" data-catalogo-id="<%= m.getCatalogoMedicamentoId() == null ? "" : m.getCatalogoMedicamentoId() %>" onclick="editarDesdeBoton(this)">Editar</button>
<form action="${pageContext.request.contextPath}/MedicamentoServlet" method="post" onsubmit="return confirm('¿Eliminar este medicamento?')"><input type="hidden" name="accion" value="eliminar"><input type="hidden" name="id" value="<%= m.getIdMedicamento() %>"><button class="btn danger small" type="submit">Eliminar</button></form>
</div></td><% } %></tr><% } %>
</tbody></table></div>
</section>
<div class="form-actions"><a class="btn secondary" href="${pageContext.request.contextPath}/panel.jsp">Volver al Dashboard</a></div>
</main></div>
<%-- El mismo formulario sirve para altas y modificaciones mediante el campo id. --%>
<% if (puedeAdministrarInventario) { %><div id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo-form"><section class="modal-box"><div class="modal-head"><h2 id="titulo-form">Agregar medicamento</h2><button class="close" type="button" onclick="cerrarFormulario()" aria-label="Cerrar">×</button></div>
<form action="${pageContext.request.contextPath}/MedicamentoServlet" method="post"><input type="hidden" id="id" name="id"><input type="hidden" name="accion" value="guardar"><input type="hidden" id="catalogoMedicamentoId" name="catalogoMedicamentoId">
<p class="muted">Elige una presentación CUM y define la unidad mínima que venderás suelta. Ejemplo: para un blíster de 10 tabletas, elige Tableta y escribe 10 en «Unidades base por blíster». El precio será por tableta y también podrás vender el blíster completo.</p><div class="form-grid"><div class="field full"><label for="buscarCatalogoInvima">Buscar en catálogo oficial CUM / INVIMA</label><input id="buscarCatalogoInvima" type="search" placeholder="Nombre, principio activo, registro o CUM" autocomplete="off" aria-controls="resultadosCatalogoInvima"><div id="resultadosCatalogoInvima" class="resultados-medicamento" role="listbox" aria-label="Presentaciones oficiales encontradas" hidden></div><small id="estadoCatalogoInvima" class="muted" aria-live="polite">Busca una presentación para completar el nombre y el registro INVIMA.</small></div><div class="field"><label for="nombre">Nombre *</label><input id="nombre" name="nombre" maxlength="100" required></div><div class="field"><label for="categoria">Grupo farmacológico *</label><input id="categoria" name="categoria" maxlength="50" list="gruposFarmacologicos" placeholder="Ej.: Analgésicos" required><datalist id="gruposFarmacologicos"><%@ include file="/WEB-INF/partials/grupos-farmacologicos.jspf" %></datalist></div><div class="field"><label for="registroInvima">Registro INVIMA</label><input id="registroInvima" name="registroInvima" maxlength="60" placeholder="Ej.: 2024M-000000-R1"></div><div class="field"><label for="lote">Lote</label><input id="lote" name="lote" maxlength="30" placeholder="Código del lote recibido (opcional)"></div><div class="field"><label for="fecha">Fecha de vencimiento</label><input id="fecha" type="date" name="fecha"></div><div class="field"><label for="stock">Stock en unidad base *</label><input id="stock" type="number" name="stock" min="0" required></div><div class="field"><label for="unidadBase">Unidad mínima del inventario *</label><select id="unidadBase" name="unidadBase" required><option value="unidad">Unidad</option><option value="caja">Caja</option><option value="paquete">Paquete</option><option value="tableta">Tableta/comprimido</option><option value="capsula">Cápsula</option><option value="sobre">Sobre</option><option value="ml">Mililitro (ml)</option><option value="gota">Gota</option><option value="ampolla">Ampolla</option><option value="frasco">Frasco</option></select></div><div class="field"><label for="unidadesPorPresentacion">Unidades base por caja/paquete *</label><input id="unidadesPorPresentacion" type="number" name="unidadesPorPresentacion" min="1" max="100000" value="1" required><small>Ejemplo: una caja de 2 blísteres con 10 tabletas cada uno contiene 20 tabletas.</small></div><div class="field"><label for="unidadesPorBlister">Unidades base por blíster (opcional)</label><input id="unidadesPorBlister" type="number" name="unidadesPorBlister" min="0" max="100000" value="0"><small>Escribe tabletas por blíster si la unidad mínima es Tableta. El número debe dividir exactamente el contenido de la caja.</small><small id="vistaPreviaPresentacion" class="muted" aria-live="polite"></small></div><div class="field"><label for="precio">Precio de venta por unidad base *</label><input id="precio" type="number" name="precio" min="0" step="0.01" required></div></div>
<div class="form-actions"><button class="btn secondary" type="button" onclick="cerrarFormulario()">Cancelar</button><button class="btn" type="submit">Guardar medicamento</button></div></form></section></div><% } %>
<script>
// Explica la relación entre la caja, el blíster y la unidad mínima antes de guardar.
function actualizarVistaPreviaPresentacion() {
  // Lee la unidad mínima configurada para este producto, como tableta o sobre.
  const base = document.getElementById('unidadBase').value;
  // Lee cuántas unidades mínimas caben en la caja o paquete.
  const porCaja = Number(document.getElementById('unidadesPorPresentacion').value) || 0;
  // Lee cuántas unidades mínimas contiene cada blíster.
  const porBlister = Number(document.getElementById('unidadesPorBlister').value) || 0;
  // Traduce los códigos internos de unidad a los nombres que verá la persona.
  const nombreUnidad = {unidad:'unidad', caja:'caja', paquete:'paquete', tableta:'tableta', capsula:'cápsula', sobre:'sobre', ml:'ml', gota:'gota', ampolla:'ampolla', frasco:'frasco'}[base] || base;
  // Busca el texto donde se mostrará el ejemplo de conversión.
  const salida = document.getElementById('vistaPreviaPresentacion');
  // Advierte si se configuró el sobre como unidad mínima aunque se ingresen tabletas por blíster.
  if (porBlister > 0 && base === 'sobre') {
    // Aclara que el factor se interpreta en unidades mínimas y no en otra presentación.
    salida.textContent = 'Ojo: así se configuró unidad mínima “sobre”; ' + porBlister + ' por blíster significa ' + porBlister + ' sobres. Para un blíster de ' + porBlister + ' tabletas, cambia la unidad mínima a Tableta.';
    // Marca visualmente la advertencia para que se note antes de guardar.
    salida.classList.add('error');
    // Detiene la vista previa normal mientras la configuración pueda ser confusa.
    return;
  }
  // Quita la advertencia anterior cuando la configuración ya no tiene ese problema.
  salida.classList.remove('error');
  // Pide el contenido de la caja antes de calcular las equivalencias.
  if (!porCaja) { salida.textContent = 'Indica cuántas unidades mínimas trae la caja.'; return; }
  // Forma la equivalencia entre una caja y su cantidad de unidades mínimas.
  const textoCaja = '1 caja = ' + porCaja + ' ' + nombreUnidad + (porCaja === 1 ? '' : 's');
  // Si el blíster divide exactamente la caja, muestra también cuántos blísteres contiene.
  salida.textContent = porBlister > 0 && porCaja % porBlister === 0
    ? textoCaja + ' = ' + (porCaja / porBlister) + ' blíster(es) de ' + porBlister + ' ' + nombreUnidad + '(es). Precio de venta: por ' + nombreUnidad + '.'
    : textoCaja + '. El precio de venta se registra por ' + nombreUnidad + '.';
}
// Actualiza la vista previa cuando cambia la unidad mínima.
document.getElementById('unidadBase').addEventListener('change', actualizarVistaPreviaPresentacion);
// Actualiza la vista previa cuando cambia el contenido de la caja.
document.getElementById('unidadesPorPresentacion').addEventListener('input', actualizarVistaPreviaPresentacion);
// Actualiza la vista previa cuando cambia el contenido del blíster.
document.getElementById('unidadesPorBlister').addEventListener('input', actualizarVistaPreviaPresentacion);
// Muestra la conversión inicial al abrir la página.
actualizarVistaPreviaPresentacion();
// Filtra las filas de la tabla mientras se escribe en el buscador.
document.getElementById('buscar').addEventListener('input', function () {
    const termino = this.value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es');
  let visibles = 0;
  document.querySelectorAll('#tabla tbody tr').forEach(fila => {
    const nombre = (fila.cells[0]?.innerText || '').trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es');
    const coincide = !termino || nombre.includes(termino);
    fila.hidden = !coincide;
    if (coincide) visibles++;
  });
  document.getElementById('cantidadResultados').textContent = visibles;
});
document.getElementById('buscar').dispatchEvent(new Event('input'));
// Busca en el catálogo CUM para vincular una ficha nueva con una presentación oficial.
let temporizadorCatalogoInvima;
document.getElementById('buscarCatalogoInvima').addEventListener('input', function () {
  window.clearTimeout(temporizadorCatalogoInvima);
  const consulta = this.value.trim();
  const resultados = document.getElementById('resultadosCatalogoInvima');
  const estado = document.getElementById('estadoCatalogoInvima');
  document.getElementById('catalogoMedicamentoId').value = '';
  if (consulta.length < 2) { resultados.replaceChildren(); resultados.hidden = true; estado.textContent = 'Escribe al menos 2 caracteres para buscar.'; return; }
  estado.textContent = 'Buscando presentaciones oficiales…';
  temporizadorCatalogoInvima = window.setTimeout(async () => {
    try {
      const url = new URL('CatalogoMedicamentoServlet', window.location.href);
      url.searchParams.set('q', consulta);
      const respuesta = await fetch(url);
      if (!respuesta.ok) throw new Error('No fue posible consultar el catálogo.');
      const filas = await respuesta.json();
      resultados.replaceChildren();
      filas.forEach(item => {
        const opcion = document.createElement('button');
        opcion.type = 'button'; opcion.className = 'resultado-medicamento'; opcion.setAttribute('role', 'option');
        opcion.textContent = [item.producto, item.componentes, item.forma, item.registro, 'CUM ' + item.cum].filter(Boolean).join(' · ');
        opcion.addEventListener('click', () => {
          document.getElementById('catalogoMedicamentoId').value = item.id;
          document.getElementById('nombre').value = (item.producto || '').slice(0, 100);
          document.getElementById('registroInvima').value = (item.registro || '').slice(0, 60);
          document.getElementById('buscarCatalogoInvima').value = item.producto || '';
          resultados.hidden = true;
          estado.textContent = 'Presentación seleccionada · CUM ' + item.cum + '. Completa grupo farmacológico, empaque, precio y stock.';
        });
        resultados.appendChild(opcion);
      });
      resultados.hidden = filas.length === 0;
      estado.textContent = filas.length ? filas.length + ' presentaciones encontradas. Selecciona la correcta.' : 'No se encontraron presentaciones para esa búsqueda.';
    } catch (error) { resultados.hidden = true; estado.textContent = 'No se pudo consultar el catálogo. Revisa la conexión y vuelve a intentar.'; }
  }, 250);
});
document.getElementById('nombre').addEventListener('input', function () {
  if (document.getElementById('catalogoMedicamentoId').value) {
    document.getElementById('catalogoMedicamentoId').value = '';
    document.getElementById('estadoCatalogoInvima').textContent = 'Cambiaste el nombre; la ficha quedó desvinculada del CUM seleccionado.';
  }
});
// Abre el diálogo limpio para registrar un medicamento nuevo.
function abrirFormulario() {
  document.querySelector('#modal form').reset();
  document.getElementById('id').value = '';
  document.getElementById('estadoCatalogoInvima').textContent = 'Busca una presentación para completar el nombre y el registro INVIMA.';
  document.getElementById('resultadosCatalogoInvima').hidden = true;
  document.getElementById('titulo-form').textContent = 'Agregar medicamento';
  actualizarVistaPreviaPresentacion();
  document.getElementById('modal').classList.add('open');
}
// Carga los datos seleccionados en el formulario de edición.
function editarDesdeBoton(boton) {
  ['id','nombre','categoria','registroInvima','lote','fecha','stock','precio'].forEach(campo => document.getElementById(campo).value = boton.dataset[campo] || '');
  document.getElementById('unidadBase').value = boton.dataset.unidadBase || 'unidad';
  document.getElementById('unidadesPorPresentacion').value = boton.dataset.unidadesPorPresentacion || '1';
  document.getElementById('unidadesPorBlister').value = boton.dataset.unidadesPorBlister || '0';
  document.getElementById('catalogoMedicamentoId').value = boton.dataset.catalogoId || '';
  document.getElementById('buscarCatalogoInvima').value = '';
  document.getElementById('resultadosCatalogoInvima').hidden = true;
  document.getElementById('estadoCatalogoInvima').textContent = boton.dataset.catalogoId ? 'Esta ficha ya está vinculada a una presentación CUM.' : 'Busca una presentación para completar el nombre y el registro INVIMA.';
  document.getElementById('titulo-form').textContent = 'Editar medicamento';
  actualizarVistaPreviaPresentacion();
  document.getElementById('modal').classList.add('open');
}
// Cierra el diálogo de medicamento.
function cerrarFormulario() { document.getElementById('modal').classList.remove('open'); }
</script></body></html>


