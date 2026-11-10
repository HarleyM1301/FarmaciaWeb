(() => {
  const form = document.getElementById('formCompra');
  if (!form) return;
  const storage = indexedDB.open('farmacia-recepcion-local', 1);
  let db;
  let currentFile = null;
  let timer;
  let actualizandoGuardado = Promise.resolve(true);
  let restoring = false;
  let barcodeRow = null;
  let cameraStream = null;
  let cameraFrame = 0;

  storage.onupgradeneeded = () => storage.result.createObjectStore('borradores', { keyPath: 'id' });
  storage.onsuccess = () => { db = storage.result; cargarBorradores(); };
  storage.onerror = () => estado('No se pudo abrir el almacenamiento local de borradores.', true);

  function transaccion(modo, tarea) {
    return new Promise((resolve, reject) => {
      if (!db) return reject(new Error('Almacenamiento no disponible'));
      const tx = db.transaction('borradores', modo);
      const peticion = tarea(tx.objectStore('borradores'));
      tx.oncomplete = () => resolve(peticion?.result);
      tx.onerror = () => reject(tx.error || new Error('No se pudo guardar el borrador'));
      tx.onabort = () => reject(tx.error || new Error('Se canceló el guardado'));
    });
  }

  function instantanea() {
    const lineas = [...document.querySelectorAll('#lineas .linea-compra')].map(fila => ({
      productoId: fila.querySelector('.producto-select').value,
      nombre: fila.querySelector('.nombre-nuevo').value,
      categoria: fila.querySelector('.categoria-nueva').value,
      catalogoId: fila.querySelector('.catalogo-invima-id').value,
      registro: fila.querySelector('.registro-invima').value,
      codigoBarras: fila.querySelector('.codigo-barras').value,
      cantidad: fila.querySelector('.cantidad-factura').value,
      costo: fila.querySelector('.costo-factura').value,
      unidad: fila.querySelector('.unidad-base').value,
      factor: fila.querySelector('.factor-empaque').value,
      blister: fila.querySelector('.blister-factor-nuevo').value,
      precioNuevo: fila.querySelector('.precio-venta-nuevo').value,
      actualizarPrecio: fila.querySelector('.actualizar-precio').checked,
      precioActual: fila.querySelector('.precio-venta-actual').value,
      lotesActivos: fila.querySelector('.control-lotes').checked,
      lotes: [...fila.nextElementSibling.querySelectorAll('.lote-detalle')].map(lote => ({
        lote: lote.querySelector('.dato-lote').value,
        vencimiento: lote.querySelector('.dato-vencimiento').value,
        cantidad: lote.querySelector('.dato-cantidad-lote').value
      }))
    }));
    return {
      proveedor: document.getElementById('proveedor').value,
      numero: document.getElementById('numeroFactura').value,
      fecha: document.getElementById('fecha').value,
      total: document.getElementById('totalFactura').value,
      margen: document.getElementById('margenSugerido').value,
      textoOCR: document.getElementById('textoOCR').value,
      tablaPegada: document.getElementById('productosPegados').value,
      lineas
    };
  }

  function tieneContenido(dato) {
    return Boolean(dato.proveedor || dato.numero || dato.total || currentFile
      || dato.lineas.some(l => l.productoId || l.nombre || l.codigoBarras || l.cantidad !== '1' || l.costo));
  }

  function guardarBorrador() {
    actualizandoGuardado = actualizandoGuardado.then(guardarBorradorAhora, guardarBorradorAhora);
    return actualizandoGuardado;
  }

  async function guardarBorradorAhora() {
    if (restoring) return true;
    const dato = instantanea();
    if (!tieneContenido(dato)) return true;
    const botonEstado = document.getElementById('estadoBorrador');
    botonEstado.hidden = false; botonEstado.textContent = 'Guardando borrador…';
    try {
      const idCampo = document.getElementById('borradorId');
      const id = idCampo.value || (globalThis.crypto?.randomUUID?.() || 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r=Math.random()*16|0; return (c==='x'?r:(r&0x3|0x8)).toString(16); }));
      const anterior = await transaccion('readonly', store => store.get(id)).catch(() => null);
      const registro = {
        id, usuario: form.dataset.usuarioId || "", dato, archivo: currentFile || anterior?.archivo || null,
        nombreArchivo: currentFile?.name || anterior?.nombreArchivo || '',
        actualizado: Date.now()
      };
      await transaccion('readwrite', store => store.put(registro));
      idCampo.value = id;
      botonEstado.textContent = `Borrador guardado en este navegador · ${new Date(registro.actualizado).toLocaleTimeString('es-CO', {hour:'2-digit',minute:'2-digit'})}`;
      botonEstado.classList.remove('error');
      cargarBorradores();
      return true;
    } catch (error) {
      botonEstado.textContent = 'No se pudo guardar el borrador local. Puedes continuar, pero evita cerrar esta página.';
      botonEstado.classList.add('error');
      return false;
    }
  }

  function programarGuardado() {
    if (restoring) return;
    clearTimeout(timer);
    timer = setTimeout(guardarBorrador, 650);
  }
  window.guardarRecepcionBorrador = programarGuardado;

  form.addEventListener('input', programarGuardado);
  form.addEventListener('change', event => {
    if (event.target.id === 'factura') currentFile = event.target.files?.[0] || currentFile;
    programarGuardado();
  });

  async function cargarBorradores() {
    if (!db) return;
    try {
      const rows = await transaccion('readonly', store => store.getAll());
      const panel = document.getElementById('borradoresRecepcion');
      const lista = document.getElementById('listaBorradoresRecepcion');
      lista.replaceChildren();
      const usuarioActual = form.dataset.usuarioId || "";
      const pendientes = (rows || []).filter(row => row.usuario === usuarioActual).sort((a,b) => b.actualizado - a.actualizado);
      panel.hidden = pendientes.length === 0;
      pendientes.forEach(b => {
        const item = document.createElement('article'); item.className = 'borrador-item';
        const info = document.createElement('div');
        const titulo = document.createElement('strong'); titulo.textContent = b.dato.numero || 'Factura sin número';
        const detalle = document.createElement('p'); detalle.textContent = [b.dato.proveedor || 'Proveedor pendiente', b.dato.fecha || '', `${b.dato.lineas.length} producto(s)`, b.nombreArchivo ? 'Con factura adjunta' : ''].filter(Boolean).join(' · ');
        info.append(titulo, detalle);
        const acciones = document.createElement('div'); acciones.className = 'inline-actions';
        const continuar = document.createElement('button'); continuar.type = 'button'; continuar.className = 'btn small'; continuar.textContent = 'Continuar'; continuar.addEventListener('click', () => restaurar(b));
        const borrar = document.createElement('button'); borrar.type = 'button'; borrar.className = 'btn danger small'; borrar.textContent = 'Eliminar'; borrar.addEventListener('click', async () => { if (confirm('¿Eliminar este borrador? No cambiará el inventario.')) { await transaccion('readwrite', s => s.delete(b.id)); cargarBorradores(); } });
        acciones.append(continuar,borrar); item.append(info,acciones); lista.appendChild(item);
      });
    } catch (error) { /* La recepción manual sigue disponible aunque el navegador no conserve borradores. */ }
  }

  async function restaurar(borrador) {
    restoring = true;
    abrir();
    document.getElementById('borradorId').value = borrador.id;
    document.getElementById('proveedor').value = borrador.dato.proveedor || '';
    document.getElementById('numeroFactura').value = borrador.dato.numero || '';
    document.getElementById('fecha').value = borrador.dato.fecha || '';
    document.getElementById('totalFactura').value = borrador.dato.total || '';
    document.getElementById('margenSugerido').value = borrador.dato.margen || '30';
    document.getElementById('textoOCR').value = borrador.dato.textoOCR || '';
    document.getElementById('ocrReview').hidden = !borrador.dato.textoOCR;
    document.getElementById('productosPegados').value = borrador.dato.tablaPegada || '';
    currentFile = borrador.archivo || null;
    if (currentFile) {
      try { const transfer = new DataTransfer(); transfer.items.add(currentFile); document.getElementById('factura').files = transfer.files; } catch (error) { estado('El borrador conservó la factura, pero el navegador no permite restaurar el archivo al formulario. Adjunta el original otra vez.', true); }
    }
    const contenedor = document.getElementById('lineas'); contenedor.replaceChildren();
    for (const l of borrador.dato.lineas) {
      const fila = agregarLinea({ nombre: l.nombre, cantidad: l.cantidad, costo: l.costo, origen: 'borrador' });
      const select = fila.querySelector('.producto-select');
      select.dataset.cargando = 'true'; select.value = l.productoId || '';
      actualizarProductoNuevo(select); delete select.dataset.cargando;
      fila.querySelector('.nombre-nuevo').value = l.nombre || '';
      fila.querySelector('.categoria-nueva').value = l.categoria || '';
      fila.querySelector('.catalogo-invima-id').value = l.catalogoId || '';
      fila.querySelector('.registro-invima').value = l.registro || '';
      fila.querySelector('.codigo-barras').value = l.codigoBarras || '';
      fila.querySelector('.cantidad-factura').value = l.cantidad || '';
      fila.querySelector('.costo-factura').value = l.costo || '';
      fila.querySelector('.unidad-base').value = l.unidad || 'unidad';
      fila.querySelector('.factor-empaque').value = l.factor || '1';
      fila.querySelector('.blister-factor-nuevo').value = l.blister || '0';
      fila.querySelector('.precio-venta-nuevo').value = l.precioNuevo || '';
      const actualiza = fila.querySelector('.actualizar-precio'); actualiza.checked = Boolean(l.actualizarPrecio); alternarPrecio(actualiza);
      fila.querySelector('.precio-venta-actual').value = l.precioActual || '';
      const control = fila.querySelector('.control-lotes'); control.checked = Boolean(l.lotesActivos); alternarLotes(control);
      if (l.lotesActivos) {
        const detalle = fila.nextElementSibling.querySelector('.lotes-lista'); detalle.replaceChildren();
        (l.lotes || []).forEach(datoLote => { agregarLote(fila.nextElementSibling.querySelector('button')); const nuevo = detalle.lastElementChild; nuevo.querySelector('.dato-lote').value = datoLote.lote || ''; nuevo.querySelector('.dato-vencimiento').value = datoLote.vencimiento || ''; nuevo.querySelector('.dato-cantidad-lote').value = datoLote.cantidad || '1'; });
      }
      actualizarConversion(fila);
    }
    if (!borrador.dato.lineas.length) agregarLinea();
    document.getElementById('resumenImportes').dispatchEvent(new Event('input'));
    document.getElementById('importStatus').hidden = true;
    document.getElementById('estadoBorrador').hidden = false;
    document.getElementById('estadoBorrador').textContent = 'Borrador restaurado. Revisa los datos antes de confirmar la recepción.';
    document.getElementById('modal').classList.add('open');
    restoring = false;
  }

  const abrirNuevoOriginal = document.getElementById('abrirRecepcion');
  abrirNuevoOriginal.addEventListener('click', () => {
    document.getElementById('borradorId').value = '';
    currentFile = null;
    document.getElementById('estadoBorrador').hidden = true;
  });

  const cerrarOriginal = window.cerrar;
  window.cerrar = async function () { const ok=await guardarBorrador(); if(ok) cerrarOriginal(); else estado('No pude guardar el borrador; el formulario queda abierto para no perder los datos.',true); };

  form.addEventListener('submit', async event => {
    if (event.defaultPrevented) return;
    event.preventDefault();
    clearTimeout(timer);
    const guardado = await guardarBorrador();
    if (!guardado) estado('No se pudo guardar una copia local; continuaré con la recepción que estás confirmando.', true);
    form.dataset.omitirBorrador = 'true';
    HTMLFormElement.prototype.submit.call(form);
  });

  const borradorTerminado = new URLSearchParams(location.search).get('borradorId');
  if (borradorTerminado) transaccion('readwrite', store => store.delete(borradorTerminado)).then(cargarBorradores).catch(() => {});

  // Captura códigos de un lector USB tipo teclado y permite buscar el CUM asociado.
  document.addEventListener('keydown', event => {
    if (!event.target.matches('.codigo-barras') || event.key !== 'Enter') return;
    event.preventDefault(); resolverCodigoBarras(event.target);
  });
  document.addEventListener('change', event => { if (event.target.matches('.codigo-barras')) resolverCodigoBarras(event.target); });

  async function resolverCodigoBarras(input) {
    const lectura = decodificarCodigoBarras(input.value);
    const codigo = lectura?.codigo || '';
    const fila = input.closest('tr');
    let aviso = input.parentElement.querySelector('.barcode-estado');
    if (!aviso) { aviso = document.createElement('small'); aviso.className = 'barcode-estado'; aviso.setAttribute('aria-live','polite'); input.parentElement.appendChild(aviso); }
    if (!/^[0-9]{8,14}$/.test(codigo)) { aviso.textContent = 'Usa un código EAN/UPC o un código GS1 que incluya GTIN.'; aviso.classList.add('error'); return; }
    input.value = codigo;
    aviso.textContent = 'Buscando presentación…'; aviso.className = 'barcode-estado';
    try {
      const url = new URL('CatalogoMedicamentoServlet', location.href); url.searchParams.set('barcode', codigo);
      const respuesta = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!respuesta.ok) throw new Error('Consulta no disponible');
      const item = await respuesta.json();
      if (!item) { aviso.textContent = 'Código sin asociación. Selecciona “Crear producto nuevo” y confirma la presentación INVIMA para guardarlo.'; aviso.classList.add('error'); return; }
      const select = fila.querySelector('.producto-select');
      const existentes = [...select.options].filter(op => op.dataset.catalogoId === String(item.id));
      if (existentes.length) {
        select.value = existentes[0].value;
        actualizarProductoNuevo(select);
      } else {
        select.value = 'nuevo'; actualizarProductoNuevo(select);
        fila.querySelector('.nombre-nuevo').value = (item.producto || '').slice(0,100);
        fila.querySelector('.registro-invima').value = item.registro || '';
        fila.querySelector('.catalogo-invima-id').value = item.id;
        const grupoATC = {A:'A · Digestivo y metabolismo',B:'B · Sangre y órganos hematopoyéticos',C:'C · Sistema cardiovascular',D:'D · Dermatológicos',G:'G · Genitourinario y hormonas sexuales',H:'H · Preparados hormonales sistémicos',J:'J · Antiinfecciosos para uso sistémico',L:'L · Antineoplásicos e inmunomoduladores',M:'M · Sistema musculoesquelético',N:'N · Sistema nervioso',P:'P · Antiparasitarios',R:'R · Sistema respiratorio',S:'S · Órganos de los sentidos',V:'V · Varios'};
        if (item.atc && grupoATC[item.atc.trim().charAt(0)]) fila.querySelector('.categoria-nueva').value = grupoATC[item.atc.trim().charAt(0)];
      }
      fila.querySelector('.codigo-barras').value = codigo;
      fila.querySelector('.catalogo-invima-id').value = item.id;
      fila.querySelector('.registro-invima').value = item.registro || '';
      if (lectura.lote || lectura.vencimiento) {
        const control=fila.querySelector('.control-lotes');control.checked=true;alternarLotes(control);
        const detalle=fila.nextElementSibling.querySelector('.lote-detalle');
        if(lectura.lote)detalle.querySelector('.dato-lote').value=lectura.lote.slice(0,30);
        if(lectura.vencimiento)detalle.querySelector('.dato-vencimiento').value=lectura.vencimiento;
      }
      aviso.textContent = `Código reconocido: ${item.producto} · CUM ${item.cum}. Revisa lote, cantidad y empaque.`; aviso.classList.add('ok');
      programarGuardado();
    } catch (error) { aviso.textContent = 'No se pudo consultar INVIMA. Puedes continuar con la búsqueda manual.'; aviso.classList.add('error'); }
  }

  // Descompone GTIN y datos básicos GS1 de DataMatrix/GS1-128 cuando están incluidos en el empaque.
  function decodificarCodigoBarras(valor) {
    let texto=String(valor||'').trim().replace(/^\](?:d2|C1)/,'');
    const directo=texto.replace(/\s/g,'');
    if(/^[0-9]{8,14}$/.test(directo))return {codigo:directo};
    const parentizado=texto.match(/\(01\)(\d{14})/);
    let codigo=parentizado?.[1]||'';
    let vencimiento='';let lote='';
    if(parentizado){
      const fecha=texto.match(/\(17\)(\d{6})/);if(fecha)vencimiento=fechaGS1(fecha[1]);
      const loteMatch=texto.match(/\(10\)([^()\x1d]+)/);if(loteMatch)lote=loteMatch[1].trim();
    }else if(/^01\d{14}/.test(directo)){
      codigo=directo.slice(2,16);const resto=directo.slice(16);
      const fecha=resto.match(/(?:^|\x1d)17(\d{6})/);if(fecha)vencimiento=fechaGS1(fecha[1]);
      const loteMatch=resto.match(/(?:^|\x1d)10([^\x1d]{1,30})(?:\x1d|$)/);if(loteMatch)lote=loteMatch[1].replace(/21[A-Za-z0-9]+$/,'').trim();
    }
    return codigo?{codigo,vencimiento,lote}:null;
  }

  function fechaGS1(valor) {
    const anio=2000+Number(valor.slice(0,2));const mes=Number(valor.slice(2,4));
    if(mes<1||mes>12)return '';
    const dia=Number(valor.slice(4,6))||new Date(anio,mes,0).getDate();
    return `${anio}-${String(mes).padStart(2,'0')}-${String(dia).padStart(2,'0')}`;
  }

  window.resolverCodigoBarras = resolverCodigoBarras;

  window.abrirCamaraCodigo = async boton => {
    if (!('BarcodeDetector' in window) || !navigator.mediaDevices?.getUserMedia) { estado('La cámara de códigos no está disponible en este navegador. Usa un lector USB o escribe el código.', true); return; }
    barcodeRow = boton.closest('tr');
    try {
      const formatos = ['ean_13','ean_8','upc_a','upc_e','code_128','itf','data_matrix'];
      const soportados = await BarcodeDetector.getSupportedFormats();
      const detector = new BarcodeDetector({ formats: formatos.filter(formato => soportados.includes(formato)) });
      cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      const modalCamara = document.getElementById('modalCamaraCodigo');
      const video = document.getElementById('videoCamaraCodigo'); video.srcObject = cameraStream;
      modalCamara.classList.add('open'); await video.play();
      document.getElementById('estadoCamaraCodigo').textContent = 'Centra el código dentro del recuadro.';
      const buscar = async () => {
        if (!cameraStream) return;
        try { const lecturas = await detector.detect(video); if (lecturas.length) { barcodeRow.querySelector('.codigo-barras').value = lecturas[0].rawValue; cerrarCamaraCodigo(); await resolverCodigoBarras(barcodeRow.querySelector('.codigo-barras')); return; } } catch (error) { /* Mantiene el escaneo activo mientras la cámara enfoca. */ }
        cameraFrame = requestAnimationFrame(buscar);
      };
      buscar();
    } catch (error) { cerrarCamaraCodigo(); estado('No fue posible abrir la cámara. Revisa el permiso del navegador o utiliza un lector USB.', true); }
  };
  window.cerrarCamaraCodigo = () => {
    if (cameraFrame) cancelAnimationFrame(cameraFrame);
    cameraStream?.getTracks().forEach(track => track.stop()); cameraStream = null;
    const video = document.getElementById('videoCamaraCodigo'); if (video) video.srcObject = null;
    document.getElementById('modalCamaraCodigo').classList.remove('open');
  };

  function estado(mensaje, error=false) { const salida=document.getElementById('importStatus'); salida.textContent=mensaje; salida.classList.toggle('error',error); salida.hidden=false; }
})();
