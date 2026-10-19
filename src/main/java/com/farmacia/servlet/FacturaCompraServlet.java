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
            int compraId = Integer.parseInt(request.getParameter("id"));
            Path archivo = new MovimientoDAO().buscarArchivoCompra(compraId);
            if (archivo == null || !Files.isRegularFile(archivo)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró el archivo de la factura.");
                return;
            }
            String tipo = Files.probeContentType(archivo);
            response.setContentType(tipo == null ? "application/octet-stream" : tipo);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "attachment; filename=\"factura-compra-" + compraId + archivo.getFileName().toString().replaceAll("^.*(\\.[^.]+)$", "$1") + "\"");
            response.setContentLengthLong(Files.size(archivo));
            try (var salida = response.getOutputStream()) { Files.copy(archivo, salida); }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "El identificador de compra no es válido.");
        } catch (Exception e) {
            getServletContext().log("No se pudo descargar el archivo de la factura", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
