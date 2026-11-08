package com.farmacia.servlet;

import com.farmacia.dao.MovimientoDAO;
import com.farmacia.modelo.LineaCompra;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Recibe ventas y facturas de compra con varias líneas de recepción técnica. */
@WebServlet("/MovimientoServlet")
@MultipartConfig(maxFileSize = 10 * 1024 * 1024, maxRequestSize = 20 * 1024 * 1024)
public class MovimientoServlet extends HttpServlet {

    /** Valida la petición y guarda todo el movimiento en una transacción. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Interpreta correctamente nombres y textos con acentos enviados por los formularios.
        request.setCharacterEncoding("UTF-8");
        // Lee el tipo para decidir si se procesa una compra o una venta.
        String tipo = request.getParameter("tipo");
        // Elige la página a la que se regresará después de guardar el movimiento.
        String pagina = "compra".equals(tipo) ? "compras.jsp" : "ventas.jsp";
        // Construye la ruta de regreso dentro del contexto de esta aplicación.
        String destino = request.getContextPath() + "/" + pagina;
        try {
            // Recupera la sesión existente sin crear otra si ya venció.
            var sesion = request.getSession(false);
            // Obtiene el ID de quien inició sesión para asociarlo a la operación.
            Object sesionId = sesion == null ? null : sesion.getAttribute("idUsuario");
            // Rechaza la petición si no hay una cuenta autenticada válida.
            if (!(sesionId instanceof Number)) throw new IllegalArgumentException("La sesión venció; inicia sesión de nuevo.");
            // Convierte el ID guardado en sesión al tipo usado por la base de datos.
            int usuarioId = ((Number) sesionId).intValue();
            // Crea el DAO que guardará cabeceras, detalles y existencias.
            MovimientoDAO dao = new MovimientoDAO();

            if ("venta".equals(tipo)) {
                // Valida y registra el flujo de salida de inventario por venta.
                guardarVenta(request, dao, usuarioId);
            } else if ("compra".equals(tipo)) {
                // Valida y registra la recepción de factura y sus productos/lotes.
                guardarCompra(request, dao, usuarioId);
            } else {
                // Detiene la petición para no procesar un tipo de movimiento desconocido.
                throw new IllegalArgumentException("Tipo de operación inválido.");
            }
            // Devuelve al usuario a la lista del módulo con confirmación de éxito.
            response.sendRedirect(destino + "?estado=ok");
        } catch (Exception e) {
            // Registra el error completo en Tomcat para facilitar el diagnóstico.
            getServletContext().log("No se pudo guardar la operación de farmacia", e);
            // Muestra un resultado genérico sin exponer el detalle técnico en la página.
            response.sendRedirect(destino + "?estado=error");
        }
    }

    /** Procesa la venta de un artículo y su método de pago. */
    private void guardarVenta(HttpServletRequest request, MovimientoDAO dao, int usuarioId) throws Exception {
        // Convierte la fecha recibida del formulario a un tipo SQL.
        Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
        // Lee el ID del medicamento seleccionado para vender.
        int medicamento = Integer.parseInt(request.getParameter("medicamentoId"));
        // Convierte la cantidad solicitada a unidades enteras.
        int cantidad = Integer.parseInt(request.getParameter("cantidad"));
        String unidadVenta = request.getParameter("unidadVenta");
        // Evita aceptar una venta sin unidades o con cantidad negativa.
        if (cantidad < 1) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        // Lee la forma de pago seleccionada en el formulario.
        String metodo = request.getParameter("metodo");
        // Permite solo los métodos de pago reconocidos por el sistema.
        if (!("Efectivo".equals(metodo) || "Tarjeta".equals(metodo) || "Transferencia".equals(metodo))) {
            throw new IllegalArgumentException("Método de pago inválido.");
        }
        // Normaliza el nombre del cliente antes de enviarlo al DAO.
        String cliente = limpiar(request.getParameter("cliente"));
        // Limita el tamaño del nombre según el campo de almacenamiento.
        if (cliente.length() > 100) throw new IllegalArgumentException("El nombre del cliente es demasiado largo.");
        // Ejecuta el guardado transaccional de cabecera, detalle, stock y trazabilidad.
        dao.registrarVenta(fecha, cliente, medicamento, cantidad, unidadVenta, metodo, usuarioId);
    }

