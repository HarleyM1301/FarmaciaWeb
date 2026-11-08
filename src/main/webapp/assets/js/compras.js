
// Filtra el historial de facturas en pantalla sin volver a consultar el servidor.
document.getElementById('buscar').addEventListener('input',function(){const q=this.value.toLocaleLowerCase('es');document.querySelectorAll('#tabla tbody tr').forEach(f=>f.hidden=!f.innerText.toLocaleLowerCase('es').includes(q))});
// Abre el modal de recepción cuando se pulsa el botón de nueva factura.
document.getElementById('abrirRecepcion').addEventListener('click',abrir);
// Abre un borrador de recepción con una línea vacía y la fecha actual.
function abrir(){const modal=document.getElementById('modal');modal.classList.add('open');document.getElementById('formCompra').reset();try{document.getElementById('margenSugerido').value=localStorage.getItem('farmacia.margenVenta')||'30'}catch(error){document.getElementById('margenSugerido').value='30'}document.getElementById('lineas').replaceChildren();document.getElementById('importStatus').hidden=true;document.getElementById('ocrReview').hidden=true;document.getElementById('textoOCR').value='';document.getElementById('factura').value='';const fecha=document.getElementById('fecha');fecha.value=new Date().toISOString().slice(0,10);fecha.dataset.default=fecha.value;agregarLinea()}
// Agrega una fila y busca coincidencias confiables entre el nombre leído y el catálogo existente.
function agregarLinea(datos={}) {
  const plantilla = document.getElementById('plantillaLinea').content.cloneNode(true);
  const fila = plantilla.querySelector('.linea-compra');
  document.getElementById('lineas').appendChild(plantilla);
  const selector = fila.querySelector('.producto-select');
  const nombreNuevo = fila.querySelector('.nombre-nuevo');
  const categoriaNueva = fila.querySelector('.categoria-nueva');
  const mensajes = [];

  if (datos.nombre) {
    nombreNuevo.value = datos.nombre;
    const opciones = [...selector.options].filter(opcion => opcion.dataset.nombre);
    const ranking = opciones
      .map(opcion => ({ opcion, puntaje: similitudNombre(datos.nombre, opcion.dataset.nombre) }))
      .sort((a, b) => b.puntaje - a.puntaje);
    const mejor = ranking[0];
    const segundo = ranking[1];
    const loteExacto = datos.lote && opciones.find(opcion =>
      normalizar(opcion.dataset.nombre) === normalizar(datos.nombre)
      && normalizar(opcion.dataset.lote) === normalizar(datos.lote));
    const puntajeMejor = loteExacto ? 1 : (mejor?.puntaje || 0);
    const margen = segundo ? puntajeMejor - segundo.puntaje : puntajeMejor;

    // Selecciona automáticamente solo coincidencias fuertes y claramente mejores que las demás.
    if (loteExacto || (puntajeMejor >= 0.90 && margen >= 0.08)) {
      const opcion = loteExacto || mejor.opcion;
      selector.value = opcion.value;
      mensajes.push(`Coincidencia segura (${Math.round(puntajeMejor * 100)} %): ${opcion.dataset.nombre}. Revisa presentación y lote.`);
    } else if (mejor && mejor.puntaje >= 0.58) {
      // Mantiene el selector sin elegir para que el usuario confirme una sugerencia dudosa.
      selector.value = '';
      mensajes.push(`Posible coincidencia: ${mejor.opcion.dataset.nombre} (${Math.round(mejor.puntaje * 100)} %). Confírmala en la lista o elige crear un producto.`);
    } else {
      // Propone un alta cuando no encuentra candidato parecido, conservando el nombre leído.
      selector.value = 'nuevo';
      mensajes.push('No encontré una coincidencia confiable. Revisa el nombre antes de registrar un producto nuevo.');
    }
  }

  // Copia unidad y contenido del catálogo si ya se eligió un medicamento existente.
  selector.dataset.cargando = 'true';
  actualizarProductoNuevo(selector);
  delete selector.dataset.cargando;
  if (selector.value === 'nuevo') {
    fila.querySelector('.catalogo-invima-wrap').hidden = false;
    if (datos.nombre) buscarCatalogoInvima(fila.querySelector('.catalogo-invima-wrap button'), datos.nombre);
    const grupo = datos.categoria || 'Otros';
    if (![...categoriaNueva.options].some(opcion => opcion.value === grupo)) {
      categoriaNueva.add(new Option(grupo, grupo));
    }
    categoriaNueva.value = grupo;
  }

  // Conserva la presentación extraída del nombre para que se pueda revisar o corregir.
  if (selector.value === 'nuevo') {
    if (datos.unidadBase) fila.querySelector('.unidad-base').value = datos.unidadBase;
    if (datos.unidadesPorPresentacion) fila.querySelector('.factor-empaque').value = datos.unidadesPorPresentacion;
  }

  // Si el OCR no separó cantidad o costo con seguridad, deja el campo vacío para captura manual.
  if (datos.origen === 'ocr' && (!datos.cantidad || !datos.costo)) {
    fila.querySelector('.cantidad-factura').value = '';
    fila.querySelector('.costo-factura').value = '';
    mensajes.push('El OCR no pudo identificar cantidad y costo con seguridad; complétalos manualmente.');
  } else {
    if (datos.cantidad !== undefined && datos.cantidad !== '') fila.querySelector('.cantidad-factura').value = datos.cantidad;
    if (datos.costo !== undefined && datos.costo !== '') fila.querySelector('.costo-factura').value = numeroISO(datos.costo);
  }

  // Actualiza la vista previa con la conversión de presentación a unidades base.
  actualizarConversion(fila);

  // Activa la captura técnica si la lectura encontró lote o vencimiento.
  if (datos.lote || datos.vencimiento) {
    const check = fila.querySelector('.control-lotes');
    check.checked = true;
    alternarLotes(check);
    const detalle = fila.nextElementSibling.querySelector('.lote-detalle');
    if (datos.lote) detalle.querySelector('.dato-lote').value = datos.lote;
    if (datos.vencimiento) detalle.querySelector('.dato-vencimiento').value = fechaISO(datos.vencimiento);
    if (datos.cantidad) detalle.querySelector('.dato-cantidad-lote').value = datos.cantidad;
  }

  // Muestra si se eligió un producto, si requiere confirmación o si se propone un alta nueva.
  const mensaje = mensajes.join(' ');
  const requiereRevision = mensajes.some(texto => /posible coincidencia|no encontré|no pudo identificar/i.test(texto));
  mostrarCoincidencia(fila, mensaje, requiereRevision);
  return fila;
}

