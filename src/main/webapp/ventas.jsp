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
<% if ("editada".equals(request.getParameter("estado"))) { %><div class="notice">Venta actualizada y existencias recalculadas.</div><% } %>
<% if ("eliminada".equals(request.getParameter("estado"))) { %><div class="notice">Venta eliminada y unidades devueltas al inventario.</div><% } %>
<% if ("error_edicion".equals(request.getParameter("estado"))) { %><div class="notice error">No se pudo modificar la venta. Revisa el stock, los datos o el registro de Tomcat.</div><% } %>
<% if ("error".equals(request.getParameter("estado")) || Boolean.TRUE.equals(request.getAttribute("errorBD"))) { %><div class="notice error">No se pudo completar la venta. Verifica la base de datos, el medicamento y el stock disponible.</div><% } %>
<section class="card"><div class="toolbar"><label class="search"><input id="buscar" type="search" value="<%= HtmlUtil.escapar(request.getParameter("buscar")) %>" placeholder="Buscar venta, fecha o cliente"></label><span class="muted"><%= ventas.size() %> ventas</span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Venta</th><th>Fecha</th><th>Cliente</th><th>Medicamento</th><th>Cantidad</th><th>Total</th><th>Acciones</th></tr></thead><tbody>
<% if (ventas.isEmpty()) { %><tr><td colspan="7" class="empty">Aún no hay ventas registradas.</td></tr><% } for (Map<String,Object> v : ventas) { String fechaEdicion = v.get("fecha") instanceof java.sql.Timestamp ? ((java.sql.Timestamp)v.get("fecha")).toLocalDateTime().toLocalDate().toString() : ""; String clienteVenta = v.get("tercero") == null || "Ocasional".equals(v.get("tercero")) ? "" : v.get("tercero").toString(); %>
<tr><td>V<%= v.get("id") %></td><td><%= HtmlUtil.escapar(v.get("fecha")) %></td><td><%= HtmlUtil.escapar(clienteVenta.isBlank() ? "Ocasional" : clienteVenta) %></td><td><%= HtmlUtil.escapar(v.get("medicamento")) %></td><td><%= v.get("cantidad") %></td><td>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", ((java.math.BigDecimal)v.get("total")).doubleValue()) %></td><td><div class="inline-actions"><button class="btn secondary small" type="button" data-id="<%= v.get("id") %>" data-fecha="<%= fechaEdicion %>" data-cliente="<%= HtmlUtil.escapar(clienteVenta) %>" data-producto="<%= v.get("productoId") %>" data-cantidad="<%= v.get("cantidad") %>" data-unidad-venta="<%= HtmlUtil.escapar(v.get("unidadVenta")) %>" data-cantidad-presentacion="<%= v.get("cantidadPresentacion") %>" data-metodo="<%= HtmlUtil.escapar(v.get("metodo")) %>" onclick="editarVenta(this)">Editar</button><form action="<%= request.getContextPath() %>/VentaServlet" method="post" onsubmit="return confirm('¿Eliminar esta venta y devolver sus unidades al inventario?')"><input type="hidden" name="accion" value="eliminar"><input type="hidden" name="id" value="<%= v.get("id") %>"><button class="btn danger small" type="submit">Eliminar</button></form></div></td></tr>
<% } %></tbody></table></div></section><div class="form-actions"><a class="btn secondary" href="<%= request.getContextPath() %>/panel.jsp">Volver al Dashboard</a></div>
</main></div>
<div id="modalEditar" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo-editar"><section class="modal-box"><div class="modal-head"><h2 id="titulo-editar">Editar venta</h2><button class="close" type="button" onclick="cerrarEditar()">×</button></div>
<form action="<%= request.getContextPath() %>/VentaServlet" method="post"><input type="hidden" name="accion" value="editar"><input type="hidden" id="ventaIdEditar" name="id"><div class="form-grid">
<div class="field"><label for="fechaEditar">Fecha</label><input id="fechaEditar" name="fecha" type="date" required></div><div class="field"><label for="clienteEditar">Cliente</label><input id="clienteEditar" name="cliente" maxlength="100" placeholder="Cliente ocasional"></div>
<div class="field full"><label for="buscarProductoEditar">Buscar medicamento</label><label class="search"><input id="buscarProductoEditar" type="search" placeholder="Escribe el nombre, lote o registro INVIMA" autocomplete="off" aria-controls="resultadosProductoEditar"></label><div id="resultadosProductoEditar" class="resultados-medicamento" role="listbox" aria-label="Medicamentos encontrados" hidden></div><small id="sinResultadosEditar" class="muted" hidden>No hay medicamentos que coincidan.</small><label for="productoEditar">Medicamento seleccionado</label><select id="productoEditar" name="productoId" required><% for (medicamento m : medicamentos) { String busqueda = m.getNombre()+" "+String.valueOf(m.getRegistroInvima())+" "+String.valueOf(m.getLote()); %><option value="<%= m.getIdMedicamento() %>" data-busqueda="<%= HtmlUtil.escapar(busqueda) %>" data-base="<%= HtmlUtil.escapar(m.getUnidadBase()) %>" data-factor="<%= m.getUnidadesPorPresentacion() %>" data-factor-blister="<%= m.getUnidadesPorBlister() %>" data-stock="<%= m.getStock() %>" data-precio="<%= m.getPrecio() %>" data-precio-blister="<%= m.getPrecioBlister() == null ? "" : m.getPrecioBlister() %>" data-precio-presentacion="<%= m.getPrecioPresentacion() == null ? "" : m.getPrecioPresentacion() %>"><%= HtmlUtil.escapar(m.getNombre()) %> · lote <%= HtmlUtil.escapar(m.getLote()) %> · stock actual <%= HtmlUtil.escapar(m.getStockDesglosado()) %></option><% } %></select></div>
<div class="field"><label for="unidadVentaEditar">Vender como</label><select id="unidadVentaEditar" name="unidadVenta" onchange="actualizarVentaUnidad('productoEditar','unidadVentaEditar','cantidadEditar','conversionEditar')"><option value="base">Unidad base</option><option value="blister" disabled>Blíster</option><option value="presentacion">Caja/paquete</option></select></div><div class="field"><label for="cantidadEditar">Cantidad en la presentación elegida</label><input id="cantidadEditar" name="cantidad" type="number" min="1" required><small id="conversionEditar" class="muted"></small></div><div class="field"><label for="metodoEditar">Método de pago</label><select id="metodoEditar" name="metodo"><option>Efectivo</option><option>Tarjeta</option><option>Transferencia</option></select></div></div><p class="muted">Al guardar, el total se recalcula con el precio actual y se ajusta el inventario. No se permite superar el stock disponible.</p><div class="form-actions"><button class="btn secondary" type="button" onclick="cerrarEditar()">Cancelar</button><button class="btn" type="submit">Guardar cambios</button></div></form></section></div>
<div id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo"><section class="modal-box"><div class="modal-head"><h2 id="titulo">Nueva venta</h2><button class="close" type="button" onclick="cerrar()">×</button></div>
<form action="<%= request.getContextPath() %>/MovimientoServlet" method="post"><input type="hidden" name="tipo" value="venta"><div class="form-grid">
<div class="field"><label for="cliente">Cliente</label><input id="cliente" name="cliente" maxlength="120" placeholder="Cliente ocasional"></div><div class="field"><label for="fecha">Fecha</label><input id="fecha" name="fecha" type="date" required></div>
<div class="field full"><label for="buscarMedicamento">Buscar medicamento</label><label class="search"><input id="buscarMedicamento" type="search" placeholder="Escribe el nombre, lote o registro INVIMA" autocomplete="off" aria-controls="resultadosMedicamento"></label><div id="resultadosMedicamento" class="resultados-medicamento" role="listbox" aria-label="Medicamentos encontrados" hidden></div><small id="sinResultadosVenta" class="muted" hidden>No hay medicamentos disponibles que coincidan.</small><label for="medicamentoId">Medicamento seleccionado</label><select id="medicamentoId" name="medicamentoId" required onchange="actualizarVentaUnidad('medicamentoId','unidadVenta','cantidad','conversionVenta')"><option value="">Selecciona un medicamento</option><% for (medicamento m : medicamentos) { String busqueda = m.getNombre()+" "+String.valueOf(m.getRegistroInvima())+" "+String.valueOf(m.getLote()); %><option value="<%= m.getIdMedicamento() %>" data-busqueda="<%= HtmlUtil.escapar(busqueda) %>" data-base="<%= HtmlUtil.escapar(m.getUnidadBase()) %>" data-factor="<%= m.getUnidadesPorPresentacion() %>" data-factor-blister="<%= m.getUnidadesPorBlister() %>" data-stock="<%= m.getStock() %>" data-precio="<%= m.getPrecio() %>" data-precio-blister="<%= m.getPrecioBlister() == null ? "" : m.getPrecioBlister() %>" data-precio-presentacion="<%= m.getPrecioPresentacion() == null ? "" : m.getPrecioPresentacion() %>" <%= m.getStock() < 1 ? "disabled" : "" %>><%= HtmlUtil.escapar(m.getNombre()) %> · lote <%= HtmlUtil.escapar(m.getLote()) %> · stock <%= HtmlUtil.escapar(m.getStockDesglosado()) %> · $<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", m.getPrecio()) %> por <%= HtmlUtil.escapar(m.getUnidadBase()) %></option><% } %></select></div>
<div class="field"><label for="unidadVenta">Vender como</label><select id="unidadVenta" name="unidadVenta" onchange="actualizarVentaUnidad('medicamentoId','unidadVenta','cantidad','conversionVenta')"><option value="base">Unidad base</option><option value="blister" disabled>Blíster</option><option value="presentacion">Caja/paquete</option></select></div><div class="field"><label for="cantidad">Cantidad en la presentación elegida</label><input id="cantidad" name="cantidad" type="number" min="1" value="1" required><small id="conversionVenta" class="muted"></small></div><div class="field"><label for="metodo">Método de pago</label><select id="metodo" name="metodo"><option>Efectivo</option><option>Tarjeta</option><option>Transferencia</option></select></div></div>
<p class="muted">El precio se toma del inventario y el sistema descuenta las unidades al guardar.</p><div class="form-actions"><button class="btn secondary" type="button" onclick="cerrar()">Cancelar</button><button class="btn" type="submit">Guardar venta</button></div></form></section></div>
<script>
// Filtra el historial sin hacer otra consulta a la base de datos.
document.getElementById('buscar').addEventListener('input',function(){const q=this.value.toLocaleLowerCase('es');document.querySelectorAll('#tabla tbody tr').forEach(f=>f.hidden=!f.innerText.toLocaleLowerCase('es').includes(q))});document.getElementById('buscar').dispatchEvent(new Event('input'));
// Abre el formulario y coloca la fecha actual.
function abrir(){document.querySelector('#modal form').reset();document.getElementById('fecha').value=new Date().toISOString().slice(0,10);document.getElementById('buscarMedicamento').value='';document.getElementById('medicamentoId').value='';document.getElementById('unidadVenta').value='base';filtrarMedicamentos('buscarMedicamento','medicamentoId','sinResultadosVenta');actualizarVentaUnidad('medicamentoId','unidadVenta','cantidad','conversionVenta');document.getElementById('modal').classList.add('open')}
// Cierra el cuadro de registro.
function cerrar(){document.getElementById('modal').classList.remove('open')}
// Filtra el selector por nombre, lote o registro INVIMA, ignorando mayúsculas y tildes.
function filtrarMedicamentos(campoId,selectorId,estadoId){const campo=document.getElementById(campoId);const selector=document.getElementById(selectorId);const consulta=normalizar(campo.value);const resultadosId=selectorId==='medicamentoId'?'resultadosMedicamento':'resultadosProductoEditar';const resultados=document.getElementById(resultadosId);const coincidencias=[...selector.options].filter(opcion=>opcion.value&&(!consulta||normalizar(opcion.dataset.busqueda||opcion.textContent).includes(consulta))&&!(selectorId==='medicamentoId'&&opcion.disabled));resultados.replaceChildren();for(const opcion of coincidencias.slice(0,20)){const boton=document.createElement('button');boton.type='button';boton.className='resultado-medicamento';boton.role='option';boton.textContent=opcion.textContent;boton.addEventListener('click',()=>{selector.value=opcion.value;selector.dispatchEvent(new Event('change',{bubbles:true}));campo.value=opcion.dataset.busqueda||opcion.textContent;resultados.hidden=true;document.getElementById(estadoId).hidden=true});resultados.appendChild(boton)}resultados.hidden=!consulta||coincidencias.length===0;document.getElementById(estadoId).hidden=coincidencias.length>0||!consulta;if(selectorId==='medicamentoId')actualizarVentaUnidad('medicamentoId','unidadVenta','cantidad','conversionVenta')}
function normalizar(texto){return (texto||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim()}
document.getElementById('buscarMedicamento').addEventListener('input',()=>filtrarMedicamentos('buscarMedicamento','medicamentoId','sinResultadosVenta'));
document.getElementById('buscarProductoEditar').addEventListener('input',()=>filtrarMedicamentos('buscarProductoEditar','productoEditar','sinResultadosEditar'));
// Calcula cuántas unidades de inventario y cuánto valor representa la presentación seleccionada.
function actualizarVentaUnidad(productoId, unidadId, cantidadId, conversionId) {
  // Localiza los controles de producto, presentación, cantidad y vista previa.
  const producto = document.getElementById(productoId);
  const unidad = document.getElementById(unidadId);
  const cantidad = document.getElementById(cantidadId);
  const salida = document.getElementById(conversionId);
  // Obtiene el producto elegido para leer sus factores y precio.
  const opcion = producto.selectedOptions[0];
  // Lee las unidades mínimas que contiene el blíster y la caja.
  const factorBlister = Number(opcion?.dataset.factorBlister) || 0;
  const factorCaja = Number(opcion?.dataset.factor) || 1;
  // Lee la unidad mínima usada por el inventario, por ejemplo tableta.
  const base = opcion?.dataset.base || 'unidad';
  // Lee el precio suelto y los precios opcionales guardados para blíster y caja.
  const precioUnidad = Number(opcion?.dataset.precio) || 0;
  const precioBlister = opcion?.dataset.precioBlister !== '' && opcion?.dataset.precioBlister != null
    ? Number(opcion.dataset.precioBlister) : precioUnidad * factorBlister;
  const precioCaja = opcion?.dataset.precioPresentacion !== '' && opcion?.dataset.precioPresentacion != null
    ? Number(opcion.dataset.precioPresentacion) : precioUnidad * factorCaja;
  // Formatea precios en pesos colombianos para mostrarlos junto a cada presentación.
  const formatoPrecio = valor => '$' + Math.round(valor).toLocaleString('es-CO');
  // Muestra el nombre y precio de la unidad mínima en la opción de venta individual.
  const opcionBase = unidad.querySelector('option[value="base"]');
  opcionBase.textContent = 'Unidad (' + base + ') · ' + formatoPrecio(precioUnidad);
  // Muestra cuántas unidades mínimas contiene y cuánto cuesta el blíster.
  const opcionBlister = unidad.querySelector('option[value="blister"]');
  opcionBlister.textContent = factorBlister > 0
    ? 'Blíster (' + factorBlister + ' ' + base + (factorBlister === 1 ? '' : 's') + ') · ' + formatoPrecio(precioBlister)
    : 'Blíster';
  // Deshabilita la venta por blíster si ese factor no está configurado.
  opcionBlister.disabled = factorBlister < 1;
  // Muestra la equivalencia y precio total de la caja o paquete.
  const opcionCaja = unidad.querySelector('option[value="presentacion"]');
  opcionCaja.textContent = 'Caja/paquete (' + factorCaja + ' ' + base + (factorCaja === 1 ? '' : 's') + ') · ' + formatoPrecio(precioCaja);
  // Si la venta por blíster dejó de estar disponible, vuelve a unidad base.
  if (opcionBlister.disabled && unidad.value === 'blister') unidad.value = 'base';
  // Elige el factor correcto según se venda unidad, blíster o caja.
  const factor = unidad.value === 'blister' ? factorBlister : unidad.value === 'presentacion' ? factorCaja : 1;
  // Limita la cantidad a las presentaciones completas que hay disponibles en stock.
  cantidad.max = productoId === 'medicamentoId' && opcion?.value
    ? String(Math.floor((Number(opcion.dataset.stock) || 0) / factor)) : '';
  // Pide seleccionar un producto antes de calcular las equivalencias.
  if (!opcion?.value) { salida.textContent = 'Selecciona un medicamento para ver la conversión.'; return; }
  // Lee la cantidad escrita y la convierte a unidades mínimas del inventario.
  const numero = Number(cantidad.value) || 0;
  const unidadesBase = numero * factor;
  // Elige el precio que corresponde a la presentación actualmente seleccionada.
  const precioPresentacion = unidad.value === 'blister' ? precioBlister : unidad.value === 'presentacion' ? precioCaja : precioUnidad;
  // Define los plurales para mostrar correctamente la unidad convertida.
  const plural = {unidad:'unidades',caja:'cajas',paquete:'paquetes',tableta:'tabletas',capsula:'cápsulas',sobre:'sobres',ml:'ml',gota:'gotas',ampolla:'ampollas',frasco:'frascos'};
  // Selecciona la forma singular o plural de la unidad base.
  const sufijo = unidadesBase === 1 ? base : (plural[base] || base + 's');
  // Calcula el total estimado con el precio específico por presentación.
  const total = precioPresentacion ? ' Total aprox.: ' + formatoPrecio(numero * precioPresentacion) + '.' : '';
  // Presenta la conversión y el precio aproximado antes de guardar la venta.
  salida.textContent = numero + ' ' + unidad.options[unidad.selectedIndex].text.toLocaleLowerCase('es') + ' = ' + unidadesBase + ' ' + sufijo + '.' + total;
}
// Recalcula la conversión mientras se cambia la cantidad de la venta nueva.
document.getElementById('cantidad').addEventListener('input',()=>actualizarVentaUnidad('medicamentoId','unidadVenta','cantidad','conversionVenta'));
// Recalcula la conversión mientras se cambia la cantidad de una venta editada.
document.getElementById('cantidadEditar').addEventListener('input',()=>actualizarVentaUnidad('productoEditar','unidadVentaEditar','cantidadEditar','conversionEditar'));
// Actualiza factores y precio cuando cambia el producto en la edición.
document.getElementById('productoEditar').addEventListener('change',()=>actualizarVentaUnidad('productoEditar','unidadVentaEditar','cantidadEditar','conversionEditar'));
document.getElementById('unidadVentaEditar').addEventListener('change',()=>actualizarVentaUnidad('productoEditar','unidadVentaEditar','cantidadEditar','conversionEditar'));
// Carga los datos de una venta existente en el formulario de edición.
function editarVenta(boton){document.getElementById('ventaIdEditar').value=boton.dataset.id;document.getElementById('fechaEditar').value=boton.dataset.fecha;document.getElementById('clienteEditar').value=boton.dataset.cliente;document.getElementById('buscarProductoEditar').value='';filtrarMedicamentos('buscarProductoEditar','productoEditar','sinResultadosEditar');document.getElementById('productoEditar').value=boton.dataset.producto;document.getElementById('unidadVentaEditar').value=boton.dataset.unidadVenta||'base';document.getElementById('cantidadEditar').value=boton.dataset.cantidadPresentacion||boton.dataset.cantidad;document.getElementById('metodoEditar').value=boton.dataset.metodo;actualizarVentaUnidad('productoEditar','unidadVentaEditar','cantidadEditar','conversionEditar');document.getElementById('modalEditar').classList.add('open')}
// Cierra el formulario de edición sin enviar modificaciones.
function cerrarEditar(){document.getElementById('modalEditar').classList.remove('open')}
</script></body></html>
