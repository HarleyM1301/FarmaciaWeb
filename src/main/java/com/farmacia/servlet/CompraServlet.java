package com.farmacia.servlet;

import com.farmacia.dao.MovimientoDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;

/** Procesa la edición de cabecera y la anulación segura de facturas de compra. */
@WebServlet("/CompraServlet")
public class CompraServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Decodifica correctamente nombres y datos enviados desde los formularios en español.
        request.setCharacterEncoding("UTF-8");
        // Define el lugar común al que vuelve el usuario después de editar o anular una factura.
        String destino = request.getContextPath() + "/compras.jsp";
        try {
            // Recupera el usuario autenticado para dejarlo asociado al cambio de inventario.
            Object sesionId = request.getSession(false) == null ? null : request.getSession(false).getAttribute("idUsuario");
            if (!(sesionId instanceof Number)) throw new IllegalArgumentException("La sesión venció.");
            int usuarioId = ((Number) sesionId).intValue();
            // Lee el identificador de factura y la acción solicitada desde el formulario.
            int id = Integer.parseInt(request.getParameter("id"));
            String accion = request.getParameter("accion");
            MovimientoDAO dao = new MovimientoDAO();
            if ("editar".equals(accion)) {
                // Convierte los datos de cabecera a tipos que el DAO y MySQL puedan validar.
                Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
                String numero = request.getParameter("numeroFactura");
                double total = Double.parseDouble(request.getParameter("total"));
                String[] idsTexto = request.getParameterValues("detalleId");
                String[] cantidadesTexto = request.getParameterValues("cantidad");
                String[] costosTexto = request.getParameterValues("costo");
                // Evita guardar una cabecera editada sin sus líneas de producto.
                if (idsTexto == null || cantidadesTexto == null || costosTexto == null) {
                    throw new IllegalArgumentException("Faltan líneas de la factura.");
                }
                int[] detalleIds = java.util.Arrays.stream(idsTexto).mapToInt(Integer::parseInt).toArray();
                int[] cantidades = java.util.Arrays.stream(cantidadesTexto).mapToInt(Integer::parseInt).toArray();
                double[] costos = java.util.Arrays.stream(costosTexto).mapToDouble(Double::parseDouble).toArray();
                // El DAO ajusta en una transacción los detalles, el total y las existencias.
                dao.actualizarCompra(id, fecha, numero, total, detalleIds, cantidades, costos, usuarioId);
                // Informa al listado que la edición se completó correctamente.
                response.sendRedirect(destino + "?estado=editada");
            } else if ("eliminar".equals(accion)) {
                // El DAO revierte stock y elimina la factura solo si la reversión es segura.
                dao.eliminarCompra(id, usuarioId);
                response.sendRedirect(destino + "?estado=eliminada");
            } else {
                throw new IllegalArgumentException("Acción no válida.");
            }
        } catch (Exception e) {
            // Registra el detalle técnico en Tomcat y muestra un mensaje controlado en pantalla.
            getServletContext().log("No se pudo modificar la factura de compra", e);
            response.sendRedirect(destino + "?estado=error_edicion");
        }
    }
}