// Agrega en bloque las filas copiadas desde Excel o una tabla de PDF.
function importarTablaPegada() {
  const campo = document.getElementById('productosPegados');
  const texto = campo.value.trim();
  if (!texto) { mostrarEstado('Copia primero las filas de productos desde Excel o una tabla de factura.', 'error'); return; }
  const filas = texto.split(/\r?\n/).map(linea => linea.trim()).filter(Boolean);
  const separador = filas[0].includes('\t') ? '\t' : (filas[0].includes(';') ? ';' : ',');
  const celdas = filas.map(linea => dividirFilaPegada(linea, separador));
  const cabecera = celdas[0].map(normalizar);
  const buscar = (...nombres) => cabecera.findIndex(nombre => nombres.some(opcion => nombre === normalizar(opcion)));
  const columnas = {
    nombre: buscar('producto', 'medicamento', 'nombre', 'descripcion', 'articulo', 'item'),
    cantidad: buscar('cantidad', 'unidades', 'qty'),
    costo: buscar('costo', 'costo unitario', 'costo por caja', 'precio compra', 'precio unitario', 'valor unitario'),
    lote: buscar('lote', 'batch'),
    vencimiento: buscar('vencimiento', 'fecha vencimiento', 'fecha de vencimiento', 'expiry'),
    precioVenta: buscar('precio publico', 'precio al publico', 'precio venta', 'precio de venta', 'pvp'),
    categoria: buscar('grupo farmacologico', 'grupo', 'categoria', 'categoría'),
    unidadBase: buscar('unidad base', 'unidad minima'),
    factor: buscar('unidades por caja', 'unidades por paquete', 'contenido caja', 'contenido paquete')
  };
  const tieneCabecera = columnas.nombre >= 0 && columnas.cantidad >= 0 && columnas.costo >= 0;
  if (filas.length - (tieneCabecera ? 1 : 0) > 500) { mostrarEstado('La carga masiva admite hasta 500 filas de productos por factura.', 'error'); return; }
  if (!tieneCabecera) {
    columnas.nombre = 0; columnas.cantidad = 1; columnas.costo = 2;
    columnas.lote = 3; columnas.vencimiento = 4; columnas.precioVenta = 5;
    columnas.unidadBase = 6; columnas.factor = 7; columnas.categoria = 8;
  }
  const inicio = tieneCabecera ? 1 : 0;
  let agregadas = 0;
  const errores = [];
  for (let i = inicio; i < celdas.length; i++) {
    const valores = celdas[i];
    const nombre = (valores[columnas.nombre] || '').trim();
    const cantidad = numeroPegado(valores[columnas.cantidad]);
    const costo = numeroPegado(valores[columnas.costo]);
    if (!nombre || !Number.isInteger(cantidad) || cantidad < 1 || !Number.isFinite(costo) || costo < 0) {
      errores.push(i + 1); continue;
    }
    const fila = agregarLinea({
      nombre, cantidad: String(cantidad), costo: String(costo),
      lote: valores[columnas.lote] || '', vencimiento: fechaISO(valores[columnas.vencimiento] || ''),
      categoria: (valores[columnas.categoria] || '').trim(),
      unidadBase: valores[columnas.unidadBase] || '',
      unidadesPorPresentacion: numeroPegado(valores[columnas.factor]) || '', origen: 'pegado'
    });
    const precioVenta = numeroPegado(valores[columnas.precioVenta]);
    if (precioVenta > 0) {
      if (fila.querySelector('.producto-select').value === 'nuevo') {
        const precio = fila.querySelector('.precio-venta-nuevo');
        precio.value = precioVenta.toFixed(2); precio.dataset.modificado = 'true';
      } else if (fila.querySelector('.producto-select').value) {
        const check = fila.querySelector('.actualizar-precio');
        check.checked = true; alternarPrecio(check);
        const precio = fila.querySelector('.precio-venta-actual');
        precio.value = precioVenta.toFixed(2); precio.dataset.modificado = 'true';
      }
    }
    actualizarConversion(fila);
    agregadas++;
  }
  if (!agregadas) { mostrarEstado('No encontré filas completas. Usa columnas Producto, Cantidad y Costo, o el orden indicado en el ejemplo.', 'error'); return; }
  campo.value = '';
  const aviso = errores.length ? ' Se omitieron filas incompletas: ' + errores.join(', ') + '.' : '';
  mostrarEstado('Se agregaron ' + agregadas + ' productos. Revisa las coincidencias, presentación, lotes y precios antes de confirmar.' + aviso, errores.length ? 'error' : 'ok');
}

