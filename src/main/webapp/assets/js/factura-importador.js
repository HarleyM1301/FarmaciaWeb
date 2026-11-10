/* Lee formatos tabulares, XML e imágenes localmente; solo prepara un borrador editable. */
(() => {
  const archivo = document.getElementById('factura');
  if (!archivo) return;

  // Importa datos estructurados o usa OCR local con imágenes; nunca confirma el inventario.
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
      else if (extension === 'zip') factura = await leerZIP(file);
      else if (extension === 'pdf') factura = await leerPDF(file);
      else if (['jpg', 'jpeg', 'png'].includes(extension)) {
        mostrarEstado('Leyendo la imagen en este navegador. Puede tardar unos segundos…');
        const texto = await leerImagen(file);
        document.getElementById('textoOCR').value = texto;
        document.getElementById('ocrReview').hidden = false;
        factura = leerOCR(texto);
      }
      else {
        mostrarEstado('El archivo quedó adjunto. Este formato no tiene lectura automática: pega la tabla de productos o agrégalos manualmente.');
        return;
      }
      completarEncabezado(factura);
      if (!factura.lineas.length) {
        mostrarEstado('No pude reconocer renglones de productos. El texto extraído quedó disponible para corregirlo o puedes agregarlos manualmente.', 'error');
        return;
      }
      const contenedor = document.getElementById('lineas');
      contenedor.replaceChildren();
      factura.lineas.forEach(linea => agregarLinea({ ...linea, origen: factura.origen || 'archivo' }));
      window.guardarRecepcionBorrador?.();
      const pendientesOCR = factura.origen === 'ocr' && factura.lineas.some(linea => !linea.cantidad || !linea.costo);
      const monedaAjena = factura.moneda && factura.moneda !== 'COP';
      const resumen = pendientesOCR
        ? `Detecté ${factura.lineas.length} producto(s), pero no pude separar con seguridad todas las cantidades y costos. Completa esos campos y confirma cada medicamento antes de guardar.`
        : `Detecté ${factura.lineas.length} producto(s). Revisa nombres, cantidades, costos y datos de factura antes de confirmar.`;
      mostrarEstado(resumen + (factura.advertencia ? ` ${factura.advertencia}` : '') + (monedaAjena ? ` La factura indica ${factura.moneda}; convierte los importes a COP antes de confirmar, porque el sistema no convierte monedas.` : ''), pendientesOCR || monedaAjena || Boolean(factura.advertencia) ? 'error' : 'ok');
    } catch (error) {
      mostrarEstado(`No pude leer la factura (${error.message}). El archivo quedó adjunto y puedes completar la recepción manualmente.`, 'error');
    }
  });

  // Extrae texto de PDF digitales o renderiza páginas localmente para usar el OCR español incluido.
  async function leerPDF(file) {
    mostrarEstado('Procesando el PDF localmente…');
    const datos = new FormData(); datos.append('archivo', file, file.name);
    const contexto = document.getElementById('ocr-runtime').dataset.context;
    const respuesta = await fetch(`${contexto}/FacturaPdfServlet`, { method: 'POST', body: datos, headers: { Accept: 'application/json' } });
    const resultado = await respuesta.json();
    if (!respuesta.ok) throw new Error(resultado.error || 'el servidor no pudo procesar el PDF');
    let texto = resultado.texto || '';
    if (resultado.paginas?.length) {
      for (let i = 0; i < resultado.paginas.length; i++) {
        const pagina = resultado.paginas[i];
        mostrarEstado(`Leyendo página ${i + 1} de ${resultado.paginas.length} con OCR local…`);
        const imagen = await fetch(pagina.imagen).then(r => r.blob());
        texto += `\n${await leerImagen(new File([imagen], `factura-pagina-${pagina.pagina}.jpg`, { type: 'image/jpeg' }))}`;
      }
    }
    const advertencia = resultado.totalPaginas > (resultado.paginas?.length || 0) && resultado.paginas?.length
      ? `El PDF tiene ${resultado.totalPaginas} páginas; se leyeron las primeras ${resultado.paginas.length}. Revisa el resto en el archivo original.` : '';
    if (!texto.trim()) throw new Error('no se encontró texto legible en el PDF');
    document.getElementById('textoOCR').value = texto;
    document.getElementById('ocrReview').hidden = false;
    const factura = leerOCR(texto);
    factura.origen = 'ocr';
    factura.advertencia = advertencia;
    return factura;
  }

  // Abre el XML DIAN de AttachedDocument o el PDF que algunos proveedores incluyen en un ZIP.
  async function leerZIP(file) {
    mostrarEstado('Abriendo factura electrónica del ZIP…');
    const datos = new FormData(); datos.append('archivo', file, file.name);
    const contexto = document.getElementById('ocr-runtime').dataset.context;
    const respuesta = await fetch(`${contexto}/FacturaZipServlet`, { method: 'POST', body: datos, headers: { Accept: 'application/json' } });
    const resultado = await respuesta.json();
    if (!respuesta.ok) throw new Error(resultado.error || 'el ZIP no contiene una factura reconocible');
    if (resultado.tipo === 'xml') return leerXML(resultado.contenido);
    const bytes = Uint8Array.from(atob(resultado.contenido), c => c.charCodeAt(0));
    return leerPDF(new File([bytes], 'factura-adjunta.pdf', { type: 'application/pdf' }));
  }

  // Completa cabecera sin borrar datos que la persona ya haya escrito.
  function completarEncabezado(factura) {
    if (factura.proveedor) document.getElementById('proveedor').value ||= factura.proveedor;
    if (factura.numero) document.getElementById('numeroFactura').value ||= factura.numero;
    const campoFecha = document.getElementById('fecha');
    if (factura.fecha && (!campoFecha.value || campoFecha.value === campoFecha.dataset.default)) {
      campoFecha.value = fechaISO(factura.fecha);
    }
    if (factura.total) document.getElementById('totalFactura').value ||= numeroOCR(factura.total);
  }

  // Ejecuta Tesseract.js y sus modelos locales; la imagen no sale del navegador.
  async function leerImagen(file) {
    if (!window.Tesseract?.createWorker) throw new Error('el lector OCR no pudo cargarse; comprueba que estén desplegados los recursos de OCR');
    const contexto = document.getElementById('ocr-runtime').dataset.context;
    const base = `${contexto}/assets/vendor/ocr`;
    let worker;
    try {
      worker = await Tesseract.createWorker('spa', 1, {
        workerPath: `${base}/worker.min.js`,
        corePath: `${base}/core/tesseract-core-lstm.wasm.js`,
        langPath: `${base}/lang/4.0.0_best_int`,
        gzip: true,
        workerBlobURL: false,
        logger: progreso => {
          if (progreso.status === 'recognizing text') {
            mostrarEstado(`Leyendo la factura: ${Math.round(progreso.progress * 100)} %`);
          }
        }
      });
      const resultado = await worker.recognize(file);
      return resultado.data.text || '';
    } finally {
      if (worker) await worker.terminate();
    }
  }

  // Extrae productos del OCR, pero deja vacíos los importes si la factura no tiene encabezados confiables.
  function leerOCR(texto) {
    const lineas = texto.split(/\r?\n/).map(linea => linea.replace(/\s+/g, ' ').trim()).filter(Boolean);
    const completo = lineas.join('\n');
    const obtener = (patron) => completo.match(patron)?.[1]?.trim() || '';
    const proveedor = obtener(/(?:proveedor|supplier|emisor)\s*[:#-]?\s*([^\n]+)/i) || lineas[0] || '';
    const moneda = obtener(/(?:moneda|currency)\s*[:#-]?\s*([A-Z]{3})\b/i);
    const numero = obtener(/(?:n(?:ú|u)?mero\s+de\s+factura|invoice\s*(?:number|no\.?|#)|factura\s*(?:n(?:ú|u)?m(?:ero)?\.?|no\.?|#))\s*[:#-]?\s*([A-Z0-9-]{3,})/i)
      || obtener(/\bfactura\s*[:#]\s*([A-Z0-9-]{3,})/i)
      || obtener(/\bfactura\s+(\d{4,})/i)
      || obtener(/\bconsecutivo\s*[:#-]?\s*([A-Z0-9-]{5,})/i)
      || obtener(/\bdoc(?:umento)?\.?\s*interno\s*[:#-]?\s*([A-Z0-9-]{3,})/i);
    let fecha = obtener(/(?:fecha|date)\s*[:#-]?\s*(\d{1,2}[/. -]\d{1,2}[/. -]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}\s+de\s+[a-záéíóú]+\s+de\s+\d{4})/i);
    if (!fecha) fecha = lineas.find(linea => /^factura\s*[:#]?\s*\d{4,}/i.test(linea))?.match(/(\d{1,2}[/. -]\d{1,2}[/. -]\d{2,4}|\d{1,2}\s+de\s+[a-záéíóú]+\s+de\s+\d{4})/i)?.[1] || '';
    if (fecha) fecha = fecha.replace(/\bde\b/gi, ' ').replace(/\s+/g, ' ').trim();
    const totalLine = [...lineas].reverse().find(linea => /\b(total|total a pagar|importe total|payable)\b/i.test(linea));
    // El total suele aparecer después de subtotal, IVA y otros valores en la misma línea.
    const valoresTotal = totalLine?.match(/\d[\d.,]*/g) || [];
    const total = valoresTotal.length ? valoresTotal[valoresTotal.length - 1] : '';
    const encabezado = lineas.findIndex(linea => /\b(descripci[oó]n|producto|medicamento|art[ií]culo)\b/i.test(linea)
      && /\b(cant|cantidad|qty|unidades)\b/i.test(linea));
    const inicio = encabezado >= 0 ? encabezado + 1 : 0;
    const encabezadosNormalizados = normalizar(completo);
    const tablaFactura = /descripcion/.test(encabezadosNormalizados)
      && /(?:cantidad|cant|qty)/.test(encabezadosNormalizados)
      && /precio(?:ud|unit|unitario)/.test(encabezadosNormalizados)
      && /(?:importe|total)/.test(encabezadosNormalizados);
    const tablaConImpuestoDescuento = tablaFactura
      && /(?:dto|descuento)/.test(encabezadosNormalizados)
      && /(?:imp|iva|impuesto)/.test(encabezadosNormalizados);
    const productos = [];
    for (const original of lineas.slice(inicio)) {
      // Termina al llegar al pie de factura para no convertir impuestos o totales en productos.
      if (/\b(subtotal|total a pagar|importe total|iva|impuesto|observaciones|forma de pago|especialidad|compras:)\b/i.test(original)) break;
      if (/\b(descripci[oó]n|producto|medicamento|cantidad|cant\.?|precio unitario|precio unit\.?|total)\b/i.test(original)) continue;
      // Quita el código de artículo cuando el OCR lo separó con una barra vertical.
      const fila = quitarCodigoArticulo(original);
      let nombre = '';
      let cantidad = '';
      let costo = '';
      if (tablaFactura) {
        // Los valores finales pertenecen a las columnas de la tabla; códigos y concentraciones quedan antes de ellas.
        const valores = [...fila.matchAll(/\d[\d.,]*/g)];
        if (valores.length < 5) continue;
        const indiceColumnas = valores.length - 5;
        const indiceCantidad = tablaConImpuestoDescuento ? indiceColumnas : indiceColumnas + 1;
        nombre = fila.slice(0, valores[indiceCantidad].index);
        cantidad = unidadesISO(valores[indiceCantidad][0]);
        if (tablaConImpuestoDescuento && valores.length >= 5) {
          // En facturas con descuento e impuesto, el total de línea refleja mejor el costo realmente facturado.
          const importeLinea = Number(numeroOCR(valores[valores.length - 1][0]));
          const unidadesLinea = Number(cantidad);
          costo = Number.isFinite(importeLinea) && unidadesLinea > 0
            ? (importeLinea / unidadesLinea).toFixed(4)
            : numeroOCR(valores[valores.length - 4][0]);
        } else {
          costo = numeroOCR(valores[indiceColumnas + 2][0]);
        }
      } else {
        // Sin cabeceras, reconoce el nombre por la presentación farmacéutica y no adivina cantidad ni costo.
        nombre = extraerNombreMedicamento(fila);
      }
      // Quita etiquetas de referencia y signos que suelen quedar pegados al nombre en el OCR.
      nombre = nombre.replace(/\b(?:c[oó]digo|ref(?:erencia)?|ean|sku)\b/gi, ' ')
        .replace(/[^\p{L}\p{N}()/-]+$/u, '').trim();
      // Descarta líneas sin un nombre suficientemente útil para compararlo con el catálogo.
      if (nombre.replace(/[^\p{L}]/gu, '').length < 4) continue;
      // Rechaza cantidades y precios evidentemente fuera de los límites de una factura.
      if (cantidad && (!costo || Number(cantidad) > 50000 || Number(costo) > 100000000)) {
        cantidad = '';
        costo = '';
      }
      const formas = '(?:comprimidos?|compr\\.?|comp\\.?|tabletas?|tabs?\\.?|c[aá]psulas?|caps?\\.?|sobres?|ampollas?|unidades?|uds?\\.?|frascos?|ml)';
      const multiplicador = nombre.match(new RegExp(`\\b(\\d{1,4})\\s*[x×]\\s*(\\d{1,4})\\s*(${formas})(?=\\W|$)`, 'i'));
      const presentacion = multiplicador || nombre.match(new RegExp(`\\b(\\d{1,5})\\s*(${formas})(?=\\W|$)`, 'i'));
      const tipo = presentacion?.[multiplicador ? 3 : 2]?.toLocaleLowerCase('es') || '';
      const unidadFactura = /\b(?:unid|unidad|und|ud)\b/i.test(fila);
      const unidadBase = /comprimido|compr|comp\.|tableta|tab/.test(tipo) ? 'tableta' : /c[aá]psula|caps?\./.test(tipo) ? 'capsula'
        : /sobre/.test(tipo) ? 'sobre' : /ampolla/.test(tipo) ? 'ampolla' : /frasco/.test(tipo) ? 'frasco'
        : /ml/.test(tipo) ? (unidadFactura ? 'frasco' : 'ml') : 'unidad';
      const unidadesPorPresentacion = /ml/.test(tipo) && unidadFactura ? 1
        : multiplicador ? Number(multiplicador[1]) * Number(multiplicador[2]) : presentacion ? Number(presentacion[1]) : 1;
      // Envía el borrador a la pantalla; si no se leyeron importes seguros quedan vacíos para captura manual.
      productos.push({ nombre, cantidad, costo, unidadBase, unidadesPorPresentacion: String(unidadesPorPresentacion) });
    }
    // Marca el origen para que la pantalla pueda explicar cuándo debe completarse cantidad y costo.
    return { proveedor, numero, fecha, total, moneda, lineas: productos, origen: 'ocr' };
  }

  // Elimina un código de producto inicial sin quitar números que pertenezcan a su concentración.
  function quitarCodigoArticulo(linea) {
    return linea
      .replace(/^\s*[([]?\s*[A-Z0-9][A-Z0-9.,/-]{3,}\s*[|¦]\s*/i, '')
      .replace(/^\s*\(?\d{1,3}[.)]?\s+/, '')
      .replace(/^\s*[A-Z]{1,5}\d[A-Z0-9-]{3,}\s+/i, '')
      .replace(/^\s*\d{8,}\s+/, '')
      .replace(/^\s*\d{5,}(?:[.,]\d+)?\s+/, '')
      .trim();
  }

  // Conserva la descripción hasta la presentación del medicamento y descarta columnas numéricas posteriores.
  function extraerNombreMedicamento(linea) {
    const presentacion = /\b\d{1,5}\s*(?:comprimidos?|compr\.?|tabletas?|tabs?\.?|c[aá]psulas?|caps?\.?|sobres?|ampollas?|frascos?|unidades?|uds?\.?|ml)(?=\W|$)/gi;
    const coincidencias = [...linea.matchAll(presentacion)];
    if (coincidencias.length) {
      const ultima = coincidencias[coincidencias.length - 1];
      return linea.slice(0, ultima.index + ultima[0].length);
    }
    const efg = linea.match(/\b(?:EFG|gen[eé]rico)\b/i);
    if (efg) return linea.slice(0, efg.index + efg[0].length);
    return linea.replace(/\s+[\d.,]+(?:\s+[\d.,]+){2,}.*$/, '').trim();
  }

  // Permite volver a interpretar el texto si OCR separó mal una fila o algún número.
  window.importarTextoOCR = () => {
    const factura = leerOCR(document.getElementById('textoOCR').value);
    completarEncabezado(factura);
    if (!factura.lineas.length) {
      mostrarEstado('Aún no reconozco filas de producto en el texto. Corrige la lectura o agrégalas manualmente.', 'error');
      return;
    }
    document.getElementById('lineas').replaceChildren();
    factura.lineas.forEach(linea => agregarLinea({ ...linea, origen: factura.origen || 'ocr' }));
    window.guardarRecepcionBorrador?.();
    const monedaAjena = factura.moneda && factura.moneda !== 'COP';
    mostrarEstado(`Detecté ${factura.lineas.length} producto(s) en el texto corregido. Revisa toda la información antes de guardar.`
      + (monedaAjena ? ` La factura indica ${factura.moneda}; convierte los importes a COP antes de confirmar.` : ''), monedaAjena ? 'error' : 'ok');
  };

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
      categoria: buscar('grupo farmacologico', 'grupo', 'categoria', 'categoría'),
      lote: buscar('lote', 'lot', 'batch', 'batch number'),
      codigoBarras: buscar('codigo de barras', 'codigo barras', 'ean', 'gtin', 'barcode', 'upc'),
      vencimiento: buscar('vencimiento', 'fecha vencimiento', 'expiry', 'expiration', 'expiry date'),
      proveedor: buscar('proveedor', 'supplier', 'vendor'),
      numero: buscar('factura', 'numero factura', 'invoice', 'invoice number', 'invoice id'),
      fecha: buscar('fecha factura', 'fecha', 'date', 'issue date', 'invoice date'),
      total: buscar('total factura', 'total', 'invoice total', 'payable amount'),
      moneda: buscar('moneda', 'currency', 'currency code')
    };
    if (columnas.nombre < 0 || columnas.cantidad < 0 || columnas.costo < 0) {
      throw new Error('faltan columnas de producto, cantidad o costo');
    }
    const lineasImportadas = filas.map(fila => ({
      nombre: celda(fila, columnas.nombre), cantidad: unidadesISO(celda(fila, columnas.cantidad)),
      costo: numeroISO(celda(fila, columnas.costo)), lote: celda(fila, columnas.lote), codigoBarras: celda(fila, columnas.codigoBarras),
      categoria: celda(fila, columnas.categoria),
      vencimiento: fechaISO(celda(fila, columnas.vencimiento))
    })).filter(fila => fila.nombre);
    return { proveedor: valorComun(filas, columnas.proveedor), numero: valorComun(filas, columnas.numero),
      fecha: valorComun(filas, columnas.fecha), total: valorComun(filas, columnas.total), moneda: valorComun(filas, columnas.moneda), lineas: lineasImportadas };
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
    let xml = new DOMParser().parseFromString(texto, 'application/xml');
    if (xml.querySelector('parsererror')) throw new Error('el XML no es válido');
    let lineasXML = [...xml.getElementsByTagName('*')].filter(n =>
      ['invoiceline', 'lineitem', 'itemline', 'detallefactura'].includes(normalizar(n.localName || n.nodeName)));
    // DIAN puede entregar un AttachedDocument que contiene la factura UBL como texto o base64.
    if (!lineasXML.length) {
      const descripciones = [...xml.getElementsByTagName('*')].filter(n => ['description','documentdescription'].includes(normalizar(n.localName || n.nodeName)));
      for (const nodo of descripciones) {
        const incrustado = nodo.textContent.trim();
        const candidatos = [incrustado];
        if (/^[A-Za-z0-9+/=\r\n]+$/.test(incrustado) && incrustado.length > 100) {
          try { candidatos.push(new TextDecoder().decode(Uint8Array.from(atob(incrustado.replace(/\s/g,'')), c => c.charCodeAt(0)))); } catch (error) { /* La descripción puede contener XML escapado y no base64. */ }
        }
        const facturaAnidada = candidatos.find(c => /<(?:\w+:)?Invoice\b/i.test(c));
        if (!facturaAnidada) continue;
        const anidado = new DOMParser().parseFromString(facturaAnidada, 'application/xml');
        if (anidado.querySelector('parsererror')) continue;
        const candidatas = [...anidado.getElementsByTagName('*')].filter(n => normalizar(n.localName || n.nodeName) === 'invoiceline');
        if (candidatas.length) { xml = anidado; lineasXML = candidatas; break; }
      }
    }
    if (!lineasXML.length) throw new Error('el XML no contiene renglones de factura reconocibles');
    const lineas = lineasXML.map(n => ({
      nombre: buscarTexto(n, ['description', 'itemname', 'name', 'descripcion', 'nombreproducto']),
      cantidad: unidadesISO(buscarTexto(n, ['invoicedquantity', 'quantity', 'qty', 'cantidad'])),
      costo: numeroISO(buscarTexto(n, ['priceamount', 'unitprice', 'unitcost', 'precio', 'costo'])),
      codigoBarras: buscarCodigoBarras(n),
      lote: buscarTexto(n, ['lotid', 'lotnumber', 'batchnumber', 'lote']),
      vencimiento: fechaISO(buscarTexto(n, ['expirydate', 'expirationdate', 'fechavencimiento']))
    })).filter(linea => linea.nombre);
    const proveedorNode = [...xml.getElementsByTagName('*')].find(n =>
      ['accountingsupplierparty', 'supplierparty', 'supplier'].includes(normalizar(n.localName || n.nodeName)));
    const proveedor = buscarTexto(proveedorNode || xml, ['suppliername', 'providername', 'nombreproveedor', 'registrationname', 'partyname', 'name']);
    const numero = buscarTexto(xml, ['invoicenumber', 'invoiceid', 'documentnumber', 'numeroFactura']) || buscarTexto(xml.documentElement, ['id']);
    const fecha = buscarTexto(xml, ['issuedate', 'invoicedate', 'fechaFactura', 'fecha']);
    const total = buscarTexto(xml, ['payableamount', 'taxinclusiveamount', 'invoicetotal', 'totalamount']);
    const moneda = buscarTexto(xml, ['documentcurrencycode', 'currencycode', 'moneda', 'currency']);
    return { proveedor, numero, fecha, total, moneda, lineas };
  }

  // Obtiene el primer texto no vacío que coincida con uno de los nombres de etiqueta.
  function buscarTexto(raiz, etiquetas) {
    const buscadas = etiquetas.map(normalizar);
    return [...raiz.getElementsByTagName('*')].find(n => buscadas.includes(normalizar(n.localName || n.nodeName)) && n.textContent.trim())?.textContent.trim() || '';
  }

  // Lee GTIN/EAN solo de identificadores estándar del artículo, sin confundirlos con el folio de factura.
  function buscarCodigoBarras(raiz) {
    return [...raiz.getElementsByTagName('*')].find(n => {
      const nombre = normalizar(n.localName || n.nodeName);
      const padre = normalizar(n.parentNode?.localName || n.parentNode?.nodeName);
      const valor = (n.textContent || '').replace(/\s/g, '');
      return nombre === 'id' && ['standarditemidentification','itemidentification'].includes(padre) && /^[0-9]{8,14}$/.test(valor);
    })?.textContent.trim() || '';
  }
  // Devuelve el primer valor repetido de una columna de cabecera del CSV.
  // Toma el primer dato disponible de una columna opcional del CSV.
  function valorComun(filas, indice) { return indice < 0 ? '' : (filas.find(f => celda(f, indice).trim()) || [])[indice] || ''; }
  // Lee una celda del CSV y la deja vacía cuando esa columna no existe.
  function celda(fila, indice) { return indice < 0 ? '' : (fila[indice] || '').trim(); }
  // Cuenta separadores en la primera fila para detectar el formato del CSV.
  function contar(texto, caracter) { return [...texto].filter(c => c === caracter).length; }
  // Convierte la cantidad importada a un entero que el inventario pueda guardar.
  function unidadesISO(valor) { const n = Number(numeroISO(valor)); return Number.isFinite(n) ? String(Math.round(n)) : ''; }
  // Interpreta puntos como separadores de miles cuando el documento está en formato local.
  function numeroOCR(valor) {
    let texto = String(valor ?? '').replace(/[^\d,.-]/g, '');
    if (texto.includes(',') && texto.includes('.')) return texto.lastIndexOf(',') > texto.lastIndexOf('.')
      ? texto.replace(/\./g, '').replace(',', '.') : texto.replace(/,/g, '');
    if (texto.includes(',')) return texto.replace(/\./g, '').replace(',', '.');
    if (/^\d{1,3}(?:\.\d{3})+$/.test(texto)) return texto.replace(/\./g, '');
    return /^-?\d+(?:\.\d+)?$/.test(texto) ? texto : '';
  }
})();
