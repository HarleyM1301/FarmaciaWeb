<%-- Importa una factura y prepara una recepción técnica con varias líneas y lotes. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.*,java.sql.SQLException,com.farmacia.dao.MedicamentoDAO,com.farmacia.dao.MovimientoDAO,com.farmacia.modelo.medicamento,com.farmacia.util.HtmlUtil" %>
<%
request.setAttribute("activePage", "compras");
List<Map<String,Object>> compras = new ArrayList<>();
List<medicamento> medicamentos = new MedicamentoDAO().listar();
try { compras = new MovimientoDAO().listar(false); } catch (SQLException e) { application.log("No se pudieron consultar las compras", e); request.setAttribute("errorBD", true); }
%>
<!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Compras | Farmacia</title><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/app.css"></head>
<body><div class="shell"><%@ include file="/WEB-INF/partials/topbar.jspf" %><%@ include file="/WEB-INF/partials/sidebar.jspf" %><main class="main">
<div class="page-heading"><div><h1>Compras y recepción técnica</h1><p>Carga la factura, revisa sus productos y registra las existencias por lote.</p></div><button class="btn" type="button" onclick="abrir()">＋ Recibir factura</button></div>
<% if ("ok".equals(request.getParameter("estado"))) { %><div class="notice">Factura guardada. Las cantidades recibidas ya están en el inventario.</div><% } %>
<% if ("error".equals(request.getParameter("estado")) || Boolean.TRUE.equals(request.getAttribute("errorBD"))) { %><div class="notice error">No se pudo completar la recepción. Revisa el registro de Tomcat para ver el detalle.</div><% } %>
<section class="card"><div class="toolbar"><label class="search"><input id="buscar" type="search" placeholder="Buscar factura, fecha o proveedor" aria-label="Buscar compra"></label><span class="muted"><%= compras.size() %> facturas</span></div>
<div class="table-wrap"><table id="tabla"><thead><tr><th>Factura</th><th>Fecha</th><th>Proveedor</th><th>Productos</th><th>Unidades</th><th>Total</th><th>Archivo</th></tr></thead><tbody>
<% if (compras.isEmpty()) { %><tr><td colspan="7" class="empty">Aún no hay compras registradas.</td></tr><% } for (Map<String,Object> c : compras) { %>
<tr><td><%= HtmlUtil.escapar(c.get("numeroFactura")) %></td><td><%= HtmlUtil.escapar(c.get("fecha")) %></td><td><%= HtmlUtil.escapar(c.get("tercero")) %></td><td><%= HtmlUtil.escapar(c.get("medicamento")) %></td><td><%= c.get("cantidad") %></td><td>$<%= String.format(java.util.Locale.forLanguageTag("es-CO"), "%,.0f", ((java.math.BigDecimal)c.get("total")).doubleValue()) %></td><td><% if (c.get("archivoFactura") != null) { %><a class="back-link" href="<%= request.getContextPath() %>/FacturaCompraServlet?id=<%= c.get("id") %>">Descargar</a><% } else { %><span class="muted">—</span><% } %></td></tr>
<% } %></tbody></table></div></section><div class="form-actions"><a class="btn secondary" href="<%= request.getContextPath() %>/panel.jsp">Volver al Dashboard</a></div>
</main></div>

