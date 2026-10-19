/* Lee formatos tabulares/XML en el navegador y crea un borrador editable; no escribe el inventario. */
(() => {
  const archivo = document.getElementById('factura');
  if (!archivo) return;

  // Intenta importar al seleccionar un archivo; los PDF y fotos se conservan para revisión manual.
  archivo.addEventListener('change', async () => {
    const file = archivo.files?.[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      mostrarEstado('El archivo supera el límite de 10 MB.', 'error');
      archivo.value = '';
      return;
    }
    const extension = file.name.split('.').pop().toLowerCase();
    try {
      let factura;
      if (['csv', 'txt'].includes(extension)) factura = leerCSV(await leerTexto(file));
      else if (extension === 'xml') factura = leerXML(await leerTexto(file));
      else {
        mostrarEstado('El archivo quedó adjunto. Este formato requiere revisión manual: completa los datos y las líneas antes de confirmar.');
        return;
      }
      document.getElementById('proveedor').value ||= factura.proveedor || '';
      document.getElementById('numeroFactura').value ||= factura.numero || '';
      document.getElementById('fecha').value ||= fechaISO(factura.fecha || '');
      document.getElementById('totalFactura').value ||= numeroISO(factura.total || '');
      if (!factura.lineas.length) {
        mostrarEstado('No encontré líneas reconocibles. El archivo queda adjunto; puedes cargar los productos manualmente.', 'error');
        return;
      }
      const contenedor = document.getElementById('lineas');
      contenedor.replaceChildren();
      factura.lineas.forEach(linea => agregarLinea(linea));
      mostrarEstado(`Leí ${factura.lineas.length} línea(s). Revisa proveedor, factura, productos, cantidades y costos antes de confirmar.`);
    } catch (error) {
      mostrarEstado(`No pude interpretar el archivo (${error.message}). Quedó adjunto y puedes completar la recepción manualmente.`, 'error');
    }
  });

  // Usa UTF-8 y recurre a Windows-1252 para facturas CSV antiguas.
  async function leerTexto(file) {
    const bytes = await file.arrayBuffer();
    try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
    catch { return new TextDecoder('windows-1252').decode(bytes); }
  }

  // Lee un CSV con separador automático y comillas dobles escapadas.
  function leerCSV(texto) {
    const lineas = texto.replace(/^\uFEFF/, '');
    const primera = lineas.split(/\r?\n/, 1)[0] || '';
    const separador = [',', ';', '\t'].sort((a, b) => contar(primera, b) - contar(primera, a))[0];
    const filas = dividirCSV(lineas, separador).filter(fila => fila.some(celda => celda.trim()));
    if (filas.length < 2) throw new Error('el CSV no tiene filas de productos');
    const cabeceras = filas.shift().map(normalizar);
    const buscar = (...nombres) => cabeceras.findIndex(c => nombres.some(n => c === normalizar(n)));
    const columnas = {
      nombre: buscar('medicamento', 'producto', 'nombre', 'descripcion', 'description', 'item', 'product', 'articulo'),
      cantidad: buscar('cantidad', 'quantity', 'qty', 'unidades'),
      costo: buscar('costo', 'cost', 'precio', 'price', 'precio unitario', 'unit price', 'unit cost', 'costo unitario'),
      lote: buscar('lote', 'lot', 'batch', 'batch number'),
      vencimiento: buscar('vencimiento', 'fecha vencimiento', 'expiry', 'expiration', 'expiry date'),
      proveedor: buscar('proveedor', 'supplier', 'vendor'),
      numero: buscar('factura', 'numero factura', 'invoice', 'invoice number', 'invoice id'),
      fecha: buscar('fecha factura', 'fecha', 'date', 'issue date', 'invoice date'),
      total: buscar('total factura', 'total', 'invoice total', 'payable amount')
    };
    if (columnas.nombre < 0 || columnas.cantidad < 0 || columnas.costo < 0) {
      throw new Error('faltan columnas de producto, cantidad o costo');
    }
    const lineasImportadas = filas.map(fila => ({
      nombre: celda(fila, columnas.nombre), cantidad: unidadesISO(celda(fila, columnas.cantidad)),
      costo: numeroISO(celda(fila, columnas.costo)), lote: celda(fila, columnas.lote),
      vencimiento: fechaISO(celda(fila, columnas.vencimiento))
    })).filter(fila => fila.nombre);
    return { proveedor: valorComun(filas, columnas.proveedor), numero: valorComun(filas, columnas.numero),
      fecha: valorComun(filas, columnas.fecha), total: valorComun(filas, columnas.total), lineas: lineasImportadas };
  }

  // Divide filas CSV sin cortar comas o punto y coma dentro de campos entrecomillados.
  function dividirCSV(texto, separador) {
    const filas = []; let fila = []; let valor = ''; let comillas = false;
    for (let i = 0; i < texto.length; i++) {
      const caracter = texto[i];
      if (caracter === '"' && comillas && texto[i + 1] === '"') { valor += '"'; i++; }
      else if (caracter === '"') comillas = !comillas;
      else if (caracter === separador && !comillas) { fila.push(valor); valor = ''; }
      else if ((caracter === '\n' || caracter === '\r') && !comillas) {
        if (caracter === '\r' && texto[i + 1] === '\n') i++;
        fila.push(valor); filas.push(fila); fila = []; valor = '';
      } else valor += caracter;
    }
    fila.push(valor); filas.push(fila);
    return filas;
  }

  // Lee etiquetas comunes de XML UBL y de exportaciones XML de proveedores.
  function leerXML(texto) {
    const xml = new DOMParser().parseFromString(texto, 'application/xml');
    if (xml.querySelector('parsererror')) throw new Error('el XML no es válido');
    const lineasXML = [...xml.getElementsByTagName('*')].filter(n =>
      ['invoiceline', 'lineitem', 'itemline', 'detallefactura'].includes(normalizar(n.localName || n.nodeName)));
    const lineas = lineasXML.map(n => ({
      nombre: buscarTexto(n, ['description', 'itemname', 'name', 'descripcion', 'nombreproducto']),
      cantidad: unidadesISO(buscarTexto(n, ['invoicedquantity', 'quantity', 'qty', 'cantidad'])),
      costo: numeroISO(buscarTexto(n, ['priceamount', 'unitprice', 'unitcost', 'precio', 'costo'])),
      lote: buscarTexto(n, ['lotid', 'lotnumber', 'batchnumber', 'lote']),
      vencimiento: fechaISO(buscarTexto(n, ['expirydate', 'expirationdate', 'fechavencimiento']))
    })).filter(linea => linea.nombre);
    const proveedorNode = [...xml.getElementsByTagName('*')].find(n =>
      ['accountingsupplierparty', 'supplierparty', 'supplier'].includes(normalizar(n.localName || n.nodeName)));
    const proveedor = buscarTexto(proveedorNode || xml, ['suppliername', 'providername', 'nombreproveedor', 'registrationname', 'partyname', 'name']);
    const numero = buscarTexto(xml, ['invoicenumber', 'invoiceid', 'documentnumber', 'numeroFactura']) || buscarTexto(xml.documentElement, ['id']);
    const fecha = buscarTexto(xml, ['issuedate', 'invoicedate', 'fechaFactura', 'fecha']);
    const total = buscarTexto(xml, ['payableamount', 'taxinclusiveamount', 'invoicetotal', 'totalamount']);
    return { proveedor, numero, fecha, total, lineas };
  }

  // Obtiene el primer texto no vacío que coincida con uno de los nombres de etiqueta.
  function buscarTexto(raiz, etiquetas) {
    const buscadas = etiquetas.map(normalizar);
    return [...raiz.getElementsByTagName('*')].find(n => buscadas.includes(normalizar(n.localName || n.nodeName)) && n.textContent.trim())?.textContent.trim() || '';
  }

  // Devuelve el primer valor repetido de una columna de cabecera del CSV.
  function valorComun(filas, indice) { return indice < 0 ? '' : (filas.find(f => celda(f, indice).trim()) || [])[indice] || ''; }
  function celda(fila, indice) { return indice < 0 ? '' : (fila[indice] || '').trim(); }
  function contar(texto, caracter) { return [...texto].filter(c => c === caracter).length; }
  function unidadesISO(valor) { const n = Number(numeroISO(valor)); return Number.isFinite(n) ? String(Math.round(n)) : ''; }
})();
