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
        request.setCharacterEncoding("UTF-8");
        String tipo = request.getParameter("tipo");
        String pagina = "compra".equals(tipo) ? "compras.jsp" : "ventas.jsp";
        String destino = request.getContextPath() + "/" + pagina;
        try {
            var sesion = request.getSession(false);
            Object sesionId = sesion == null ? null : sesion.getAttribute("idUsuario");
            if (!(sesionId instanceof Number)) throw new IllegalArgumentException("La sesión venció; inicia sesión de nuevo.");
            int usuarioId = ((Number) sesionId).intValue();
            MovimientoDAO dao = new MovimientoDAO();

            if ("venta".equals(tipo)) {
                guardarVenta(request, dao, usuarioId);
            } else if ("compra".equals(tipo)) {
                guardarCompra(request, dao, usuarioId);
            } else {
                throw new IllegalArgumentException("Tipo de operación inválido.");
            }
            response.sendRedirect(destino + "?estado=ok");
        } catch (Exception e) {
            getServletContext().log("No se pudo guardar la operación de farmacia", e);
            response.sendRedirect(destino + "?estado=error");
        }
    }

    /** Procesa la venta de un artículo y su método de pago. */
    private void guardarVenta(HttpServletRequest request, MovimientoDAO dao, int usuarioId) throws Exception {
        Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
        int medicamento = Integer.parseInt(request.getParameter("medicamentoId"));
        int cantidad = Integer.parseInt(request.getParameter("cantidad"));
        if (cantidad < 1) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        String metodo = request.getParameter("metodo");
        if (!("Efectivo".equals(metodo) || "Tarjeta".equals(metodo) || "Transferencia".equals(metodo))) {
            throw new IllegalArgumentException("Método de pago inválido.");
        }
        String cliente = limpiar(request.getParameter("cliente"));
        if (cliente.length() > 100) throw new IllegalArgumentException("El nombre del cliente es demasiado largo.");
        dao.registrarVenta(fecha, cliente, medicamento, cantidad, metodo, usuarioId);
    }

    /** Valida todas las líneas de la factura y guarda el archivo original cuando se adjunta. */
    private void guardarCompra(HttpServletRequest request, MovimientoDAO dao, int usuarioId) throws Exception {
        Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
        String proveedor = limpiar(request.getParameter("proveedor"));
        String numeroFactura = limpiar(request.getParameter("numeroFactura"));
        String totalIngresado = limpiar(request.getParameter("totalFactura"));
        Double totalFactura = totalIngresado.isBlank() ? null : Double.valueOf(totalIngresado);
        if (proveedor.isBlank() || proveedor.length() > 100 || numeroFactura.isBlank() || numeroFactura.length() > 60) {
            throw new IllegalArgumentException("Completa el proveedor y el número de factura.");
        }

        String[] productos = request.getParameterValues("productoId");
        String[] nombres = request.getParameterValues("nombreNuevo");
        String[] preciosVenta = request.getParameterValues("precioVenta");
        String[] lotes = request.getParameterValues("lote");
        String[] vencimientos = request.getParameterValues("vencimiento");
        String[] cantidades = request.getParameterValues("cantidad");
        String[] costos = request.getParameterValues("costo");
        if (productos == null || productos.length == 0 || productos.length > 50
                || !mismoTamanio(productos.length, nombres, preciosVenta, lotes, vencimientos, cantidades, costos)) {
            throw new IllegalArgumentException("La factura debe tener entre 1 y 50 líneas completas.");
        }

        List<LineaCompra> lineas = new ArrayList<>();
        for (int i = 0; i < productos.length; i++) {
            LineaCompra linea = new LineaCompra();
            boolean nuevo = "nuevo".equals(productos[i]);
            linea.setProductoId(nuevo ? 0 : Integer.parseInt(productos[i]));
            linea.setNombreNuevo(limpiar(nombres[i]));
            linea.setPrecioVenta(decimalOpcional(preciosVenta[i]));
            linea.setLote(limpiar(lotes[i]));
            linea.setVencimiento(vencimientos[i] == null || vencimientos[i].isBlank()
                    ? null : Date.valueOf(LocalDate.parse(vencimientos[i])));
            linea.setCantidad(Integer.parseInt(cantidades[i]));
            linea.setCosto(Double.parseDouble(costos[i]));
            lineas.add(linea);
        }

        Path adjunto = guardarAdjunto(request.getPart("factura"));
        String rutaAdjunto = adjunto == null ? null : adjunto.toAbsolutePath().toString();
        if (rutaAdjunto != null && rutaAdjunto.length() > 255) {
            Files.deleteIfExists(adjunto);
            throw new IllegalArgumentException("La ruta del archivo es demasiado larga para guardarla.");
        }
        try {
            dao.registrarCompra(fecha, proveedor, numeroFactura, rutaAdjunto, totalFactura, lineas, usuarioId);
        } catch (Exception e) {
            if (adjunto != null) Files.deleteIfExists(adjunto);
            throw e;
        }
    }

    /** Guarda el original fuera de la carpeta pública del sitio, con nombre generado. */
    private Path guardarAdjunto(Part parte) throws IOException {
        if (parte == null || parte.getSize() == 0) return null;
        String enviado = parte.getSubmittedFileName();
        String extension = "";
        if (enviado != null && enviado.lastIndexOf('.') >= 0) {
            extension = enviado.substring(enviado.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
        }
        List<String> permitidas = List.of(".pdf", ".xml", ".csv", ".txt", ".xlsx", ".xls", ".jpg", ".jpeg", ".png");
        if (!permitidas.contains(extension)) throw new IllegalArgumentException("Formato no admitido. Usa PDF, XML, Excel, CSV o imagen.");
        Path carpeta = Path.of(System.getProperty("user.home"), "FarmaciaWeb", "facturas").toAbsolutePath().normalize();
        Files.createDirectories(carpeta);
        Path archivo = carpeta.resolve(UUID.randomUUID() + extension).normalize();
        if (!archivo.startsWith(carpeta)) throw new IllegalArgumentException("Nombre de archivo no válido.");
        try (var entrada = parte.getInputStream()) { Files.copy(entrada, archivo); }
        return archivo;
    }

    /** Comprueba que el navegador envió el mismo número de valores para todas las columnas. */
    private boolean mismoTamanio(int esperado, String[]... columnas) {
        for (String[] columna : columnas) if (columna == null || columna.length != esperado) return false;
        return true;
    }

    /** Convierte un precio de venta opcional sin forzar el campo en productos existentes. */
    private double decimalOpcional(String valor) {
        return valor == null || valor.isBlank() ? 0 : Double.parseDouble(valor);
    }

    /** Recorta entradas de texto que pueden quedar vacías. */
    private String limpiar(String valor) { return valor == null ? "" : valor.trim(); }
}