    /** Valida todas las líneas de la factura y guarda el archivo original cuando se adjunta. */
    private void guardarCompra(HttpServletRequest request, MovimientoDAO dao, int usuarioId) throws Exception {
        // Convierte la fecha del formulario al tipo SQL que se almacena en compras.
        Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
        // Limpia el nombre del proveedor y el número de factura.
        String proveedor = limpiar(request.getParameter("proveedor"));
        String numeroFactura = limpiar(request.getParameter("numeroFactura"));
        String totalIngresado = limpiar(request.getParameter("totalFactura"));
        // Acepta una factura sin total informado o convierte el total capturado a número.
        Double totalFactura = totalIngresado.isBlank() ? null : Double.valueOf(totalIngresado);
        // Exige proveedor y número para identificar y evitar duplicar la factura.
        if (proveedor.isBlank() || proveedor.length() > 100 || numeroFactura.isBlank() || numeroFactura.length() > 60) {
            throw new IllegalArgumentException("Completa el proveedor y el número de factura.");
        }

        // Lee cada columna repetida enviada por las filas dinámicas del formulario.
        String[] productos = request.getParameterValues("productoId");
        String[] nombres = request.getParameterValues("nombreNuevo");
        String[] categorias = request.getParameterValues("categoriaNueva");
        String[] preciosVenta = request.getParameterValues("precioVenta");
        String[] actualizarPrecios = request.getParameterValues("actualizarPrecioVenta");
        String[] lotes = request.getParameterValues("lote");
        String[] vencimientos = request.getParameterValues("vencimiento");
        String[] cantidades = request.getParameterValues("cantidad");
        String[] costos = request.getParameterValues("costo");
        String[] unidades = request.getParameterValues("unidadBase");
        String[] factores = request.getParameterValues("factorEmpaque");
        String[] factoresBlister = request.getParameterValues("unidadesPorBlister");
        String[] catalogos = request.getParameterValues("catalogoMedicamentoId");
        String[] registrosInvima = request.getParameterValues("registroInvima");
        // Confirma que haya filas y que todas las columnas tengan la misma cantidad de valores.
        if (productos == null || productos.length == 0 || productos.length > 500
                || !mismoTamanio(productos.length, nombres, categorias, preciosVenta, actualizarPrecios, lotes, vencimientos, cantidades, costos, unidades, factores, factoresBlister, catalogos, registrosInvima)) {
            throw new IllegalArgumentException("La factura debe tener entre 1 y 500 líneas completas.");
        }

        // Acumula las líneas validadas para pasarlas juntas al DAO transaccional.
        List<LineaCompra> lineas = new ArrayList<>();
        // Convierte cada fila del formulario a un objeto de dominio LineaCompra.
        for (int i = 0; i < productos.length; i++) {
            // Crea el objeto que representa la fila actual de la factura.
            LineaCompra linea = new LineaCompra();
            // Determina si la línea selecciona un producto existente o uno nuevo.
            boolean nuevo = "nuevo".equals(productos[i]);
            // Usa cero como ID cuando el producto se creará al confirmar la compra.
            linea.setProductoId(nuevo ? 0 : Integer.parseInt(productos[i]));
            // Copia nombre, precio de venta y lote tras quitar espacios exteriores.
            linea.setNombreNuevo(limpiar(nombres[i]));
            linea.setCategoria(limpiar(categorias[i]));
            linea.setPrecioVenta(decimalOpcional(preciosVenta[i]));
            linea.setActualizarPrecioVenta("true".equalsIgnoreCase(actualizarPrecios[i]));
            linea.setLote(limpiar(lotes[i]));
            // Convierte el vencimiento capturado o deja el valor vacío como NULL.
            linea.setVencimiento(vencimientos[i] == null || vencimientos[i].isBlank()
                    ? null : Date.valueOf(LocalDate.parse(vencimientos[i])));
            // Lee cuántas unidades base contiene cada caja o paquete.
            int factor = Integer.parseInt(factores[i]);
            // Lee la cantidad de presentaciones que aparecen en la factura.
            int cantidadPresentaciones = Integer.parseInt(cantidades[i]);
            // Evita factores nulos o una multiplicación que produzca un stock desmesurado.
            if (factor < 1 || (long) factor * cantidadPresentaciones > 10000000) {
                throw new IllegalArgumentException("Revisa la cantidad y el contenido de cada caja o paquete.");
            }
            // Convierte paquetes o cajas a la cantidad real de unidades base recibidas.
            linea.setCantidad(factor * cantidadPresentaciones);
            // Convierte el costo por presentación a costo por unidad mínima.
            linea.setCosto(Double.parseDouble(costos[i]) / factor);
            // Guarda unidad base y factor para comprobar la ficha del producto en el DAO.
            linea.setUnidadBase(unidades[i]);
            linea.setUnidadesPorPresentacion(factor);
            linea.setUnidadesPorBlister(Integer.parseInt(factoresBlister[i]));
            String idCatalogo = limpiar(catalogos[i]);
            linea.setCatalogoMedicamentoId(idCatalogo.isBlank() ? null : Long.valueOf(idCatalogo));
            linea.setRegistroInvima(limpiar(registrosInvima[i]));
            // Añade la línea preparada a la colección de recepción.
            lineas.add(linea);
        }

        // Exige confirmación explícita si el total de productos no coincide con el total impreso.
        double totalCalculado = lineas.stream().mapToDouble(linea -> linea.getCosto() * linea.getCantidad()).sum();
        boolean diferenciaConfirmada = Boolean.parseBoolean(request.getParameter("diferenciaConfirmada"));
        if (totalFactura != null && Math.abs(totalFactura - totalCalculado) > 1.0 && !diferenciaConfirmada) {
            throw new IllegalArgumentException("El total de la factura difiere de las líneas; revísalo y confirma la diferencia.");
        }

        // Guarda de forma privada el archivo original adjunto, si se envió uno.
        Path adjunto = guardarAdjunto(request.getPart("factura"));
        // Obtiene la ruta normalizada que se almacenará junto con la cabecera de compra.
        String rutaAdjunto = adjunto == null ? null : adjunto.toAbsolutePath().toString();
        // Rechaza rutas demasiado largas para el campo de base de datos y borra el adjunto.
        if (rutaAdjunto != null && rutaAdjunto.length() > 255) {
            Files.deleteIfExists(adjunto);
            throw new IllegalArgumentException("La ruta del archivo es demasiado larga para guardarla.");
        }
        try {
            // Registra factura, líneas, stock y trazabilidad en una sola transacción.
            dao.registrarCompra(fecha, proveedor, numeroFactura, rutaAdjunto, totalFactura, lineas, usuarioId);
        } catch (Exception e) {
            // Borra el archivo si no se pudo guardar la compra que lo referencia.
            if (adjunto != null) Files.deleteIfExists(adjunto);
            // Propaga el fallo para que doPost registre el error y devuelva su estado.
            throw e;
        }
    }