// Divide una fila tabulada o delimitada respetando campos entre comillas.
function dividirFilaPegada(linea, separador) {
  const celdas = []; let valor = ''; let comillas = false;
  for (let i = 0; i < linea.length; i++) {
    const caracter = linea[i];
    if (caracter === '"' && comillas && linea[i + 1] === '"') { valor += '"'; i++; }
    else if (caracter === '"') comillas = !comillas;
    else if (caracter === separador && !comillas) { celdas.push(valor); valor = ''; }
    else valor += caracter;
  }
  celdas.push(valor);
  return celdas;
}

// Interpreta separadores decimales y de miles habituales en facturas colombianas.
function numeroPegado(valor) {
  let texto = String(valor ?? '').replace(/[^\d,.-]/g, '');
  if (!texto) return NaN;
  const coma = texto.lastIndexOf(','); const punto = texto.lastIndexOf('.');
  if (coma >= 0 && punto >= 0) texto = coma > punto ? texto.replace(/\./g, '').replace(',', '.') : texto.replace(/,/g, '');
  else if (coma >= 0) texto = /,\d{3}$/.test(texto) ? texto.replace(/,/g, '') : texto.replace(',', '.');
  else if (/^\d{1,3}(?:\.\d{3})+$/.test(texto)) texto = texto.replace(/\./g, '');
  const numero = Number(texto);
  return Number.isFinite(numero) ? numero : NaN;
}

