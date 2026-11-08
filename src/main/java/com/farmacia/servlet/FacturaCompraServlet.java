package com.farmacia.servlet;

import com.farmacia.dao.MovimientoDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Entrega al personal autorizado el archivo original adjunto a una compra. */
@WebServlet("/FacturaCompraServlet")
public class FacturaCompraServlet extends HttpServlet {

    /** Descarga una factura guardada sin exponer la carpeta privada del servidor. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Lee el identificador de compra que envía el enlace de descarga.
            int compraId = Integer.parseInt(request.getParameter("id"));
            // Busca la ruta privada del archivo asociada a esa compra.
            Path archivo = new MovimientoDAO().buscarArchivoCompra(compraId);
            // Devuelve 404 si no existe un archivo válido para la factura.
            if (archivo == null || !Files.isRegularFile(archivo)) {
                // Informa que no hay un archivo válido que pueda descargarse.
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró el archivo de la factura.");
                return;
            }
            // Detecta el tipo de archivo y usa uno genérico si no puede identificarlo.
            // Detecta el tipo MIME del archivo para que el navegador lo reciba correctamente.
            String tipo = Files.probeContentType(archivo);
            response.setContentType(tipo == null ? "application/octet-stream" : tipo);
            // Envía un nombre de descarga y evita que el navegador interprete el contenido.
            // Evita que el navegador intente interpretar la factura como contenido activo.
            response.setHeader("X-Content-Type-Options", "nosniff");
            // Construye un nombre de descarga controlado y conserva la extensión del archivo.
            response.setHeader("Content-Disposition", "attachment; filename=\"factura-compra-" + compraId + archivo.getFileName().toString().replaceAll("^.*(\\.[^.]+)$", "$1") + "\"");
            // Envía el tamaño del archivo antes de copiarlo al flujo HTTP.
            response.setContentLengthLong(Files.size(archivo));
            // Copia el archivo al flujo HTTP y libera el flujo al terminar.
            try (var salida = response.getOutputStream()) { Files.copy(archivo, salida); }
        } catch (NumberFormatException e) {
            // Informa que el enlace contiene un identificador que no es numérico.
            // Devuelve HTTP 400 para señalar que el enlace de descarga está mal formado.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "El identificador de compra no es válido.");
        } catch (Exception e) {
            // Registra el fallo y responde sin revelar rutas internas del servidor.
            getServletContext().log("No se pudo descargar el archivo de la factura", e);
            // Devuelve HTTP 500 sin exponer al navegador rutas privadas ni detalles internos.
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