    /** Guarda el original fuera de la carpeta pública del sitio, con nombre generado. */
    private Path guardarAdjunto(Part parte) throws IOException {
        // Ignora el campo cuando no se adjuntó un archivo.
        if (parte == null || parte.getSize() == 0) return null;
        // Lee el nombre original solo para determinar la extensión declarada.
        String enviado = parte.getSubmittedFileName();
        // Inicializa una extensión vacía para archivos sin nombre reconocible.
        String extension = "";
        if (enviado != null && enviado.lastIndexOf('.') >= 0) {
            extension = enviado.substring(enviado.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
        }
        // Define las extensiones de documentos e imágenes admitidas en una recepción.
        List<String> permitidas = List.of(".pdf", ".xml", ".csv", ".txt", ".xlsx", ".xls", ".jpg", ".jpeg", ".png");
        // Rechaza formatos que no estén incluidos en la lista permitida.
        if (!permitidas.contains(extension)) throw new IllegalArgumentException("Formato no admitido. Usa PDF, XML, Excel, CSV o imagen.");
        // Resuelve el directorio privado de facturas fuera del contenido web público.
        Path carpeta = Path.of(System.getProperty("user.home"), "FarmaciaWeb", "facturas").toAbsolutePath().normalize();
        // Crea la carpeta si es la primera factura que se guarda en el equipo.
        Files.createDirectories(carpeta);
        // Genera un nombre aleatorio para no confiar en el nombre proporcionado por el cliente.
        Path archivo = carpeta.resolve(UUID.randomUUID() + extension).normalize();
        // Comprueba que la ruta final permanezca dentro del directorio privado.
        if (!archivo.startsWith(carpeta)) throw new IllegalArgumentException("Nombre de archivo no válido.");
        // Copia el contenido recibido y cierra el flujo al terminar.
        try (var entrada = parte.getInputStream()) { Files.copy(entrada, archivo); }
        // Devuelve la ruta privada para asociarla a la factura.
        return archivo;
    }

    /** Comprueba que el navegador envió el mismo número de valores para todas las columnas. */
    private boolean mismoTamanio(int esperado, String[]... columnas) {
        // Rechaza columnas faltantes o con una cantidad distinta de filas.
        for (String[] columna : columnas) if (columna == null || columna.length != esperado) return false;
        // Confirma que todas las columnas tienen el número de elementos esperado.
        return true;
    }

    /** Convierte un precio de venta opcional sin forzar el campo en productos existentes. */
    private double decimalOpcional(String valor) {
        // Devuelve cero si el campo no se capturó y convierte el texto cuando sí existe.
        return valor == null || valor.isBlank() ? 0 : Double.parseDouble(valor);
    }

    /** Recorta entradas de texto que pueden quedar vacías. */
    // Convierte el parámetro ausente en texto vacío y elimina espacios laterales.
    private String limpiar(String valor) { return valor == null ? "" : valor.trim(); }
}