<%-- El archivo se conserva y las líneas importadas siempre se pueden corregir antes de confirmar. --%>
<div id="modal" class="modal" role="dialog" aria-modal="true" aria-labelledby="titulo"><section class="modal-box" style="width:min(1100px,96vw)"><div class="modal-head"><h2 id="titulo">Cargar factura y hacer recepción técnica</h2><button class="close" type="button" onclick="cerrar()" aria-label="Cerrar">×</button></div>
<form id="formCompra" action="<%= request.getContextPath() %>/MovimientoServlet" method="post" enctype="multipart/form-data"><input type="hidden" name="tipo" value="compra">
<div class="form-grid">
<div class="field"><label for="factura">Archivo de factura</label><input id="factura" name="factura" type="file" accept=".xml,.csv,.txt,.pdf,.xlsx,.xls,.jpg,.jpeg,.png"></div>
<div class="field"><label for="proveedor">Proveedor *</label><input id="proveedor" name="proveedor" maxlength="100" required></div>
<div class="field"><label for="numeroFactura">Número de factura *</label><input id="numeroFactura" name="numeroFactura" maxlength="60" required></div>
<div class="field"><label for="fecha">Fecha de factura *</label><input id="fecha" name="fecha" type="date" required></div>
<div class="field"><label for="totalFactura">Total según factura (opcional)</label><input id="totalFactura" name="totalFactura" type="number" min="0" step="0.01" placeholder="Se calcula desde los productos"></div>
</div>
<div id="importStatus" class="notice" hidden aria-live="polite"></div>
<section class="card" style="margin-top:16px;padding:16px"><div class="page-heading" style="margin:0 0 12px"><div><h3 style="margin:0">Productos de la factura</h3><p class="muted" style="margin:4px 0 0">Una fila por producto facturado. Marca los medicamentos que deben registrarse con uno o varios lotes.</p></div><button class="btn secondary" type="button" onclick="agregarLinea()">＋ Agregar producto</button></div>
<div class="table-wrap"><table><thead><tr><th>Producto</th><th>Cantidad total</th><th>Costo unitario</th><th>Recepción técnica</th><th></th></tr></thead><tbody id="lineas"></tbody></table></div>
<p class="muted">La suma de las cantidades asignadas a los lotes debe coincidir con la cantidad de la factura. Si solo se identifica un lote, agrega uno; si hay varios, agrega una fila por cada lote.</p></section>
<template id="plantillaLinea"><tr class="linea-compra">
<td><select class="producto-select" required onchange="actualizarProductoNuevo(this)"><option value="">Selecciona un producto</option>
<% for (medicamento m : medicamentos) { %><option value="<%= m.getIdMedicamento() %>" data-nombre="<%= HtmlUtil.escapar(m.getNombre()) %>" data-lote="<%= HtmlUtil.escapar(m.getLote()) %>"><%= HtmlUtil.escapar(m.getNombre()) %> · lote <%= HtmlUtil.escapar(m.getLote()) %> · stock <%= m.getStock() %></option><% } %>
<option value="nuevo">＋ Crear producto nuevo</option></select><input class="nombre-nuevo" maxlength="100" placeholder="Nombre del producto nuevo" hidden><input class="precio-venta-nuevo" type="number" min="0.01" step="0.01" placeholder="Precio de venta" hidden></td>
<td><input class="cantidad-factura" type="number" min="1" step="1" value="1" required></td><td><input class="costo-factura" type="number" min="0" step="0.01" placeholder="0,00" required></td>
<td><label><input class="control-lotes" type="checkbox" onchange="alternarLotes(this)"> Asignar lote(s)</label></td>
<td><button class="btn danger small" type="button" onclick="quitarLinea(this)" aria-label="Quitar línea">Quitar</button></td></tr>
<tr class="fila-lotes" hidden><td colspan="5"><strong>Distribución de recepción técnica</strong><div class="lotes-lista"></div><button class="btn secondary small" type="button" onclick="agregarLote(this)">＋ Agregar lote</button></td></tr></template>
<template id="plantillaLote"><div class="lote-detalle" style="display:grid;grid-template-columns:1fr 1fr 1fr auto;gap:8px;margin:8px 0"><label>Lote<input class="dato-lote" style="width:100%" maxlength="30" placeholder="Número de lote"></label><label>Vencimiento<input class="dato-vencimiento" style="width:100%" type="date"></label><label>Unidades de este lote<input class="dato-cantidad-lote" style="width:100%" type="number" min="1" step="1" value="1" required></label><button class="btn danger small" type="button" onclick="this.closest('.lote-detalle').remove()">Quitar</button></div></template>
<div id="lineasSerializadas"></div>
<div class="form-actions"><button class="btn secondary" type="button" onclick="cerrar()">Cancelar</button><button class="btn" type="submit">Confirmar recepción e ingresar al inventario</button></div>
</form></section></div>
<script src="<%= request.getContextPath() %>/assets/js/factura-importador.js"></script>
<script>
// Filtra el historial de facturas que ya se cargaron.
document.getElementById('buscar').addEventListener('input',function(){const q=this.value.toLocaleLowerCase('es');document.querySelectorAll('#tabla tbody tr').forEach(f=>f.hidden=!f.innerText.toLocaleLowerCase('es').includes(q))});
// Abre un borrador de recepción con una línea vacía y la fecha actual.
function abrir(){document.getElementById('formCompra').reset();document.getElementById('lineas').replaceChildren();document.getElementById('importStatus').hidden=true;document.getElementById('fecha').value=new Date().toISOString().slice(0,10);agregarLinea();document.getElementById('modal').classList.add('open')}
// Agrega una fila; se pueden repetir productos para registrar lotes distintos.
function agregarLinea(datos={}){const plantilla=document.getElementById('plantillaLinea').content.cloneNode(true);const fila=plantilla.querySelector('.linea-compra');document.getElementById('lineas').appendChild(plantilla);if(datos.nombre){const opciones=[...fila.querySelector('.producto-select').options].filter(o=>o.dataset.nombre);const coincidencia=opciones.find(o=>normalizar(o.dataset.nombre)===normalizar(datos.nombre)&&(!datos.lote||normalizar(o.dataset.lote)===normalizar(datos.lote)))||opciones.find(o=>normalizar(o.dataset.nombre)===normalizar(datos.nombre));if(coincidencia)fila.querySelector('.producto-select').value=coincidencia.value;else{fila.querySelector('.producto-select').value='nuevo';fila.querySelector('.nombre-nuevo').value=datos.nombre}actualizarProductoNuevo(fila.querySelector('.producto-select'))}if(datos.cantidad!==undefined&&datos.cantidad!=='')fila.querySelector('.cantidad-factura').value=datos.cantidad;if(datos.costo!==undefined&&datos.costo!=='')fila.querySelector('.costo-factura').value=numeroISO(datos.costo);if(datos.lote||datos.vencimiento){const check=fila.querySelector('.control-lotes');check.checked=true;alternarLotes(check);const detalle=fila.nextElementSibling.querySelector('.lote-detalle');if(datos.lote)detalle.querySelector('.dato-lote').value=datos.lote;if(datos.vencimiento)detalle.querySelector('.dato-vencimiento').value=fechaISO(datos.vencimiento);detalle.querySelector('.dato-cantidad-lote').value=datos.cantidad||1}}
// Muestra los datos adicionales que se necesitan para crear un medicamento desconocido.
function actualizarProductoNuevo(select){const fila=select.closest('tr');const nuevo=select.value==='nuevo';const nombre=fila.querySelector('.nombre-nuevo');const precio=fila.querySelector('.precio-venta-nuevo');nombre.hidden=!nuevo;precio.hidden=!nuevo;nombre.required=nuevo;precio.required=nuevo}
// Abre o cierra la distribución por lotes de una línea facturada.
function alternarLotes(check){const detalle=check.closest('tr').nextElementSibling;detalle.hidden=!check.checked;detalle.querySelectorAll('input').forEach(input=>input.disabled=!check.checked);if(check.checked&&!detalle.querySelector('.lote-detalle'))agregarLote(detalle.querySelector('button'))}
// Añade una captura de unidades, lote y vencimiento identificados en recepción.
function agregarLote(boton){const lista=boton.closest('td').querySelector('.lotes-lista');lista.appendChild(document.getElementById('plantillaLote').content.cloneNode(true))}
// Evita dejar el formulario sin líneas por accidente.
function quitarLinea(boton){const filas=document.querySelectorAll('#lineas .linea-compra');if(filas.length<=1){mostrarEstado('La recepción debe conservar al menos un producto.','error');return}const fila=boton.closest('tr');fila.nextElementSibling.remove();fila.remove()}
// Muestra el resultado de lectura o las instrucciones para terminar a mano.
function mostrarEstado(texto,tipo='ok'){const estado=document.getElementById('importStatus');estado.textContent=texto;estado.classList.toggle('error',tipo==='error');estado.hidden=false}
// Normaliza nombres para comparar productos y cabeceras de distintos proveedores.
function normalizar(texto){return (texto||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]/g,'')}
// Convierte fechas habituales de facturas a AAAA-MM-DD para los campos date.
function fechaISO(valor){const s=(valor||'').trim();if(/^\d{4}-\d{2}-\d{2}/.test(s))return s.slice(0,10);const m=s.match(/^(\d{1,2})[/. -](\d{1,2})[/. -](\d{4})$/);return m?`${m[3]}-${m[2].padStart(2,'0')}-${m[1].padStart(2,'0')}`:''}
// Convierte valores con separadores decimales locales a un número para el formulario.
function numeroISO(valor){let s=String(valor??'').replace(/[^\d,.-]/g,'');if(s.includes(',')&&s.includes('.'))s=s.lastIndexOf(',')>s.lastIndexOf('.')?s.replace(/\./g,'').replace(',','.'):s.replace(/,/g,'');else if(s.includes(','))s=s.replace(',','.');return /^-?\d+(\.\d+)?$/.test(s)?s:''}
// Serializa cada producto en una línea simple o en varias asignaciones de lotes.
document.getElementById('formCompra').addEventListener('submit',function(event){const salida=document.getElementById('lineasSerializadas');salida.replaceChildren();let totalLineas=0;for(const fila of document.querySelectorAll('#lineas .linea-compra')){const select=fila.querySelector('.producto-select');const cantidad=Number(fila.querySelector('.cantidad-factura').value);const lotesFila=fila.nextElementSibling;let partes=[{lote:'',vencimiento:'',cantidad}];if(fila.querySelector('.control-lotes').checked){partes=[...lotesFila.querySelectorAll('.lote-detalle')].map(d=>({lote:d.querySelector('.dato-lote').value,vencimiento:d.querySelector('.dato-vencimiento').value,cantidad:Number(d.querySelector('.dato-cantidad-lote').value)}));const sum=partes.reduce((s,p)=>s+p.cantidad,0);if(!partes.length||partes.some(p=>!Number.isInteger(p.cantidad)||p.cantidad<1)||sum!==cantidad){event.preventDefault();mostrarEstado('Las unidades distribuidas en los lotes deben sumar exactamente la cantidad facturada.','error');return}}totalLineas+=partes.length;for(const parte of partes){const valores={productoId:select.value,nombreNuevo:fila.querySelector('.nombre-nuevo').value,precioVenta:fila.querySelector('.precio-venta-nuevo').value,lote:parte.lote,vencimiento:parte.vencimiento,cantidad:parte.cantidad,costo:fila.querySelector('.costo-factura').value};for(const [nombre,valor] of Object.entries(valores)){const campo=document.createElement('input');campo.type='hidden';campo.name=nombre;campo.value=valor;salida.appendChild(campo)}}}if(totalLineas>50){event.preventDefault();mostrarEstado('La recepción admite hasta 50 asignaciones de producto y lote.','error')}});
// Cierra el diálogo de recepción.
function cerrar(){document.getElementById('modal').classList.remove('open')}
</script></body></html>
