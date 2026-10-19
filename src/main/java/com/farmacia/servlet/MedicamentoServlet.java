package com.farmacia.servlet;

import com.farmacia.dao.MedicamentoDAO;
import com.farmacia.modelo.medicamento;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/MedicamentoServlet")
/** Procesa altas, modificaciones y eliminaciones del inventario. */
public class MedicamentoServlet extends HttpServlet {

    /** Valida el formulario y guarda el cambio solicitado por POST. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Identifica si el formulario solicita guardar o eliminar.
            String accion = request.getParameter("accion");
            MedicamentoDAO dao = new MedicamentoDAO();
            String destino = request.getContextPath() + "/medicamentos.jsp";

            if ("eliminar".equals(accion)) {
                int id = Integer.parseInt(request.getParameter("id"));
                response.sendRedirect(destino + (dao.eliminar(id) ? "?deleted=1" : "?error=1"));
                return;
            }

            // Comprueba y normaliza la entrada antes de guardarla.
            String nombre = request.getParameter("nombre");
            String lote = request.getParameter("lote");
            String fecha = request.getParameter("fecha");
            int stock = Integer.parseInt(request.getParameter("stock"));
            double precio = Double.parseDouble(request.getParameter("precio"));
            if (nombre == null || nombre.isBlank() || lote == null || lote.isBlank()
                    || fecha == null || fecha.isBlank() || stock < 0
                    || !Double.isFinite(precio) || precio < 0) {
                response.sendRedirect(destino + "?error=1");
                return;
            }

            medicamento m = new medicamento();
            m.setNombre(nombre);
            m.setLote(lote);
            m.setFechaVencimiento(fecha);
            m.setStock(stock);
            m.setPrecio(precio);

            String id = request.getParameter("id");
            boolean guardado = id == null || id.isBlank()
                    ? dao.insertar(m)
                    : actualizarConId(dao, m, id);

            response.sendRedirect(destino + (guardado ? "?success=1" : "?error=1"));
        } catch (Exception e) {
            getServletContext().log("No se pudo guardar el medicamento", e);
            response.sendRedirect(request.getContextPath() + "/medicamentos.jsp?error=1");
        }
    }

    // Completa la edición con el ID oculto que envía el formulario modal.
    private boolean actualizarConId(MedicamentoDAO dao, medicamento m, String id) {
        m.setIdMedicamento(Integer.parseInt(id));
        return dao.actualizar(m);
    }
}
