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

/** Procesa la edición y anulación de ventas desde su historial. */
@WebServlet("/VentaServlet")
public class VentaServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Interpreta nombres de clientes con caracteres propios del español.
        request.setCharacterEncoding("UTF-8");
        String destino = request.getContextPath() + "/ventas.jsp";
        try {
            // Recupera el ID de la sesión para asociar la trazabilidad al usuario autenticado.
            var sesion = request.getSession(false);
            Object idSesion = sesion == null ? null : sesion.getAttribute("idUsuario");
            if (!(idSesion instanceof Number usuarioId)) throw new IllegalArgumentException("La sesión venció.");
            int ventaId = Integer.parseInt(request.getParameter("id"));
            MovimientoDAO dao = new MovimientoDAO();
            // Actualiza los datos de la venta o la elimina con reposición transaccional del stock.
            if ("editar".equals(request.getParameter("accion"))) {
                Date fecha = Date.valueOf(LocalDate.parse(request.getParameter("fecha")));
                String cliente = request.getParameter("cliente");
                int productoId = Integer.parseInt(request.getParameter("productoId"));
                int cantidad = Integer.parseInt(request.getParameter("cantidad"));
                String unidadVenta = request.getParameter("unidadVenta");
                String metodo = request.getParameter("metodo");
                dao.actualizarVenta(ventaId, fecha, cliente == null ? "" : cliente.trim(),
                        productoId, cantidad, unidadVenta, metodo, usuarioId.intValue());
                response.sendRedirect(destino + "?estado=editada");
            } else if ("eliminar".equals(request.getParameter("accion"))) {
                dao.eliminarVenta(ventaId, usuarioId.intValue());
                response.sendRedirect(destino + "?estado=eliminada");
            } else {
                throw new IllegalArgumentException("Acción no válida.");
            }
        } catch (Exception e) {
            // Deja el error detallado en Tomcat y muestra un aviso general en la pantalla.
            getServletContext().log("No se pudo modificar la venta", e);
            response.sendRedirect(destino + "?estado=error_edicion");
        }
    }
}
