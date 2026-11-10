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
<p class="muted">Elige una presentación CUM y completa los datos como los ves físicamente. Ejemplo: caja de 2 blísteres con 10 tabletas cada uno: unidad suelta Tableta, 20 unidades mínimas por caja y 2 blísteres por caja. El precio será por tableta. El stock se ingresa como cajas completas, blísteres sueltos y tabletas sueltas.</p><div class="form-grid"><div class="field full"><label for="buscarCatalogoInvima">Buscar en catálogo oficial CUM / INVIMA</label><input id="buscarCatalogoInvima" type="search" placeholder="Nombre, principio activo, registro o CUM" autocomplete="off" aria-controls="resultadosCatalogoInvima"><div id="resultadosCatalogoInvima" class="resultados-medicamento" role="listbox" aria-label="Presentaciones oficiales encontradas" hidden></div><small id="estadoCatalogoInvima" class="muted" aria-live="polite">Busca una presentación para completar el nombre y el registro INVIMA.</small></div><div class="field"><label for="nombre">Nombre *</label><input id="nombre" name="nombre" maxlength="100" required></div><div class="field"><label for="categoria">Grupo farmacológico *</label><input id="categoria" name="categoria" maxlength="50" list="gruposFarmacologicos" placeholder="Ej.: Analgésicos" required><datalist id="gruposFarmacologicos"><%@ include file="/WEB-INF/partials/grupos-farmacologicos.jspf" %></datalist></div><div class="field"><label for="registroInvima">Registro INVIMA</label><input id="registroInvima" name="registroInvima" maxlength="60" placeholder="Ej.: 2024M-000000-R1"></div><div class="field"><label for="lote">Lote</label><input id="lote" name="lote" maxlength="30" placeholder="Código del lote recibido (opcional)"></div><div class="field"><label for="fecha">Fecha de vencimiento</label><input id="fecha" type="date" name="fecha"></div><input id="stock" type="hidden" name="stock" value="0"><div class="field"><label for="cajasStockInicial">Cajas completas en existencia *</label><input id="cajasStockInicial" type="number" min="0" step="1" value="0" required></div><div class="field"><label for="blisteresStockInicial">Blísteres sueltos</label><input id="blisteresStockInicial" type="number" min="0" step="1" value="0"><small>Blísteres que no forman una caja completa.</small></div><div class="field"><label for="unidadesSueltasStockInicial">Unidades sueltas</label><input id="unidadesSueltasStockInicial" type="number" min="0" step="1" value="0"><small>Tabletas, cápsulas u otra unidad mínima fuera del blíster.</small></div><div class="field full"><small id="vistaPreviaStock" class="muted" aria-live="polite"></small></div><div class="field"><label for="unidadBase">Unidad que venderás por separado *</label><select id="unidadBase" name="unidadBase" required><option value="unidad">Unidad</option><option value="caja">Caja</option><option value="paquete">Paquete</option><option value="tableta">Tableta/comprimido</option><option value="capsula">Cápsula</option><option value="sobre">Sobre</option><option value="ml">Mililitro (ml)</option><option value="gota">Gota</option><option value="ampolla">Ampolla</option><option value="frasco">Frasco</option></select></div><div class="field"><label for="unidadesPorPresentacion">Unidades mínimas que trae cada caja *</label><input id="unidadesPorPresentacion" type="number" name="unidadesPorPresentacion" min="1" max="100000" value="1" required><small>Cuenta las unidades más pequeñas: por ejemplo, 20 tabletas por caja.</small></div><div class="field"><label for="blisteresPorCaja">Blísteres/sobres que trae cada caja (opcional)</label><input id="blisteresPorCaja" type="number" min="0" max="100000" step="1" value="0"><input id="unidadesPorBlister" type="hidden" name="unidadesPorBlister" value="0"><small>Escribe cuántos blísteres o sobres vienen dentro de una caja. El sistema calcula las unidades de cada blíster.</small><small id="vistaPreviaPresentacion" class="muted" aria-live="polite"></small></div><div class="field"><label for="precio">Precio por unidad mínima *</label><input id="precio" type="number" name="precio" min="0" step="0.01" required></div></div>
<div class="form-actions"><button class="btn secondary" type="button" onclick="cerrarFormulario()">Cancelar</button><button class="btn" type="submit">Guardar medicamento</button></div></form></section></div><% } %>
<script>
// Convierte las cajas, blísteres y unidades que la persona ingresa a unidades de inventario.
function actualizarVistaPreviaPresentacion() {
  // Lee la unidad más pequeña que se vende, por ejemplo tableta o cápsula.
  const base = document.getElementById('unidadBase').value;
  // Lee el total de unidades mínimas que trae una caja completa.
  const porCaja = Number(document.getElementById('unidadesPorPresentacion').value) || 0;
  // Lee cuántos blísteres o sobres trae la caja completa.
  const blisteresPorCaja = Number(document.getElementById('blisteresPorCaja').value) || 0;
  // Busca el campo oculto que usa el servidor para recibir el factor por blíster.
  const campoUnidadesPorBlister = document.getElementById('unidadesPorBlister');
  // Calcula cuántas unidades mínimas trae cada blíster cuando la división es exacta.
  const porBlister = blisteresPorCaja > 0 && porCaja % blisteresPorCaja === 0 ? porCaja / blisteresPorCaja : 0;
  // Pasa el factor calculado al campo que guarda la ficha en la base de datos.
  campoUnidadesPorBlister.value = String(porBlister);
  // Traduce los códigos internos de unidad a nombres que se puedan leer.
  const nombreUnidad = {unidad:'unidad',caja:'caja',paquete:'paquete',tableta:'tableta',capsula:'cápsula',sobre:'sobre',ml:'ml',gota:'gota',ampolla:'ampolla',frasco:'frasco'}[base] || base;
  // Localiza los campos para contar existencia en cajas, blísteres y unidades sueltas.
  const cajasStock = Number(document.getElementById('cajasStockInicial').value) || 0;
  const blisteresStock = Number(document.getElementById('blisteresStockInicial').value) || 0;
  const unidadesSueltasStock = Number(document.getElementById('unidadesSueltasStockInicial').value) || 0;
  // Convierte las presentaciones contadas a la cantidad total que entiende el inventario.
  const stockTotal = cajasStock * porCaja + blisteresStock * porBlister + unidadesSueltasStock;
  // Escribe el total convertido en el campo que espera el servidor.
  document.getElementById('stock').value = String(stockTotal);
  // Prepara la cuenta para mostrar cada tipo de empaque que aporta existencias.
  const partesStock = [];
  // Añade a la cuenta las cajas completas y su equivalencia en unidades mínimas.
  if (cajasStock > 0) partesStock.push(cajasStock + ' cajas × ' + porCaja);
  // Añade a la cuenta los blísteres sueltos y su equivalencia en unidades mínimas.
  if (blisteresStock > 0) partesStock.push(blisteresStock + ' blísteres × ' + porBlister);
  // Añade a la cuenta las unidades que no completan un blíster.
  if (unidadesSueltasStock > 0) partesStock.push(unidadesSueltasStock + ' sueltas');
  // Muestra la suma completa y su resultado en unidades mínimas.
  document.getElementById('vistaPreviaStock').textContent = (partesStock.length ? partesStock.join(' + ') + ' = ' : '')
    + stockTotal + ' ' + nombreUnidad + (stockTotal === 1 ? '' : 's') + ' en inventario.';
  // Permite ver la conversión entre caja, blíster y unidad mínima.
  const salida = document.getElementById('vistaPreviaPresentacion');
  // Impide guardar una caja cuyo contenido no se pueda repartir en blísteres iguales.
  const empaqueInvalido = blisteresPorCaja > 0 && (!porCaja || porCaja % blisteresPorCaja !== 0)
    || blisteresStock > 0 && porBlister === 0;
  // Explica por qué el número de blísteres no coincide con las unidades por caja.
  document.getElementById('blisteresPorCaja').setCustomValidity(empaqueInvalido ? 'Las unidades por caja deben dividirse exactamente entre los blísteres por caja, y el blíster debe estar configurado.' : '');
  // Indica el ejemplo corregible si el contenido del empaque no cuadra.
  if (empaqueInvalido) { salida.textContent = 'Revisa el contenido: las unidades de la caja deben dividirse exactamente entre sus blísteres.'; salida.classList.add('error'); return; }
  // Quita el aviso de error cuando la presentación ya es válida.
  salida.classList.remove('error');
  // Muestra una instrucción mientras falte indicar cuántas unidades trae la caja.
  if (!porCaja) { salida.textContent = 'Escribe cuántas unidades mínimas trae una caja.'; return; }
  // Forma la equivalencia principal entre una caja y sus unidades mínimas.
  const textoCaja = '1 caja = ' + porCaja + ' ' + nombreUnidad + (porCaja === 1 ? '' : 's');
  // Añade cuántos blísteres vienen en la caja y cuántas unidades trae cada uno.
  salida.textContent = blisteresPorCaja > 0
    ? textoCaja + ' = ' + blisteresPorCaja + (blisteresPorCaja === 1 ? ' blíster' : ' blísteres') + ' de ' + porBlister + ' ' + nombreUnidad + (porBlister === 1 ? '' : 's') + '. El precio se registra por ' + nombreUnidad + '.'
    : textoCaja + '. El precio se registra por ' + nombreUnidad + '.';
}
// Recalcula las equivalencias al cambiar la unidad mínima.
document.getElementById('unidadBase').addEventListener('change', actualizarVistaPreviaPresentacion);
// Recalcula los factores cuando se cambia el total de unidades por caja.
document.getElementById('unidadesPorPresentacion').addEventListener('input', actualizarVistaPreviaPresentacion);
// Recalcula las unidades por blíster cuando se cambia cuántos blísteres trae la caja.
document.getElementById('blisteresPorCaja').addEventListener('input', actualizarVistaPreviaPresentacion);
// Recalcula el stock al cambiar las cajas completas existentes.
document.getElementById('cajasStockInicial').addEventListener('input', actualizarVistaPreviaPresentacion);
// Recalcula el stock al cambiar los blísteres sueltos existentes.
document.getElementById('blisteresStockInicial').addEventListener('input', actualizarVistaPreviaPresentacion);
// Recalcula el stock al cambiar las unidades que están sueltas.
document.getElementById('unidadesSueltasStockInicial').addEventListener('input', actualizarVistaPreviaPresentacion);
// Dibuja las equivalencias iniciales al cargar la pantalla.
actualizarVistaPreviaPresentacion();
// Desglosa una existencia guardada para mostrarla en cajas, blísteres y unidades sueltas.
function cargarExistenciaEnPresentaciones(stock, porCaja, porBlister) {
  // Calcula cuántas cajas completas hay en el stock actual.
  const cajas = porCaja > 0 ? Math.floor(stock / porCaja) : 0;
  // Separa las unidades que sobran después de contar las cajas completas.
  const restanteTrasCajas = stock - cajas * porCaja;
  // Calcula los blísteres enteros que caben en las unidades restantes.
  const blisteres = porBlister > 0 ? Math.floor(restanteTrasCajas / porBlister) : 0;
  // Calcula las unidades mínimas que quedan fuera de caja y blíster.
  const unidadesSueltas = restanteTrasCajas - blisteres * porBlister;
  // Coloca el desglose en los campos que la persona puede entender y ajustar.
  document.getElementById('cajasStockInicial').value = String(cajas);
  document.getElementById('blisteresStockInicial').value = String(blisteres);
  document.getElementById('unidadesSueltasStockInicial').value = String(unidadesSueltas);
}
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
  // Copia a la ventana de edición los datos generales, el lote y el precio.
  ['id','nombre','categoria','registroInvima','lote','fecha','precio'].forEach(campo => document.getElementById(campo).value = boton.dataset[campo] || '');
  // Restaura la unidad mínima que usa esta ficha para controlar existencias.
  document.getElementById('unidadBase').value = boton.dataset.unidadBase || 'unidad';
  // Restaura cuántas unidades mínimas representa una caja de esta presentación.
  document.getElementById('unidadesPorPresentacion').value = boton.dataset.unidadesPorPresentacion || '1';
  // Lee el factor de unidades mínimas por blíster que ya está guardado.
  const porBlister = Number(boton.dataset.unidadesPorBlister) || 0;
  // Calcula cuántos blísteres trae la caja para mostrarlos en el nuevo formulario.
  document.getElementById('blisteresPorCaja').value = porBlister > 0
    ? String(Number(boton.dataset.unidadesPorPresentacion) / porBlister) : '0';
  // Desglosa el stock guardado en cajas completas, blísteres y unidades sueltas.
  cargarExistenciaEnPresentaciones(Number(boton.dataset.stock) || 0,
    Number(boton.dataset.unidadesPorPresentacion) || 1, porBlister);
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