// Compara nombres y descarta candidatos con concentración o presentación distintas.
function similitudNombre(a, b) {
  const x = normalizar(a);
  const y = normalizar(b);
  if (!x || !y) return 0;
  if (x === y) return 1;
  if (difierenDatosFarmaceuticos(a, b)) return 0;

  // Calcula la distancia de edición para tolerar errores pequeños del OCR.
  let anterior = Array.from({ length: y.length + 1 }, (_, i) => i);
  for (let i = 1; i <= x.length; i++) {
    const actual = [i];
    for (let j = 1; j <= y.length; j++) {
      actual[j] = Math.min(actual[j - 1] + 1, anterior[j] + 1,
        anterior[j - 1] + (x[i - 1] === y[j - 1] ? 0 : 1));
    }
    anterior = actual;
  }
  const edicion = 1 - anterior[y.length] / Math.max(x.length, y.length);

  // Premia los nombres que comparten términos aunque el OCR haya omitido signos o palabras.
  const tokensA = new Set(String(a).toLocaleLowerCase('es').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').split(/[^a-z0-9]+/).filter(token => token.length > 1));
  const tokensB = new Set(String(b).toLocaleLowerCase('es').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').split(/[^a-z0-9]+/).filter(token => token.length > 1));
  const comunes = [...tokensA].filter(token => tokensB.has(token)).length;
  const tokens = comunes / Math.max(tokensA.size, tokensB.size, 1);
  return Math.max(edicion, edicion * 0.45 + tokens * 0.55);
}

// Evita sugerir el mismo principio activo con una concentración o tamaño de caja diferente.
function difierenDatosFarmaceuticos(a, b) {
  const concentraciones = texto => [...String(texto).toLowerCase().matchAll(/(\d+(?:[.,]\d+)?)\s*(mg|mcg|μg|ug|g|ml|%)/g)]
    .map(match => `${match[1].replace(',', '.')}${match[2]}`);
  const presentaciones = texto => {
    const match = String(texto).toLowerCase().match(/\b(\d{1,5})\s*(comprimidos?|compr\.?|tabletas?|tabs?\.?|c[aá]psulas?|caps?\.?|sobres?|ampollas?|frascos?|unidades?|uds?\.?|ml)(?=\W|$)/);
    if (!match) return '';
    const unidad = match[2].replace(/\.$/, '');
    const tipo = /compr|comp|tableta|tab/.test(unidad) ? 'tableta'
      : /caps|cápsula/.test(unidad) ? 'capsula'
      : /sobre/.test(unidad) ? 'sobre'
      : /ampolla/.test(unidad) ? 'ampolla'
      : /frasco/.test(unidad) ? 'frasco'
      : /ml/.test(unidad) ? 'ml'
      : 'unidad';
    return `${match[1]}:${tipo}`;
  };
  const dosisA = concentraciones(a), dosisB = concentraciones(b);
  const cajaA = presentaciones(a), cajaB = presentaciones(b);
  return (dosisA.length && dosisB.length && dosisA.join('|') !== dosisB.join('|'))
    || (cajaA && cajaB && cajaA !== cajaB);
}

// Explica el estado de identificación debajo del selector de cada línea.
function mostrarCoincidencia(fila, texto, requiereRevision = Boolean(texto)) {
  const aviso = fila.querySelector('.coincidencia-producto');
  aviso.textContent = texto;
  aviso.classList.toggle('requiere-revision', Boolean(texto) && requiereRevision);
}
// Muestra los campos de alta o copia la unidad del medicamento seleccionado.
function actualizarProductoNuevo(select) {
  const fila = select.closest('tr');
  const nuevo = select.value === 'nuevo';
  const nombre = fila.querySelector('.nombre-nuevo');
  const categoria = fila.querySelector('.categoria-nueva');
  const categoriaWrap = fila.querySelector('.categoria-nueva-wrap');
  const precio = fila.querySelector('.precio-venta-nuevo');
  const blisterWrap = fila.querySelector('.blister-nuevo-wrap');
  const blisterField = fila.querySelector('.blister-factor-nuevo');
  const precioNuevo = fila.querySelector('.precio-nuevo-wrap');
  const precioExistente = fila.querySelector('.precio-existente');
  const catalogoWrap = fila.querySelector('.catalogo-invima-wrap');
  const checkActualizar = fila.querySelector('.actualizar-precio');
  const campoPrecioExistente = fila.querySelector('.precio-venta-actual');
  nombre.hidden = !nuevo;
  catalogoWrap.hidden = !nuevo;
  categoriaWrap.hidden = !nuevo;
  categoria.required = nuevo;
  precioNuevo.hidden = !nuevo;
  blisterWrap.hidden = !nuevo;
  blisterField.disabled = !nuevo;
  precioExistente.hidden = nuevo || !select.value;
  nombre.required = nuevo;
  precio.required = nuevo;
  if (nuevo || !select.value) {
    checkActualizar.checked = false;
    campoPrecioExistente.disabled = true;
    campoPrecioExistente.required = false;
  }
  if (nuevo) blisterField.value = '0';
  if (!nuevo) { fila.querySelector('.catalogo-invima-id').value=''; fila.querySelector('.registro-invima').value=''; }
  if (nuevo && !categoria.value) categoria.value = 'Otros';
  const opcion = select.selectedOptions[0];

  // Copia unidad y factor desde el catálogo cuando se eligió un medicamento existente.
  if (opcion?.dataset.unidad) {
    fila.querySelector('.unidad-base').value = opcion.dataset.unidad;
    fila.querySelector('.factor-empaque').value = opcion.dataset.factor || 1;
  }
  if (!nuevo && select.value) blisterField.value = opcion.dataset.blisterFactor || '0';
  if (!nuevo && select.value) {
    const precioActual = campoPrecioExistente;
    precioActual.value = opcion.dataset.precio || '';
    precioActual.dataset.actual = precioActual.value;
    checkActualizar.checked = false;
    precioActual.disabled = true;
    precioActual.required = false;
    delete precioActual.dataset.modificado;
    sugerirPrecio(fila);
  }
  actualizarConversion(fila);

  // Actualiza el aviso después de una selección manual, sin borrar la sugerencia inicial del OCR.
  if (!select.dataset.cargando) {
    if (nuevo) mostrarCoincidencia(fila, 'Se registrará como producto nuevo. Revisa nombre, precio y presentación antes de confirmar.');
    else if (select.value) mostrarCoincidencia(fila, `Producto seleccionado: ${opcion.dataset.nombre}. Verifica lote y presentación.`);
    else mostrarCoincidencia(fila, 'Selecciona el medicamento correcto o elige crear uno nuevo.');
  }
}
// Consulta el catálogo oficial CUM/INVIMA sin seleccionar coincidencias automáticamente.
async function buscarCatalogoInvima(boton, textoForzado='') {
  const fila=boton.closest('tr');
  const q=(textoForzado||fila.querySelector('.nombre-nuevo').value||'').trim();
  const salida=fila.querySelector('.catalogo-invima-resultados'); salida.replaceChildren();
  if(q.length<2){salida.textContent='Escribe al menos dos letras del medicamento.';return}
  boton.disabled=true;salida.textContent='Buscando en el catálogo oficial…';
  try{
    const url=new URL('CatalogoMedicamentoServlet',window.location.href);url.searchParams.set('q',q);
    const respuesta=await fetch(url,{headers:{'Accept':'application/json'}});
    if(!respuesta.ok)throw new Error('No se pudo consultar el catálogo.');
    const coincidencias=await respuesta.json();salida.replaceChildren();
    if(!coincidencias.length){salida.textContent='Sin resultados en el catálogo cargado. Puedes revisar el nombre o importar el CSV oficial desde Catálogo INVIMA.';return}
    const lista=document.createElement('div');lista.className='catalogo-candidatos';
    // Construye opciones seguras con los datos oficiales y guarda únicamente la coincidencia elegida.
    coincidencias.forEach(item=>{const opcion=document.createElement('button');opcion.type='button';opcion.className='btn secondary small';opcion.style='display:block;width:100%;text-align:left;margin-top:6px;white-space:normal';opcion.textContent=`${item.producto||'Producto'} · ${item.componentes||'principio activo no detallado'} · ${item.forma||''} · ${item.via||''} · ${item.registro||'sin registro visible'} · CUM ${item.cum||'—'}${item.descripcion?' · '+item.descripcion:''}`;opcion.addEventListener('click',()=>{
      fila.querySelector('.catalogo-invima-id').value=item.id;
      fila.querySelector('.registro-invima').value=item.registro||'';
      const nombre=fila.querySelector('.nombre-nuevo');if(!nombre.dataset.modificado)nombre.value=(item.producto||q).slice(0,100);
      const grupoATC={A:'A · Digestivo y metabolismo',B:'B · Sangre y órganos hematopoyéticos',C:'C · Sistema cardiovascular',D:'D · Dermatológicos',G:'G · Genitourinario y hormonas sexuales',H:'H · Preparados hormonales sistémicos',J:'J · Antiinfecciosos para uso sistémico',L:'L · Antineoplásicos e inmunomoduladores',M:'M · Sistema musculoesquelético',N:'N · Sistema nervioso',P:'P · Antiparasitarios',R:'R · Sistema respiratorio',S:'S · Órganos de los sentidos',V:'V · Varios'};
      const categoria=fila.querySelector('.categoria-nueva');if(item.atc&&grupoATC[item.atc.trim().charAt(0)])categoria.value=grupoATC[item.atc.trim().charAt(0)];
      salida.textContent=`Presentación confirmada: ${item.producto||''}. Registro: ${item.registro||'—'}. CUM ${item.cum||'—'}. Completa/verifica la unidad y contenido de empaque.`;
      mostrarCoincidencia(fila,'Coincidencia CUM confirmada por el usuario. Revisa concentración, presentación, cantidad por caja y lote antes de guardar.');
    });lista.appendChild(opcion)});salida.appendChild(lista);
  }catch(error){salida.textContent=error.message||'Error al consultar INVIMA.'}
  finally{boton.disabled=false}
}
// Habilita la edición únicamente cuando la persona solicita cambiar el precio público.
function alternarPrecio(check){const fila=check.closest('tr');const campo=fila.querySelector('.precio-venta-actual');campo.disabled=!check.checked;campo.required=check.checked;if(check.checked&&fila.querySelector('.sugerencia-precio').dataset.valor&&!campo.dataset.modificado)campo.value=campo.dataset.valor;actualizarRevision()}
// Calcula precio sugerido con margen sobre el precio de venta: costo / (1 - margen).
function sugerirPrecio(fila){const costo=Number(fila.querySelector('.costo-factura').value)||0;const factor=Number(fila.querySelector('.factor-empaque').value)||1;const margen=Number(document.getElementById('margenSugerido').value)||0;const valor=costo>0&&margen<95?Math.ceil((costo/factor)/(1-margen/100)/50)*50:0;const texto=valor?`Sugerido: $${valor.toLocaleString('es-CO')} por ${fila.querySelector('.unidad-base').value} (margen bruto aprox. ${margen} %).`:'';const sugerencia=fila.querySelector('.sugerencia-precio');if(sugerencia){sugerencia.dataset.valor=valor?valor.toFixed(2):'';sugerencia.textContent=texto;const campo=fila.querySelector('.precio-venta-actual');if(!campo.dataset.modificado)campo.value=fila.querySelector('.actualizar-precio').checked&&valor?valor.toFixed(2):(campo.dataset.actual||'');}const nueva=fila.querySelector('.precio-venta-nuevo');if(nueva){fila.querySelector('.sugerencia-nueva').textContent=texto;if(!nueva.dataset.modificado&&valor)nueva.value=valor.toFixed(2)}}
// Recalcula sugerencias cuando cambian los costos o el margen elegido.
function actualizarSugerenciasPrecio(){const margen=document.getElementById('margenSugerido').value;if(Number(margen)>=0&&Number(margen)<=95){try{localStorage.setItem('farmacia.margenVenta',margen)}catch(error){}}document.querySelectorAll('#lineas .linea-compra').forEach(sugerirPrecio)}
// Deja constancia si el usuario modifica manualmente el precio propuesto.
document.addEventListener('input',event=>{if(event.target.matches('.precio-venta-actual'))event.target.dataset.modificado='true';if(event.target.matches('.costo-factura,.cantidad-factura,.factor-empaque')){sugerirPrecio(event.target.closest('tr'));actualizarRevision()}});
// Muestra la recepción en cajas, blísteres y unidades base para evitar conversiones ocultas.
function actualizarConversion(fila){if(!fila)return;const cantidad=Number(fila.querySelector('.cantidad-factura').value)||0;const factor=Number(fila.querySelector('.factor-empaque').value)||0;const blister=Number(fila.querySelector('.blister-factor-nuevo').value)||0;const base=fila.querySelector('.unidad-base').value;const singular={unidad:'unidad',caja:'caja',paquete:'paquete',tableta:'tableta',capsula:'cápsula',sobre:'sobre',ml:'ml',gota:'gota',ampolla:'ampolla',frasco:'frasco'}[base]||base;const total=cantidad*factor;let detalle='Inventario: '+total+' '+singular+(total===1?'':'s');if(factor>1&&blister>0&&factor%blister===0)detalle+=' ('+(factor/blister)+' blíster'+(factor/blister===1?'':'es')+' por caja)';fila.querySelector('.conversion-preview').textContent=detalle;sugerirPrecio(fila);actualizarRevision()}
// Compara el total leído de la factura con la suma de cantidades por costo unitario de presentación.
function actualizarRevision(){const suma=[...document.querySelectorAll('#lineas .linea-compra')].reduce((total,fila)=>total+(Number(fila.querySelector('.cantidad-factura').value)||0)*(Number(fila.querySelector('.costo-factura').value)||0),0);const informado=Number(document.getElementById('totalFactura').value);const salida=document.getElementById('resumenImportes');if(!suma){salida.textContent='Completa cantidades y costos para calcular el total de las líneas.';return}const formato=v=>new Intl.NumberFormat('es-CO',{style:'currency',currency:'COP',maximumFractionDigits:2}).format(v);salida.textContent=`Total de líneas: ${formato(suma)}. `+(informado>0?`Total de factura: ${formato(informado)}. Diferencia: ${formato(informado-suma)}.`:'Ingresa el total impreso en la factura para compararlo.');salida.dataset.calculado=suma.toFixed(2)}
// Mantiene actualizado el total comparativo mientras se escribe el valor de la factura.
document.getElementById('totalFactura').addEventListener('input',actualizarRevision);
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
function fechaISO(valor){const s=(valor||'').trim();if(/^\d{4}-\d{2}-\d{2}/.test(s))return s.slice(0,10);const texto=s.toLocaleLowerCase('es').normalize('NFD').replace(/[\u0300-\u036f]/g,'');const meses={enero:'01',febrero:'02',marzo:'03',abril:'04',mayo:'05',junio:'06',julio:'07',agosto:'08',septiembre:'09',setiembre:'09',octubre:'10',noviembre:'11',diciembre:'12'};const nombrada=texto.match(/^(\d{1,2})\s+([a-z]+)\s+(\d{4})$/);if(nombrada&&meses[nombrada[2]])return nombrada[3]+'-'+meses[nombrada[2]]+'-'+nombrada[1].padStart(2,'0');const m=s.match(/^(\d{1,2})[/. -](\d{1,2})[/. -](\d{4})$/);return m?m[3]+'-'+m[2].padStart(2,'0')+'-'+m[1].padStart(2,'0'):''}
// Convierte valores con separadores decimales locales a un número para el formulario.
function numeroISO(valor){let s=String(valor??'').replace(/[^\d,.-]/g,'');if(s.includes(',')&&s.includes('.'))s=s.lastIndexOf(',')>s.lastIndexOf('.')?s.replace(/\./g,'').replace(',','.'):s.replace(/,/g,'');else if(s.includes(','))s=s.replace(',','.');return /^-?\d+(\.\d+)?$/.test(s)?s:''}
// Serializa cada producto en una línea simple o en varias asignaciones de lotes.
document.getElementById('formCompra').addEventListener('submit',function(event){const salida=document.getElementById('lineasSerializadas');salida.replaceChildren();let totalLineas=0;let totalCalculado=0;for(const fila of document.querySelectorAll('#lineas .linea-compra')){const select=fila.querySelector('.producto-select');const cantidad=Number(fila.querySelector('.cantidad-factura').value);const costo=Number(fila.querySelector('.costo-factura').value);totalCalculado+=cantidad*costo;const aplicarPrecio=select.value==='nuevo'||fila.querySelector('.actualizar-precio').checked;const precioFinal=select.value==='nuevo'?fila.querySelector('.precio-venta-nuevo').value:fila.querySelector('.precio-venta-actual').value;const lotesFila=fila.nextElementSibling;let partes=[{lote:'',vencimiento:'',cantidad}];if(fila.querySelector('.control-lotes').checked){partes=[...lotesFila.querySelectorAll('.lote-detalle')].map(d=>({lote:d.querySelector('.dato-lote').value,vencimiento:d.querySelector('.dato-vencimiento').value,cantidad:Number(d.querySelector('.dato-cantidad-lote').value)}));const sum=partes.reduce((s,p)=>s+p.cantidad,0);if(!partes.length||partes.some(p=>!Number.isInteger(p.cantidad)||p.cantidad<1)||sum!==cantidad){event.preventDefault();mostrarEstado('Las cantidades distribuidas por lote deben sumar las cajas o paquetes de la factura.','error');return}}totalLineas+=partes.length;for(const parte of partes){const valores={productoId:select.value,nombreNuevo:fila.querySelector('.nombre-nuevo').value,categoriaNueva:fila.querySelector('.categoria-nueva').value,precioVenta:precioFinal,actualizarPrecioVenta:aplicarPrecio?'true':'false',lote:parte.lote,vencimiento:parte.vencimiento,cantidad:parte.cantidad,costo:costo,unidadBase:fila.querySelector('.unidad-base').value,factorEmpaque:fila.querySelector('.factor-empaque').value,unidadesPorBlister:fila.querySelector('.blister-factor-nuevo').value,catalogoMedicamentoId:fila.querySelector('.catalogo-invima-id').value,registroInvima:fila.querySelector('.registro-invima').value};for(const [nombre,valor] of Object.entries(valores)){const campo=document.createElement('input');campo.type='hidden';campo.name=nombre;campo.value=valor;salida.appendChild(campo)}}}if(totalLineas>500){event.preventDefault();mostrarEstado('La recepción admite hasta 500 asignaciones de producto y lote.','error');return}const totalFactura=Number(document.getElementById('totalFactura').value);const diferencia=totalFactura>0&&Math.abs(totalFactura-totalCalculado)>1;const aceptada=!diferencia||confirm(`El total de las líneas (${totalCalculado.toFixed(2)}) difiere del total de la factura (${totalFactura.toFixed(2)}). ¿Deseas continuar?`);document.getElementById('diferenciaConfirmada').value=aceptada?'true':'false';if(!aceptada){event.preventDefault();salida.replaceChildren()}});
// Cierra el diálogo de recepción.
function cerrar(){document.getElementById('modal').classList.remove('open')}
